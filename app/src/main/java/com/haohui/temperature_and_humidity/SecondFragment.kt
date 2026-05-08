package com.haohui.temperature_and_humidity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.haohui.temperature_and_humidity.databinding.FragmentSecondBinding
import com.haohui.temperature_and_humidity.model.ReportRecord
import com.haohui.temperature_and_humidity.reporting.CopyTextBuilder
import com.haohui.temperature_and_humidity.reporting.NetworkDemoClient
import com.haohui.temperature_and_humidity.storage.LocalReportStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SecondFragment : Fragment() {

    private var _binding: FragmentSecondBinding? = null
    private val binding get() = _binding!!
    private lateinit var reportStore: LocalReportStore
    private val copyTextBuilder = CopyTextBuilder()
    private val networkDemoClient = NetworkDemoClient()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
    private var latestRecord: ReportRecord? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentSecondBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        reportStore = LocalReportStore(requireContext().applicationContext)

        binding.buttonSecond.setOnClickListener {
            findNavController().navigate(R.id.action_SecondFragment_to_FirstFragment)
        }
        binding.buttonCopyLatest.setOnClickListener {
            copyLatestReport()
        }
        binding.buttonNetworkDemo.setOnClickListener {
            confirmNetworkDemo()
        }
        renderHistory()
    }

    private fun renderHistory() {
        val records = reportStore.list()
        latestRecord = records.firstOrNull()
        if (records.isEmpty()) {
            binding.textHistory.setText(R.string.history_empty)
            binding.textCopyPreview.setText(R.string.copy_preview_empty)
            binding.buttonCopyLatest.isEnabled = false
            binding.buttonNetworkDemo.isEnabled = false
            binding.textNetworkDemo.setText(R.string.network_demo_empty)
            return
        }

        binding.buttonCopyLatest.isEnabled = true
        binding.buttonNetworkDemo.isEnabled = true
        binding.textHistory.text = records.joinToString("\n\n") { record ->
            val time = dateFormat.format(Date(record.createdAtMillis))
            val network = record.networkDemoSummary.ifBlank { record.networkDemoStatus.label }
            "${record.pointName}  ${record.displayTemperature()}  ${record.displayHumidity()}  ${record.displayPressure()}\n$time  ${record.status.label}  $network"
        }
        latestRecord?.let {
            binding.textCopyPreview.text = copyTextBuilder.build(it).content
            binding.textNetworkDemo.text = it.networkDemoSummary.ifBlank { getString(R.string.network_demo_empty) }
        }
    }

    private fun copyLatestReport() {
        val record = latestRecord ?: return
        val copyText = copyTextBuilder.build(record)
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("疾控温湿度报告", copyText.content))
        reportStore.markCopied(record.id)
        Snackbar.make(binding.root, "已复制最近一条报告", Snackbar.LENGTH_LONG).show()
        renderHistory()
    }

    private fun confirmNetworkDemo() {
        val record = latestRecord ?: return
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("网络演示")
            .setMessage("将向 httpbin 发送温湿度和气压演示 payload，不发送明文点位、音频或真实 token；这不代表提交疾控系统。")
            .setPositiveButton("开始演示") { _, _ ->
                runNetworkDemo(record)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun runNetworkDemo(record: ReportRecord) {
        binding.buttonNetworkDemo.isEnabled = false
        binding.textNetworkDemo.text = "网络演示处理中..."
        executor.execute {
            val log = networkDemoClient.postReport(record)
            reportStore.saveNetworkDemoLog(log)
            requireActivity().runOnUiThread {
                Snackbar.make(binding.root, log.displaySummary(), Snackbar.LENGTH_LONG).show()
                renderHistory()
            }
        }
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
