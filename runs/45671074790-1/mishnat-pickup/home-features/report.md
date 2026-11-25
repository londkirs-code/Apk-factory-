# UI test — mishnat-pickup (scenario)

**Result:** ❌ FAILED  
**Package:** `org.mishnatyosef.pickup`  
**Device:** sdk_gphone64_x86_64 · Android 13 (API 33) · 1080x2400  
**Duration:** 734.8s

## Steps

| # | Action | Detail | Result | Time |
|---|---|---|---|---|
| 1 | clear_data |  | ✅ | 1.37s |
| 2 | push | src='fixtures/ui-test-features.txt', dest='/sdcard/Download/ui-test-features.txt' | ✅ | 42.16s |
| 3 | launch |  | ✅ | 5.23s |
| 4 | wait_for | text='כניסה לחשבון' | ❌ — element not found within 30s: text='כניסה לחשבון' | 31.67s |
| 5 | scroll_to | text='ייבוא רשימה מקובץ' | ❌ — not found after scrolling: text='ייבוא רשימה מקובץ' | 16.72s |
| 6 | tap | text='ייבוא רשימה מקובץ' | ❌ — element not found within 10s: text='ייבוא רשימה מקובץ' | 10.91s |
| 7 | tap | text='ui-test-features' | ❌ — element not found within 10s: text='ui-test-features' | 11.08s |
| 8 | wait_for | text='נמצאו 1 רשימות' | ❌ — element not found within 15s: text='נמצאו 1 רשימות' | 16.72s |
| 9 | tap | text='ייבא רשימות' | ❌ — element not found within 10s: text='ייבא רשימות' | 10.42s |
| 10 | wait_for | 1+2+3+4+1.5 units | ❌ — element not found within 10s: text='נאספו 0 מתוך 11.5' | 10.51s |
| 11 | screenshot | name='imported-5-items' | ✅ | 2.51s |
| 12 | assert_text | decimal quantity kept | ❌ — element not found within 5s: text='1.5' | 7.62s |
| 13 | tap | text='חיפוש פריט' | ❌ — element not found within 10s: text='חיפוש פריט' | 10.69s |
| 14 | type | value='Pasta', hide_keyboard=True | ✅ | 0.94s |
| 15 | wait | seconds=1 | ✅ | 1.11s |
| 16 | screenshot | name='search-pasta' | ✅ | 2.5s |
| 17 | assert_text | text='Pasta' | ❌ — element not found within 5s: text='Pasta' | 7.6s |
| 18 | assert_not_visible | search filters other items | ✅ | 2.68s |
| 19 | type | class='EditText' | ❌ — element not found within 10s: class='EditText' | 10.43s |
| 20 | wait | seconds=1 | ✅ | 1.12s |
| 21 | assert_text | cleared search shows all items | ❌ — element not found within 5s: text='מלפפון' | 7.55s |
| 22 | long_press | text='Pasta' | ❌ — element not found within 10s: text='Pasta' | 10.41s |
| 23 | wait_for | text='איסוף כמות חלקית' | ❌ — element not found within 5s: text='איסוף כמות חלקית' | 7.62s |
| 24 | screenshot | name='partial-dialog' | ✅ | 2.5s |
| 25 | swipe | RTL slider: try to pick more units | ❌ — element not found within 10s: class='SeekBar' | 10.38s |
| 26 | screenshot | name='partial-dialog-moved' | ✅ | 2.48s |
| 27 | tap | text='אישור הכמות' | ❌ — element not found within 10s: text='אישור הכמות' | 10.38s |
| 28 | wait | seconds=1 | ✅ | 1.17s |
| 29 | scroll_to | the partial card sits in the collected section, below the fold | ❌ — not found after scrolling: text='נאספו חלקית' | 18.18s |
| 30 | screenshot | name='partial-collected' | ✅ | 2.5s |
| 31 | scroll_to | text='נאספו 1 מתוך' | ❌ — not found after scrolling: text='נאספו 1 מתוך' | 19.51s |
| 32 | tap | desc='איפוס ליקוט' | ❌ — element not found within 10s: desc='איפוס ליקוט' | 10.44s |
| 33 | wait_for | text='להחזיר את כל המוצרים' | ❌ — element not found within 5s: text='להחזיר את כל המוצרים' | 7.57s |
| 34 | screenshot | name='reset-confirm' | ✅ | 2.51s |
| 35 | tap | text='איפוס' | ❌ — element not found within 10s: text='איפוס' | 10.3s |
| 36 | wait_for | text='נאספו 0 מתוך 11.5' | ❌ — element not found within 5s: text='נאספו 0 מתוך 11.5' | 7.55s |
| 37 | tap | desc='רשימה לפי חשבון' | ❌ — element not found within 10s: desc='רשימה לפי חשבון' | 10.41s |
| 38 | wait | seconds=1.5 | ✅ | 1.72s |
| 39 | screenshot | name='view-by-account' | ✅ | 3.07s |
| 40 | tap | desc='רשימה מאוחדת' | ❌ — element not found within 10s: desc='רשימה מאוחדת' | 10.37s |
| 41 | wait | seconds=1.5 | ✅ | 1.63s |
| 42 | screenshot | name='view-combined' | ✅ | 2.49s |
| 43 | tap | text='גבינה' | ❌ — element not found within 10s: text='גבינה' | 10.37s |
| 44 | assert_text | text='נאספו 1.5 מתוך 11.5' | ❌ — element not found within 5s: text='נאספו 1.5 מתוך 11.5' | 7.58s |
| 45 | rotate | to='landscape' | ✅ | 3.09s |
| 46 | wait | seconds=1.5 | ✅ | 1.64s |
| 47 | assert_text | progress survives rotation | ❌ — element not found within 5s: text='נאספו 1.5 מתוך 11.5' | 7.6s |
| 48 | screenshot | name='landscape' | ✅ | 2.47s |
| 49 | rotate | to='portrait' | ✅ | 1.75s |
| 50 | wait | seconds=1.5 | ✅ | 1.63s |
| 51 | tap | desc='הגדרות' | ❌ — element not found within 10s: desc='הגדרות' | 10.38s |
| 52 | wait_for | text='סיכום מהיר' | ❌ — element not found within 8s: text='סיכום מהיר' | 10.45s |
| 53 | screenshot | name='settings-top' | ✅ | 2.48s |
| 54 | swipe | direction='up' | ✅ | 1.36s |
| 55 | screenshot | name='settings-middle' | ✅ | 2.52s |
| 56 | swipe | direction='up' | ✅ | 1.32s |
| 57 | swipe | direction='up' | ✅ | 1.32s |
| 58 | screenshot | name='settings-bottom' | ✅ | 2.5s |
| 59 | scroll_to | text='רשימות שמורות' | ❌ — not found after scrolling: text='רשימות שמורות' | 23.21s |
| 60 | tap | text='רשימות שמורות' | ❌ — element not found within 10s: text='רשימות שמורות' | 10.33s |
| 61 | wait | seconds=2 | ✅ | 2.14s |
| 62 | screenshot | name='saved-lists' | ✅ | 2.54s |
| 63 | back |  | ✅ | 0.97s |
| 64 | wait | seconds=1 | ✅ | 1.18s |
| 65 | back |  | ✅ | 1.0s |
| 66 | wait | seconds=1 | ✅ | 1.13s |
| 67 | launch | restart=False | ✅ | 2.99s |
| 68 | screenshot | name='before-explore' | ✅ | 2.5s |
| 69 | explore-tap | לא להציג שוב (עד העדכון הבא) | ✅ | 1.39s |
| 70 | explore-tap | הבנתי ומסכים | ✅ | 1.58s |
| 71 | explore-tap | name@example.com | ✅ | 1.4s |
| 72 | explore-tap | הקלידו את הסיסמה | ✅ | 1.37s |
| 73 | explore-tap | 🔐  התחברות | ✅ | 1.36s |
| 74 | explore-tap | name@example.com | ✅ | 1.4s |
| 75 | explore-tap | הקלידו את הסיסמה | ✅ | 1.38s |
| 76 | explore-tap | name@example.com | ✅ | 1.38s |
| 77 | explore-tap | הקלידו את הסיסמה | ✅ | 1.38s |
| 78 | explore-tap | 🔐  התחברות | ✅ | 1.36s |
| 79 | explore-tap | 📚  ניהול רשימות | ✅ | 2.1s |
| 80 | explore-tap | חזרה | ✅ | 1.48s |
| 81 | explore-tap | name@example.com | ✅ | 1.37s |
| 82 | explore-tap | 📚  ניהול רשימות | ✅ | 2.11s |
| 83 | explore-tap | שיתוף / שיתוף | ✅ | 2.19s |
| 84 | explore-tap | ייבוא / ייבוא | ✅ | 2.52s |
| 85 | explore-tap | סגירה | ✅ | 2.55s |
| 86 | explore-tap | הקלידו את הסיסמה | ✅ | 1.38s |
| 87 | explore-tap | 🔐  התחברות | ✅ | 1.37s |
| 88 | explore-tap | 📥  ייבוא רשימה מקובץ | ✅ | 1.65s |
| 89 | explore | left app to com.google.android.documentsui | ✅ | 0s |
| 90 | explore-tap | ☁️  הבאה מהענן לפי מספר | ✅ | 1.98s |
| 91 | explore-tap | סגירה | ✅ | 1.55s |
| 69 | explore | auto-explore from the list screen | ✅ | 172.55s |
| 70 | assert_foreground |  | ✅ | 0.16s |

