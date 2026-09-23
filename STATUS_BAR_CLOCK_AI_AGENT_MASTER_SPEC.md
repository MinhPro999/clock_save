# STATUS BAR CLOCK — AI AGENT MASTER IMPLEMENTATION SPECIFICATION

**Phiên bản:** 1.0  
**Ngày:** 23/09/2026  
**Mục tiêu:** Hướng dẫn AI Agent tự triển khai toàn bộ một ứng dụng Android native từ con số 0 đến trạng thái **sẵn sàng build APK**, nhưng **TUYỆT ĐỐI KHÔNG tự build APK trong giai đoạn này**.

---

# 1. Tuyên bố nhiệm vụ

AI Agent phải tự mình thực hiện toàn bộ dự án sau khi nhận file này:

1. Kiểm tra môi trường phát triển Android.
2. Kiểm tra thiết bị Android Box nếu thiết bị đang kết nối ADB.
3. Chẩn đoán chính xác cách Android Box đang hiển thị/ẩn Clock trên Status Bar.
4. Tạo project Android native mới từ con số 0.
5. Thiết kế kiến trúc Kotlin tối giản.
6. Implement chức năng hiển thị Clock trên Status Bar.
7. Ưu tiên sử dụng Clock/SystemUI thật nếu ROM của Android Box cho phép.
8. Có Accessibility Overlay làm fallback khi SystemUI không thể duy trì Clock.
9. Cơ chế phải chịu được việc người dùng mở Google Maps, YouTube hoặc ứng dụng khác.
10. Cơ chế phải được kiểm tra sau reboot nếu có thiết bị thật.
11. Kiểm tra source code, manifest, Gradle configuration, lint, test và khả năng cấu hình build.
12. Dừng lại tại trạng thái **PROJECT READY FOR MANUAL APK BUILD**.
13. **KHÔNG chạy `assembleDebug`, `assembleRelease`, `bundle`, `install`, hoặc bất kỳ lệnh nào tạo APK/AAB**, trừ khi người dùng yêu cầu riêng ở giai đoạn sau.

AI Agent không được hỏi lại những thông tin đã có trong tài liệu này. Nếu thiếu dữ liệu phần cứng, hãy tiếp tục tạo project và ghi rõ phần nào đang chờ kiểm thử thực tế.

---

# 2. Phạm vi sản phẩm — CỰC KỲ NGHIÊM NGẶT

Ứng dụng chỉ có **MỘT chức năng duy nhất**:

> Làm cho đồng hồ thời gian luôn hiển thị ở vùng Status Bar trên Android Box, kể cả khi một ứng dụng khác như Google Maps hoặc YouTube đang ở foreground.

Không phát triển bất kỳ tính năng phụ nào.

## Không được thêm

- Hiển thị giây.
- Floating ball.
- Widget.
- Quick Settings tile.
- Notification.
- Internet.
- Server.
- Firebase.
- Analytics.
- AdMob.
- quảng cáo.
- tài khoản.
- đăng nhập.
- cơ sở dữ liệu.
- đồng bộ dữ liệu.
- font selector.
- màu sắc tùy chỉnh.
- kích thước Clock tùy chỉnh.
- vị trí tùy chỉnh cho người dùng.
- lựa chọn 12/24 giờ trong app.
- màn hình Settings riêng.
- Help/About riêng.
- theme selector.
- nhiều nút điều khiển.
- subscription.
- purchase.
- permission manager phức tạp.

## UI ứng dụng

Chỉ được có **một màn hình chính** và **một công tắc ON/OFF**.

Có thể có:

- tên ứng dụng.
- một dòng mô tả rất ngắn.
- một Switch duy nhất.

Không tạo thêm button để mở Settings.

Khi người dùng bật Switch mà Accessibility Service chưa được cấp quyền, app có thể mở **Android Accessibility Settings của hệ thống**. Đây là luồng bắt buộc do hệ điều hành, không được tạo thêm một giao diện cấp quyền riêng.

---

# 3. Bối cảnh phần cứng thực tế

Android Box mục tiêu có hành vi:

### Home

Clock xuất hiện:

```text
+------------------------------------------+
|  22:38                              ...  |
+------------------------------------------+
```

### Khi mở Google Maps / YouTube

Status Bar vẫn còn nhưng Clock biến mất:

```text
+------------------------------------------+
|                                     ...  |
+------------------------------------------+
```

Mục tiêu của project:

```text
+------------------------------------------+
|  22:38                              ...  |
+------------------------------------------+

Google Maps đang foreground
=> Clock vẫn phải nhìn thấy.
```

Đây là yêu cầu quan trọng nhất để đánh giá thành công.

---

# 4. Tham chiếu hành vi của ứng dụng hiện hữu

Ứng dụng tham khảo:

`Status Bar Clock: Show Seconds`

Package:

`com.idealai.showseconds`

Google Play hiện mô tả rằng ứng dụng này sử dụng Accessibility Service API để nhanh chóng bật hiển thị seconds trên Status Bar.

Nguồn:

https://play.google.com/store/apps/details?id=com.idealai.showseconds

Nguồn APK metadata/lịch sử phát hành có thể được dùng để hiểu hành vi bên ngoài, nhưng:

> **KHÔNG sao chép mã nguồn, tài nguyên, tên, branding, package hoặc logic độc quyền của ứng dụng đó.**

Chỉ dùng behavior làm reference.

Đặc biệt, ứng dụng mục tiêu của chúng ta **không cần seconds**.

---

# 5. Giả thuyết kỹ thuật cần kiểm chứng

AOSP có Status Bar Clock thuộc SystemUI.

Trong các nhánh AOSP có cơ chế:

