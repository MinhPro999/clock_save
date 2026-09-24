# PHASE 2.1 — FINAL HARDENING PROMPT
## STATUS BAR CLOCK / `clock_save`

**Repo:** `MinhPro999/clock_save`  
**Branch:** `main`  
**Mục tiêu:** Sửa các lỗi lifecycle/foreground-detection còn sót lại sau Phase 2 để source đạt trạng thái **READY FOR APK BUILD**, nhưng **TUYỆT ĐỐI KHÔNG BUILD APK/AAB** trong Phase này.

---

# 1. NHIỆM VỤ

Bạn là AI Agent chịu trách nhiệm trực tiếp chỉnh sửa repository hiện tại.

Không tạo project mới.

Không reset repository.

Không xóa lịch sử.

Hãy đọc toàn bộ source hiện tại trước khi sửa.

Mục tiêu của Phase 2.1 chỉ là:

```text
FIX REMAINING CORRECTNESS ISSUES
        ↓
RUN VALIDATION
        ↓
UPDATE REPORT
        ↓
STOP
```

Không thêm feature mới.

---

# 2. BỐI CẢNH SẢN PHẨM

Ứng dụng chỉ có một chức năng:

> Giữ đồng hồ `HH:mm` hiển thị tại góc trái vùng Status Bar khi người dùng mở ứng dụng khác, trong khi tránh tạo đồng hồ thứ hai trên Home nếu Android Box đã có clock native.

UI ứng dụng:

```text
1 màn hình
+
1 Switch ON/OFF
```

Không được thêm:

- settings.
- seconds.
- date.
- widget.
- floating ball.
- quảng cáo.
- Internet.
- analytics.
- server.
- database.
- notification.
- Foreground Service trừ khi chứng minh bắt buộc.
- `SYSTEM_ALERT_WINDOW`.
- `WRITE_SECURE_SETTINGS`.
- `RECEIVE_BOOT_COMPLETED` chỉ để "cho có".

Runtime chính vẫn là:

```text
AccessibilityService
        ↓
ClockController
        ↓
AccessibilityOverlayClockStrategy
        ↓
TYPE_ACCESSIBILITY_OVERLAY
```

`SystemUiClockStrategy` chỉ là future/diagnostic extension.

---

# 3. BLOCKER #1 — REBOOT / COLD START LIFECYCLE

## Lỗi hiện tại

`ClockStateMachine` khởi tạo:

```kotlin
state = ClockState.OFF
```

Sau reboot/process recreation:

```text
saved preference:
    enabled = true

state machine:
    OFF
```

`ClockController.onAccessibilityConnected()` gọi `activate()` nhưng `activate()` hiện chỉ cho phép chạy khi state:

```text
ON_PENDING_ACCESSIBILITY
hoặc
ERROR_RECOVERABLE
```

Vì vậy có khả năng:

```text
reboot
 ↓
AccessibilityService reconnect
 ↓
saved enabled = true
 ↓
state vẫn OFF
 ↓
activate() không chạy
 ↓
clock không được tạo
```

Đây vi phạm yêu cầu quan trọng nhất của sản phẩm.

---

# 4. FIX BẮT BUỘC CHO REBOOT

Trong:

```text
ClockController.kt
```

khi:

```text
onAccessibilityConnected(service)
```

và:

```text
ClockStateStore.isEnabled(service) == true
```

phải bảo đảm state machine chuyển sang trạng thái có thể activate.

Cách ưu tiên:

```text
onAccessibilityConnected
        ↓
if saved enabled
        ↓
if currentState == OFF
        → machine.onEnable()
        ↓
ensureHomeDetector()
        ↓
activate(service)
```

Sau đó:

```text
OFF
 ↓
ON_PENDING_ACCESSIBILITY
 ↓
ON_ACTIVE_OVERLAY
```

Không reset preference.

Không yêu cầu người dùng mở lại app.

Không cần ADB.

Không dùng `BOOT_COMPLETED`.

---

# 5. TEST BẮT BUỘC CHO REBOOT/COLD START

Phải bổ sung pure unit test cho state/restore logic.

