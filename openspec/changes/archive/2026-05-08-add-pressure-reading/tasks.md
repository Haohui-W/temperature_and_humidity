## 1. Measurement Model

- [x] 1.1 Add optional `pressureHpa` to the measurement result model with display helpers for pressure value and kPa formatting.
- [x] 1.2 Keep existing temperature, humidity, confidence, source, quality, and degraded-result behavior unchanged.

## 2. Pressure Reader

- [x] 2.1 Add a `PressureReader` boundary in the measurement package that returns an optional hPa reading.
- [x] 2.2 Implement Android pressure reading using `SensorManager` and `Sensor.TYPE_PRESSURE`.
- [x] 2.3 Use a bounded timeout, unregister the sensor listener after success or timeout, and return null for missing, invalid, or unavailable readings.
- [x] 2.4 Validate pressure readings with a broad physical range before exposing them to measurement results.

## 3. Measurement Flow Integration

- [x] 3.1 Inject the pressure reader into `MeasurementService` alongside the existing acoustic and thermal estimators.
- [x] 3.2 Read pressure during a one-tap measurement without requesting new runtime permissions.
- [x] 3.3 Pass the optional pressure reading into the completed measurement result without using it to calculate temperature, humidity, confidence, or quality.
- [x] 3.4 Ensure pressure absence does not convert an otherwise successful temperature/humidity measurement into a failure.

## 4. Home Dashboard UI

- [x] 4.1 Update `FirstFragment` binding so successful pressure readings update the existing `text_pressure` card value.
- [x] 4.2 Display pressure in kPa and keep the existing `kPa` unit label.
- [x] 4.3 Keep the pressure card as `-- kPa` when pressure is unavailable, including on devices without a barometer.
- [x] 4.4 Preserve the current dashboard layout, refresh button behavior, and report controls.

## 5. Verification

- [x] 5.1 Add or update focused unit tests for pressure available, pressure unavailable, and pressure invalid cases using fake readers.
- [x] 5.2 Run the relevant Gradle unit test task and fix failures.
- [x] 5.3 Build the app or run the closest available Gradle verification task to confirm resources and Kotlin compile.
- [x] 5.4 Manually verify on a device or emulator that the pressure card shows a real kPa value when supported and `-- kPa` when unsupported.

## 6. Reporting Integration

- [x] 6.1 Add optional pressure to `ReportRecord` and copy it from `MeasurementResult` when saving a report.
- [x] 6.2 Update local report serialization to persist pressure while remaining backward compatible with existing records.
- [x] 6.3 Include pressure in report history and copy text, using `-- kPa` when pressure is unavailable.
- [x] 6.4 Add or update reporting tests for pressure in copy text and missing-pressure fallback.
- [x] 6.5 Re-run unit tests and debug build after reporting integration.