```text
com.android.systemui.statusbar.policy.Clock
```

và:

```text
clock_seconds
```

Ngoài ra, SystemUI có cơ chế chính sách/visibility khiến Clock có thể bị ẩn.

Nguồn AOSP tham khảo:

https://android.googlesource.com/platform/frameworks/base/+/19ed5b0b29d7bf821bcae9ba5b088df03c7056d3/packages/SystemUI/src/com/android/systemui/statusbar/policy/Clock.java

Không được giả định rằng mọi OEM/Android Box đều dùng chính xác implementation của AOSP.

Mục tiêu của AI Agent là:

> **Xác định hành vi thực tế của ROM trước khi quyết định implementation cuối cùng.**

---

# 6. Kiến trúc mục tiêu

Ưu tiên:

```text
MainActivity
      |
      v
AccessibilityService
      |
      +-----------------------------+
      |                             |
      v                             v
SystemUI Clock Strategy       Accessibility Overlay Strategy
      |                             |
      |                             |
      +-------------+---------------+
                    |
                    v
          Clock luôn nhìn thấy
```

## Thứ tự ưu tiên

### Priority 1 — SystemUI / native Clock

Nếu Android Box có Clock thật trong SystemUI nhưng bị hide theo foreground application, ưu tiên tìm cách điều khiển/khôi phục **native SystemUI Clock**.

Ưu điểm:

- đúng SystemUI.
- không phải tự cập nhật thời gian.
- ít tài nguyên.
- ít code.
- Clock tự theo múi giờ và locale hệ thống.
- sau khi cấu hình thành công, app có thể không cần chạy liên tục.

### Priority 2 — Accessibility Overlay

Nếu SystemUI method không thể làm Clock luôn hiển thị, sử dụng:

```text
AccessibilityService
        |
        v
TYPE_ACCESSIBILITY_OVERLAY
        |
        v
Clock View
```

Android xác nhận Accessibility Service có thể tạo accessibility overlay trên nội dung màn hình.

Nguồn chính thức:

https://developer.android.com/reference/android/accessibilityservice/AccessibilityService

https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_ACCESSIBILITY_OVERLAY

**Không mặc định sử dụng `TYPE_APPLICATION_OVERLAY`.**

Lý do:

`TYPE_APPLICATION_OVERLAY` không phải lựa chọn tối ưu cho mục tiêu này vì Application Overlay chịu thứ tự lớp dưới các cửa sổ hệ thống quan trọng.

Chỉ dùng `SYSTEM_ALERT_WINDOW` nếu quá trình chẩn đoán và thử nghiệm thực tế chứng minh rằng Android Box bắt buộc phải dùng nó. Không thêm permission thừa.

---

# 7. Nguyên tắc kiến trúc: app không phải chạy đồng hồ 24/7 nếu không cần

Không được mặc định thiết kế:

```text
Timer 1 giây
+
Foreground Service
+
WakeLock
```

chỉ để hiện giờ.

Nếu native SystemUI Clock hoạt động, SystemUI vốn đã có logic cập nhật Clock.

Mục tiêu cần đạt là:

```text
App cấu hình một lần
        |
        v
SystemUI tiếp quản Clock
        |
        v
App không cần tự chạy timer
```

Foreground Service chỉ được thêm nếu sau khi thử nghiệm fallback thực tế chứng minh là cần thiết.

Đặc biệt tránh thêm Foreground Service chỉ vì yêu cầu "chạy nền".

---

# 8. Giai đoạn 0 — kiểm tra môi trường trước khi tạo project

AI Agent phải chạy kiểm tra trong Terminal.

## 8.1 Kiểm tra Java

```bash
java -version
```

Yêu cầu ưu tiên:

```text
JDK 17
```

## 8.2 Kiểm tra Android SDK

```bash
echo "$ANDROID_HOME"
echo "$ANDROID_SDK_ROOT"
adb version
sdkmanager --list | head -n 40
```

## 8.3 Kiểm tra Gradle

```bash
gradle --version
```

## 8.4 Kiểm tra thiết bị ADB

```bash
adb devices -l
```

Nếu có Android Box:

```text
device
```

thì tiếp tục chẩn đoán phần cứng.

Nếu không có thiết bị:

> Không được dừng project. Tạo project và chuyển phần hardware validation sang trạng thái PENDING_DEVICE_TEST.

---

# 9. Toolchain đề xuất tại thời điểm tạo project

Tại ngày 23/09/2026:

- Android Gradle Plugin: ưu tiên stable hiện hành nếu tương thích.
- AGP 9.4.0 là stable hiện hành được Android Developers công bố ngày 18/09/2026.
- AGP 9.4 yêu cầu Gradle 9.6.0 tối thiểu/default và JDK 17.
- Android 17/API 37 là API tối đa được AGP 9.4 hỗ trợ.
- Kotlin stable hiện hành: 2.4.20, phát hành 07/09/2026.

Nguồn:

https://developer.android.com/build/releases/agp-9-4-0-release-notes

https://kotlinlang.org/docs/releases.html

https://kotlinlang.org/docs/whatsnew2420.html

### Quy tắc

AI Agent phải kiểm tra môi trường thực tế trước.

Không hạ Gradle/AGP/Kotlin xuống phiên bản cũ chỉ để "cho dễ".

Không dùng Kotlin EAP/Beta nếu stable có thể đáp ứng.

Không dùng dependency ngoài nếu không cần.

---

# 10. Target SDK / Compile SDK

Ưu tiên:

```text
compileSdk = 36
targetSdk = 36
minSdk = 26
```

Lý do:

