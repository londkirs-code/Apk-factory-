# UI test — mishnat-yosef (scenario)

**Result:** ✅ PASSED  
**Package:** `org.mishnatyosef.mobile`  
**Device:** sdk_gphone64_x86_64 · Android 13 (API 33) · 1080x2400  
**Duration:** 55.7s

## Steps

| # | Action | Detail | Result | Time |
|---|---|---|---|---|
| 1 | clear_data |  | ✅ | 0.3s |
| 2 | shell | cmd='pm grant org.mishnatyosef.mobile android.permission.POST_NOTIFICATIONS', ignore_error | ✅ | 0.23s |
| 3 | shell | cmd='appops set org.mishnatyosef.mobile SCHEDULE_EXACT_ALARM allow', ignore_errors=True | ✅ | 0.19s |
| 4 | launch |  | ✅ | 3.99s |
| 5 | wait_for | text='ברוכים הבאים' | ✅ | 2.0s |
| 6 | tap | desc='פתיחת הגדרות ופעולות' | ✅ | 3.02s |
| 7 | wait_for | text='פעולות מהירות' | ✅ | 2.0s |
| 8 | screenshot | name='day-dialog' | ✅ | 2.25s |
| 9 | tap | text='מצב לילה' | ✅ | 3.02s |
| 10 | wait | seconds=1.5 | ✅ | 1.53s |
| 11 | assert_text | button label flipped | ✅ | 2.0s |
| 12 | assert_text | dialog still open | ✅ | 2.0s |
| 13 | screenshot | name='night-dialog' | ✅ | 2.23s |
| 14 | tap | desc='סגירת חלון הפעולות' | ✅ | 3.04s |
| 15 | assert_not_visible | text='פעולות מהירות' | ✅ | 2.49s |
| 16 | screenshot | name='night-login-screen' | ✅ | 2.24s |
| 17 | tap | desc='פתיחת הגדרות ופעולות' | ✅ | 3.02s |
| 18 | wait_for | text='פעולות מהירות' | ✅ | 1.99s |
| 19 | screenshot | name='night-dialog-reopened' | ✅ | 2.24s |
| 20 | tap | text='מצב יום' | ✅ | 3.01s |
| 21 | wait | seconds=1.5 | ✅ | 1.53s |
| 22 | assert_text | text='מצב לילה' | ✅ | 1.99s |
| 23 | screenshot | name='back-to-day-dialog' | ✅ | 2.25s |
| 24 | tap | desc='סגירת חלון הפעולות' | ✅ | 3.01s |
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
