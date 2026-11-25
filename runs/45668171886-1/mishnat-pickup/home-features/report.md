# UI test — mishnat-pickup (scenario)

**Result:** ❌ FAILED  
**Package:** `org.mishnatyosef.pickup`  
**Device:** sdk_gphone64_x86_64 · Android 13 (API 33) · 1080x2400  
**Duration:** 579.9s

## Steps

| # | Action | Detail | Result | Time |
|---|---|---|---|---|
| 1 | clear_data |  | ✅ | 0.31s |
| 2 | push | src='fixtures/ui-test-features.txt', dest='/sdcard/Download/ui-test-features.txt' | ✅ | 43.2s |
| 3 | launch |  | ✅ | 5.65s |
| 4 | wait_for | text='כניסה לחשבון' | ❌ — element not found within 30s: text='כניסה לחשבון' | 31.46s |
| 5 | scroll_to | text='ייבוא רשימה מקובץ' | ❌ — not found after scrolling: text='ייבוא רשימה מקובץ' | 16.38s |
| 6 | tap | text='ייבוא רשימה מקובץ' | ❌ — element not found within 10s: text='ייבוא רשימה מקובץ' | 10.58s |
| 7 | tap | text='ui-test-features' | ❌ — element not found within 10s: text='ui-test-features' | 11.19s |
| 8 | wait_for | text='נמצאו 1 רשימות' | ❌ — element not found within 15s: text='נמצאו 1 רשימות' | 16.01s |
| 9 | tap | text='ייבא רשימות' | ❌ — element not found within 10s: text='ייבא רשימות' | 10.11s |
| 10 | wait_for | 1+2+3+4+1.5 units | ❌ — element not found within 10s: text='נאספו 0 מתוך 11.5' | 10.15s |
| 11 | screenshot | name='imported-5-items' | ✅ | 2.4s |
| 12 | assert_text | decimal quantity kept | ❌ — element not found within 5s: text='1.5' | 7.48s |
| 13 | tap | text='חיפוש פריט' | ❌ — element not found within 10s: text='חיפוש פריט' | 10.23s |
| 14 | type | value='Pasta', hide_keyboard=True | ✅ | 0.89s |
| 15 | wait | seconds=1 | ✅ | 1.07s |
| 16 | screenshot | name='search-pasta' | ✅ | 2.39s |
| 17 | assert_text | text='Pasta' | ❌ — element not found within 5s: text='Pasta' | 7.38s |
| 18 | assert_not_visible | search filters other items | ✅ | 2.55s |
| 19 | type | class='EditText' | ❌ — element not found within 10s: class='EditText' | 10.15s |
| 20 | wait | seconds=1 | ✅ | 1.06s |
| 21 | assert_text | cleared search shows all items | ❌ — element not found within 5s: text='מלפפון' | 7.4s |
| 22 | long_press | text='Pasta' | ❌ — element not found within 10s: text='Pasta' | 10.12s |
| 23 | wait_for | text='איסוף כמות חלקית' | ❌ — element not found within 5s: text='איסוף כמות חלקית' | 7.38s |
| 24 | screenshot | name='partial-dialog' | ✅ | 2.38s |
| 25 | swipe | RTL slider: try to pick more units | ❌ — element not found within 10s: class='SeekBar' | 10.08s |
| 26 | screenshot | name='partial-dialog-moved' | ✅ | 2.78s |
| 27 | tap | text='אישור הכמות' | ❌ — element not found within 10s: text='אישור הכמות' | 10.16s |
| 28 | wait | seconds=1 | ✅ | 1.1s |
| 29 | scroll_to | the partial card sits in the collected section, below the fold | ❌ — not found after scrolling: text='נאספו חלקית' | 17.99s |
| 30 | screenshot | name='partial-collected' | ✅ | 2.35s |
| 31 | scroll_to | text='נאספו 1 מתוך' | ❌ — not found after scrolling: text='נאספו 1 מתוך' | 17.91s |
| 32 | tap | desc='איפוס ליקוט' | ❌ — element not found within 10s: desc='איפוס ליקוט' | 10.05s |
| 33 | wait_for | text='להחזיר את כל המוצרים' | ❌ — element not found within 5s: text='להחזיר את כל המוצרים' | 7.34s |
| 34 | screenshot | name='reset-confirm' | ✅ | 2.41s |
| 35 | tap | text='איפוס' | ❌ — element not found within 10s: text='איפוס' | 10.11s |
| 36 | wait_for | text='נאספו 0 מתוך 11.5' | ❌ — element not found within 5s: text='נאספו 0 מתוך 11.5' | 7.42s |
| 37 | tap | desc='רשימה לפי חשבון' | ❌ — element not found within 10s: desc='רשימה לפי חשבון' | 10.15s |
| 38 | wait | seconds=1.5 | ✅ | 1.57s |
| 39 | screenshot | name='view-by-account' | ✅ | 2.37s |
| 40 | tap | desc='רשימה מאוחדת' | ❌ — element not found within 10s: desc='רשימה מאוחדת' | 10.08s |
| 41 | wait | seconds=1.5 | ✅ | 1.58s |
| 42 | screenshot | name='view-combined' | ✅ | 2.56s |
| 43 | tap | text='גבינה' | ❌ — element not found within 10s: text='גבינה' | 10.13s |
| 44 | assert_text | text='נאספו 1.5 מתוך 11.5' | ❌ — element not found within 5s: text='נאספו 1.5 מתוך 11.5' | 7.38s |
| 45 | rotate | to='landscape' | ✅ | 2.96s |
| 46 | wait | seconds=1.5 | ✅ | 1.57s |
| 47 | assert_text | progress survives rotation | ❌ — element not found within 5s: text='נאספו 1.5 מתוך 11.5' | 7.4s |
| 48 | screenshot | name='landscape' | ✅ | 2.36s |
| 49 | rotate | to='portrait' | ✅ | 1.68s |
| 50 | wait | seconds=1.5 | ✅ | 1.59s |
| 51 | tap | desc='הגדרות' | ❌ — element not found within 10s: desc='הגדרות' | 10.07s |
| 52 | wait_for | text='סיכום מהיר' | ❌ — element not found within 8s: text='סיכום מהיר' | 10.07s |
| 53 | screenshot | name='settings-top' | ✅ | 2.36s |
| 54 | swipe | direction='up' | ✅ | 1.26s |
| 55 | screenshot | name='settings-middle' | ✅ | 2.37s |
| 56 | swipe | direction='up' | ✅ | 1.27s |
| 57 | swipe | direction='up' | ✅ | 1.25s |
| 58 | screenshot | name='settings-bottom' | ✅ | 2.36s |
| 59 | scroll_to | text='רשימות שמורות' | ❌ — not found after scrolling: text='רשימות שמורות' | 21.0s |
| 60 | tap | text='רשימות שמורות' | ❌ — element not found within 10s: text='רשימות שמורות' | 10.12s |
| 61 | wait | seconds=2 | ✅ | 2.08s |
| 62 | screenshot | name='saved-lists' | ✅ | 2.35s |
| 63 | back |  | ✅ | 0.91s |
| 64 | wait | seconds=1 | ✅ | 1.09s |
| 65 | back |  | ✅ | 0.89s |
| 66 | wait | seconds=1 | ✅ | 1.1s |
| 67 | launch | restart=False | ✅ | 2.89s |
| 68 | screenshot | name='before-explore' | ✅ | 2.35s |
| 69 | explore | left app to com.google.android.apps.nexuslauncher | ✅ | 0s |
| 70 | explore | left app to com.google.android.apps.nexuslauncher | ✅ | 0s |
| 71 | explore | left app to com.google.android.apps.nexuslauncher | ✅ | 0s |
| 72 | explore | left app to com.google.android.apps.nexuslauncher | ✅ | 0s |
| 73 | explore | left app to com.google.android.apps.nexuslauncher | ✅ | 0s |
| 74 | explore | left app to com.google.android.apps.nexuslauncher | ✅ | 0s |
| 69 | explore | auto-explore from the list screen | ✅ | 34.32s |
| 70 | assert_foreground |  | ⚠️ — foreground app is com.google.android.apps.nexuslauncher, expected org.mishnatyosef.pickup | 0.02s |

## Notes

- explore: app kept leaving the foreground; stopping
- explore: 0 distinct screens, 0 elements tapped, 6 relaunches, 34s

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

**046-left-app-to-com-google-android-apps-nexuslauncher.png**

<img src="screenshots/046-left-app-to-com-google-android-apps-nexuslauncher.png" width="270">

**047-left-app-to-com-google-android-apps-nexuslauncher.png**

<img src="screenshots/047-left-app-to-com-google-android-apps-nexuslauncher.png" width="270">

**048-left-app-to-com-google-android-apps-nexuslauncher.png**

<img src="screenshots/048-left-app-to-com-google-android-apps-nexuslauncher.png" width="270">

**049-left-app-to-com-google-android-apps-nexuslauncher.png**

<img src="screenshots/049-left-app-to-com-google-android-apps-nexuslauncher.png" width="270">

**050-left-app-to-com-google-android-apps-nexuslauncher.png**

<img src="screenshots/050-left-app-to-com-google-android-apps-nexuslauncher.png" width="270">

**051-left-app-to-com-google-android-apps-nexuslauncher.png**

<img src="screenshots/051-left-app-to-com-google-android-apps-nexuslauncher.png" width="270">

**052-final.png**

<img src="screenshots/052-final.png" width="270">