- Android 16/API 36 là lựa chọn stable phù hợp cho production baseline.
- Không cần target API 37 beta chỉ vì AGP hỗ trợ API 37.
- minSdk 26 giúp code đơn giản và vẫn bao phủ Android Box tương đối rộng.

Nếu môi trường/thiết bị mục tiêu yêu cầu baseline khác, AI Agent có thể thay đổi nhưng phải ghi rõ lý do trong report.

---

# 11. Tên project

Project name:

```text
status_bar_clock
```

Application label:

```text
Status Bar Clock
```

Package:

```text
com.minh.statusbarclock
```

Nếu package này đã tồn tại trong workspace thì đổi sang package độc đáo khác nhưng phải ghi rõ.

---

# 12. Project structure bắt buộc

Tạo project tối giản.

Gợi ý:

```text
status_bar_clock/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/
│   │       │   └── com/minh/statusbarclock/
│   │       │       ├── MainActivity.kt
│   │       │       ├── ClockAccessibilityService.kt
│   │       │       ├── ClockController.kt
│   │       │       ├── SystemUiClockStrategy.kt
│   │       │       └── AccessibilityOverlayClockStrategy.kt
│   │       ├── res/
│   │       │   ├── layout/
│   │       │   │   └── activity_main.xml
│   │       │   ├── values/
│   │       │   │   ├── strings.xml
│   │       │   │   ├── colors.xml
│   │       │   │   └── themes.xml
│   │       │   └── xml/
│   │       │       └── accessibility_service_config.xml
│   │       └── AndroidManifest.xml
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
└── README.md
```

Không tạo package/folder cho những tính năng không tồn tại.

---

# 13. MainActivity

MainActivity chỉ có nhiệm vụ:

1. Hiển thị Switch.
2. Kiểm tra trạng thái Accessibility Service.
3. Khi OFF:
   - tắt Clock.
   - remove fallback overlay nếu đang tồn tại.
4. Khi ON:
   - nếu Accessibility Service chưa bật thì mở Accessibility Settings.
   - sau khi user bật service và quay lại, đồng bộ Switch.
5. Không làm việc nặng.
6. Không chứa Timer.
7. Không duy trì Clock bằng Activity lifecycle.

Activity có thể bị destroy/recreate mà Clock vẫn phải hoạt động.

---

# 14. UI duy nhất

Giao diện mục tiêu:

```text
+--------------------------------+
|                                |
|       STATUS BAR CLOCK         |
|                                |
|      Hiển thị đồng hồ          |
|                                |
|                    [ ON ]      |
|                                |
+--------------------------------+
```

Không cần thiết kế đẹp phức tạp.

Có thể dùng Android standard widget.

Switch là control duy nhất.

Không thêm:

```text
Settings
Font
Color
Position
Seconds
About
Help
```

---

# 15. Persistent state

Dùng `SharedPreferences` hoặc `DataStore` tùy lựa chọn đơn giản nhất.

Chỉ cần lưu:

```text
enabled = true / false
```

Không lưu dữ liệu khác.

Không cần database.

Khi app được mở lại:

```text
UI Switch = trạng thái saved
```

Nhưng trạng thái thực tế của Accessibility Service phải luôn được kiểm tra lại, không được chỉ tin vào preference.

---

# 16. Accessibility Service

Tạo:

```text
ClockAccessibilityService.kt
```

Service phải:

- khai báo đúng `BIND_ACCESSIBILITY_SERVICE`.
- có accessibility service XML.
- chỉ xin capability cần thiết.
- không thu thập dữ liệu người dùng.
- không đọc nội dung các ứng dụng vì mục tiêu không cần thiết.
- không ghi log nội dung màn hình.
- không can thiệp vào thao tác người dùng ngoài phần cần cho Clock.

Nếu có thể triển khai mà không cần `canRetrieveWindowContent=true`, không bật capability đó.

Nếu SystemUI strategy cần inspect UI để thực hiện thao tác, chỉ bật capability tối thiểu cần thiết và ghi rõ trong README/manifest rationale.

Nguồn chính thức:

https://developer.android.com/reference/android/accessibilityservice/AccessibilityService

---

# 17. SystemUI strategy

Tạo abstraction:

```kotlin
interface ClockStrategy {
    fun start(): Boolean
    fun stop()
    fun isSupported(): Boolean
}
```

Tạo ít nhất:

```text
SystemUiClockStrategy
AccessibilityOverlayClockStrategy
```

`ClockController` quyết định chiến lược.

---

# 18. SystemUiClockStrategy — yêu cầu

Không được hard-code một hành vi giả định rằng mọi Android đều giống nhau.

Strategy phải trải qua các bước:

```text
1. Detect
2. Probe
3. Enable
4. Verify
5. Monitor
6. Repair if needed
```

## Detect

Phải xác định:

- Android API level.
- Build fingerprint.
- manufacturer.
- model.
- SystemUI package.
- các key SystemUI/Secure Settings liên quan Clock nếu có thể quan sát.

## Probe

Kiểm tra những key có thể liên quan:

```bash
adb shell settings get secure clock_seconds
adb shell settings get secure icon_blacklist
```

và:

```bash
adb shell dumpsys statusbar
```

Không được giả định rằng hai key này chắc chắn tồn tại trên OEM.

## Foreground comparison

Nếu có Android Box, thực hiện dump ở hai trạng thái:

### Trạng thái A

Home đang hiển thị.

### Trạng thái B

Google Maps hoặc YouTube đang foreground.

So sánh:

```text
dumpsys statusbar
dumpsys window windows
settings list secure | grep -i clock
settings get secure icon_blacklist
```

