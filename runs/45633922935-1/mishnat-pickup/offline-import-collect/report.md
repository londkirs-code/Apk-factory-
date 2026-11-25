# UI test — mishnat-pickup (scenario)

**Result:** ✅ PASSED  
**Package:** `org.mishnatyosef.pickup`  
**Device:** sdk_gphone64_x86_64 · Android 13 (API 33) · 1080x2400  
**Duration:** 101.8s

## Steps

| # | Action | Detail | Result | Time |
|---|---|---|---|---|
| 1 | clear_data |  | ✅ | 0.32s |
| 2 | push | src='fixtures/ui-test-list.txt', dest='/sdcard/Download/ui-test-list.txt' | ✅ | 3.1s |
| 3 | launch |  | ✅ | 3.08s |
| 4 | wait_for | text='כניסה לחשבון' | ✅ | 2.02s |
| 5 | screenshot | name='login' | ✅ | 2.26s |
| 6 | assert_text | text='משנת אוסף' | ✅ | 2.0s |
| 7 | assert_text | text='רשימות שמורות' | ✅ | 1.98s |
| 8 | scroll_to | text='ייבוא רשימה מקובץ' | ✅ | 2.0s |
| 9 | tap | text='ייבוא רשימה מקובץ' | ✅ | 3.04s |
| 10 | wait | seconds=3 | ✅ | 3.03s |
| 11 | screenshot | name='file-picker' | ✅ | 2.26s |
| 12 | tap | file shown directly (Recent) | ✅ | 3.03s |
| 13 | tap | otherwise open the picker drawer | ⚠️ — element not found within 3s: desc='Show roots' | 4.66s |
| 14 | tap | text='Downloads' | ⚠️ — element not found within 4s: text='Downloads' | 4.65s |
| 15 | tap | text='ui-test-list' | ⚠️ — element not found within 8s: text='ui-test-list' | 9.96s |
| 16 | wait_for | text='נמצאו 1 רשימות' | ✅ | 2.16s |
| 17 | screenshot | name='import-summary' | ✅ | 2.38s |
| 18 | tap | text='ייבא רשימות' | ✅ | 3.12s |
| 19 | wait_for | text='נאספו 0 מתוך 6' | ✅ | 2.07s |
| 20 | assert_text | TXT quantity shown as 2, not 2.0 | ✅ | 1.99s |
| 21 | assert_not_visible | text='2.0' | ✅ | 2.53s |
| 22 | screenshot | name='list-imported' | ✅ | 2.28s |
| 23 | tap | text='לחם' | ✅ | 3.04s |
| 24 | assert_text | text='נאספו 1 מתוך 6' | ✅ | 2.0s |
| 25 | screenshot | name='one-collected' | ✅ | 2.27s |
| 26 | tap | text='חלב' | ✅ | 3.02s |
| 27 | assert_text | text='נאספו 3 מתוך 6' | ✅ | 1.99s |
| 28 | tap | text='מלפפון' | ✅ | 2.62s |
| 29 | screenshot | celebration animation lasts ~1.5s; uiautomator cannot read the screen while it animates, s | ✅ | 2.45s |
| 30 | screenshot | name='celebration' | ✅ | 2.25s |
| 31 | wait | seconds=5 | ✅ | 5.03s |
| 32 | assert_text | text='נאספו 6 מתוך 6' | ✅ | 1.99s |
| 33 | screenshot | name='all-collected' | ✅ | 2.23s |
| 34 | tap | tap again returns it to pending | ✅ | 3.01s |
| 35 | assert_text | text='נאספו 5 מתוך 6' | ✅ | 1.99s |
| 36 | screenshot | name='one-returned' | ✅ | 2.23s |

## Screenshots

**001-login.png**

<img src="screenshots/001-login.png" width="270">

**002-file-picker.png**

<img src="screenshots/002-file-picker.png" width="270">

**003-import-summary.png**

<img src="screenshots/003-import-summary.png" width="270">

**004-list-imported.png**

<img src="screenshots/004-list-imported.png" width="270">

**005-one-collected.png**

<img src="screenshots/005-one-collected.png" width="270">

**006-celebration.png**

<img src="screenshots/006-celebration.png" width="270">

**007-celebration.png**

<img src="screenshots/007-celebration.png" width="270">

**008-all-collected.png**

<img src="screenshots/008-all-collected.png" width="270">

**009-one-returned.png**

<img src="screenshots/009-one-returned.png" width="270">
