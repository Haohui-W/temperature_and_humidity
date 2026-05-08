## MODIFIED Requirements

### Requirement: 本地报告保存
系统 SHALL 将用户确认的报告保存到本地 SQLite 存储，并对敏感报告 payload 加密；系统 SHALL 维护报告状态和更新时间，当气压可用时，系统 SHALL 保存气压。

#### Scenario: 保存报告成功
- **WHEN** 用户确认保存报告且测量结果包含气压
- **THEN** 系统 SHALL 将报告和气压保存到 SQLite，并将状态设置为“已保存”

#### Scenario: 保存无气压报告
- **WHEN** 用户确认保存报告但测量结果不包含气压
- **THEN** 系统 SHALL 保存温度、湿度和其他报告字段，并 SHALL 将气压保存为不可用状态

#### Scenario: 本地保存失败
- **WHEN** SQLite 写入或加密处理失败
- **THEN** 系统 SHALL 告知用户保存失败，并 SHALL NOT 展示报告已保存状态

#### Scenario: 无旧数据迁移
- **WHEN** App 使用 SQLite 作为本地报告存储
- **THEN** 系统 SHALL 直接读写 SQLite，并 SHALL NOT 实现旧 SharedPreferences 报告迁移路径

### Requirement: 报告历史
系统 SHALL 提供本地 SQLite 报告历史列表，便于用户查看最近保存、已复制和带有网络演示记录的报告。

#### Scenario: 查看报告历史
- **WHEN** 用户进入报告历史界面
- **THEN** 系统 SHALL 展示本地保存的报告记录、点位、温湿度、气压、创建时间、当前状态和可用时的网络演示状态

#### Scenario: 查看单条报告详情
- **WHEN** 用户选择一条报告历史记录
- **THEN** 系统 SHALL 展示该报告的完整字段、可复制文本和可用时的网络演示摘要

### Requirement: 无 API 填报边界
系统 SHALL NOT 在当前阶段自动调用疾控填报 API、自动提交报告、自动同步报告或自动填充第三方系统；系统 SHALL 仅在网络演示边界内提供 httpbin 调用，且该调用 SHALL NOT 被展示为真实提交。

#### Scenario: 保存报告后不自动提交
- **WHEN** 用户保存报告
- **THEN** 系统 SHALL 仅保存本地报告并提供复制文本，SHALL NOT 发起真实疾控 API 提交

#### Scenario: 用户复制报告后不声称已提交
- **WHEN** 用户复制待填报文本
- **THEN** 系统 SHALL 将状态记录为“已复制”，并 SHALL NOT 将状态展示为“已提交”

#### Scenario: 网络演示完成
- **WHEN** 用户触发 httpbin 网络演示且调用成功
- **THEN** 系统 SHALL 记录“网络演示成功”，并 SHALL NOT 将报告状态展示为“已提交疾控系统”
