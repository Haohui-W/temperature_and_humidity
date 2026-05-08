## Why

当前 MVP 已跑通测量与报告主流程，但首页仍接近 Android 模板表单，和参考截图中的“环境参数首页”差距明显；同时 APK 启动图标仍是默认 Android 图标，不利于演示和现场辨识。

本变更聚焦产品观感打磨：让首页第一屏更接近参考截图，并用临时 emoji 风格图标替换默认模板图标。

## What Changes

- 将测量首页重排为参考截图风格的“环境参数”仪表盘首页。
- 首页展示温度、湿度、气压三张参数卡片，并保留现有一键测量能力。
- 将测量按钮调整为接近截图的描边刷新按钮样式。
- 不展示参考截图中的红色框线和红色说明文字；它们仅作为标注，不属于应用 UI。
- 增加底部导航外观，包含“首页、任务、物资、采样、更多”入口。
- “首页”承载真实测量主流程；其他底部入口暂时展示空白占位页，不实现业务模块。
- 替换默认 Android 启动图标，使用临时 emoji 风格图标作为 APK 图标。
- 保持现有 XML/ViewBinding/Navigation 架构，不迁移到 Jetpack Compose。

## Capabilities

### New Capabilities
- `home-dashboard-ui`: 覆盖首页环境参数仪表盘、底部占位导航、截图标注排除规则，以及临时 emoji 风格启动图标。

### Modified Capabilities
- 无。

## Impact

- 影响 Android XML 布局资源：`activity_main.xml`、`content_main.xml`、首页 Fragment 布局，以及新增的占位页面布局。
- 影响 Android Navigation 配置和 Fragment 代码，用于支持底部入口与空白占位页。
- 影响主题、颜色、drawable、mipmap 或 launcher icon 资源，用于实现首页视觉和 APK 图标。
- 不改变测量算法、报告保存、复制文本、隐私权限和本地加密存储的业务行为。
- 不引入新业务模块；“任务、物资、采样、更多”仅为视觉占位。
