## ADDED Requirements

### Requirement: 内置麦克风间距机型库
系统 SHALL 提供内置静态机型库，用于根据设备品牌和型号返回双麦克风间距，并 SHALL 包含技术方案中列出的机型族和未知机型默认值。

#### Scenario: 匹配华为机型
- **WHEN** 设备品牌/型号匹配华为 P50、P40 或 Mate40 机型族
- **THEN** 系统 SHALL 返回 5.0cm 的双麦克风间距，并标记来源为内置表

#### Scenario: 匹配小米机型
- **WHEN** 设备品牌/型号匹配小米 13、12 或 11 机型族
- **THEN** 系统 SHALL 返回 4.8cm 的双麦克风间距，并标记来源为内置表

#### Scenario: 匹配 OPPO 机型
- **WHEN** 设备品牌/型号匹配 OPPO Find X5 或 Reno8 机型族
- **THEN** 系统 SHALL 返回 4.9cm 的双麦克风间距，并标记来源为内置表

#### Scenario: 匹配 vivo 机型
- **WHEN** 设备品牌/型号匹配 vivo X80、X70 或 S15 机型族
- **THEN** 系统 SHALL 返回 5.1cm 的双麦克风间距，并标记来源为内置表

#### Scenario: 匹配荣耀机型
- **WHEN** 设备品牌/型号匹配荣耀 Magic4、60 或 50 机型族
- **THEN** 系统 SHALL 返回 4.9cm 的双麦克风间距，并标记来源为内置表

#### Scenario: 匹配红米机型
- **WHEN** 设备品牌/型号匹配红米 Note12 或 Note11 机型族
- **THEN** 系统 SHALL 返回 4.7cm 的双麦克风间距，并标记来源为内置表

#### Scenario: 未知机型
- **WHEN** 设备品牌/型号无法匹配内置机型库
- **THEN** 系统 SHALL 返回 5.0cm 的默认双麦克风间距，并标记来源为未知机型默认值

### Requirement: 机型匹配结果可追踪
系统 SHALL 在测量结果中保留机型匹配摘要，包含匹配来源、使用的麦克风间距和是否使用默认值。

#### Scenario: 使用默认机型距离
- **WHEN** 系统使用未知机型默认麦克风间距完成声学估算
- **THEN** 测量结果 SHALL 包含默认机型提示，并 SHALL NOT 声称该设备已完成校准

#### Scenario: 使用内置机型距离
- **WHEN** 系统通过内置机型库匹配到麦克风间距
- **THEN** 测量结果 SHALL 包含机型匹配来源和麦克风间距摘要

### Requirement: 云端机型库不在当前范围
系统 SHALL NOT 在本变更中从云端下载、上传或自动更新机型库。

#### Scenario: App 启动
- **WHEN** 用户启动 App
- **THEN** 系统 SHALL 使用本地内置机型库，并 SHALL NOT 因无法访问云端机型库阻断测量

#### Scenario: 新机型无法匹配
- **WHEN** 新机型未包含在内置机型库中
- **THEN** 系统 SHALL 使用未知机型默认值，并 SHALL NOT 自动上报机型信息到云端
