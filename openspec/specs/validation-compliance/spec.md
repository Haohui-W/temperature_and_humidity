# validation-compliance Specification

## Purpose
TBD - created by archiving change complete-tech-plan-implementation. Update Purpose after archive.
## Requirements
### Requirement: 未标定能力声明限制
系统 SHALL 在缺少实验室或现场验证证据时，将声学模型、热特征模型和融合结果标记为未标定演示估算，并 SHALL NOT 展示具体精度、覆盖率或认证完成声明。

#### Scenario: 使用演示模型生成结果
- **WHEN** 测量结果由未标定演示模型生成
- **THEN** 系统 SHALL 在结果来源或说明中展示未标定/演示估算提示

#### Scenario: 展示测量结果
- **WHEN** 系统没有关联的验证证据
- **THEN** 系统 SHALL NOT 展示“误差≤0.1℃”“99% 覆盖率”或“已合规认证”等声明

### Requirement: 验证证据记录
系统 SHALL 支持记录或引用验证证据摘要，用于区分工程演示结果和经过验证的模型/机型/算法能力。

#### Scenario: 没有验证证据
- **WHEN** 模型、机型库或算法没有验证证据
- **THEN** 系统 SHALL 将对应能力标记为未验证或未标定

#### Scenario: 存在验证证据摘要
- **WHEN** 后续版本提供验证证据摘要
- **THEN** 系统 SHALL 能够展示证据摘要标识，但 SHALL NOT 自动推导未记录的精度或合规结论

### Requirement: 隐私与合规门禁
系统 SHALL 将隐私与合规作为发布门禁，任何网络演示、模型更新或报告存储变更都 MUST 保持音频不上传、敏感字段最小化和本地加密边界。

#### Scenario: 网络演示功能开启
- **WHEN** 用户使用网络演示功能
- **THEN** 系统 SHALL 仍然遵守不上传原始音频、不发送明文点位和不发送真实 token 的约束

#### Scenario: 保存本地报告
- **WHEN** 系统保存本地报告或网络演示记录
- **THEN** 系统 SHALL 加密敏感报告 payload，并 SHALL 仅保存必要摘要

