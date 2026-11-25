# UI test — mishnat-pickup (scenario)

**Result:** ❌ FAILED  
**Package:** `org.mishnatyosef.pickup`  
**Device:** sdk_gphone64_x86_64 · Android 13 (API 33) · 1080x2400  
**Duration:** 350.1s

## Steps

| # | Action | Detail | Result | Time |
|---|---|---|---|---|
| 1 | clear_data |  | ✅ | 0.5s |
| 2 | push | src='fixtures/ui-test-features.txt', dest='/sdcard/Download/ui-test-features.txt' | ✅ | 31.38s |
| 3 | launch |  | ✅ | 4.84s |
| 4 | wait_for | text='כניסה לחשבון' | ✅ | 3.18s |
| 5 | scroll_to | text='ייבוא רשימה מקובץ' | ✅ | 2.24s |
| 6 | tap | text='ייבוא רשימה מקובץ' | ✅ | 3.51s |
| 7 | tap | text='ui-test-features' | ✅ | 5.93s |
| 8 | wait_for | text='נמצאו 1 רשימות' | ✅ | 2.97s |
| 9 | tap | text='ייבא רשימות' | ✅ | 3.56s |
| 10 | wait_for | 1+2+3+4+1.5 units | ✅ | 2.74s |
| 11 | screenshot | name='imported-5-items' | ✅ | 3.56s |
| 12 | assert_text | decimal quantity kept | ✅ | 2.28s |
| 13 | tap | text='חיפוש פריט' | ✅ | 3.48s |
| 14 | type | value='Pasta', hide_keyboard=True | ✅ | 2.65s |
| 15 | wait | seconds=1 | ✅ | 1.07s |
| 16 | screenshot | name='search-pasta' | ✅ | 2.79s |
| 17 | assert_text | text='Pasta' | ✅ | 2.43s |
| 18 | assert_not_visible | search filters other items | ✅ | 3.11s |
| 19 | type | class='EditText' | ✅ | 4.23s |
| 20 | wait | seconds=1 | ✅ | 1.25s |
| 21 | assert_text | cleared search shows all items | ✅ | 2.47s |
| 22 | long_press | text='Pasta' | ✅ | 4.78s |
| 23 | wait_for | text='איסוף כמות חלקית' | ✅ | 2.53s |
| 24 | screenshot | name='partial-dialog' | ✅ | 3.13s |
| 25 | swipe | RTL slider: try to pick more units | ✅ | 3.84s |
| 26 | screenshot | name='partial-dialog-moved' | ✅ | 2.89s |
| 27 | tap | text='אישור הכמות' | ✅ | 3.53s |
| 28 | wait | seconds=1 | ✅ | 1.47s |
| 29 | assert_text | text='נאספו חלקית' | ❌ — element not found within 5s: text='נאספו חלקית' | 6.03s |
| 30 | screenshot | name='partial-collected' | ✅ | 2.67s |
| 31 | tap | desc='איפוס ליקוט' | ✅ | 3.6s |
| 32 | wait_for | text='להחזיר את כל המוצרים' | ✅ | 2.25s |
| 33 | screenshot | name='reset-confirm' | ✅ | 2.37s |
| 34 | tap | text='איפוס' | ✅ | 3.11s |
| 35 | wait_for | text='נאספו 0 מתוך 11.5' | ✅ | 2.15s |
| 36 | tap | desc='רשימה לפי חשבון' | ✅ | 3.12s |
| 37 | wait | seconds=1.5 | ✅ | 1.58s |
| 38 | screenshot | name='view-by-account' | ✅ | 2.47s |
| 39 | tap | desc='רשימה מאוחדת' | ✅ | 3.1s |
| 40 | wait | seconds=1.5 | ✅ | 1.58s |
| 41 | screenshot | name='view-combined' | ✅ | 2.43s |
| 42 | tap | text='גבינה' | ✅ | 3.11s |
| 43 | assert_text | text='נאספו 1.5 מתוך 11.5' | ✅ | 2.09s |
| 44 | rotate | to='landscape' | ✅ | 1.64s |
| 45 | wait | seconds=1.5 | ✅ | 1.59s |
| 46 | assert_text | progress survives rotation | ✅ | 2.1s |
| 47 | screenshot | name='landscape' | ✅ | 2.37s |
| 48 | rotate | to='portrait' | ✅ | 1.63s |
| 49 | wait | seconds=1.5 | ✅ | 1.58s |
| 50 | tap | desc='הגדרות' | ✅ | 3.18s |
| 51 | wait_for | text='סיכום מהיר' | ✅ | 2.08s |
| 52 | screenshot | name='settings-top' | ✅ | 2.46s |
| 53 | swipe | direction='up' | ✅ | 1.37s |
| 54 | screenshot | name='settings-middle' | ✅ | 2.72s |
| 55 | swipe | direction='up' | ✅ | 1.34s |
| 56 | swipe | direction='up' | ✅ | 1.35s |
| 57 | screenshot | name='settings-bottom' | ✅ | 2.41s |
| 58 | scroll_to | text='רשימות שמורות' | ✅ | 2.07s |
| 59 | tap | text='רשימות שמורות' | ✅ | 3.1s |
| 60 | wait | seconds=2 | ✅ | 2.08s |
| 61 | screenshot | name='saved-lists' | ✅ | 2.36s |
| 62 | back |  | ✅ | 0.92s |
| 63 | wait | seconds=1 | ✅ | 1.15s |
| 64 | back |  | ✅ | 1.2s |
| 65 | wait | seconds=1 | ✅ | 1.1s |
| 66 | launch | restart=False | ✅ | 3.15s |
| 67 | screenshot | name='before-explore' | ✅ | 2.45s |
| 68 | explore-tap | ⚙ / הגדרות | ✅ | 1.3s |
| 69 | explore-tap | × / סגירת ההגדרות | ✅ | 1.31s |
| 70 | explore-tap | ⚙ / הגדרות | ✅ | 1.33s |
| 71 | explore-tap | רטט ארוך בסיום הרשימה | ✅ | 1.35s |
| 72 | explore-tap | צליל הצלחה ממושך בסיום הרשימה | ✅ | 1.3s |
| 73 | explore-tap | רשימה מאוחדת | ✅ | 1.32s |
| 74 | explore-tap | רשימה לפי חשבון | ✅ | 1.31s |
| 75 | explore-tap | חיפוש פריט ברשימה… | ✅ | 1.33s |
| 76 | explore-tap | ⚙ / הגדרות | ✅ | 1.31s |
| 77 | explore-tap | לחם, כמות 1 | ✅ | 1.32s |
| 78 | explore-tap | ⚙ / הגדרות | ✅ | 1.31s |
| 79 | explore-tap | רשימה מאוחדת | ✅ | 1.32s |
| 80 | explore-tap | רשימה לפי חשבון | ✅ | 1.31s |
| 81 | explore-tap | חיפוש פריט ברשימה… | ✅ | 1.33s |
| 82 | explore-tap | ⚙ / הגדרות | ✅ | 1.31s |
| 83 | explore-tap | חלב, כמות 2 | ✅ | 1.31s |
| 84 | explore-tap | ⚙ / הגדרות | ✅ | 1.32s |
| 85 | explore-tap | רשימה מאוחדת | ✅ | 1.31s |
| 86 | explore-tap | רשימה לפי חשבון | ✅ | 1.3s |
| 87 | explore-tap | חיפוש פריט ברשימה… | ✅ | 1.31s |
| 88 | explore-tap | ⚙ / הגדרות | ✅ | 1.91s |
| 89 | explore-tap | מלפפון, כמות 3 | ✅ | 1.34s |
| 90 | explore-tap | ⚙ / הגדרות | ✅ | 1.36s |
| 91 | explore-tap | רשימה מאוחדת | ✅ | 1.35s |
| 92 | explore-tap | רשימה לפי חשבון | ✅ | 1.32s |
| 93 | explore-tap | חיפוש פריט ברשימה… | ✅ | 1.35s |
| 94 | explore-tap | ⚙ / הגדרות | ✅ | 1.32s |
| 68 | explore | auto-explore from the list screen | ✅ | 141.16s |
| 69 | assert_foreground |  | ✅ | 0.12s |

