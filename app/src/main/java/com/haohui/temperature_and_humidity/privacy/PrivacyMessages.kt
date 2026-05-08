package com.haohui.temperature_and_humidity.privacy

object PrivacyMessages {
    const val MICROPHONE_RATIONALE = "本地采集短时音频用于估算环境温湿度，音频只在内存处理，处理后立即清理，不上传原始音频。"
    const val MICROPHONE_DENIED = "未授权麦克风，将使用热特征降级模式；该结果为未标定演示估算，置信度可能降低。"
    const val POINT_SPEECH_RATIONALE = "点位语音识别仅用于填写点位名称；也可以跳过并手动输入。"
}
