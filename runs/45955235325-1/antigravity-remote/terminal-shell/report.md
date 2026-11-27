# UI test — antigravity-remote (scenario)

**Result:** ❌ FAILED  
**Package:** `com.londkirs.antigravityremote`  
**Device:** sdk_gphone64_x86_64 · Android 13 (API 33) · 1080x2400  
**Duration:** 34.0s

## Steps

| # | Action | Detail | Result | Time |
|---|---|---|---|---|
| 1 | launch | clear_data=True | ✅ | 3.88s |
| 2 | tap | text='Allow' | ⚠️ — element not found within 4s: text='Allow' | 5.47s |
| 3 | wait_for | text='Antigravity on Android' | ✅ | 2.13s |
| 4 | screenshot | name='home' | ✅ | 2.52s |
| 5 | tap | text='Shell' | ✅ | 3.44s |
| 6 | wait | seconds=4 | ✅ | 4.03s |
| 7 | tap | text='Allow' | ✅ | 3.4s |
| 8 | screenshot | name='terminal-opened' | ✅ | 2.34s |
| 9 | assert_foreground |  | ❌ — foreground app is com.google.android.permissioncontroller, expected com.londkirs.antigravityremote | 0.04s |

## Screenshots

**001-home.png**

<img src="screenshots/001-home.png" width="270">

**002-terminal-opened.png**

<img src="screenshots/002-terminal-opened.png" width="270">

**003-step9-assert_foreground-FAILED.png**

<img src="screenshots/003-step9-assert_foreground-FAILED.png" width="270">

**004-final.png**

<img src="screenshots/004-final.png" width="270">
