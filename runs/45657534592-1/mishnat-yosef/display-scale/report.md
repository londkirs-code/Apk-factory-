# UI test — mishnat-yosef (scenario)

**Result:** ✅ PASSED  
**Package:** `org.mishnatyosef.mobile`  
**Device:** sdk_gphone64_x86_64 · Android 13 (API 33) · 1080x2400  
**Duration:** 78.5s

## Steps

| # | Action | Detail | Result | Time |
|---|---|---|---|---|
| 1 | clear_data |  | ✅ | 0.49s |
| 2 | shell | cmd='pm grant org.mishnatyosef.mobile android.permission.POST_NOTIFICATIONS', ignore_error | ✅ | 0.3s |
| 3 | shell | cmd='appops set org.mishnatyosef.mobile SCHEDULE_EXACT_ALARM allow', ignore_errors=True | ✅ | 0.29s |
| 4 | launch |  | ✅ | 3.15s |
| 5 | wait_for | text='ברוכים הבאים' | ✅ | 2.03s |
| 6 | tap | desc='פתיחת הגדרות ופעולות' | ✅ | 3.02s |
| 7 | wait_for | text='פעולות מהירות' | ✅ | 2.02s |
| 8 | swipe | desc='גודל התצוגה' | ✅ | 3.59s |
| 9 | wait | seconds=3 | ✅ | 3.03s |
| 10 | screenshot | name='scale-left-end' | ✅ | 2.24s |
| 11 | assert_foreground |  | ✅ | 0.05s |
| 12 | wait_for | dialog reopens after the scale change | ✅ | 2.02s |
| 13 | swipe | desc='גודל התצוגה' | ✅ | 3.67s |
| 14 | wait | seconds=3 | ✅ | 3.03s |
| 15 | screenshot | name='scale-right-end' | ✅ | 2.25s |
| 16 | assert_foreground |  | ✅ | 0.05s |
| 17 | back |  | ✅ | 0.85s |
| 18 | screenshot | name='login-at-changed-scale' | ✅ | 2.23s |
| 19 | rotate | to='landscape' | ✅ | 1.57s |
| 20 | wait | seconds=2 | ✅ | 2.03s |
| 21 | screenshot | name='landscape-changed-scale' | ✅ | 2.24s |
| 22 | rotate | to='portrait' | ✅ | 1.57s |
| 23 | wait | seconds=2 | ✅ | 2.03s |
| 24 | tap | desc='פתיחת הגדרות ופעולות' | ✅ | 3.0s |
| 25 | wait_for | text='פעולות מהירות' | ✅ | 2.0s |
| 26 | rotate | to='landscape' | ✅ | 1.57s |
| 27 | wait | seconds=2 | ✅ | 2.03s |
| 28 | wait_for | dialog is reopened after rotation | ✅ | 2.01s |
| 29 | screenshot | name='dialog-landscape' | ✅ | 2.24s |
| 30 | rotate | to='portrait' | ✅ | 1.58s |
| 31 | wait | seconds=2 | ✅ | 2.03s |
| 32 | wait_for | still open after rotating back | ✅ | 2.0s |
| 33 | wait_for | text='פעולות מהירות' | ✅ | 1.99s |
| 34 | tap | text='איפוס תצוגה' | ✅ | 3.01s |
| 35 | wait | seconds=3 | ✅ | 3.06s |
| 36 | screenshot | name='after-reset' | ✅ | 2.26s |
| 37 | assert_text | text='100%' | ✅ | 1.99s |
| 38 | assert_foreground |  | ✅ | 0.05s |

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
