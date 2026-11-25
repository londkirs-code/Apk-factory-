# UI test — mishnat-yosef (scenario)

**Result:** ✅ PASSED  
**Package:** `org.mishnatyosef.mobile`  
**Device:** sdk_gphone64_x86_64 · Android 13 (API 33) · 1080x2400  
**Duration:** 89.2s

## Steps

| # | Action | Detail | Result | Time |
|---|---|---|---|---|
| 1 | clear_data |  | ✅ | 0.17s |
| 2 | shell | cmd='pm grant org.mishnatyosef.mobile android.permission.POST_NOTIFICATIONS', ignore_error | ✅ | 0.16s |
| 3 | shell | cmd='appops set org.mishnatyosef.mobile SCHEDULE_EXACT_ALARM allow', ignore_errors=True | ✅ | 0.13s |
| 4 | launch |  | ✅ | 3.53s |
| 5 | wait_for | text='ברוכים הבאים' | ✅ | 2.53s |
| 6 | tap | desc='פתיחת הגדרות ופעולות' | ✅ | 3.63s |
| 7 | wait_for | text='פעולות מהירות' | ✅ | 2.78s |
| 8 | swipe | desc='גודל התצוגה' | ✅ | 4.28s |
| 9 | wait | seconds=3 | ✅ | 3.17s |
| 10 | screenshot | name='scale-left-end' | ✅ | 3.5s |
| 11 | assert_foreground |  | ✅ | 0.16s |
| 12 | wait_for | dialog reopens after the scale change | ✅ | 2.26s |
| 13 | swipe | desc='גודל התצוגה' | ✅ | 3.89s |
| 14 | wait | seconds=3 | ✅ | 3.13s |
| 15 | screenshot | name='scale-right-end' | ✅ | 3.38s |
| 16 | assert_foreground |  | ✅ | 0.12s |
| 17 | back |  | ✅ | 0.98s |
| 18 | screenshot | name='login-at-changed-scale' | ✅ | 3.02s |
| 19 | rotate | to='landscape' | ✅ | 1.77s |
| 20 | wait | seconds=2 | ✅ | 2.08s |
| 21 | screenshot | name='landscape-changed-scale' | ✅ | 2.83s |
| 22 | rotate | to='portrait' | ✅ | 1.71s |
| 23 | wait | seconds=2 | ✅ | 2.1s |
| 24 | tap | desc='פתיחת הגדרות ופעולות' | ✅ | 3.51s |
| 25 | wait_for | text='פעולות מהירות' | ✅ | 2.2s |
| 26 | rotate | to='landscape' | ✅ | 2.38s |
| 27 | wait | seconds=2 | ✅ | 2.05s |
| 28 | wait_for | dialog is reopened after rotation | ✅ | 2.42s |
| 29 | screenshot | name='dialog-landscape' | ✅ | 2.52s |
| 30 | rotate | to='portrait' | ✅ | 1.78s |
| 31 | wait | seconds=2 | ✅ | 2.08s |
| 32 | wait_for | still open after rotating back | ✅ | 2.5s |
| 33 | wait_for | text='פעולות מהירות' | ✅ | 2.1s |
| 34 | tap | text='איפוס תצוגה' | ✅ | 3.2s |
| 35 | wait | seconds=3 | ✅ | 3.07s |
| 36 | screenshot | name='after-reset' | ✅ | 2.51s |
| 37 | assert_text | text='100%' | ✅ | 2.0s |
| 38 | assert_foreground |  | ✅ | 0.07s |

## Screenshots

**001-scale-left-end.png**

<img src="screenshots/001-scale-left-end.png" width="270">

**002-scale-right-end.png**

<img src="screenshots/002-scale-right-end.png" width="270">

**003-login-at-changed-scale.png**

<img src="screenshots/003-login-at-changed-scale.png" width="270">

**004-landscape-changed-scale.png**

<img src="screenshots/004-landscape-changed-scale.png" width="270">

**005-dialog-landscape.png**

<img src="screenshots/005-dialog-landscape.png" width="270">

**006-after-reset.png**

<img src="screenshots/006-after-reset.png" width="270">

**007-final.png**

<img src="screenshots/007-final.png" width="270">