## Notes

- explore: 8 distinct screens, 22 elements tapped, 3 relaunches, 172s

## Screenshots

**001-step4-wait_for-FAILED.png**

<img src="screenshots/001-step4-wait_for-FAILED.png" width="270">

**002-step5-scroll_to-FAILED.png**

<img src="screenshots/002-step5-scroll_to-FAILED.png" width="270">

**003-step6-tap-FAILED.png**

<img src="screenshots/003-step6-tap-FAILED.png" width="270">

**004-step7-tap-FAILED.png**

<img src="screenshots/004-step7-tap-FAILED.png" width="270">

**005-step8-wait_for-FAILED.png**

<img src="screenshots/005-step8-wait_for-FAILED.png" width="270">

**006-step9-tap-FAILED.png**

<img src="screenshots/006-step9-tap-FAILED.png" width="270">

**007-step10-wait_for-FAILED.png**

<img src="screenshots/007-step10-wait_for-FAILED.png" width="270">

**008-imported-5-items.png**

<img src="screenshots/008-imported-5-items.png" width="270">

**009-step12-assert_text-FAILED.png**

<img src="screenshots/009-step12-assert_text-FAILED.png" width="270">

**010-step13-tap-FAILED.png**

<img src="screenshots/010-step13-tap-FAILED.png" width="270">

