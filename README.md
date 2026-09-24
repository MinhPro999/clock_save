# Clock Save

Ứng dụng Android native tối giản với **một chức năng duy nhất**: giữ đồng hồ
thời gian luôn hiển thị ở vùng Status Bar của Android Box — kể cả khi một ứng
dụng khác (Google Maps, YouTube…) đang ở foreground.

```text
+------------------------------------------+
|  22:38                              ...  |
+------------------------------------------+

Google Maps đang foreground
=> Clock vẫn nhìn thấy.
```

Không có tính năng phụ nào khác: không giây, không date, không widget, không
floating ball, không Settings riêng, không quảng cáo, không Internet, không
server, không analytics, không database.

---

## Trạng thái hiện tại

```text
Source implementation: COMPLETE
Build configuration: READY
Unit tests: PASS (37/37)
Lint: PASS
Hardware validation: PENDING (Android Box chưa kết nối ADB)
APK: NOT BUILT
AAB: NOT BUILT
```

> ⚠️ **Project này CHƯA được build thành APK/AAB** (đúng quy trình Phase 2:
> chỉ validate source, test, lint và compile). Chủ dự án tự build khi sang
> Phase tiếp theo (xem mục “Cách build sau này”).

---

## Cách chạy project

Yêu cầu môi trường:

- JDK 17
- Android SDK (đã cài platform `android-36` và build-tools `36.0.0`)
- Gradle wrapper đi kèm project (`./gradlew`, Gradle 9.3.1)

Toolchain (đã kiểm chứng với môi trường thực tế, xem `IMPLEMENTATION_REPORT.md`):

| Thành phần | Phiên bản |
|---|---|
| Gradle | 9.3.1 |
| Android Gradle Plugin | 9.1.0 |
| Kotlin (built-in Kotlin của AGP) | 2.2.21 |
| JDK | 17 |
| compileSdk / targetSdk | 36 |
| minSdk | 26 |

> Ghi chú: file `gradle.properties` có đúng một dòng trỏ đường dẫn máy cá nhân:
> `android.aapt2FromMavenOverride` tới `aapt2` trong SDK máy hiện tại (bắt buộc
> vì mạng không tải được artifact aapt2 từ Maven). Khi build trên máy khác, hãy
> sửa đường dẫn này hoặc xóa dòng override.

Mở thư mục `status_bar_clock/` bằng Android Studio, hoặc dùng dòng lệnh:

```bash
# Kiểm tra cấu hình build (không tạo APK)
./gradlew --version
./gradlew help
./gradlew tasks
./gradlew test
./gradlew lint
./gradlew :app:compileDebugKotlin
```

### Cách bật Accessibility

1. Mở app **Status Bar Clock**.
2. Bật **Switch ON**.
3. Nếu Accessibility Service chưa được cấp, app sẽ tự mở **Android
   Accessibility Settings** của hệ thống. Tìm **Status Bar Clock** và bật nó.
4. Quay lại app — Switch đã đồng bộ. Xong.

### Cách dùng Switch

- **ON**: overlay Accessibility bật. Khi ứng dụng khác ở foreground (Maps,
  YouTube…) đồng hồ hiển thị ở góc trái; khi ở Home thì overlay ẩn (không bị
  duplicate clock với đồng hồ của launcher/SystemUI).
- **OFF**: tắt hoàn toàn, overlay bị gỡ, trạng thái được lưu lại.

---

## Kiến trúc

```text
AccessibilityService (TYPE_WINDOW_STATE_CHANGED -> packageName)
      |
      v
ClockController (state machine + visibility policy)
      |
      v
AccessibilityOverlayClockStrategy (TYPE_ACCESSIBILITY_OVERLAY)
      |
      +-- HOME       => overlay hidden
      +-- OTHER APP  => overlay visible
```

Quy tắc runtime:

- **Runtime duy nhất là Accessibility Overlay.** Không Foreground Service,
  không `WRITE_SECURE_SETTINGS`, không `SYSTEM_ALERT_WINDOW`, không phụ thuộc
  ADB.
- `SystemUiClockStrategy` (điều khiển Clock thật của SystemUI) hiện chỉ là
  **future/diagnostic extension**: giữ lại trong source nhưng controller không
  hề gọi nó, và `knownHooks = emptyList()` không ảnh hưởng runtime.
- **Không timer 1 giây.** Overlay chỉ redraw mỗi phút, căn đúng ranh giới phút.
- **Không add/remove window liên tục theo từng event.** `show()/hide()` chỉ đổi
  visibility của view; nếu visibility đã đúng thì không làm gì.
- **Không đọc nội dung màn hình**: `canRetrieveWindowContent=false`; chỉ dùng
  `event.packageName` cho quyết định HOME, không log package/content của app khác.
