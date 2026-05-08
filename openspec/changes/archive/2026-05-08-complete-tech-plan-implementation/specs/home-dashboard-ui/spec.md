## ADDED Requirements

### Requirement: 演示模型与校准状态展示
系统 SHALL 在首页测量结果区域展示演示模型状态、校准状态和机型匹配摘要，避免用户将未标定演示估算理解为生产级测量。

#### Scenario: 展示未标定演示估算
- **WHEN** 测量结果由未标定演示模型生成
- **THEN** 首页 SHALL 展示未标定或演示估算提示

#### Scenario: 展示机型匹配摘要
- **WHEN** 测量结果包含机型麦克风间距来源
- **THEN** 首页 SHALL 展示机型匹配来源或未知机型默认值提示

### Requirement: 网络演示状态展示
系统 SHALL 在报告相关界面展示 httpbin 网络演示状态，并 SHALL NOT 将该状态展示为真实疾控提交状态。

#### Scenario: 网络演示未触发
- **WHEN** 已保存报告没有网络演示记录
- **THEN** 界面 SHALL 展示可触发网络演示的入口或未演示状态

#### Scenario: 网络演示成功
- **WHEN** 已保存报告存在成功的 httpbin 网络演示记录
- **THEN** 界面 SHALL 展示“网络演示成功”，并 SHALL NOT 展示“已提交”

#### Scenario: 网络演示失败
- **WHEN** 已保存报告存在失败的 httpbin 网络演示记录
- **THEN** 界面 SHALL 展示失败摘要，并 SHALL 保留报告本地已保存状态

### Requirement: 降级和失败原因展示
系统 SHALL 在首页或结果确认区域展示声学不可用、热特征降级、JNI TDOA 失败、气压不可用和质控失败等影响结果的原因。

#### Scenario: JNI TDOA 失败
- **WHEN** 声学路径因 JNI TDOA 失败而不可用
- **THEN** 首页 SHALL 展示声学不可用或已降级原因

#### Scenario: 使用旧版 MVP 声学兜底
- **WHEN** 测量结果包含旧版 MVP 声学启发式兜底来源
- **THEN** 首页 SHALL 在状态或来源摘要中展示“声学兜底”提示
- **AND** 首页 SHALL 展示该结果为未标定演示估算，并保留 TDOA 无效或声速异常的原始降级原因

#### Scenario: 麦克风权限拒绝
- **WHEN** 用户拒绝麦克风权限后生成热特征降级结果
- **THEN** 首页 SHALL 展示热特征模式和置信度限制
