# IMPLEMENTATION REPORT — Status Bar Clock (Phase 2 Remediation)

**Ngày:** 24/09/2026
**Trạng thái tổng thể:**

```text
Source implementation: COMPLETE
Build configuration: READY
Unit tests: PASS
Lint: PASS
Hardware validation: PENDING
APK: NOT BUILT
AAB: NOT BUILT
```

> Phase 2 quy định **không build APK/AAB**. Toàn bộ validate dưới đây chỉ gồm
> source, test, lint và compile — không chạy `assembleDebug/Release`,
> `bundleDebug/Release`, `installDebug/Release`. Không có file `.apk`/`.aab`
> nào được tạo trong workspace.

---

## 1. Environment

```text
OS:            macOS (Darwin 27.0.0, arm64)
JDK:           OpenJDK 17.0.15 (Homebrew)
Gradle:        9.3.1 (wrapper; phân phối -all đã có sẵn trong cache máy)
AGP:           9.1.0 (com.android.application)
Kotlin:        2.2.21 (built-in Kotlin của AGP)
Android SDK:   platforms android-31..36; build-tools 36.0.0; adb 35.0.2
compileSdk/targetSdk: 36   minSdk: 26
```

Toolchain giữ nguyên như Phase 1 (Gradle 9.3.1 + AGP 9.1.0 + Kotlin 2.2.21 +
JDK 17) vì đang compile ổn định và máy không thể tải toolchain mới hơn
(services.gradle.org ~80 B/s, dl.google.com ~12–15 KB/s). Không nâng version
chỉ vì muốn dùng version mới hơn.

`gradle.properties`: đã kiểm tra đường dẫn máy cá nhân. Chỉ còn **một** dòng
`android.aapt2FromMavenOverride=/Users/m.mac/...` — dòng này **bắt buộc** trên
máy này (artifact aapt2 trên Maven không tải được với tốc độ mạng hiện tại);
đã ghi chú rõ trong file và README rằng máy khác phải sửa/xóa. Không còn đường
dẫn cá nhân nào khác.

## 2. Device

```text
adb devices -l => trống — không có thiết bị kết nối ADB
Hardware validation = PENDING
```

Không có khẳng định nào về reboot hay tương thích OEM cho tới khi test trên
thiết bị thật.

## 3. Kiến trúc runtime (thay đổi chính của Phase 2)

```text
AccessibilityService (TYPE_WINDOW_STATE_CHANGED -> event.packageName)
        |
        v
ClockController (state machine + ClockVisibilityPolicy)
        |
        v
AccessibilityOverlayClockStrategy (TYPE_ACCESSIBILITY_OVERLAY)
        |
        +-- HOME       => overlay hidden
        +-- OTHER APP  => overlay visible
```

- `SystemUiClockStrategy` **không còn nằm trên runtime critical path**.
  `ClockController` không tham chiếu nó. File được giữ làm future/diagnostic
  extension; `knownHooks = emptyList()` không thể làm application unusable.
- Không tạo Foreground Service, không `WRITE_SECURE_SETTINGS`, không
  `SYSTEM_ALERT_WINDOW`, không phụ thuộc ADB.

### Vì sao Accessibility Overlay

AOSP phân lớp: `TYPE_STATUS_BAR` = layer 17, `TYPE_ACCESSIBILITY_OVERLAY` =
layer 30 → overlay nằm trên Status Bar. Overlay do Accessibility Service tạo,
không cần quyền đặc biệt nào ngoài `BIND_ACCESSIBILITY_SERVICE`.

## 4. Các thành phần

| Thành phần | Thay đổi Phase 2 |
|---|---|
| `ClockController` | Bỏ SystemUI path; flow: service connected → foreground package → HOME hide / other show. Repar/retry khi `ERROR_RECOVERABLE`. |
| `ClockAccessibilityService` | Truyền `event.packageName` cho controller; không đọc text/contentDescription/node tree; `canRetrieveWindowContent=false`. |
| `HomeDetector` (mới) | Phát hiện launcher bằng `ACTION_MAIN` + `CATEGORY_HOME`, không hard-code package. Không xác định được launcher → trả `not home` → hiển thị overlay (ưu tiên không mất Clock). |
| `ClockVisibilityPolicy` (mới) | Luật show/hide thuần Kotlin: OFF/pending/error → hidden; active + non-home → visible; unknown → visible. |
| `AccessibilityOverlayClockStrategy` | Trạng thái `overlayAttached` riêng (set khi `addView()` thành công, clear khi `removeView()` chạy); `show()/hide()` idempotent, không add/remove window theo từng event. |
| `ClockStateMachine` | Thêm `onOverlayActive()`; giữ `ON_ACTIVE_SYSTEMUI`/`onActivationResult` cho future extension (không ảnh hưởng runtime). |
| Manifest | Service `android:exported="true"` + `BIND_ACCESSIBILITY_SERVICE`. Không thêm permission nào mới. |
| `accessibility_service_config.xml` | `canRetrieveWindowContent=false`, `canPerformGestures=false`, chỉ `typeWindowStateChanged`. |

### Window & Clock

- Type `TYPE_ACCESSIBILITY_OVERLAY`; flags tối thiểu `FLAG_NOT_FOCUSABLE |
  FLAG_NOT_TOUCHABLE | FLAG_LAYOUT_IN_SCREEN`; gravity `TOP | START`; `x=0, y=0`.
