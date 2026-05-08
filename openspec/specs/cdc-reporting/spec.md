# cdc-reporting Specification

## Purpose
TBD - created by archiving change build-cdc-temp-humidity-mvp. Update Purpose after archive.
## Requirements
### Requirement: 点位名称采集与修正
系统 SHALL 支持通过语音识别或手动输入采集点位名称，并在保存报告前允许用户修正点位名称。

#### Scenario: 语音识别点位成功
- **WHEN** 用户选择语音录入点位且系统识别出点位名称
- **THEN** 系统 SHALL 将识别结果填入点位字段，并允许用户编辑

#### Scenario: 语音识别不可用
- **WHEN** 语音识别能力不可用、识别失败或用户不授权相关权限
- **THEN** 系统 SHALL 提供手动输入点位名称的路径

#### Scenario: 保存前校验点位
- **WHEN** 用户尝试保存报告但点位名称为空
- **THEN** 系统 SHALL 阻止保存并提示用户填写点位名称

### Requirement: 报告草稿创建
系统 SHALL 根据通过质控的测量结果和用户确认的点位名称创建本地报告草稿。

#### Scenario: 创建报告草稿
- **WHEN** 用户确认点位名称和测量结果
- **THEN** 系统 SHALL 创建包含点位、温度、湿度、测量时间、置信度和质控说明的报告草稿

#### Scenario: 拒绝异常测量结果
- **WHEN** 测量结果未通过质控或缺少温度/湿度值
- **THEN** 系统 SHALL 阻止创建报告草稿

### Requirement: 本地报告保存
系统 SHALL 将用户确认的报告保存到本地加密存储，并维护报告状态和更新时间。

#### Scenario: 保存报告成功
- **WHEN** 用户确认保存报告
- **THEN** 系统 SHALL 将报告保存到本地存储，并将状态设置为“已保存”

#### Scenario: 本地保存失败
- **WHEN** 本地存储写入或加密处理失败
- **THEN** 系统 SHALL 告知用户保存失败，并 SHALL NOT 展示报告已保存状态

### Requirement: 待复制结果生成
系统 SHALL 为已保存报告生成格式稳定、可复制到疾控填报系统的文本结果。

#### Scenario: 生成复制文本
- **WHEN** 用户查看已保存报告
- **THEN** 系统 SHALL 生成包含点位、温度、湿度、测量时间和必要质控说明的复制文本

#### Scenario: 用户复制结果
- **WHEN** 用户点击复制操作
- **THEN** 系统 SHALL 将复制文本写入系统剪贴板，并将报告状态更新为“已复制”

### Requirement: 报告历史
系统 SHALL 提供本地报告历史列表，便于用户查看最近保存和已复制的报告。

#### Scenario: 查看报告历史
- **WHEN** 用户进入报告历史界面
- **THEN** 系统 SHALL 展示本地保存的报告记录、点位、温湿度、创建时间和当前状态

#### Scenario: 查看单条报告详情
- **WHEN** 用户选择一条报告历史记录
- **THEN** 系统 SHALL 展示该报告的完整字段和可复制文本

### Requirement: 无 API 填报边界
系统 SHALL NOT 在 MVP 阶段自动调用疾控填报 API、自动提交报告或自动填充第三方系统。

#### Scenario: 保存报告后不自动提交
- **WHEN** 用户保存报告
- **THEN** 系统 SHALL 仅保存本地报告并提供复制文本，SHALL NOT 发起网络提交

#### Scenario: 用户复制报告后不声称已提交
- **WHEN** 用户复制待填报文本
- **THEN** 系统 SHALL 将状态记录为“已复制”，并 SHALL NOT 将状态展示为“已提交”