Mục tiêu là tìm tín hiệu cho thấy Clock bị ẩn khi foreground application thay đổi.

---

# 19. Không được dùng WRITE_SECURE_SETTINGS như một cách hợp pháp cho app release

Không được thiết kế app release dựa trên:

```text
android.permission.WRITE_SECURE_SETTINGS
```

vì đây không phải quyền thông thường dành cho third-party application.

ADB có thể dùng trong giai đoạn **diagnostic only** để kiểm chứng giả thuyết.

Ví dụ:

```bash
adb shell settings put secure clock_seconds 1
```

chỉ được sử dụng trong phòng thí nghiệm để tìm hiểu SystemUI behavior nếu cần.

Không biến lệnh ADB thành dependency bắt buộc của application.

---

# 20. Kiểm tra một giả thuyết cụ thể về native SystemUI

Nếu Android Box cho phép kiểm tra trực tiếp, AI Agent phải thử:

```bash
adb shell settings get secure clock_seconds
```

và nếu phù hợp với ROM:

```bash
adb shell settings put secure clock_seconds 0
```

Sau đó quan sát:

- Home.
- Google Maps.
- YouTube.

Mục đích không phải là sử dụng seconds, mà là xác minh:

> SystemUI Clock có thực sự tồn tại và phản ứng với SystemUI tuner configuration hay không?

Nếu thay đổi này không ảnh hưởng đến Clock visibility, không tiếp tục giả định rằng `clock_seconds` là lời giải.

---

# 21. Nếu tìm thấy SystemUI mechanism có thể điều khiển bằng Accessibility

Nếu diagnostics chứng minh có một SystemUI/Settings UI mà user có thể bật/tắt Clock, AccessibilityService có thể:

1. mở màn hình hệ thống tương ứng.
2. nhận Accessibility events.
3. tìm node đúng bằng:
   - text/content description/resource id nếu ổn định.
4. click/check node.
5. thoát màn hình hệ thống.
6. verify Clock.

Không được hard-code node text bằng ngôn ngữ duy nhất nếu có thể dùng resource id/content description.

Nếu UI node thay đổi theo OEM:

```text
SystemUiClockStrategy
```

phải trả về unsupported thay vì thực hiện thao tác mù.

---

# 22. Accessibility Overlay fallback

Nếu native SystemUI không thể đảm bảo Clock hiển thị trên Maps/YouTube, sử dụng:

```text
TYPE_ACCESSIBILITY_OVERLAY
```

Documentation:

https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_ACCESSIBILITY_OVERLAY

Accessibility Service docs:

https://developer.android.com/reference/android/accessibilityservice/AccessibilityService

## Overlay requirements

Clock View phải:

- nhỏ.
- không touchable.
- không focusable.
- không chặn tương tác.
- không tạo vùng thao tác vô hình.
- đặt ở góc trái Status Bar.
- không có animation.
- không nhấp nháy.
- không hiển thị notification.
- không thay đổi layout ứng dụng bên dưới.

Gợi ý flags:

```text
FLAG_NOT_FOCUSABLE
FLAG_NOT_TOUCHABLE
FLAG_LAYOUT_IN_SCREEN
```

Chỉ thêm flags khác sau khi test.

---

# 23. Tính toán vị trí Overlay

Không hard-code:

```text
y = 0
```

một cách mù quáng.

Lấy:

```text
status_bar_height
```

từ Android resources nếu có.

Sau đó thử:

```text
Gravity.TOP | Gravity.START
```

và điều chỉnh vị trí bằng insets/display metrics.

Phải kiểm tra:

- màn hình ngang.
- màn hình dọc nếu Box hỗ trợ.
- độ phân giải khác nhau.
- density khác nhau.

Vì đây là Android Box ô tô, chế độ landscape là trường hợp ưu tiên.

---

# 24. Clock rendering

Nếu phải dùng fallback overlay:

Không được dùng:

```text
Timer.period = 1000ms
```

nếu không cần.

Dùng thời gian hệ thống:

```text
System.currentTimeMillis()
```

và căn chỉnh refresh theo boundary của phút.

Vì yêu cầu chỉ hiển thị:

```text
HH:mm
```

nên không cần update mỗi giây.

Nếu phút hiện tại không thay đổi:

```text
không redraw
```

Mục tiêu là tiêu thụ tài nguyên tối thiểu.

---

# 25. Format thời gian

Không tự tạo timezone.

Không hard-code UTC.

Dùng locale/timezone/date-format của hệ thống.

Ưu tiên:

```text
HH:mm
```

nhưng nếu SystemUI native tự quyết định format, không can thiệp format của native Clock.

Fallback overlay phải cố gắng tương thích 24-hour setting của hệ thống.

Có thể dùng:

```kotlin
DateFormat.is24HourFormat(context)
```

để chọn format.

---

# 26. Màu của fallback Clock

Không tạo settings cho màu.

Dùng mặc định có độ tương phản tốt.

Nếu có thể, xác định icon/text color theo background/status bar hiện tại.

Tuy nhiên:

> Không biến màu Clock thành một tính năng tùy chỉnh.

Nếu logic màu động quá phức tạp, dùng một màu mặc định ổn định và tối giản.

---

# 27. Repair logic

Khi một ứng dụng khác foreground:

```text
AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
```

có thể được dùng để biết foreground window thay đổi nếu thật sự cần.

Nhưng không được đọc nội dung ứng dụng.

Nếu native SystemUI Clock bị ẩn:

```text
try SystemUi repair
```

nếu repair fail:

```text
activate fallback overlay
```

Không chạy vòng lặp vô hạn.

Không tạo event storm.

Có debounce/throttle nếu cần.

---

