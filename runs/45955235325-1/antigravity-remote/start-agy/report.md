# UI test — antigravity-remote (scenario)

**Result:** ❌ FAILED  
**Package:** `com.londkirs.antigravityremote`  
**Device:** sdk_gphone64_x86_64 · Android 13 (API 33) · 1080x2400  
**Duration:** 53.0s

## Steps

| # | Action | Detail | Result | Time |
|---|---|---|---|---|
| 1 | launch | clear_data=True | ✅ | 5.38s |
| 2 | tap | text='Allow' | ⚠️ — element not found within 4s: text='Allow' | 5.75s |
| 3 | shell | cmd='run-as com.londkirs.antigravityremote sh -c \'mkdir -p files/bin && echo "#!/system/b | ✅ | 0.29s |
| 4 | back |  | ✅ | 1.27s |
| 5 | launch |  | ✅ | 4.33s |
| 6 | tap | text='Allow' | ⚠️ — element not found within 4s: text='Allow' | 5.78s |
| 7 | wait_for | text='Start Antigravity' | ✅ | 2.42s |
| 8 | screenshot | name='home-installed' | ✅ | 3.15s |
| 9 | tap | text='Start Antigravity' | ✅ | 3.53s |
| 10 | wait | seconds=5 | ✅ | 5.08s |
| 11 | tap | text='Allow' | ✅ | 5.04s |
| 12 | screenshot | name='agy-session' | ✅ | 3.42s |
| 13 | assert_foreground |  | ❌ — foreground app is com.google.android.permissioncontroller, expected com.londkirs.antigravityremote | 0.08s |

## Screenshots

**001-home-installed.png**

<img src="screenshots/001-home-installed.png" width="270">

**002-agy-session.png**

<img src="screenshots/002-agy-session.png" width="270">

**003-step13-assert_foreground-FAILED.png**

<img src="screenshots/003-step13-assert_foreground-FAILED.png" width="270">

**004-final.png**

<img src="screenshots/004-final.png" width="270">
