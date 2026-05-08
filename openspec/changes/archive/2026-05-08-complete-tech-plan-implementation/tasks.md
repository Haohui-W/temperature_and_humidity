## 1. Native TDOA 与声学测量链路

- [x] 1.1 配置 Android native build：新增 CMake/native 源目录，并在 Gradle 中启用 `externalNativeBuild`。
- [x] 1.2 定义 `TdoaEstimator` 接口和结果模型，包含样本偏移、时间差、相关峰值、输入质量和失败原因。
- [x] 1.3 实现 JNI/C++ TDOA 互相关，窗口限制为 ±100 采样点，并处理空输入、长度不一致和零能量输入。
- [x] 1.4 增加 Kotlin 参考 TDOA 实现，仅用于 JVM 单元测试对照或 native 不可用时的明确 fallback。
- [x] 1.5 拆分音频采集与声道处理：新增/重构 48kHz、16bit、stereo PCM 采集和左右声道拆分逻辑。
- [x] 1.6 将 `AndroidAcousticEstimator` 接入 JNI TDOA、机型麦克风间距、气压和声学演示模型。
- [x] 1.7 确保声学完成或失败时清理原始 PCM、左右声道和临时分析缓冲区。
- [x] 1.8 在 TDOA 无效或声速越界时保留旧版 MVP 声学启发式作为低置信度最后兜底，并标注原始降级原因。

## 2. 演示模型、机型库与质控

- [x] 2.1 新增模型元数据结构，包含 `modelId`、`version`、`calibrated` 和 `summary`。
- [x] 2.2 实现 `AcousticDemoModel`，基于声速、气压和输入质量输出未标定演示温湿度。
- [x] 2.3 实现 `ThermalDemoModel`，基于电池温度、CPU 温度、CPU 负载和稳定度输出未标定演示温湿度。
- [x] 2.4 新增 `DeviceMicCatalog`，内置华为、小米、OPPO、vivo、荣耀、红米机型表和未知机型 5.0cm 默认值。
- [x] 2.5 在测量结果中加入模型状态、机型匹配来源、麦克风间距和未标定提示。
- [x] 2.6 扩展 `FusionEngine` 与 `QualityControl`，覆盖声速合理性、输入质量、置信度、降级原因和演示状态。
- [x] 2.7 麦克风拒绝或声学不可用时跳过声学路径，立即输出热特征演示估算且不强制等待 60 秒。

## 3. SQLite 本地存储迁移

- [x] 3.1 使用 `SQLiteOpenHelper` 新增本地数据库，创建 `reports` 和 `network_demo_logs` 表。
- [x] 3.2 复用 Android Keystore + AES/GCM 加密敏感报告 payload，并将加密 payload 写入 SQLite。
- [x] 3.3 实现报告保存、读取、列表、状态更新和复制状态写入的 SQLite 存储路径。
- [x] 3.4 移除旧 SharedPreferences 迁移路径，确认本地报告直接读写 SQLite。
- [x] 3.5 更新报告历史和复制文本流程，使其从 SQLite 读取并展示气压、演示状态和机型/质控摘要。

## 4. httpbin 网络演示

- [x] 4.1 在 Manifest 中添加网络权限，并保证网络不可用不影响本地保存和复制流程。
- [x] 4.2 实现可配置的 httpbin 网络演示客户端，默认端点为公开测试端点。
- [x] 4.3 构造最小化 payload：允许真实温湿度和气压，禁止明文点位、原始音频、点位语音音频和真实 token。
- [x] 4.4 实现网络演示成功、失败、超时和解析错误处理，并保存摘要到 `network_demo_logs`。
- [x] 4.5 在报告界面增加用户显式触发网络演示的入口，不在保存报告后自动上传。
- [x] 4.6 确保网络演示状态只显示为“网络演示成功/失败”，不得显示为“已提交疾控系统”。

## 5. UI、权限与隐私展示

- [x] 5.1 更新首页/结果区域，展示未标定演示估算、机型匹配来源、麦克风间距和降级原因。
- [x] 5.2 更新麦克风权限拒绝提示，说明热特征降级模式、未标定演示估算和置信度限制。
- [x] 5.3 更新报告历史/详情界面，展示 SQLite 报告、网络演示状态和失败摘要。
- [x] 5.4 增加网络演示首次触发说明，告知公开测试端点、发送字段范围和非真实提交边界。
- [x] 5.5 更新复制文本或来源摘要，避免将未标定演示估算描述为生产级测量或合规结果。

## 6. 测试与验证

- [x] 6.1 增加 JNI/C++ TDOA 合成信号测试或 instrumentation 验证，并与 Kotlin 参考实现做容差对照。
- [x] 6.2 增加声道拆分、无效 TDOA、声速异常和音频缓冲清理测试。
- [x] 6.3 增加机型库匹配测试，覆盖技术方案中的机型族和未知机型默认值。
- [x] 6.4 增加演示模型输出范围、模型元数据和未标定状态测试。
- [x] 6.5 增加融合与质控测试，覆盖声速异常降级、热特征降级和低置信度失败。
- [x] 6.6 增加 SQLite 保存、读取、无旧数据迁移、加密失败和复制状态测试。
- [x] 6.7 增加 network-demo payload 最小化、成功记录、失败记录和非真实提交状态测试。
- [x] 6.8 运行 `./gradlew test` 并修复失败。
- [x] 6.9 如 native 或 Android 框架路径需要设备/模拟器验证，运行相关 instrumentation 测试或记录无法运行的原因。
  - 记录：本轮不再继续操作设备；设备连接与实机验证由用户自行执行。此前尝试运行 `connectedDebugAndroidTest` 时，设备安装被系统限制拦截，错误为 `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`。

## 7. OpenSpec 与文档收尾

- [x] 7.1 运行 `openspec validate complete-tech-plan-implementation --type change --strict`，确保变更规格仍然有效。
- [x] 7.2 对照 `docs/技术方案.md` 和本变更 specs，确认所有刻意降级项都有非目标、卡点或未标定说明。
- [x] 7.3 更新必要的用户可见文案，避免出现真实 API 提交、精度、覆盖率或合规认证完成的误导性描述。