# 28. Khi app được tắt

OFF phải có nghĩa:

```text
Clock feature disabled.
```

Thực hiện:

1. dừng strategy hiện tại.
2. remove Accessibility Overlay.
3. nếu SystemUI strategy đã thay đổi một setting nào đó, restore trạng thái ban đầu nếu có thể.
4. cập nhật preference:

```text
enabled = false
```

Không để overlay tồn tại sau OFF.

---

# 29. Reboot behavior

Mục tiêu:

```text
ON
↓
reboot Android Box
↓
SystemUI startup
↓
Clock visible
```

Không được hứa "vĩnh viễn trên mọi OEM" nếu chưa test.

AI Agent phải tạo test record:

```text
BOOT_TEST = PASS / FAIL / NOT_TESTED
```

Nếu Accessibility Service được Android tự bind lại sau boot:

```text
service reconnect
```

thì Controller khôi phục trạng thái.

Nếu cần `BOOT_COMPLETED`, chỉ thêm BroadcastReceiver sau khi chứng minh có nhu cầu.

Không thêm BootReceiver chỉ vì nghĩ rằng app nào cũng cần.

---

# 30. Foreground Service — chỉ được thêm khi bắt buộc

Không tạo Foreground Service trong phiên bản đầu nếu:

```text
SystemUI strategy hoạt động
```

và:

```text
Accessibility Overlay vẫn hoạt động ổn định mà không cần FGS.
```

Chỉ thêm khi diagnostic chứng minh lifecycle của fallback bị hệ thống chấm dứt và FGS là giải pháp cần thiết.

Nếu thêm:

- khai báo đúng foreground service type.
- không chọn type giả.
- không chạy service khi feature OFF.
- notification phải tuân thủ Android.
- không dùng FGS chỉ để giữ timer.

Android hiện có các giới hạn nghiêm ngặt đối với background/foreground service, đặc biệt với Android 12+ và các thay đổi từ Android 14+.

Nguồn:

https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start

https://developer.android.com/develop/background-work/services/fgs/changes

---

# 31. Permission policy

Manifest phải tối giản.

Chỉ thêm permission thật sự cần.

Ưu tiên:

```text
BIND_ACCESSIBILITY_SERVICE
```

Không mặc định thêm:

```text
SYSTEM_ALERT_WINDOW
REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
FOREGROUND_SERVICE
RECEIVE_BOOT_COMPLETED
INTERNET
```

Mỗi permission phát sinh phải có lý do kỹ thuật được ghi trong implementation report.

Đặc biệt:

> Không xin INTERNET.

Ứng dụng này không có chức năng mạng.

---

# 32. Không dùng dependency ngoài nếu không cần

Ưu tiên Android SDK + Kotlin standard library.

Không dùng:

- Retrofit.
- OkHttp.
- Firebase.
- Compose dependency stack nếu XML đủ dùng.
- third-party UI framework.
- third-party overlay framework.
- analytics SDK.
- ad SDK.

Project càng nhỏ càng tốt.

---

# 33. XML hay Jetpack Compose?

Ưu tiên:

```text
XML + Android Views
```

vì UI chỉ có một Switch và project cần tối giản.

Không đưa Compose vào project chỉ để dựng một Switch nếu nó làm dependency graph lớn hơn.

---

# 34. Gradle configuration

AI Agent phải dùng Kotlin DSL:

```text
build.gradle.kts
settings.gradle.kts
```

AGP 9.x có built-in Kotlin support.

Không thêm plugin Kotlin Android riêng nếu phiên bản AGP đang dùng đã cung cấp built-in Kotlin và project không cần plugin đó.

Nguồn:

https://developer.android.com/build/releases/agp-9-0-0-release-notes

AI Agent phải kiểm tra syntax theo phiên bản AGP đã chọn, không sao chép Gradle syntax cũ một cách máy móc.

---

# 35. Manifest

Manifest cần tối giản.

Bắt buộc có:

```text
MainActivity
ClockAccessibilityService
```

Accessibility service phải:

```text
android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
```

và metadata:

```text
android.accessibilityservice
```

Không expose activity/service không cần thiết.

Nếu không cần exported cho component nào thì:

```text
android:exported="false"
```

Nếu Android yêu cầu exported=true vì component là launcher/activity hoặc vì intent-filter tương ứng, cấu hình đúng quy định.

---

# 36. Không thu thập dữ liệu người dùng

Accessibility Service không được dùng để:

- đọc nội dung Google Maps.
- đọc nội dung YouTube.
- đọc mật khẩu.
- ghi nội dung ứng dụng.
- chụp màn hình.
- theo dõi hoạt động người dùng.

Chỉ sử dụng Accessibility ở mức tối thiểu phục vụ mục đích Clock.

---

# 37. Logging

Trong development có thể dùng:

```text
Log.d()
Log.i()
Log.w()
Log.e()
```

Nhưng không log:

- nội dung node của ứng dụng khác.
- dữ liệu cá nhân.
- URL.
- text message.
- thông tin tài khoản.

Tạo tag rõ ràng:

```text
StatusBarClock
```

Khi release, có thể giảm logging.

---

# 38. Error handling

Các trường hợp sau không được crash app:

- Accessibility chưa bật.
- Accessibility service bị disconnect.
- WindowManager addView thất bại.
- Overlay đã tồn tại.
- Overlay chưa tồn tại nhưng stop() được gọi.
- SystemUI strategy unsupported.
- foreground app thay đổi liên tục.
- màn hình xoay.
- display metrics thay đổi.
- Android Box reboot.
- SystemUI restart.

Dùng state machine đơn giản:

