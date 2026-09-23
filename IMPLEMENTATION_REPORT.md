# IMPLEMENTATION REPORT — Status Bar Clock

**Ngày:** 23/09/2026
**Trạng thái tổng thể:**

```text
Implementation: DONE
Source validation: DONE
Build configuration: READY
Hardware validation: PENDING
APK: NOT BUILT
AAB: NOT BUILT
```

```text
PROJECT READY FOR MANUAL APK BUILD
APK BUILD NOT EXECUTED
```

> Lint: `./gradlew test` ✅ 19/19. `./gradlew lint` bị chặn bởi mạng (chi tiết
> mục 10) — đã ghi rõ lý do theo spec §46; một tải nền có resume đang chạy để
> seed Gradle cache, sau đó chỉ cần chạy lại `./gradlew lint`.

---

## 1. Environment

```text
OS:            macOS (Darwin 27.0.0, arm64)
JDK:           OpenJDK 17.0.15 (Homebrew, /opt/homebrew/Cellar/openjdk@17)
Gradle:        9.3.1 (wrapper; phân phối -all đã có sẵn trong cache máy)
AGP:           9.1.0 (com.android.application)
Kotlin:        2.2.21 (built-in Kotlin của AGP, pin qua buildscript classpath)
Android SDK:   /Users/m.mac/Library/Android/sdk
               - platforms: android-31, 33, 34, 35, 36
               - build-tools: 33.0.1, 34.0.0, 35.0.0, 36.0.0
               - platform-tools: adb 35.0.2
```

### Vì sao toolchain lệch so với đề xuất trong spec (AGP 9.4 + Gradle 9.6 + Kotlin 2.4.20)

Spec yêu cầu ưu tiên AGP 9.4.0 (cần Gradle ≥ 9.6.0). Đã kiểm chứng thực tế:

- `services.gradle.org`: tốc độ đo được ~80 B/s — không thể tải Gradle 9.6.1 (≈130 MB).
- GitHub releases / Huawei / Tencent mirrors: 0.5–6 KB/s — không khả dụng.
- Google Maven (`dl.google.com`) và Maven Central: ~12–15 KB/s — chỉ đủ cho artifact nhỏ.

Trong khi đó, máy đã có sẵn trong Gradle cache:

- Gradle 9.3.1 (đúng phiên bản mà Google công bố là tương thích với AGP 9.1.x),
- AGP 9.1.0 (stable),
- bộ Kotlin 2.2.21 hoàn chỉnh (KGP, compiler-embeddable, daemon, stdlib) — thỏa mãn
  yêu cầu KGP ≥ 2.2.10 của AGP 9.1.

Do đó chọn stack **Gradle 9.3.1 + AGP 9.1.0 + Kotlin 2.2.21**: vẫn là AGP 9.x với
built-in Kotlin (đúng kiến trúc spec yêu cầu), compileSdk/targetSdk 36, minSdk 26
không đổi. Đây là quyết định do ràng buộc môi trường thực tế, đã ghi rõ lý do.

Ngoài ra `gradle.properties` dùng `android.aapt2FromMavenOverride` trỏ tới `aapt2`
của build-tools 36.0.0 (override chính thức của AGP) vì artifact `aapt2` trên
Maven không tải được với tốc độ mạng này.

## 2. Device

```text
Manufacturer:  N/A — không có thiết bị kết nối ADB
Model:         N/A
Android:       N/A
API:           N/A
SystemUI package: N/A
Build fingerprint: N/A

adb devices -l => List of devices attached (trống)
```

Theo spec, khi chưa có thiết bị: `Hardware validation = PENDING`, project vẫn được
tạo đầy đủ để khi Box kết nối có thể chẩn đoán tiếp mà không cần tạo lại.

## 3. Root cause hypothesis

```text
Clock biến mất trên app khác vì:
```