- Activity có thể bị destroy/recreate bất kỳ lúc nào; Service giữ Clock hoạt động.
- Trạng thái duy nhất được lưu: `enabled = true/false` (SharedPreferences).

### Window & Clock

- Window type: `TYPE_ACCESSIBILITY_OVERLAY` (layer 30 — nằm trên
  `TYPE_STATUS_BAR` layer 17 trong AOSP).
- Flags tối thiểu: `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE | FLAG_LAYOUT_IN_SCREEN`.
- Gravity `TOP | START`, vị trí `x = 0`, `y = 0`.
- Hiển thị `HH:mm` / `hh:mm` theo 24-hour setting của hệ thống.

## File chính

```text
app/src/main/java/com/minh/statusbarclock/
├── MainActivity.kt                       # 1 màn hình, 1 Switch
├── ClockAccessibilityService.kt          # Accessibility Service
├── ClockController.kt                    # Quyết định show/hide overlay
├── ClockVisibilityPolicy.kt              # Luật hiển thị (thuần Kotlin)
├── HomeDetector.kt                       # Phát hiện launcher (ACTION_MAIN + CATEGORY_HOME)
├── ClockStateMachine.kt                  # State machine thuần Kotlin
├── ClockStrategy.kt                      # Interface chung
├── AccessibilityOverlayClockStrategy.kt  # Runtime chính (overlay)
├── SystemUiClockStrategy.kt              # Future/diagnostic extension
├── ClockStateStore.kt                    # SharedPreferences (enabled)
├── ClockText.kt                          # Format HH:mm (thuần Kotlin)
└── TimeAligner.kt                        # Căn ranh giới phút (thuần Kotlin)
```

## Permission rationale

| Permission | Dùng? | Lý do |
|---|---|---|
| `BIND_ACCESSIBILITY_SERVICE` | Có | Bắt buộc để đăng ký Accessibility Service — chức năng chính. |
| Internet | **Không** | Ứng dụng không có chức năng mạng. |
| `SYSTEM_ALERT_WINDOW` | **Không** | Overlay dùng `TYPE_ACCESSIBILITY_OVERLAY` (không phải `TYPE_APPLICATION_OVERLAY`). |
| Foreground Service | **Không** | Accessibility Service tự giữ lifecycle; chưa có bằng chứng bắt buộc. |
| `RECEIVE_BOOT_COMPLETED` | **Không** | Android tự bind lại Accessibility Service sau reboot. |
| `WRITE_SECURE_SETTINGS` | **Không** | Không hợp lệ cho app release; ADB chỉ dùng cho diagnostic. |

## Hardware validation (chờ thiết bị)

Android Box mục tiêu chưa kết nối ADB, nên phần kiểm chứng phần cứng đang ở
trạng thái **PENDING**. Chưa có bất kỳ khẳng định nào về reboot hay tương thích
OEM cho tới khi test trên thiết bị thật.

Khi Box kết nối ADB, chạy theo thứ tự:

```bash
# 1. Thu dữ liệu ở 3 trạng thái (script chỉ đọc, KHÔNG sửa hệ thống)
tools/diagnose_device.sh home
tools/diagnose_device.sh maps
tools/diagnose_device.sh youtube

# 2. So sánh
tools/compare_statusbar.sh
```

### Test matrix sau khi có thiết bị

- Home: overlay **ẩn** (không duplicate clock).
- Google Maps / YouTube foreground: overlay **hiển thị**.
- Clock không chặn touch, không ảnh hưởng app đang chạy.
- Màn hình tắt/mở, xoay ngang/dọc, reboot (Accessibility re-bind).
- Bật/tắt Switch, persistence sau khi thoát app.

## Cách build sau này

> Phase hiện tại **không build APK/AAB**. Khi chuyển sang phase build, chủ dự án
> tự chạy:

```bash
./gradlew assembleDebug     # APK debug
./gradlew assembleRelease   # APK release (cần cấu hình signing cho release)
./gradlew installDebug      # cài lên thiết bị đang kết nối ADB
```

## Tham khảo kỹ thuật

- AccessibilityService:
  https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
- TYPE_ACCESSIBILITY_OVERLAY:
  https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_ACCESSIBILITY_OVERLAY
- Foreground service restrictions:
  https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- AGP 9.1 release notes:
  https://developer.android.com/build/releases/agp-9-1-0-release-notes
- AOSP Status Bar Clock:
  https://android.googlesource.com/platform/frameworks/base/+/19ed5b0b29d7bf821bcae9ba5b088df03c7056d3/packages/SystemUI/src/com/android/systemui/statusbar/policy/Clock.java