Test phải mô hình hóa đúng trường hợp:

```text
new app process
state = OFF
saved enabled = true
service reconnect
→ activation path được phép chuyển tới ON_ACTIVE_OVERLAY
```

Nếu kiến trúc hiện tại khiến phần restore khó test vì phụ thuộc Android framework, tạo một hàm pure helper hoặc transition method nhỏ để test được.

Không đưa Android framework vào unit test chỉ để đạt coverage.

Mục tiêu:

```text
reboot restore test = PASS
```

---

# 6. BLOCKER #2 — SYSTEMUI / SYSTEM WINDOW ACCESSIBILITY EVENTS

## Rủi ro hiện tại

Service nhận:

```text
TYPE_WINDOW_STATE_CHANGED
```

và truyền:

```text
event.packageName
```

vào Controller.

Controller hiện coi:

```text
package != launcher
```

là:

```text
OTHER APP
```

Nhưng Android Box có thể phát Accessibility event từ:

```text
com.android.systemui
```

hoặc một package hệ thống trung gian.

Khi đó:

```text
HOME
 ↓
SystemUI event
 ↓
package != launcher
 ↓
overlay SHOW
```

có khả năng tạo duplicate clock ngay trên Home.

---

# 7. FIX SYSTEM PACKAGE DETECTION

Tạo pure logic hoặc helper rõ ràng để xử lý system window.

Không hard-code nhiều package không có căn cứ.

Ít nhất coi package SystemUI thực tế:

```text
com.android.systemui
```

là package hệ thống và **không dùng một event SystemUI đơn lẻ để kết luận user đã rời Home**.

Có thể truyền thêm trạng thái:

```text
foregroundPackage
+
foregroundPackageIsSystemUi
```

nhưng phải giữ kiến trúc đơn giản.

---

# 8. QUY TẮC FOREGROUND MỚI

Không được hiểu:

```text
non-home package
=
user app đang foreground
```

một cách tuyệt đối.

Ưu tiên logic:

```text
HOME launcher
    → hide overlay

Known SystemUI/system event
    → giữ trạng thái visibility hiện tại

Known user application
    → show overlay

Unknown/null
    → show overlay
```

Đây là policy nhằm giảm false-negative.

Quan trọng:

> Không để một event ngắn từ SystemUI làm mất clock hoặc tạo duplicate clock nếu Home vẫn đang là cửa sổ chính.

---

# 9. KHÔNG ĐƯỢC ĐỌC NỘI DUNG APP

Vẫn giữ:

```xml
android:canRetrieveWindowContent="false"
```

Không sử dụng:

```text
event.text
event.contentDescription
rootInActiveWindow
source
AccessibilityNodeInfo
```

Không screenshot.

Không gesture.

Không log dữ liệu app khác.

Chỉ sử dụng metadata tối thiểu:

```text
eventType
packageName
```

---

# 10. BLOCKER #3 — OVERLAY ATTACHED STATE

Code hiện đã có:

```text
overlayAttached
clockView
```

đây là hướng đúng.

Nhưng `overlayAttached = true` chỉ chứng minh `addView()` từng thành công.

Nếu OEM tự loại window:

```text
clockView != null
overlayAttached == true
```

nhưng window thực tế không còn.

Khi đó repair có thể không attach lại.

---

# 11. FIX OVERLAY HEALTH CHECK

Thêm helper:

```text
isWindowActuallyAttached()
```

Ưu tiên kiểm tra:

```text
overlayAttached
+
clockView != null
+
clockView.isAttachedToWindow
```

Có thể cần kiểm tra API level nếu property không phù hợp với minSdk.

Logic:

```text
if overlay state says attached
    but view.isAttachedToWindow == false
        → considered detached
```

Khi detached:

```text
clear stale reference
↓
attach new view
```

Không tạo duplicate window.

---

# 12. REPAIR FLOW

Khi foreground event hoặc configuration event tới:

```text
ensureOverlayHealth()
```

Không làm:

```text
remove + add
```

nếu window vẫn khỏe.

Chỉ reattach khi:

```text
really detached
```

---

