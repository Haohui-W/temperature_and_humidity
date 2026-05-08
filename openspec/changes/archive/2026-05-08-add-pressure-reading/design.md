## Context

当前首页已经包含温度、湿度、气压三张卡片，其中气压卡片固定显示占位值。现有 AndroidManifest 已将 `android.hardware.sensor.barometer` 声明为可选硬件，测量模型中的 `SignalSnapshot` 也预留了 `pressureHpa`，但测量主流程没有真实读取或展示气压。

Android 气压计通过 `Sensor.TYPE_PRESSURE` 暴露，单位是 hPa。参考截图使用 kPa 展示，因此 UI 需要做单位转换：`kPa = hPa / 10`。

```
刷新数据
   │
   ▼
MeasurementService
   │
   ├─ AcousticEstimator ─┐
   ├─ ThermalEstimator  ─┼─ FusionEngine ── 温度/湿度/置信度
   └─ PressureReader  ───┘
          │
          ▼
    MeasurementResult.pressureHpa?
          │
          ├────────▶ 首页气压卡片: 101.3 kPa 或 -- kPa
          │
          └────────▶ ReportRecord.pressureHpa? / 复制文本
```

## Goals / Non-Goals

**Goals:**

- 在一键测量过程中尝试读取设备真实气压。
- 有有效读数时，在首页气压卡片展示 kPa 数值。
- 无气压计、读数超时或读数无效时，继续显示 `-- kPa`，并保持温湿度测量可成功。
- 保存报告时保留可用气压，并在历史与复制文本中展示。
- 保持现有 Kotlin/XML/ViewBinding 架构和可测试边界。

**Non-Goals:**

- 不把气压纳入当前温湿度融合算法或质控规则。
- 不新增手动气压录入。
- 不要求模拟器或所有真机都具备气压计。

## Decisions

### 1. 新增 `PressureReader` 边界，而不是把传感器代码写进 Fragment

测量层新增一个小接口，例如 `PressureReader.readPressureHpa(): Double?`。Android 实现负责使用 `SensorManager` 读取 `TYPE_PRESSURE`，测试中可用 fake reader 覆盖可用、缺失、无效等路径。

备选方案是直接在 `FirstFragment` 读取传感器。这样会把 UI、传感器生命周期和测量状态绑在一起，不利于单元测试，也不符合现有 `AcousticEstimator` / `ThermalEstimator` 的边界风格。

### 2. 气压作为可选测量附加值，不参与融合成败

`MeasurementResult` 增加可空气压字段，例如 `pressureHpa: Double?`。`FusionEngine` 可以在生成最终温湿度结果时携带该字段，但不使用它计算温度、湿度或置信度。

备选方案是把气压作为声学估算输入参与算法修正。技术方案曾提到气压输入，但当前实现的声学估算仍是占位性质，缺少校准数据；贸然参与融合会让结果看起来更“科学”，实际却不可验证。先展示真实读数更稳。

### 3. 使用短超时的一次性读取

测量服务在热特征读取后尝试读取一次气压，并设置短超时。读取成功即注销监听器；失败、超时或无传感器则返回 null。有效范围建议先用宽松物理范围过滤，例如 300-1100 hPa，避免明显异常值进入 UI。

备选方案是 App 常驻监听气压。这样可以让首页实时刷新，但会增加生命周期、耗电和前后台状态处理复杂度；当前用户触发“刷新数据”时读取一次，更贴合已有交互。

### 4. 首页值和单位分开展示，保持当前卡片结构

当前 XML 中气压卡片已经将数值和单位拆成两个 TextView。实现时只更新 `text_pressure` 数值部分，单位继续使用 `kPa`，缺失时数值显示 `--`。

备选方案是把数值和单位合并成一个文本。当前拆分方式已经贴合截图和布局，继续沿用能减少 UI 改动。

### 5. 报告存储增加可选气压并兼容旧记录

`ReportRecord` 增加 `pressureHpa: Double?`，保存草稿时从 `MeasurementResult` 复制该字段。`LocalReportStore` 序列化新增一列气压；反序列化同时兼容旧的 11 列记录和新的 12 列记录，旧记录气压为 null。

备选方案是只在首页展示气压，不落库。用户已经指出保存结构缺少气压；如果报告历史与复制文本不包含气压，现场填报仍然缺字段，因此需要把气压带入报告边界。

## Risks / Trade-offs

- 设备没有气压计 → 保留 `-- kPa`，不把气压缺失视为测量失败。
- 模拟器通常没有真实气压输入 → 单元测试使用 fake reader，真机验证作为手工验收项。
- 传感器回调可能迟迟不来 → 使用短超时并确保注销监听器，避免测量按钮长时间不可用。
- 气压读数是环境气压，不等于天气站海平面气压 → UI 只标注“气压/kPa”，不加入额外解释或算法承诺。
- 旧报告没有气压字段 → 反序列化兼容旧格式，并在历史与复制文本中显示 `-- kPa`。