```text
OFF
ON_PENDING_ACCESSIBILITY
ON_ACTIVE_SYSTEMUI
ON_ACTIVE_OVERLAY
ERROR_RECOVERABLE
```

Không cần tạo framework state management.

---

# 39. ClockController

Controller chịu trách nhiệm:

```text
enable()
disable()
onAccessibilityConnected()
onAccessibilityDisconnected()
onForegroundWindowChanged()
onConfigurationChanged()
```

Luồng:

```text
enable()
    |
    v
SystemUiClockStrategy.start()
    |
    +---- success ---> ACTIVE_SYSTEMUI
    |
    +---- fail ------> AccessibilityOverlayStrategy.start()
                             |
                             +---- success ---> ACTIVE_OVERLAY
                             |
                             +---- fail ------> ERROR_RECOVERABLE
```

Nếu SystemUI sau đó mất Clock:

```text
ACTIVE_SYSTEMUI
      |
      v
verify/repair
      |
      +--- success -> ACTIVE_SYSTEMUI
      |
      +--- fail ---> ACTIVE_OVERLAY
```

---

# 40. Không tạo vòng phụ thuộc

Không để:

```text
MainActivity -> Service -> Activity
```

theo kiểu giữ Activity sống.

Activity chỉ là UI control.

Service là lifecycle độc lập.

---

# 41. Test matrix tối thiểu

## A. UI

- [ ] App mở được.
- [ ] Chỉ có một Switch.
- [ ] Switch OFF.
- [ ] Switch ON.
- [ ] Sau khi app restart, trạng thái đúng.
- [ ] Không có UI thừa.

## B. Accessibility

- [ ] Service xuất hiện trong Android Accessibility Settings.
- [ ] bật service không crash.
- [ ] quay lại app, Switch phản ánh đúng.
- [ ] tắt service bên ngoài app, app phát hiện trạng thái.

## C. Clock

### Home

- [ ] Clock visible.

### Google Maps

- [ ] Clock visible.

### YouTube

- [ ] Clock visible.

### Một app khác

- [ ] Clock visible.

## D. Lifecycle

- [ ] màn hình tắt/mở.
- [ ] app khác foreground.
- [ ] SystemUI restart nếu có thể test.
- [ ] orientation landscape/portrait nếu thiết bị hỗ trợ.
- [ ] reboot.

---

# 42. Hardware test bắt buộc trước khi kết luận

Nếu Android Box đang kết nối ADB, AI Agent phải thu:

```bash
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell getprop ro.product.manufacturer
adb shell getprop ro.product.model
adb shell getprop ro.build.fingerprint
adb shell pm path com.android.systemui
adb shell settings get secure clock_seconds
adb shell settings get secure icon_blacklist
adb shell dumpsys statusbar
adb shell dumpsys window windows
```

Thực hiện ít nhất ở:

```text
STATE_HOME
STATE_GOOGLE_MAPS
STATE_YOUTUBE
```

Lưu output trong:

```text
diagnostics/
├── home/
├── maps/
└── youtube/
```

Không commit các output có dữ liệu nhạy cảm nếu có.

---

# 43. Diagnostic scripts

AI Agent nên tạo:

```text
tools/diagnose_device.sh
```

Script phải:

1. kiểm tra adb.
2. kiểm tra device.
3. tạo thư mục diagnostics.
4. hỏi/trạng thái hiện tại không cần tương tác phức tạp.
5. thu các command output.
6. ghi timestamp.
7. không sửa hệ thống.

Có thể tạo thêm:

```text
tools/compare_statusbar.sh
```

để so sánh Home/Maps/YouTube.

Các script này chỉ phục vụ development/diagnostics, không thuộc APK.

---

# 44. Không dùng adb để giả lập thành phẩm

ADB có thể dùng:

- diagnostics.
- đọc log.
- kiểm tra SystemUI.
- thử nghiệm hypothesis.

Không được biến thành yêu cầu để app release hoạt động.

Thành phẩm phải là:

```text
cài APK
+
bật Accessibility một lần
+
bật Switch
=
hoạt động độc lập
```

---

# 45. Build configuration phải sẵn sàng nhưng KHÔNG build APK

AI Agent phải kiểm tra:

```bash
./gradlew --version
./gradlew tasks
./gradlew help
./gradlew lint
./gradlew test
```

Có thể chạy các task kiểm tra nếu cần.

Nhưng KHÔNG được chạy:

```bash
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew bundleDebug
./gradlew bundleRelease
```

Cũng không chạy:

```bash
./gradlew installDebug
./gradlew installRelease
```

Không mở APK bằng bundletool.

Không tạo AAB.

Không tạo signed APK.

---

# 46. Quy tắc đặc biệt về "có thể build"

Sau khi hoàn tất, AI Agent phải chứng minh bằng cấu hình rằng project có khả năng build.

Báo cáo phải chỉ ra:

```text
Gradle version:
AGP version:
Kotlin version:
JDK version:
compileSdk:
targetSdk:
minSdk:
applicationId:
```

và:

```text
Build configuration status:
READY / NOT READY
```

`READY` không có nghĩa là đã build APK.

`READY` nghĩa:

- Gradle files hợp lệ.
- dependencies hợp lệ.
- source tree đầy đủ.
- manifest hợp lệ.
- resources hợp lệ.
- test/lint đã chạy hoặc lý do không chạy được đã ghi rõ.
- không có lỗi known blocker.
- APK có thể được build ở bước tiếp theo bởi người dùng.

---

# 47. Acceptance Criteria — Định nghĩa hoàn thành

Project chỉ được xem là hoàn thành khi đạt:

## Code

