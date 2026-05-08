## Context

当前 App 已有 MVP：一键测量、声学/热特征估算框架、融合质控、本地报告保存、历史展示和复制文本。`docs/技术方案.md` 还包含若干未实现能力：TDOA 声学链路、机型麦克风间距库、预训练模型、网络/API 对接、SQLite 离线存储、合规与现场验证。

本设计按当前现实条件收敛范围：疾控 API 暂不存在，预训练模型暂不存在，云端机型库暂不存在。因此本变更实现“工程演示与替换边界”，不声明真实提交能力或测量精度。

## Goals / Non-Goals

**Goals:**

- 建立完整测量链路的工程结构：音频采集、声道拆分、JNI/C++ TDOA、机型麦克风间距、演示模型、热特征、融合和质控。
- 用内置静态机型表替代暂不可用的云端机型库，并保留后续替换边界。
- 用演示模型替代不存在的预训练模型，并在结果和 UI 中明确“未标定/演示估算”。
- 将报告和网络演示记录迁移到 SQLite，本地存储仍是正式主流程。
- 增加 httpbin 网络演示调用，用于验证网络权限、请求构造、响应解析和失败提示。
- 保持隐私边界：测量音频不落盘、不上传；httpbin 不模拟真实疾控提交。

**Non-Goals:**

- 不接入真实疾控 API，不自动填报第三方系统，不实现联网后自动同步。
- 不实现云端机型库、云端校准数据更新或后台 fleet 管理。
- 不引入真实预训练模型、TFLite 推理或生产级标定。
- 不声明“150+ 机型覆盖”“99% 覆盖率”“误差≤0.1℃”或合规认证完成。
- 不上传原始音频或点位语音识别音频。

## Decisions

### 1. 测量层拆成可替换流水线

保留现有 `measurement` 包边界，新增或拆分以下角色：

- `AudioSampler`：负责 48kHz、16bit、stereo PCM 采集。
- `StereoChannelSplitter`：把 interleaved PCM 拆分为 left/right。
- `TdoaEstimator`：计算左右声道延迟。
- `DeviceMicCatalog`：按品牌/型号返回麦克风间距。
- `AcousticDemoModel`：基于声速、气压和输入质量输出演示温湿度。
- `ThermalDemoModel`：基于电池温度、CPU 温度、CPU 负载和稳定度输出演示温湿度。
- `FusionEngine` / `QualityControl`：继续负责置信度加权、范围校验和降级结果。

这样 UI 和报告层只依赖 `MeasurementResult`，后续把演示模型替换为真实模型时不需要重写页面和存储。

备选方案是继续把音频分析写在 `AndroidAcousticEstimator` 内部。它改动少，但 TDOA、模型、机型库和测试会混在一起，后续很难替换。

### 2. TDOA 必须落到 JNI/C++，Kotlin 仅作测试参考

TDOA 逻辑通过接口隔离：`TdoaEstimator.estimate(left, right, sampleRate)`。本变更必须提供 JNI/C++ 实现，窗口限制在技术方案中的 ±100 采样点，并通过 JNI 返回样本偏移、时间差和相关峰值等调试信息。

Kotlin 实现只允许作为单元测试参考或 JVM 环境下的 fallback，不作为 Android 主路径。这样既满足技术方案中的 JNI 要求，也保留可测试性：同一组合成信号应能验证 Kotlin 参考实现和 native 实现返回一致或在容差范围内。

### 3. 模型采用“演示模型 + 元数据”而非伪造预训练模型

声学和热特征模型都定义统一元数据：

- `modelId`
- `version`
- `calibrated: Boolean`
- `summary`

本阶段内置 `calibrated = false` 的 demo model。测量结果、来源说明和 UI 必须能展示“未标定/演示估算”。当 TDOA 无效、声速为 0/无穷、声速超出质控范围或模型输入不足时，声学结果不可用或降级到热特征结果。

备选方案是把公式直接写死并隐藏模型状态。这样短期更快，但会让用户误以为已经具备生产模型能力。

### 4. 机型库使用内置静态表

新增 `DeviceMicCatalog`，按 `Build.MANUFACTURER` 和 `Build.MODEL` 做宽松匹配。先内置技术方案中的表：

| 品牌 | 机型 | 双麦距离 |
| --- | --- | --- |
| 华为 | P50/P40/Mate40 | 5.0cm |
| 小米 | 13/12/11 | 4.8cm |
| OPPO | Find X5/Reno8 | 4.9cm |
| vivo | X80/X70/S15 | 5.1cm |
| 荣耀 | Magic4/60/50 | 4.9cm |
| 红米 | Note12/11 | 4.7cm |
| 未知 | 默认 | 5.0cm |

匹配结果需要带来源：`BUILT_IN_EXACT`、`BUILT_IN_BRAND_FAMILY`、`DEFAULT_UNKNOWN`。未知机型可以继续测量，但结果必须保留未校准/默认机型提示。

### 5. SQLite 使用平台 API，不引入 Room/SQLCipher