# 13. BLOCKER #4 — ERROR RECOVERY LOOP

Hiện `ERROR_RECOVERABLE` có thể retry khi có window event.

Phải bảo đảm không có loop:

```text
failure
 ↓
retry
 ↓
failure
 ↓
retry liên tục
```

Áp dụng debounce/backoff đơn giản.

Không cần hệ thống retry phức tạp.

Ví dụ:

```text
minimum 1 second between repair attempts
```

Có thể dùng Handler hiện tại.

---

# 14. BLOCKER #5 — VISIBILITY STATE CONSISTENCY

Kiểm tra:

```text
desiredVisible
overlayAttached
view.visibility
ClockState
foregroundPackage
```

phải nhất quán.

Các invariant:

### OFF

```text
ClockState = OFF
overlay should NOT be visible
```

### Pending Accessibility

```text
overlay not visible
```

### Active + HOME

```text
overlay hidden
```

### Active + OTHER APP

```text
overlay visible
```

### Active + unknown

```text
overlay visible
```

---

# 15. IMPORTANT — KHÔNG remove window khi HOME nếu không cần

Khi HOME:

```text
view.visibility = GONE
```

giữ window attached.

Mục đích:

```text
Home
 ↓
hidden

Maps
 ↓
VISIBLE
```

chuyển trạng thái nhanh mà không add/remove window liên tục.

Chỉ detach khi:

```text
OFF
service destroyed
configuration requires recreation
actual window failure
```

---

# 16. CONFIGURATION CHANGES

Khi configuration thay đổi:

```text
orientation
density
screen configuration
```

phải:

```text
preserve desired visibility
↓
detach
↓
attach
↓
restore visibility
```

Không làm mất `enabled=true`.

---

# 17. HOME DETECTOR — RÀ SOÁT LẠI

`HomeDetector` hiện query:

```text
ACTION_MAIN
CATEGORY_HOME
```

đây là đúng.

Nhưng kiểm tra:

```text
resolved package set
```

có bao gồm package của hệ thống trung gian hay không.

Không coi mọi package trả về từ query là "foreground launcher hiện tại".

Mục tiêu:

```text
isHomePackage(actualLauncherPackage) == true
```

Không hard-code launcher.

Không thêm danh sách package OEM giả định.

---

# 18. FOREGROUND INITIAL STATE

Một tình huống quan trọng:

Service kết nối khi người dùng đang ở Maps.

Có thể chưa nhận được event window ngay lập tức.

Lúc đó:

```text
foregroundPackage = null
```

Policy hiện tại:

```text
null → show overlay
```

Giữ nguyên.

Điều này ưu tiên yêu cầu:

> Không được mất Clock.

Khi event Maps tới:

```text
still visible
```

Khi event Home tới:

```text
hide
```

---

# 19. MAIN ACTIVITY — KHÔNG SỬA THÀNH UI PHỨC TẠP

Giữ:

```text
MainActivity
```

chỉ có:

```text
title
description
Switch
```

Không thêm status card.

Không thêm error page.

Không thêm button Settings.

Không thêm "Accessibility enabled" button.

---

# 20. MANIFEST — GIỮ TỐI GIẢN

Manifest mục tiêu:

```text
MainActivity
AccessibilityService
```

và permission:

```text
BIND_ACCESSIBILITY_SERVICE
```

Không thêm:

```text
INTERNET
SYSTEM_ALERT_WINDOW
FOREGROUND_SERVICE
RECEIVE_BOOT_COMPLETED
WRITE_SECURE_SETTINGS
```

trừ khi phát sinh blocker có bằng chứng.

---

# 21. ACCESSIBILITY XML

Giữ:

```xml
android:accessibilityEventTypes="typeWindowStateChanged"
android:canRetrieveWindowContent="false"
android:canPerformGestures="false"
```

Không mở rộng capability.

---

# 22. SYSTEM UI STRATEGY

Không cần triển khai native SystemUI trong Phase 2.1.

Giữ:

```text
SystemUiClockStrategy.kt
```

nhưng:

```text
future / diagnostic only
```