- [ ] Kotlin native.
- [ ] project tạo từ con số 0.
- [ ] code sạch.
- [ ] class nhỏ, dễ bảo trì.
- [ ] không có code thừa.
- [ ] không có dependency thừa.

## Product

- [ ] Một chức năng.
- [ ] Một Switch.
- [ ] Không quảng cáo.
- [ ] Không Internet.
- [ ] Không server.
- [ ] Không analytics.
- [ ] Không seconds.

## Clock

- [ ] Clock hiển thị trên Home.
- [ ] Clock hiển thị khi Google Maps foreground.
- [ ] Clock hiển thị khi YouTube foreground.
- [ ] Clock nằm sát khu vực Status Bar góc trái.
- [ ] Clock không chặn touch.
- [ ] Clock không ảnh hưởng app đang chạy.

## Persistence

- [ ] ON được lưu.
- [ ] Restart app không làm mất trạng thái.
- [ ] Accessibility reconnect được xử lý.
- [ ] reboot test nếu có thiết bị.

## Build readiness

- [ ] Gradle configuration OK.
- [ ] lint không có blocker.
- [ ] test không có blocker.
- [ ] project tree đầy đủ.
- [ ] APK chưa được build.

---

# 48. Điều kiện không được phép tuyên bố "hoàn thành"

Không được nói "đã hoàn thành" nếu:

- chỉ hoạt động trên Home.
- mở Maps là Clock biến mất.
- chỉ overlay được trong app của chính mình.
- cần mở app lại sau mỗi lần reboot.
- cần chạy ADB mỗi lần bật máy.
- cần Internet.
- cần `SYSTEM_ALERT_WINDOW` nhưng chưa chứng minh.
- cần FGS nhưng chưa kiểm chứng.
- SystemUI strategy chưa thử.
- fallback overlay chưa thử.
- project có compile error.

Trong trường hợp chưa đạt hardware test:

```text
PROJECT_SOURCE_READY
+
HARDWARE_VALIDATION_PENDING
```

không được gọi là:

```text
PROJECT_COMPLETE
```

---

# 49. Báo cáo cuối cùng AI Agent phải tạo

Tạo:

```text
IMPLEMENTATION_REPORT.md
```

Nội dung tối thiểu:

## 1. Environment

```text
OS:
JDK:
Gradle:
AGP:
Kotlin:
Android SDK:
```

## 2. Device

```text
Manufacturer:
Model:
Android:
API:
SystemUI package:
Build fingerprint:
```

## 3. Root cause hypothesis

Giải thích ngắn:

```text
Clock biến mất trên app khác vì:
...
```

và bằng chứng:

```text
ADB evidence:
...
```

## 4. Strategy đã chọn

Một trong:

```text
SYSTEM_UI
ACCESSIBILITY_OVERLAY
HYBRID
```

## 5. Vì sao

Nêu bằng chứng kỹ thuật.

## 6. Files

Liệt kê file chính.

## 7. Permissions

Bảng:

| Permission | Có/Không | Lý do |
|---|---|---|
| Accessibility | Có | chức năng chính |
| Internet | Không | không cần |
| SYSTEM_ALERT_WINDOW | ... | ... |
| Foreground Service | ... | ... |
| Boot Completed | ... | ... |

## 8. Tests

```text
Home:
Maps:
YouTube:
Reboot:
Accessibility reconnect:
Landscape:
```

Mỗi test:

```text
PASS
FAIL
NOT_TESTED
```

## 9. Known limitations

Không giấu giới hạn OEM.

## 10. Build readiness

```text
READY / NOT READY
```

và xác nhận:

```text
NO APK WAS BUILT
```

---

# 50. README bắt buộc

Tạo:

```text
README.md
```

README phải viết bằng tiếng Việt.

Nội dung:

- App làm gì.
- Cách chạy project.
- Cách bật Accessibility.
- Cách dùng Switch.
- Kiến trúc ngắn gọn.
- Permission rationale.
- Hardware validation.
- Cách build sau này.
- Cảnh báo rằng phiên bản hiện tại chưa build APK.

Không đưa hướng dẫn quảng cáo, monetization hoặc Play Store vì ngoài phạm vi.

---

# 51. Quy trình thực thi toàn bộ — AI Agent phải làm theo thứ tự

## PHASE 0 — Understand & inspect

1. Đọc toàn bộ file specification này.
2. Kiểm tra workspace.
3. Kiểm tra JDK.
4. Kiểm tra Android SDK.
5. Kiểm tra Gradle.
6. Kiểm tra ADB.
7. Kiểm tra Android Box nếu đang kết nối.

## PHASE 1 — Hardware diagnostics

1. Thu Android version.
2. Thu API.
3. Thu manufacturer/model.
4. Thu SystemUI package.
5. Thu `clock_seconds`.
6. Thu `icon_blacklist`.
7. Thu `dumpsys statusbar`.
8. Thu `dumpsys window`.
9. So sánh Home/Maps/YouTube.
10. Kết luận hypothesis.

## PHASE 2 — Create project

1. Tạo project `status_bar_clock`.
2. Kotlin.
3. XML.
4. Gradle Kotlin DSL.
5. Minimal dependencies.
6. Minimal manifest.
7. Tạo source tree.

## PHASE 3 — Core implementation

1. MainActivity.
2. Switch state.
3. Accessibility Service.
4. ClockController.
5. SystemUI strategy.
6. Accessibility Overlay fallback.
7. persistence.
8. lifecycle handling.
9. recovery.

## PHASE 4 — Diagnostics integration

1. `tools/diagnose_device.sh`.
2. logging.
3. test utilities.
4. docs.

## PHASE 5 — Static/build readiness validation