**011-search-pasta.png**

<img src="screenshots/011-search-pasta.png" width="270">

**012-step17-assert_text-FAILED.png**

<img src="screenshots/012-step17-assert_text-FAILED.png" width="270">

**013-step19-type-FAILED.png**

<img src="screenshots/013-step19-type-FAILED.png" width="270">

**014-step21-assert_text-FAILED.png**

<img src="screenshots/014-step21-assert_text-FAILED.png" width="270">

**015-step22-long_press-FAILED.png**

<img src="screenshots/015-step22-long_press-FAILED.png" width="270">

**016-step23-wait_for-FAILED.png**

<img src="screenshots/016-step23-wait_for-FAILED.png" width="270">

**017-partial-dialog.png**

<img src="screenshots/017-partial-dialog.png" width="270">

**018-step25-swipe-FAILED.png**

<img src="screenshots/018-step25-swipe-FAILED.png" width="270">

**019-partial-dialog-moved.png**

<img src="screenshots/019-partial-dialog-moved.png" width="270">

**020-step27-tap-FAILED.png**

<img src="screenshots/020-step27-tap-FAILED.png" width="270">

**021-step29-scroll_to-FAILED.png**

<img src="screenshots/021-step29-scroll_to-FAILED.png" width="270">

**022-partial-collected.png**