## Notes

- explore: 10 distinct screens, 27 elements tapped, 0 relaunches, 141s

## Screenshots

**001-imported-5-items.png**

<img src="screenshots/001-imported-5-items.png" width="270">

**002-search-pasta.png**

<img src="screenshots/002-search-pasta.png" width="270">

**003-partial-dialog.png**

<img src="screenshots/003-partial-dialog.png" width="270">

**004-partial-dialog-moved.png**

<img src="screenshots/004-partial-dialog-moved.png" width="270">

**005-step29-assert_text-FAILED.png**

<img src="screenshots/005-step29-assert_text-FAILED.png" width="270">

**006-partial-collected.png**

<img src="screenshots/006-partial-collected.png" width="270">

**007-reset-confirm.png**

<img src="screenshots/007-reset-confirm.png" width="270">

**008-view-by-account.png**

<img src="screenshots/008-view-by-account.png" width="270">

**009-view-combined.png**

<img src="screenshots/009-view-combined.png" width="270">

**010-landscape.png**

<img src="screenshots/010-landscape.png" width="270">

**011-settings-top.png**

<img src="screenshots/011-settings-top.png" width="270">

**012-settings-middle.png**

<img src="screenshots/012-settings-middle.png" width="270">

**013-settings-bottom.png**

<img src="screenshots/013-settings-bottom.png" width="270">

**014-saved-lists.png**

<img src="screenshots/014-saved-lists.png" width="270">

**015-before-explore.png**

<img src="screenshots/015-before-explore.png" width="270">

**016-screen-1.png**

<img src="screenshots/016-screen-1.png" width="270">

**017-screen-2.png**

<img src="screenshots/017-screen-2.png" width="270">

**018-screen-3.png**

<img src="screenshots/018-screen-3.png" width="270">

**019-screen-4.png**

<img src="screenshots/019-screen-4.png" width="270">

**020-screen-5.png**

<img src="screenshots/020-screen-5.png" width="270">

**021-screen-6.png**

<img src="screenshots/021-screen-6.png" width="270">

**022-screen-7.png**

<img src="screenshots/022-screen-7.png" width="270">

**023-screen-8.png**

<img src="screenshots/023-screen-8.png" width="270">

**024-screen-9.png**

<img src="screenshots/024-screen-9.png" width="270">

**025-screen-10.png**

<img src="screenshots/025-screen-10.png" width="270">

**026-final.png**

<img src="screenshots/026-final.png" width="270">
