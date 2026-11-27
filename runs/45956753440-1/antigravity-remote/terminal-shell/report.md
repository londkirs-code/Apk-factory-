# UI test — antigravity-remote (scenario)

**Result:** ✅ PASSED  
**Package:** `com.londkirs.antigravityremote`  
**Device:** sdk_gphone64_x86_64 · Android 13 (API 33) · 1080x2400  
**Duration:** 41.5s

## Steps

| # | Action | Detail | Result | Time |
|---|---|---|---|---|
| 1 | launch | clear_data=True | ✅ | 4.13s |
| 2 | shell | cmd='pm grant com.londkirs.antigravityremote android.permission.POST_NOTIFICATIONS', ignor | ✅ | 0.31s |
| 3 | tap | regex='^Allow$' | ⚠️ — element not found within 3s: regex='^Allow$' | 5.7s |
| 4 | wait_for | text='Antigravity on Android' | ✅ | 2.12s |
| 5 | screenshot | name='home' | ✅ | 2.43s |
| 6 | tap | text='Shell' | ✅ | 3.31s |
| 7 | wait | seconds=4 | ✅ | 4.05s |
| 8 | tap | regex='^Allow$' | ⚠️ — element not found within 3s: regex='^Allow$' | 5.31s |
| 9 | screenshot | name='terminal-opened' | ✅ | 2.82s |
| 10 | assert_foreground |  | ✅ | 0.24s |
| 11 | type | value='echo hello-from-terminal; id', enter=True | ✅ | 1.64s |
| 12 | wait | seconds=2 | ✅ | 2.03s |
| 13 | screenshot | name='terminal-command' | ✅ | 2.57s |
| 14 | assert_foreground |  | ✅ | 0.06s |

## Screenshots

**001-home.png**

<img src="screenshots/001-home.png" width="270">

**002-terminal-opened.png**

<img src="screenshots/002-terminal-opened.png" width="270">

**003-terminal-command.png**

<img src="screenshots/003-terminal-command.png" width="270">

**004-final.png**

<img src="screenshots/004-final.png" width="270">