- Không dùng `TYPE_APPLICATION_OVERLAY`.
- Hiển thị `HH:mm`/`hh:mm` theo 24-hour setting hệ thống; không giây, không
  date; chỉ redraw theo minute boundary (`TimeAligner`).

### Lifecycle

- `onServiceConnected()`: nếu saved enabled → restore overlay visibility theo
  foreground package hiện biết (unknown → show).
- `onUnbind()`/`onDestroy()`: cancel callbacks, remove overlay, clear service
  instance (`ClockController.onAccessibilityDisconnected`).
- Service re-bind (reboot) → `onAccessibilityConnected` kích hoạt lại overlay.

## 5. Permissions

| Permission | Có/Không | Lý do |
|---|---|---|
| `BIND_ACCESSIBILITY_SERVICE` | Có | chức năng chính |
| Internet | Không | không có chức năng mạng |
| `SYSTEM_ALERT_WINDOW` | Không | overlay dùng `TYPE_ACCESSIBILITY_OVERLAY` |
| Foreground Service | Không | chưa có bằng chứng kỹ thuật bắt buộc |
| `RECEIVE_BOOT_COMPLETED` | Không | Android tự bind lại Accessibility Service |
| `WRITE_SECURE_SETTINGS` | Không | không hợp lệ cho app release |

## 6. Tests

Unit test đã chạy trên máy (`./gradlew test`):

```text
./gradlew test => BUILD SUCCESSFUL
37 tests: 0 failures, 0 errors   (mục tiêu Phase 2: >= 25)
```

Bộ test:

- `ClockStateMachineTest` — 12 tests (giữ toàn bộ test Phase 1 + 2 test mới
  cho `onOverlayActive`).
- `TimeAlignerTest` — 5 tests (giữ nguyên).
- `ClockTextTest` — 4 tests (giữ nguyên).
- `HomeDetectionTest` — 4 tests (mới): home package / other / null / launcher unknown.
- `ClockVisibilityPolicyTest` — 12 tests (mới), bao gồm đúng các ca bắt buộc:
  - Home package → overlay hidden
  - Other package → overlay visible
  - Unknown package → overlay visible
  - OFF → overlay hidden
  - ON + service connected → correct visibility
  - `ON_ACTIVE_SYSTEMUI` không ảnh hưởng runtime overlay.

Hardware matrix (PENDING — chưa có thiết bị):

```text
Home (no duplicate clock):   NOT_TESTED
Maps / YouTube (visible):    NOT_TESTED
Touch passthrough:           NOT_TESTED
Lifecycle / reconnect:       NOT_TESTED
Switch ON/OFF + persistence: NOT_TESTED
Reboot:                      NOT_TESTED
```

## 7. Build readiness

```text
READY
```

Đã chạy trong Phase 2:

- `./gradlew --version` ✓ (Gradle 9.3.1, JDK 17)
- `./gradlew help` ✓
- `./gradlew tasks` ✓
- `./gradlew test` ✓ (37/37, 0 failures, 0 errors)
- `./gradlew :app:compileDebugKotlin` ✓
- `./gradlew lint` ✓ PASS — 0 errors. Các warning đã được xử lý
  (`QueryPermissionsNeeded`, `MissingApplicationIcon`, `ObsoleteSdkInt`,
  `MonochromeLauncherIcon`, `StaticFieldLeak`, `UnusedResources`); chỉ còn
  đúng 1 warning `AndroidGradlePluginVersion` (thông báo có Gradle mới hơn
  9.3.1) — giữ nguyên theo quyết định toolchain vì mạng không tải được Gradle
  mới, đã ghi rõ ở mục 1.

Không chạy: `assembleDebug`, `assembleRelease`, `bundleDebug`, `bundleRelease`,
`installDebug`, `installRelease`.

```text
NO APK WAS BUILT
NO AAB WAS BUILT
```

Đã quét toàn bộ workspace: không tồn tại file `.apk`/`.aab` nào.

## 8. Known limitations

- Chưa có bằng chứng phần cứng: hành vi thực tế của Android Box (immersive
  mode, launcher mặc định, vị trí status bar) còn là giả thuyết tới khi chạy
  `tools/diagnose_device.sh`.
- Overlay bị hệ thống hủy âm thầm (nếu có trên một số OEM) sẽ được repair qua
  `repairIfNeeded()` khi có event cửa sổ tiếp theo; nếu thực tế chứng minh
  overlay bị hủy định kỳ, mới cân nhắc Foreground Service (chưa thêm vì chưa có
  bằng chứng).
- Vị trí `x=0, y=0` theo spec; cần kiểm chứng trực quan trên thiết bị thật.

## 9. Definition of Done — Phase 2

```text
[✓] Runtime dùng Accessibility Overlay (SystemUI chỉ là future/diagnostic)
[✓] Home không bị duplicate clock (overlay hidden khi foreground là launcher)
[✓] Maps/YouTube/other app → overlay visible (logic + unit test)
[✓] Overlay không chặn touch (FLAG_NOT_TOUCHABLE / NOT_FOCUSABLE)
[✓] Service lifecycle đúng (connect/disconnect/rebind, cancel callbacks)
[✓] Switch ON/OFF đúng (enable/disable + state machine)
[✓] Persistence đúng (SharedPreferences enabled)
[✓] Tests pass (37/37, >= 25)
[✓] Gradle configuration ready (Gradle 9.3.1 / AGP 9.1.0 / Kotlin 2.2.21)
[✓] APK chưa build
[✓] AAB chưa build
[ ] Hardware validation (PENDING — cần Android Box kết nối ADB)
```