Không đưa vào runtime path.

---

# 23. TESTS — MỤC TIÊU SAU PHASE 2.1

Giữ toàn bộ test hiện tại.

Bổ sung tối thiểu:

```text
1. Cold start + saved enabled → activation permitted
2. Reconnect after OFF runtime state + saved enabled
3. SystemUI package does not force wrong visibility transition
4. Home → hidden
5. Other app → visible
6. Unknown package → visible
7. Overlay health: attached
8. Overlay health: stale detached
9. No duplicate attach
10. Error retry/debounce behavior
```

Mục tiêu:

```text
ALL TESTS PASS
```

Không bắt buộc số lượng chính xác nếu test coverage logic đã đủ.

---

# 24. CODE QUALITY

Trong khi sửa:

- không tạo singleton thừa.
- không thêm dependency.
- không thêm coroutine nếu Handler hiện tại đủ.
- không thêm AndroidX nếu project chưa cần.
- không refactor toàn bộ project.
- không đổi package name.
- không đổi applicationId.
- không đổi UI scope.
- không đổi Gradle stack nếu không có lỗi.

Ưu tiên patch nhỏ, dễ review.

---

# 25. VALIDATION COMMANDS

Sau khi sửa, được phép chạy:

```bash
./gradlew --version
./gradlew help
./gradlew tasks
./gradlew test
./gradlew lint
./gradlew :app:compileDebugKotlin
```

Nếu cần:

```bash
./gradlew :app:processDebugResources
```

Không được chạy:

```bash
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew bundleDebug
./gradlew bundleRelease
./gradlew installDebug
./gradlew installRelease
```

---

# 26. APK/AAB CẤM TUYỆT ĐỐI

Trong Phase 2.1:

```text
APK = NOT BUILT
AAB = NOT BUILT
```

Không tạo file:

```text
*.apk
*.aab
```

Không dùng Android Studio để Generate APK.

Không dùng Gradle assemble.

---

# 27. REPORT

Cập nhật:

```text
IMPLEMENTATION_REPORT.md
```

Trạng thái cuối phải:

```text
Source implementation: COMPLETE
Build configuration: READY
Unit tests: PASS
Lint: PASS
Hardware validation: PENDING
APK: NOT BUILT
AAB: NOT BUILT
```

Thêm:

```text
Phase 2.1 hardening:
- cold-start/reboot state restoration fixed
- SystemUI event handling hardened
- overlay health check hardened
- recovery loop debounced
```

---

# 28. GIỚI HẠN PHẢI GHI RÕ

Vì Android Box không kết nối với máy:

```text
Hardware validation = PENDING
```

Không tuyên bố:

```text
OEM compatibility guaranteed
```

Không tuyên bố:

```text
reboot verified
```

Không tuyên bố:

```text
Maps/YouTube verified on target Box
```

Chỉ có thể nói:

```text
Source implementation and static/unit validation complete.
Target-device validation is still pending.
```

---

# 29. DEFINITION OF DONE

Phase 2.1 chỉ DONE khi:

```text
[✓] cold-start/reboot restore path fixed
[✓] saved enabled=true restores runtime state
[✓] SystemUI events cannot incorrectly force Home → visible
[✓] Home detection remains dynamic
[✓] unknown foreground package → visible
[✓] overlay health can detect stale attachment
[✓] no duplicate attach
[✓] repair is debounced
[✓] OFF removes overlay
[✓] Home hides overlay
[✓] Other app shows overlay
[✓] service disconnect clears lifecycle
[✓] tests pass
[✓] lint pass
[✓] compileDebugKotlin pass
[✓] no APK
[✓] no AAB
```

---

# 30. CUỐI CÙNG

Sau khi mọi điều kiện trên đạt:

```text
STOP
```

Không build.

Không install.

Không generate APK.

Chỉ commit/push source + report.

Kết thúc report bằng:

```text
PHASE 2.1 COMPLETE
SOURCE READY FOR MANUAL APK BUILD
HARDWARE VALIDATION PENDING
APK NOT BUILT
AAB NOT BUILT
```

**END OF PHASE 2.1**
