package com.haohui.temperature_and_humidity

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.haohui.temperature_and_humidity.databinding.FragmentFirstBinding
import com.haohui.temperature_and_humidity.measurement.AndroidAcousticEstimator
import com.haohui.temperature_and_humidity.measurement.AndroidThermalEstimator
import com.haohui.temperature_and_humidity.measurement.MeasurementService
import com.haohui.temperature_and_humidity.model.MeasurementResult
import com.haohui.temperature_and_humidity.model.MeasurementSessionState
import com.haohui.temperature_and_humidity.model.ReportSaveResult
import com.haohui.temperature_and_humidity.privacy.PrivacyMessages
import com.haohui.temperature_and_humidity.reporting.DraftResult
import com.haohui.temperature_and_humidity.reporting.ReportFactory
import com.haohui.temperature_and_humidity.storage.LocalReportStore
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val reportFactory = ReportFactory()
    private var latestMeasurement: MeasurementResult? = null
    private lateinit var reportStore: LocalReportStore

    private val microphonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        runMeasurement(allowAcoustic = granted)
    }

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val pointName = matches?.firstOrNull().orEmpty()
            if (pointName.isNotBlank()) {
                binding.editPointName.setText(pointName)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        reportStore = LocalReportStore(requireContext().applicationContext)

        binding.buttonMeasure.setOnClickListener {
            startMeasurementWithPermission()
        }
        binding.buttonVoicePoint.setOnClickListener {
            startPointSpeechInput()
        }
        binding.buttonSaveReport.setOnClickListener {
            saveReport()
        }
        binding.buttonHistory.setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
        }
    }

    private fun startMeasurementWithPermission() {
        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            runMeasurement(allowAcoustic = true)
        } else {
            AlertDialog.Builder(requireContext())
                .setTitle("麦克风权限")
                .setMessage(PrivacyMessages.MICROPHONE_RATIONALE)
                .setPositiveButton("授权并测量") { _, _ ->
                    microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
                .setNegativeButton("使用降级模式") { _, _ ->
                    runMeasurement(allowAcoustic = false)
                }
                .show()
        }
    }

    private fun runMeasurement(allowAcoustic: Boolean) {
        setMeasurementControlsEnabled(false)
        updateStatus(if (allowAcoustic) "正在测量，请稍候..." else PrivacyMessages.MICROPHONE_DENIED)
        executor.execute {
            val service = MeasurementService(
                acousticEstimator = AndroidAcousticEstimator(requireContext().applicationContext),
                thermalEstimator = AndroidThermalEstimator(requireContext().applicationContext)
            )
            val state = service.runMeasurement(allowAcoustic)
            requireActivity().runOnUiThread {
                setMeasurementControlsEnabled(true)
                when (state) {
                    is MeasurementSessionState.Completed -> showMeasurement(state.result)
                    is MeasurementSessionState.Failed -> {
                        latestMeasurement = null
                        updateStatus(state.message)
                        showSnackbar(state.message)
                    }
                    MeasurementSessionState.Idle -> updateStatus(getString(R.string.status_idle))
                    is MeasurementSessionState.Running -> updateStatus(state.message)
                }
            }
        }
    }

    private fun showMeasurement(result: MeasurementResult) {
        latestMeasurement = result
        binding.textTemperature.text = result.displayTemperature()
        binding.textHumidity.text = result.displayHumidity()
        binding.textConfidence.text = "置信度：${result.displayConfidence()}"
        binding.textSource.text = "来源：${result.sourceSummary}"
        binding.textQuality.text = "质控：${result.quality.message.ifBlank { "质控通过" }}"
        val status = if (result.isDegraded) {
            "测量完成（降级）：${result.degradationReason?.userMessage.orEmpty()}"
        } else {
            "测量完成，可确认点位并保存。"
        }
        updateStatus(status)
    }

    private fun startPointSpeechInput() {
        AlertDialog.Builder(requireContext())
            .setTitle("点位语音识别")
            .setMessage(PrivacyMessages.POINT_SPEECH_RATIONALE)
            .setPositiveButton("开始识别") { _, _ ->
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINA.toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                try {
                    speechLauncher.launch(intent)
                } catch (_: ActivityNotFoundException) {
                    showSnackbar("当前设备不支持语音识别，请手动输入点位")
                }
            }
            .setNegativeButton("手动输入", null)
            .show()
    }

    private fun saveReport() {
        val measurement = latestMeasurement
        if (measurement == null) {
            showSnackbar("请先完成一次有效测量")
            return
        }
        when (val draft = reportFactory.createDraft(binding.editPointName.text.toString(), measurement)) {
            is DraftResult.Failure -> showSnackbar(draft.error.userMessage)
            is DraftResult.Success -> when (val result = reportStore.saveDraft(draft.draft)) {
                is ReportSaveResult.Failure -> showSnackbar(result.error.userMessage)
                is ReportSaveResult.Success -> {
                    showSnackbar("报告已保存，可到报告历史复制")
                    updateStatus("已保存报告：${result.record.pointName}")
                }
            }
        }
    }

    private fun setMeasurementControlsEnabled(enabled: Boolean) {
        binding.buttonMeasure.isEnabled = enabled
        binding.buttonSaveReport.isEnabled = enabled
        binding.buttonVoicePoint.isEnabled = enabled
        binding.buttonHistory.isEnabled = enabled
    }

    private fun updateStatus(message: String) {
        binding.textStatus.text = message
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
    }
}
