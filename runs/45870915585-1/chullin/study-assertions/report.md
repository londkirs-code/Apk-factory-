# UI test — chullin (scenario)

**Result:** ❌ FAILED  
**Package:** `com.londkirs.chullin`  
**Device:** sdk_gphone64_x86_64 · Android 13 (API 33) · 1080x2400  
**Duration:** 107.9s

## Steps

| # | Action | Detail | Result | Time |
|---|---|---|---|---|
| 1 | launch | clear_data=True | ✅ | 4.21s |
| 2 | wait_for | text='חולין' | ✅ | 2.84s |
| 3 | assert_text | text='הכל שוחטין' | ✅ | 2.62s |
| 4 | scroll_to | text='הבא' | ✅ | 6.22s |
| 5 | assert_text | text='הבא' | ✅ | 2.46s |
| 6 | screenshot | name='study-start' | ✅ | 2.83s |
| 7 | scroll_to | text="מתני' הכל שוחטין" | ✅ | 2.4s |
| 8 | tap | text="מתני' הכל שוחטין" | ✅ | 3.56s |
| 9 | wait | seconds=1 | ✅ | 1.15s |
| 10 | assert_text | text='נלמדו 1 מתוך 6' | ✅ | 2.89s |
| 11 | tap | text="מתני' הכל שוחטין" | ✅ | 3.42s |
| 12 | wait | seconds=1 | ✅ | 1.07s |
| 13 | assert_text | text='נלמדו 0 מתוך 6' | ✅ | 2.48s |
| 14 | swipe | direction='down' | ✅ | 1.46s |
| 15 | swipe | direction='down' | ✅ | 1.22s |
| 16 | swipe | direction='down' | ✅ | 1.38s |
| 17 | wait | seconds=2 | ✅ | 2.12s |
| 18 | tap | text='⚙' | ✅ | 3.63s |
| 19 | wait_for | text='ערכת נושא' | ✅ | 2.24s |
| 20 | tap | text='כהה' | ✅ | 3.46s |
| 21 | wait | seconds=2 | ✅ | 2.12s |
| 22 | screenshot | name='dark-theme' | ✅ | 12.48s |
| 23 | wait | seconds=2 | ✅ | 2.05s |
| 24 | tap | text='⚙' | ❌ — uiautomator dump failed:  | 11.89s |

## Notes

- UI dump for 002-dark-theme.png failed: uiautomator dump failed: 
- UI dump for 003-step24-tap-FAILED.png failed: uiautomator dump failed: 
- UI dump for 004-final.png failed: uiautomator dump failed: 

## Screenshots

**001-study-start.png**

<img src="screenshots/001-study-start.png" width="270">

**002-dark-theme.png**

<img src="screenshots/002-dark-theme.png" width="270">

**003-step24-tap-FAILED.png**

<img src="screenshots/003-step24-tap-FAILED.png" width="270">

**004-final.png**

<img src="screenshots/004-final.png" width="270">
