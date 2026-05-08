# network-demo Specification

## Purpose
TBD - created by archiving change complete-tech-plan-implementation. Update Purpose after archive.
## Requirements
### Requirement: httpbin 网络演示调用
系统 SHALL 提供可关闭的网络演示能力，用于向 httpbin 等公开测试端点发送最小化报告摘要，以验证网络权限、请求构造、响应解析和错误提示。

#### Scenario: 用户触发网络演示
- **WHEN** 用户在已保存报告上触发网络演示
- **THEN** 系统 SHALL 向配置的测试端点发送演示 payload，并展示网络演示处理中状态

#### Scenario: 网络演示成功
- **WHEN** 测试端点返回成功响应
- **THEN** 系统 SHALL 展示“网络演示成功”状态，并 SHALL NOT 展示“已提交疾控系统”

#### Scenario: 网络演示失败
- **WHEN** 网络不可用、端点超时或服务返回错误
- **THEN** 系统 SHALL 展示网络演示失败原因摘要，并 SHALL 保留本地报告不受影响

### Requirement: 网络演示 payload 最小化
系统 SHALL 限制网络演示 payload 内容，允许包含真实温湿度和气压，但 SHALL NOT 包含原始音频、点位语音音频、真实疾控 token 或明文点位名称。

#### Scenario: 构造演示 payload
- **WHEN** 系统准备发送网络演示请求
- **THEN** payload SHALL 包含报告 id、温度、湿度、可用时的气压、置信度、是否演示估算和脱敏摘要

#### Scenario: 点位名称存在
- **WHEN** 报告包含用户输入或识别出的点位名称
- **THEN** 网络演示 payload SHALL NOT 包含明文点位名称

#### Scenario: 音频数据存在于测量流程
- **WHEN** 网络演示请求被构造
- **THEN** 网络演示 payload SHALL NOT 包含原始音频或可还原原始音频的数据

### Requirement: 网络演示记录
系统 SHALL 将网络演示结果记录到本地 SQLite，包含端点、请求摘要、响应状态、成功/失败状态、错误摘要和创建时间。

#### Scenario: 记录成功调用
- **WHEN** 网络演示调用成功
- **THEN** 系统 SHALL 在 SQLite 中保存成功记录和响应摘要

#### Scenario: 记录失败调用
- **WHEN** 网络演示调用失败
- **THEN** 系统 SHALL 在 SQLite 中保存失败记录和错误摘要

#### Scenario: 查看报告历史
- **WHEN** 用户查看包含网络演示记录的报告
- **THEN** 系统 SHALL 展示网络演示状态，但 SHALL NOT 将其作为真实提交状态展示

