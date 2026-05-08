## 1. Visual Resources

- [x] 1.1 Add or update color resources for the light gray page background, white cards, primary teal/blue accent, muted labels, and bottom navigation states.
- [x] 1.2 Add drawable resources for the rounded dashboard container, parameter cards, outlined refresh button, and any required bottom navigation item backgrounds.
- [x] 1.3 Add or update text resources for “环境参数”, “刷新数据”, “温度”, “湿度”, “气压”, “首页”, “任务”, “物资”, “采样”, and “更多”.

## 2. Home Dashboard Layout

- [x] 2.1 Refactor `fragment_first.xml` into a dashboard layout with a white rounded container and three parameter cards for temperature, humidity, and pressure.
- [x] 2.2 Preserve or update ViewBinding ids so existing Kotlin measurement logic can update temperature, humidity, status, point input, voice input, save report, and history actions.
- [x] 2.3 Render pressure as a visible `-- kPa` placeholder without treating it as measured data.
- [x] 2.4 Restyle the existing measurement button as a full-width outlined refresh button that still triggers the one-tap measurement flow.
- [x] 2.5 Ensure the red annotation frame and annotation text from `docs/screenshot.png` do not appear in the app UI.

## 3. App Shell And Placeholder Tabs

- [x] 3.1 Adjust the Activity/app shell so the first screen feels like the screenshot rather than a default toolbar-based template page.
- [x] 3.2 Add bottom navigation or an equivalent XML-based bottom bar with “首页、任务、物资、采样、更多”.
- [x] 3.3 Keep “首页” connected to the real measurement dashboard.
- [x] 3.4 Add blank placeholder destination(s) for “任务、物资、采样、更多” without implementing business features.
- [x] 3.5 Update navigation handling so users can move from placeholder tabs back to the home dashboard.

## 4. Launcher Icon

- [x] 4.1 Replace the default Android launcher foreground/background artwork with a temporary emoji style temperature/humidity themed icon.
- [x] 4.2 Verify `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round` resolve to the new non-default icon resources.
- [x] 4.3 Check the icon remains recognizable within adaptive icon safe areas for both square and round masks.

## 5. Verification

- [x] 5.1 Run `./gradlew :app:assembleDebug` to verify resources and code compile.
- [x] 5.2 Manually review the home screen against `docs/screenshot.png`, confirming the dashboard/card/button/bottom-nav structure is close while red annotations are absent.
- [x] 5.3 Verify a measurement can still be started from the new refresh button and that completed results update the temperature and humidity cards.
- [x] 5.4 Verify non-home bottom tabs show blank placeholder pages and do not expose unfinished business workflows.
