# UI test — mishnat-yosef (scenario)

**Result:** ✅ PASSED  
**Package:** `org.mishnatyosef.mobile`  
**Device:** sdk_gphone64_x86_64 · Android 13 (API 33) · 1080x2400  
**Duration:** 54.5s

## Steps

| # | Action | Detail | Result | Time |
|---|---|---|---|---|
| 1 | clear_data |  | ✅ | 0.14s |
| 2 | shell | cmd='pm grant org.mishnatyosef.mobile android.permission.POST_NOTIFICATIONS', ignore_error | ✅ | 0.09s |
| 3 | shell | cmd='appops set org.mishnatyosef.mobile SCHEDULE_EXACT_ALARM allow', ignore_errors=True | ✅ | 0.07s |
| 4 | launch |  | ✅ | 3.31s |
| 5 | wait_for | text='ברוכים הבאים' | ✅ | 2.4s |
| 6 | tap | desc='פתיחת הגדרות ופעולות' | ✅ | 3.08s |
| 7 | wait_for | text='פעולות מהירות' | ✅ | 2.1s |
| 8 | screenshot | name='day-dialog' | ✅ | 2.28s |
| 9 | tap | text='מצב לילה' | ✅ | 3.07s |
| 10 | wait | seconds=1.5 | ✅ | 1.53s |
| 11 | assert_text | button label flipped | ✅ | 2.02s |
| 12 | assert_text | dialog still open | ✅ | 2.09s |
| 13 | screenshot | name='night-dialog' | ✅ | 2.29s |
| 14 | tap | desc='סגירת חלון הפעולות' | ✅ | 3.06s |
| 15 | assert_not_visible | text='פעולות מהירות' | ✅ | 2.55s |
| 16 | screenshot | name='night-login-screen' | ✅ | 2.25s |
| 17 | tap | desc='פתיחת הגדרות ופעולות' | ✅ | 3.05s |
| 18 | wait_for | text='פעולות מהירות' | ✅ | 2.05s |
| 19 | screenshot | name='night-dialog-reopened' | ✅ | 2.25s |
| 20 | tap | text='מצב יום' | ✅ | 3.04s |
| 21 | wait | seconds=1.5 | ✅ | 1.53s |
| 22 | assert_text | text='מצב לילה' | ✅ | 2.05s |
| 23 | screenshot | name='back-to-day-dialog' | ✅ | 2.27s |
| 24 | tap | desc='סגירת חלון הפעולות' | ✅ | 3.08s |
| 25 | assert_foreground |  | ✅ | 0.05s |

## Screenshots

**001-day-dialog.png**

<img src="screenshots/001-day-dialog.png" width="270">

**002-night-dialog.png**

<img src="screenshots/002-night-dialog.png" width="270">

**003-night-login-screen.png**

<img src="screenshots/003-night-login-screen.png" width="270">

**004-night-dialog-reopened.png**

<img src="screenshots/004-night-dialog-reopened.png" width="270">

**005-back-to-day-dialog.png**

<img src="screenshots/005-back-to-day-dialog.png" width="270">

**006-final.png**

<img src="screenshots/006-final.png" width="270">