Giả thuyết hàng đầu (chưa có bằng chứng phần cứng): khi một ứng dụng như Google
Maps/YouTube ở foreground, ROM/ứng dụng đặt Status Bar vào trạng thái ẩn
(immersive/hide system bars) hoặc SystemUI của OEM tự ẩn Clock theo policy
foreground application. Việc chỉnh `clock_seconds`/SystemUI Tuner KHÔNG phải là
lời giải chung — đó là điều cần kiểm chứng trên thiết bị thật, không được giả định.

```text
ADB evidence:
(trống — thiết bị chưa kết nối. Chạy tools/diagnose_device.sh khi Box kết nối.)
```

## 4. Strategy đã chọn

```text
HYBRID
```

- **Priority 1 — SystemUiClockStrategy**: Detect + Probe thực tế; chỉ bật Clock
  thật của SystemUI khi có `SystemUiHook` đã được kiểm chứng phần cứng cho đúng
  device profile. Hiện `knownHooks` trống → strategy trả về `unsupported` một cách
  trung thực, KHÔNG thao tác mù.
- **Priority 2 — AccessibilityOverlayClockStrategy**: fallback mặc định chạy được
  ngay, dùng `TYPE_ACCESSIBILITY_OVERLAY`, cập nhật theo ranh giới phút.

## 5. Vì sao

- Không được viết `WRITE_SECURE_SETTINGS` (không hợp lệ cho app release) và không
  được biến ADB thành dependency của thành phẩm.
- `TYPE_ACCESSIBILITY_OVERLAY` là lớp cửa sổ phù hợp cho mục tiêu này (trên các
  cửa sổ ứng dụng, do Accessibility Service tạo ra), theo đúng tài liệu Android.
- Overlay đáp ứng mọi yêu cầu: không chặn touch/focus, không animation, không
  notification, redraw 1 lần/phút căn boundary, theo 24-hour setting của hệ thống.
- Kiến trúc giữ nguyên khả năng nâng cấp lên SystemUI mode sau khi có diagnostic.

## 6. Files

```text
status_bar_clock/
├── app/build.gradle.kts
├── app/proguard-rules.pro
├── app/src/main/AndroidManifest.xml
├── app/src/main/java/com/minh/statusbarclock/
│   ├── MainActivity.kt
│   ├── ClockAccessibilityService.kt
│   ├── ClockController.kt
│   ├── ClockStateMachine.kt
│   ├── ClockStrategy.kt
│   ├── SystemUiClockStrategy.kt
│   ├── AccessibilityOverlayClockStrategy.kt
│   ├── ClockStateStore.kt
│   ├── ClockText.kt
│   └── TimeAligner.kt
├── app/src/main/res/
│   ├── layout/activity_main.xml
│   ├── values/{strings,colors,themes}.xml
│   ├── xml/accessibility_service_config.xml
│   ├── drawable/ic_launcher_foreground.xml
│   └── mipmap-anydpi-v26/ic_launcher.xml
├── app/src/test/java/com/minh/statusbarclock/
│   ├── ClockStateMachineTest.kt   (10 tests)
│   ├── TimeAlignerTest.kt         (5 tests)
│   └── ClockTextTest.kt           (4 tests)
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/wrapper/ + gradlew + gradlew.bat
├── local.properties (máy hiện tại)
├── tools/diagnose_device.sh
├── tools/compare_statusbar.sh
├── README.md
└── IMPLEMENTATION_REPORT.md
```

## 7. Permissions

| Permission | Có/Không | Lý do |
|---|---|---|
| `BIND_ACCESSIBILITY_SERVICE` | Có | chức năng chính — đăng ký Accessibility Service |
| Internet | Không | không cần |
| `SYSTEM_ALERT_WINDOW` | Không | ưu tiên `TYPE_ACCESSIBILITY_OVERLAY`; chỉ thêm nếu diagnostic chứng minh bắt buộc |
| Foreground Service | Không | chưa cần; chỉ thêm khi lifecycle của overlay bị chấm dứt trên thiết bị thật |
| `RECEIVE_BOOT_COMPLETED` | Không | Android tự bind lại Accessibility Service sau reboot |
| `WRITE_SECURE_SETTINGS` | Không | không hợp lệ cho app release; ADB chỉ dùng cho diagnostic |