<img src="screenshots/022-partial-collected.png" width="270">

**023-step31-scroll_to-FAILED.png**

<img src="screenshots/023-step31-scroll_to-FAILED.png" width="270">

**024-step32-tap-FAILED.png**

<img src="screenshots/024-step32-tap-FAILED.png" width="270">

**025-step33-wait_for-FAILED.png**

<img src="screenshots/025-step33-wait_for-FAILED.png" width="270">

**026-reset-confirm.png**

<img src="screenshots/026-reset-confirm.png" width="270">

**027-step35-tap-FAILED.png**

<img src="screenshots/027-step35-tap-FAILED.png" width="270">

**028-step36-wait_for-FAILED.png**

<img src="screenshots/028-step36-wait_for-FAILED.png" width="270">

**029-step37-tap-FAILED.png**

<img src="screenshots/029-step37-tap-FAILED.png" width="270">

**030-view-by-account.png**

<img src="screenshots/030-view-by-account.png" width="270">

**031-step40-tap-FAILED.png**

<img src="screenshots/031-step40-tap-FAILED.png" width="270">

**032-view-combined.png**

<img src="screenshots/032-view-combined.png" width="270">

**033-step43-tap-FAILED.png**

<img src="screenshots/033-step43-tap-FAILED.png" width="270">

**034-step44-assert_text-FAILED.png**

<img src="screenshots/034-step44-assert_text-FAILED.png" width="270">

**035-step47-assert_text-FAILED.png**

<img src="screenshots/035-step47-assert_text-FAILED.png" width="270">

**036-landscape.png**

<img src="screenshots/036-landscape.png" width="270">

**037-step51-tap-FAILED.png**

<img src="screenshots/037-step51-tap-FAILED.png" width="270">

**038-step52-wait_for-FAILED.png**

<img src="screenshots/038-step52-wait_for-FAILED.png" width="270">

**039-settings-top.png**

<img src="screenshots/039-settings-top.png" width="270">

**040-settings-middle.png**

<img src="screenshots/040-settings-middle.png" width="270">

**041-settings-bottom.png**

<img src="screenshots/041-settings-bottom.png" width="270">

**042-step59-scroll_to-FAILED.png**

<img src="screenshots/042-step59-scroll_to-FAILED.png" width="270">

**043-step60-tap-FAILED.png**

<img src="screenshots/043-step60-tap-FAILED.png" width="270">

**044-saved-lists.png**

<img src="screenshots/044-saved-lists.png" width="270">

**045-before-explore.png**

<img src="screenshots/045-before-explore.png" width="270">

**046-screen-1.png**

<img src="screenshots/046-screen-1.png" width="270">

**047-screen-2.png**

<img src="screenshots/047-screen-2.png" width="270">

**048-screen-3.png**

<img src="screenshots/048-screen-3.png" width="270">

**049-screen-4.png**

<img src="screenshots/049-screen-4.png" width="270">

**050-screen-5.png**

<img src="screenshots/050-screen-5.png" width="270">

**051-screen-6.png**

<img src="screenshots/051-screen-6.png" width="270">

**052-screen-7.png**

<img src="screenshots/052-screen-7.png" width="270">

**053-left-app-to-com-google-android-documentsui.png**

<img src="screenshots/053-left-app-to-com-google-android-documentsui.png" width="270">

**054-screen-8.png**

<img src="screenshots/054-screen-8.png" width="270">

**055-final.png**

<img src="screenshots/055-final.png" width="270">
