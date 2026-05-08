## MODIFIED Requirements

### Requirement: 麦克风权限说明
系统 SHALL 在请求麦克风权限前说明权限用途、音频处理范围和拒绝后的降级行为。

#### Scenario: 首次请求麦克风权限
- **WHEN** 用户首次启动需要声学估算的测量
- **THEN** 系统 SHALL 展示麦克风权限用途说明，并在用户确认后请求系统权限

#### Scenario: 用户拒绝麦克风权限
- **WHEN** 用户拒绝麦克风权限
- **THEN** 系统 SHALL 进入不依赖麦克风的热特征降级测量路径，并说明该模式为未标定演示估算且置信度可能降低

### Requirement: 音频本地处理
系统 SHALL 将温湿度测量所需音频限制为本地内存处理，并 SHALL NOT 上传、持久化或外部分享原始测量音频。

#### Scenario: 测量音频采集完成
- **WHEN** 系统完成声学估算或声学估算失败
- **THEN** 系统 SHALL 清理测量音频缓冲区、左右声道缓冲区和临时分析缓冲区，并释放相关音频采集资源

#### Scenario: 保存报告
- **WHEN** 系统保存本地报告
- **THEN** 系统 SHALL NOT 将原始音频或可还原原始音频的数据写入本地报告

#### Scenario: 网络演示
- **WHEN** 系统构造 httpbin 网络演示 payload
- **THEN** 系统 SHALL NOT 将原始测量音频或点位语音识别音频写入 payload

### Requirement: 本地报告数据最小化
系统 SHALL 仅保存报告、网络演示和审计展示所需字段，并避免保存不必要的原始传感器数据或音频数据。

#### Scenario: 保存报告字段
- **WHEN** 用户保存报告
- **THEN** 系统 SHALL 仅保存点位、温度、湿度、可用时的气压、测量时间、置信度、质控说明、复制状态、演示模型状态、机型匹配摘要和必要的来源摘要

#### Scenario: 调试信息处理
- **WHEN** 系统记录测量失败、质控失败或网络演示失败原因
- **THEN** 系统 SHALL 仅保存错误类别和摘要，SHALL NOT 保存原始音频或完整传感器采样序列

### Requirement: 本地报告加密
系统 SHALL 对本地 SQLite 中保存的报告敏感 payload 进行加密，并使用 Android Keystore 或等效平台能力保护加密密钥。

#### Scenario: 写入本地报告
- **WHEN** 系统将报告写入 SQLite
- **THEN** 系统 SHALL 加密点位名称和温湿度报告字段

#### Scenario: 读取本地报告
- **WHEN** 用户查看报告历史或报告详情
- **THEN** 系统 SHALL 在应用内解密并展示报告字段

## ADDED Requirements

### Requirement: 网络演示隐私边界
系统 SHALL 在用户触发网络演示前说明该调用使用公开测试端点，且不是疾控系统提交。

#### Scenario: 用户首次触发网络演示
- **WHEN** 用户首次触发 httpbin 网络演示
- **THEN** 系统 SHALL 告知网络演示的用途、发送字段范围和非真实提交边界

#### Scenario: 发送网络演示请求
- **WHEN** 系统发送 httpbin 网络演示请求
- **THEN** 系统 SHALL NOT 发送明文点位名称、原始音频、点位语音音频或真实疾控 token