为了贴合技术方案并控制依赖，使用 `SQLiteOpenHelper` 管理本地数据库，而不是引入 Room。数据加密继续复用现有 Android Keystore + AES/GCM 的 `ReportCipher` 思路，对敏感 payload 加密后写入 SQLite。本变更不规划后续升级到 Room 或 SQLCipher。

建议表：

- `reports`：保存报告 id、加密 payload、测量时间、创建/更新时间、状态、是否演示估算、校准摘要。
- `network_demo_logs`：保存网络演示 id、关联报告 id、端点、请求摘要、HTTP 状态码、成功/失败、错误摘要、创建时间。
- `schema_meta`：保存数据库版本和一次性迁移标记。

当前应用尚未上线，不保留旧 SharedPreferences 数据迁移路径；实现时直接使用 SQLite 作为唯一报告存储。

备选方案是 Room。它类型安全更强，但需要新增依赖和迁移代码；当前数据结构简单，平台 SQLite 足够。

### 6. httpbin 是网络演示，不是提交

新增 `network-demo` 边界，默认使用可配置的测试端点，例如 `https://httpbin.org/post`。调用必须由用户显式触发或由开发开关控制，不在保存报告后自动上传。

请求内容应最小化：不发送原始音频，不发送点位语音音频，不发送真实疾控 token。httpbin 演示 payload 允许包含真实温湿度和气压，用于验证序列化和响应解析；点位名称可能含现场信息，默认不发送明文点位，只发送本地报告 id、置信度、是否演示估算和脱敏摘要。

网络调用结果只记录为“网络演示成功/失败”，不能显示“已提交疾控系统”。

备选方案是接入真实 API 客户端占位。由于接口合同不存在，占位客户端容易形成假能力和错误状态语义。

### 7. UI 显示演示、降级和校准状态

首页结果区域需要继续展示温度、湿度、气压、置信度、来源和质控，同时补充：

- 演示模型/未标定提示。
- 机型匹配来源和麦克风间距。
- 声学失败或热特征降级原因。
- 网络演示状态，不展示真实提交状态。

麦克风权限拒绝后应跳过声学路径并展示热特征模式说明。本变更不强制真实等待 60 秒，可以立即输出演示估算，但必须在状态和结果中明确标注“热特征模式/未标定演示估算”，避免把快速演示误认为真实稳定测量。

### 8. 验证与合规作为门禁，不作为代码宣称

新增 `validation-compliance` 能力只记录证据要求和展示限制。代码可以提供以下支撑：

- 音频 buffer 清理测试。
- TDOA 合成信号测试。
- 模型输出范围测试。
- 机型库匹配测试。
- SQLite 迁移和加密读写测试。
- httpbin 成功/失败路径测试。

但没有实验室/现场数据前，UI 和文档不得宣称真实精度、覆盖率或合规认证完成。

## Risks / Trade-offs

- [Risk] 演示模型输出被误认为真实测量能力 → Mitigation：模型元数据强制标记 `calibrated = false`，UI 和复制文本展示未标定说明。
- [Risk] httpbin 是第三方公开测试服务，可能泄露现场信息 → Mitigation：默认不发送明文点位和音频，只发送最小化演示 payload，并要求用户显式触发。
- [Risk] 静态机型表覆盖有限 → Mitigation：未知机型默认 5.0cm，同时标记默认来源，不展示覆盖率或精度承诺。
- [Risk] JNI/C++ 增加构建复杂度和设备兼容风险 → Mitigation：使用 `externalNativeBuild`/CMake 管理 native 代码，保留 Kotlin 参考实现用于测试对照，并增加 native 加载失败时的明确降级错误。
- [Risk] 实机 TDOA 长期返回 `0-2` samples，导致按静态麦距反推声速异常 → Mitigation：已用 Kotlin 参考实现复核，现象不只出现在 native；当前记录为声源几何、双扬声器/多声源、环境漫反射、系统伪 stereo/音频处理或静态麦距未标定等综合风险。代码将 TDOA 无效和声速越界拆分为不同降级原因，并在 `docs/声学TDOA实机问题记录.md` 中记录排查结论。
- [Risk] 本地存储切换到 SQLite 后无法读取开发阶段旧 SharedPreferences 数据 → Mitigation：应用尚未上线，接受清空开发期旧数据，避免引入无意义迁移复杂度。
- [Risk] SQLite 加密不是 SQLCipher 全库加密 → Mitigation：敏感 payload 使用 Keystore AES/GCM 加密，网络演示日志只保存脱敏摘要。

## Migration Plan

1. 新增 SQLite 存储实现和数据表。
2. 将报告保存、读取、列表、复制状态和网络演示日志全部切换到 SQLite。
3. 不实现旧 SharedPreferences 数据迁移；开发期旧数据可丢弃。
4. 新增网络演示日志表后，所有 httpbin 调用结果只写 SQLite。

## Open Questions

- 暂无。当前开放问题已收口为设计决策：热特征降级不强制等待 60 秒；httpbin payload 允许包含真实温湿度和气压但不发送明文点位、音频或真实 token；SQLite 使用平台 API；TDOA 本变更必须落到 JNI/C++。
