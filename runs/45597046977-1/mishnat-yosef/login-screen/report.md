# UI test — mishnat-yosef (scenario)

**Result:** ✅ PASSED  
**Package:** `org.mishnatyosef.mobile`  
**Device:** sdk_gphone64_x86_64 · Android 13 (API 33) · 1080x2400  
**Duration:** 51.9s

## Steps

| # | Action | Detail | Result | Time |
|---|---|---|---|---|
| 1 | clear_data |  | ✅ | 0.12s |
| 2 | shell | cmd='pm grant org.mishnatyosef.mobile android.permission.POST_NOTIFICATIONS', ignore_error | ✅ | 0.06s |
| 3 | shell | cmd='appops set org.mishnatyosef.mobile SCHEDULE_EXACT_ALARM allow', ignore_errors=True | ✅ | 0.11s |
| 4 | launch |  | ✅ | 2.98s |
| 5 | wait_for | text='ברוכים הבאים' | ✅ | 2.35s |
| 6 | back | close an update/notice dialog if one opened over the login screen | ✅ | 0.85s |
| 7 | assert_foreground |  | ⚠️ — foreground app is org.mishnatyosef.pickup, expected org.mishnatyosef.mobile | 0.02s |
| 8 | launch | restart=False | ✅ | 2.75s |
| 9 | screenshot | name='login' | ✅ | 2.27s |
| 10 | assert_text | text='התחברות לחשבון' | ✅ | 2.02s |
| 11 | assert_text | text='התחברו עם החשבון שלכם' | ✅ | 2.02s |
| 12 | assert_visible | email + password fields | ✅ | 2.06s |
| 13 | tap | submit the empty form | ✅ | 3.04s |
| 14 | assert_text | text='יש למלא אימייל וסיסמה' | ✅ | 2.03s |
| 15 | screenshot | name='empty-form-validation' | ✅ | 2.24s |
| 16 | type | class='EditText' | ✅ | 4.84s |
| 17 | assert_text | text='ui-test@example.com' | ✅ | 2.29s |
| 18 | screenshot | name='email-typed' | ✅ | 2.28s |
| 19 | rotate | to='landscape' | ✅ | 1.59s |
| 20 | assert_text | text='ברוכים הבאים' | ✅ | 2.03s |
| 21 | assert_text | typed email survives rotation | ✅ | 2.03s |
| 22 | assert_text | validation message survives rotation | ✅ | 2.04s |
| 23 | screenshot | name='landscape' | ✅ | 2.25s |
| 24 | rotate | to='portrait' | ✅ | 1.75s |
| 25 | assert_foreground |  | ✅ | 0.47s |
| 26 | assert_text | still there after rotating back | ✅ | 2.49s |
| 27 | screenshot | name='back-to-portrait' | ✅ | 2.32s |

## Screenshots

**001-login.png**

<img src="screenshots/001-login.png" width="270">

**002-empty-form-validation.png**

<img src="screenshots/002-empty-form-validation.png" width="270">

**003-email-typed.png**

<img src="screenshots/003-email-typed.png" width="270">

**004-landscape.png**

<img src="screenshots/004-landscape.png" width="270">

**005-back-to-portrait.png**

<img src="screenshots/005-back-to-portrait.png" width="270">