1. Kotlin syntax check.
2. Gradle configuration check.
3. resources check.
4. manifest check.
5. lint.
6. test.
7. project tree review.
8. inspect generated errors.
9. sửa toàn bộ blocker.

## PHASE 6 — STOP

Dừng lại.

**KHÔNG BUILD APK.**

Tạo:

```text
IMPLEMENTATION_REPORT.md
```

và xác nhận:

```text
PROJECT READY FOR MANUAL APK BUILD
APK BUILD NOT EXECUTED
```

---

# 52. Nếu hardware chưa kết nối

Không được dừng.

Thực hiện:

```text
PHASE 0
↓
PHASE 1 = PARTIAL
↓
PHASE 2
↓
PHASE 3
↓
PHASE 4
↓
PHASE 5
↓
STOP
```

Trong report:

```text
Hardware validation:
PENDING

Reason:
Target Android Box was not connected through ADB during implementation.
```

Nhưng code phải được thiết kế để sau khi Box kết nối có thể tiếp tục chẩn đoán mà không cần tạo lại project.

---

# 53. Nguyên tắc xử lý khi gặp lỗi

AI Agent không được dừng ngay và hỏi người dùng nếu lỗi có thể tự điều tra.

Trình tự:

```text
read error
↓
identify root cause
↓
check local environment
↓
check official documentation
↓
apply fix
↓
re-run validation
```

Chỉ báo BLOCKED khi thực sự cần phần cứng/quyền truy cập mà AI Agent không có.

Không sửa bằng cách hạ hàng loạt dependency xuống phiên bản cũ mà không phân tích nguyên nhân.

---

# 54. Không được làm quá scope

Nếu trong quá trình implementation AI Agent nghĩ ra:

- Settings screen,
- seconds,
- widget,
- floating bubble,
- color picker,
- font selector,
- quick tile,

thì **không implement**.

Mục tiêu duy nhất:

```text
ONE SWITCH
      ↓
ONE FUNCTION
      ↓
CLOCK ON STATUS BAR
```

---

# 55. Tiêu chí UX cuối cùng

Người dùng phải có cảm giác:

```text
Cài app
↓
mở app
↓
bật ON
↓
cấp Accessibility một lần
↓
xong
```

Sau đó:

```text
Home
→ Clock

Google Maps
→ Clock

YouTube
→ Clock

Restart Box
→ Clock
```

Không cần thao tác hàng ngày.

---

# 56. Tiêu chí hiệu năng

Ứng dụng phải:

- idle gần như không dùng CPU khi native SystemUI mode.
- không timer không cần thiết.
- không wake lock nếu không cần.
- không network.
- memory footprint nhỏ.
- không redraw overlay liên tục.
- không tạo thread thừa.
- không polling 100ms/500ms.
- không polling ADB.
- không Accessibility event logging vô hạn.

---

# 57. Security / privacy

Accessibility Service là quyền nhạy cảm.

Do đó:

- chỉ làm đúng chức năng Clock.
- không đọc dữ liệu ngoài phạm vi.
- không gửi dữ liệu ra ngoài.
- không Internet.
- không analytics.
- không tracking.
- không có hidden behavior.

Nếu có thể đạt mục tiêu mà không bật `canRetrieveWindowContent`, hãy tắt.

---

# 58. Tài liệu kỹ thuật chính thức cần tham khảo

AI Agent phải ưu tiên tài liệu chính thức.

### AccessibilityService

https://developer.android.com/reference/android/accessibilityservice/AccessibilityService

### Accessibility Overlay

https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_ACCESSIBILITY_OVERLAY

### Background/Foreground Service restrictions

https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start

https://developer.android.com/develop/background-work/services/fgs/changes

### Android Gradle Plugin

https://developer.android.com/build/releases/agp-9-4-0-release-notes

### Kotlin releases

https://kotlinlang.org/docs/releases.html

### AOSP Status Bar Clock

https://android.googlesource.com/platform/frameworks/base/+/19ed5b0b29d7bf821bcae9ba5b088df03c7056d3/packages/SystemUI/src/com/android/systemui/statusbar/policy/Clock.java

---

# 59. Tuyên bố cuối cùng dành cho AI Agent

Bạn đang xây dựng **một Android utility cực kỳ nhỏ**, không phải một ứng dụng đa chức năng.

Hãy ưu tiên:

```text
Correctness
>
Reliability
>
Minimal permissions
>
Minimal dependencies
>
Minimal CPU/RAM
>
Minimal code
```

Không ưu tiên:

```text
extra features
animations
beautiful UI
framework complexity
analytics
monetization
```

Mục tiêu cuối cùng là:

```text
STATUS BAR CLOCK
       |
       v
+-------------------------------+
|  Clock luôn hiện trên Status  |
|  Bar kể cả khi app khác mở    |
+-------------------------------+

UI:
[ ON / OFF ]

Nothing else.
```

**Quan trọng nhất:**

> Không build APK trong nhiệm vụ này.  
> Chỉ đưa project tới trạng thái **READY TO BUILD**, sau đó dừng để chủ dự án kiểm tra source code trước khi tự build APK.

---

# 60. Definition of Done

AI Agent chỉ được kết thúc khi đã có tối thiểu:

```text
status_bar_clock/
├── app/
├── tools/
├── README.md
└── IMPLEMENTATION_REPORT.md
```

và report cuối:

```text
Implementation: DONE
Source validation: DONE
Build configuration: READY
Hardware validation: PASS / PARTIAL / PENDING
APK: NOT BUILT
AAB: NOT BUILT
```

Không được tạo file APK trong workspace.

**END OF MASTER SPECIFICATION**