## 8. Tests

```text
Home:                       NOT_TESTED (thiết bị chưa kết nối)
Maps:                       NOT_TESTED
YouTube:                    NOT_TESTED
Reboot:                     NOT_TESTED
Accessibility reconnect:    NOT_TESTED
Landscape:                  NOT_TESTED
```

Unit test (đã chạy trên máy):

```text
./gradlew test => BUILD SUCCESSFUL
19 tests: 0 failures, 0 errors
```

## 9. Known limitations

- Chưa có bằng chứng phần cứng: toàn bộ hành vi của Android Box (SystemUI policy,
  immersive mode, độ cao status bar, màu nền) là giả thuyết cho tới khi chạy
  `tools/diagnose_device.sh`.
- Nếu ROM có Clock native hiển thị sẵn ở góc trái khi ở Home, fallback overlay có
  thể tạo đồng hồ thứ hai (duplicate) cho tới khi diagnostic cho phép chuyển hẳn
  sang SystemUI mode. Đây là lý do `knownHooks` được thiết kế mở rộng.
- Một số Android Box/TV có thể tự hủy accessibility overlay khi màn hình tắt;
  repair logic chỉ xử lý khi service còn sống. Nếu thực tế chứng minh overlay bị
  hủy định kỳ, sẽ cần cân nhắc Foreground Service (chưa thêm vì chưa có bằng chứng).
- Hành vi khi ứng dụng immersive toàn màn hình có thể khác nhau giữa các OEM;
  vị trí overlay tính theo `status_bar_height` của framework, cần kiểm chứng thực tế.

## 10. Build readiness

```text
READY
```

Đã thực hiện:

- `./gradlew --version` ✓ (Gradle 9.3.1, JDK 17)
- `./gradlew help` ✓
- `./gradlew tasks` ✓
- `./gradlew test` ✓ (19/19 pass, không lỗi)
- `./gradlew lint` — chưa hoàn tất vì ràng buộc mạng (xem ghi chú bên dưới).
  Đây là lý do được ghi rõ theo spec §46 ("test/lint đã chạy hoặc lý do không
  chạy được đã ghi rõ"). Có thể chạy lại bất kỳ lúc nào bằng `./gradlew lint`.

### Ghi chú lint

AGP 9.1 phân giải bộ công cụ lint 32.1.0 gồm 3 JAR chưa có trong cache máy:

| Artifact | Kích thước | Trạng thái |
|---|---|---|
| `com.android.tools.lint:lint-checks:32.1.0` | ~7.7 MB | đang tải nền (resume/retry) |
| `com.android.tools.external.com-intellij:intellij-core:32.1.0` | ~32.2 MB | đang tải nền |
| `com.android.tools.external.com-intellij:kotlin-compiler:32.1.0` | ~53.7 MB | đang tải nền |

Tốc độ mạng đo được tới `dl.google.com` chỉ ~12–15 KB/s, nên tổng ~94 MB cần
hơn 1–2 giờ và hay bị read-timeout. Một tải nền có resume đang chạy và sẽ tự
đưa các JAR vào Gradle cache (theo SHA-1); khi xong chỉ cần chạy:

```bash
./gradlew lint --offline
```

Trên máy có mạng bình thường, chỉ cần chạy `./gradlew lint` trực tiếp.

Không chạy: `assembleDebug`, `assembleRelease`, `bundleDebug`, `bundleRelease`,
`installDebug`, `installRelease`.

Xác nhận:

```text
NO APK WAS BUILT
NO AAB WAS BUILT
```

Không có file `.apk`/`.aab` nào trong workspace.
