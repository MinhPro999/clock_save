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

Không có tính năng phụ nào khác: không giây, không widget, không floating ball,
không Settings riêng, không quảng cáo, không Internet, không server, không
analytics, không database.

---

## Trạng thái hiện tại

```text
PROJECT READY FOR MANUAL APK BUILD
APK BUILD NOT EXECUTED
Hardware validation: PENDING (Android Box chưa kết nối ADB trong lúc triển khai)
```

> ⚠️ **Phiên bản này CHƯA được build thành APK.** Chủ dự án cần kiểm tra source
> code rồi tự build (xem mục “Cách build sau này”).
>
> `./gradlew test` đã chạy và đạt 19/19. `./gradlew lint` cần tải bộ lint 32.1.0
> (~94 MB) nhưng mạng máy hiện tại quá chậm — lý do đã ghi trong
> `IMPLEMENTATION_REPORT.md`; khi mạng tốt chỉ cần chạy lại `./gradlew lint`.

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

> Ghi chú: file `gradle.properties` đang trỏ `android.aapt2FromMavenOverride`
> tới `aapt2` trong SDK của máy hiện tại (để tránh phải tải artifact từ Maven
> trên mạng chậm). Khi build trên máy khác, hãy sửa đường dẫn này hoặc xóa dòng
> override.

Mở thư mục `status_bar_clock/` bằng Android Studio, hoặc dùng dòng lệnh:

```bash
# Kiểm tra cấu hình build (không tạo APK)
./gradlew help
./gradlew tasks
./gradlew test
./gradlew lint
```

### Cách bật Accessibility

1. Mở app **Status Bar Clock**.
2. Bật **Switch ON**.
3. Nếu Accessibility Service chưa được cấp, app sẽ tự mở **Android
   Accessibility Settings** của hệ thống. Tìm **Status Bar Clock** và bật nó.
4. Quay lại app — Switch đã đồng bộ. Xong.

### Cách dùng Switch

- **ON**: đồng hồ luôn hiển thị ở góc trái Status Bar (ưu tiên dùng Clock thật
  của SystemUI; nếu ROM không hỗ trợ thì dùng Accessibility Overlay).
- **OFF**: tắt đồng hồ, overlay bị gỡ hoàn toàn, trạng thái được lưu lại.

---

## Kiến trúc ngắn gọn

```text
MainActivity (chỉ là UI: 1 Switch)
      |
      v
ClockAccessibilityService (lifecycle độc lập)
      |
      +--> ClockController (quyết định chiến lược, state machine)
                |
                +--> SystemUiClockStrategy          (ưu tiên 1: Clock thật của SystemUI)
                +--> AccessibilityOverlayClockStrategy (ưu tiên 2: TYPE_ACCESSIBILITY_OVERLAY)
```

Quy tắc:

- **Không timer 1 giây.** Overlay chỉ redraw mỗi phút, căn đúng ranh giới phút.
- **Không Foreground Service, không WakeLock, không BootReceiver** cho đến khi
  diagnostic thực tế chứng minh là bắt buộc.
- **Không đọc nội dung màn hình**: `canRetrieveWindowContent=false`.
- Activity có thể bị destroy/recreate bất kỳ lúc nào; Service giữ Clock hoạt động.
- Trạng thái duy nhất được lưu: `enabled = true/false` (SharedPreferences).

## File chính

```text
app/src/main/java/com/minh/statusbarclock/
├── MainActivity.kt                       # 1 màn hình, 1 Switch
├── ClockAccessibilityService.kt          # Accessibility Service
├── ClockController.kt                    # Quyết định chiến lược
├── ClockStateMachine.kt                  # State machine thuần Kotlin
├── ClockStrategy.kt                      # Interface chung
├── SystemUiClockStrategy.kt              # Chiến lược ưu tiên 1
├── AccessibilityOverlayClockStrategy.kt  # Chiến lược fallback
├── ClockStateStore.kt                    # SharedPreferences (enabled)
├── ClockText.kt                          # Format HH:mm (thuần Kotlin)
└── TimeAligner.kt                        # Căn ranh giới phút (thuần Kotlin)
```

## Permission rationale

| Permission | Dùng? | Lý do |
|---|---|---|
| `BIND_ACCESSIBILITY_SERVICE` | Có | Bắt buộc để đăng ký Accessibility Service — chức năng chính. |
| Internet | **Không** | Ứng dụng không có chức năng mạng. |
| `SYSTEM_ALERT_WINDOW` | **Không** | Ưu tiên `TYPE_ACCESSIBILITY_OVERLAY`; chỉ thêm nếu diagnostic chứng minh bắt buộc. |
| Foreground Service | **Không** | Chưa cần; chỉ thêm khi lifecycle của overlay bị hệ thống chấm dứt trên thiết bị thật. |
| `RECEIVE_BOOT_COMPLETED` | **Không** | Android tự bind lại Accessibility Service sau reboot; chỉ thêm nếu test thực tế thất bại. |
| `WRITE_SECURE_SETTINGS` | **Không** | Không hợp lệ cho app release; ADB chỉ dùng cho diagnostic. |

## Hardware validation (chờ thiết bị)

Android Box mục tiêu chưa kết nối ADB trong lúc triển khai, nên phần kiểm chứng
phần cứng đang ở trạng thái **PENDING**.

Khi Box kết nối ADB, chạy theo thứ tự:

```bash
# 1. Thu dữ liệu ở 3 trạng thái (script chỉ đọc, KHÔNG sửa hệ thống)
tools/diagnose_device.sh home
tools/diagnose_device.sh maps
tools/diagnose_device.sh youtube

# 2. So sánh
tools/compare_statusbar.sh
```

Dựa trên bằng chứng thu được, quyết định:

- Nếu ROM có cơ chế SystemUI kiểm soát Clock khả dụng → đăng ký
  `SystemUiHook` đã kiểm chứng trong `SystemUiClockStrategy.knownHooks`.
- Nếu không → giữ Accessibility Overlay (hiện là chiến lược mặc định hoạt động).

### Test matrix sau khi có thiết bị

- Clock hiển thị trên Home.
- Clock hiển thị khi Google Maps foreground.
- Clock hiển thị khi YouTube foreground.
- Clock không chặn touch, không ảnh hưởng app đang chạy.
- Màn hình tắt/mở, xoay ngang/dọc, reboot.

## Cách build sau này

> Chủ dự án kiểm tra source trước, sau đó tự build bằng một trong các lệnh sau:

```bash
./gradlew assembleDebug     # APK debug
./gradlew assembleRelease   # APK release (cần cấu hình signing cho release)
./gradlew installDebug      # cài lên thiết bị đang kết nối ADB
```

Lưu ý: project này **chưa hề** được build APK trong quá trình triển khai.

## Tham khảo kỹ thuật

- AccessibilityService:
  https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
- TYPE_ACCESSIBILITY_OVERLAY:
  https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_ACCESSIBILITY_OVERLAY
- Foreground service restrictions:
  https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- AGP 9.1 release notes:
  https://developer.android.com/build/releases/agp-9-1-0-release-notes
- AGP 9.4 release notes (được spec đề xuất; chưa dùng vì mạng không tải được Gradle 9.6):
  https://developer.android.com/build/releases/agp-9-4-0-release-notes
- AOSP Status Bar Clock:
  https://android.googlesource.com/platform/frameworks/base/+/19ed5b0b29d7bf821bcae9ba5b088df03c7056d3/packages/SystemUI/src/com/android/systemui/statusbar/policy/Clock.java
