# UI test — antigravity-remote (scenario)

**Result:** ❌ FAILED  
**Package:** `com.londkirs.antigravityremote`  
**Device:** sdk_gphone64_x86_64 · Android 13 (API 33) · 1080x2400  
**Duration:** 378.8s

## Steps

| # | Action | Detail | Result | Time |
|---|---|---|---|---|
| 1 | launch | clear_data=True | ✅ | 3.21s |
| 2 | wait_for | text='Setup guide' | ❌ — element not found within 20s: text='Setup guide' | 21.05s |
| 3 | tap | desc='Connect to a computer instead' | ❌ — element not found within 10s: desc='Connect to a computer instead' | 10.1s |
| 4 | wait | seconds=1 | ✅ | 1.03s |
| 5 | launch |  | ✅ | 3.23s |
| 6 | wait | seconds=2 | ✅ | 2.03s |
| 7 | screenshot | name='01-home-tip' | ✅ | 2.25s |
| 8 | wait | seconds=2 | ✅ | 2.03s |
| 9 | tap | text='Got it' | ⚠️ — element not found within 10s: text='Got it' | 10.0s |
| 10 | screenshot | name='02-home-empty' | ✅ | 2.23s |
| 11 | tap | desc='New chat' | ❌ — element not found within 10s: desc='New chat' | 10.02s |
| 12 | wait | seconds=3 | ✅ | 3.03s |
| 13 | screenshot | name='03-chat-empty' | ✅ | 2.24s |
| 14 | wait | seconds=2 | ✅ | 2.03s |
| 15 | tap | text='Got it' | ⚠️ — element not found within 10s: text='Got it' | 12.64s |
| 16 | tap | text='Message' | ❌ — element not found within 10s: text='Message' | 12.67s |
| 17 | type | text='Message' | ❌ — element not found within 10s: text='Message' | 10.0s |
| 18 | screenshot | name='04b-keyboard' | ✅ | 2.25s |
| 19 | key | key='BACK' | ✅ | 0.85s |
| 20 | screenshot | name='04-chat-typed' | ✅ | 2.23s |
| 21 | tap | desc='Attach' | ❌ — element not found within 10s: desc='Attach' | 12.64s |
| 22 | wait | seconds=1 | ✅ | 1.03s |
| 23 | screenshot | name='05-attach-sheet' | ✅ | 2.24s |
| 24 | back |  | ✅ | 0.88s |
| 25 | tap | desc='More' | ❌ — element not found within 10s: desc='More' | 12.61s |
| 26 | wait | seconds=1 | ✅ | 1.03s |
| 27 | screenshot | name='06-chat-menu' | ✅ | 2.69s |
| 28 | tap | text='Chat info' | ❌ — element not found within 10s: text='Chat info' | 12.61s |
| 29 | wait | seconds=1 | ✅ | 1.03s |
| 30 | screenshot | name='07-chat-info' | ✅ | 2.7s |
| 31 | back |  | ✅ | 0.84s |
| 32 | back |  | ✅ | 0.84s |
| 33 | wait | seconds=1 | ✅ | 1.03s |
| 34 | wait | seconds=2 | ✅ | 2.03s |
| 35 | tap | text='Got it' | ⚠️ — element not found within 10s: text='Got it' | 12.6s |
| 36 | screenshot | name='08-home-draft' | ✅ | 2.7s |
| 37 | long_press | text='New chat' | ❌ — element not found within 10s: text='New chat' | 12.58s |
| 38 | screenshot | name='09-home-selection' | ✅ | 2.7s |
| 39 | back |  | ✅ | 0.84s |
| 40 | tap | desc='More' | ❌ — element not found within 10s: desc='More' | 12.59s |
| 41 | screenshot | name='10-home-menu' | ✅ | 2.71s |
| 42 | tap | text='Settings' | ❌ — element not found within 10s: text='Settings' | 12.57s |
| 43 | wait | seconds=1 | ✅ | 1.03s |
| 44 | screenshot | name='11-settings' | ✅ | 2.69s |
| 45 | scroll_to | text='App version' | ❌ — not found after scrolling: text='App version' | 27.49s |
| 46 | screenshot | name='12-settings-advanced' | ✅ | 2.26s |
| 47 | scroll_to | text='Theme' | ❌ — not found after scrolling: text='Theme' | 28.99s |
| 48 | tap | text='Theme' | ❌ — element not found within 10s: text='Theme' | 10.07s |
| 49 | wait | seconds=1 | ✅ | 1.03s |
| 50 | screenshot | name='13-theme-sheet' | ✅ | 2.27s |
| 51 | tap | text='Dark' | ❌ — element not found within 10s: text='Dark' | 10.05s |
| 52 | wait | seconds=2 | ✅ | 2.03s |
| 53 | screenshot | name='14-settings-dark' | ✅ | 2.28s |
| 54 | back |  | ✅ | 0.85s |
| 55 | wait | seconds=1 | ✅ | 1.03s |
| 56 | screenshot | name='15-home-dark' | ✅ | 2.27s |
| 57 | tap | text='New chat' | ❌ — element not found within 10s: text='New chat' | 12.65s |
| 58 | wait | seconds=2 | ✅ | 2.04s |
| 59 | screenshot | name='16-chat-dark' | ✅ | 2.24s |

## Screenshots

**001-step2-wait_for-FAILED.png**

<img src="screenshots/001-step2-wait_for-FAILED.png" width="270">

**002-step3-tap-FAILED.png**

<img src="screenshots/002-step3-tap-FAILED.png" width="270">

**003-01-home-tip.png**

<img src="screenshots/003-01-home-tip.png" width="270">

**004-02-home-empty.png**

<img src="screenshots/004-02-home-empty.png" width="270">

**005-step11-tap-FAILED.png**

<img src="screenshots/005-step11-tap-FAILED.png" width="270">

**006-03-chat-empty.png**

<img src="screenshots/006-03-chat-empty.png" width="270">

**007-step16-tap-FAILED.png**

<img src="screenshots/007-step16-tap-FAILED.png" width="270">

**008-step17-type-FAILED.png**

<img src="screenshots/008-step17-type-FAILED.png" width="270">

**009-04b-keyboard.png**

<img src="screenshots/009-04b-keyboard.png" width="270">

**010-04-chat-typed.png**

<img src="screenshots/010-04-chat-typed.png" width="270">

**011-step21-tap-FAILED.png**

<img src="screenshots/011-step21-tap-FAILED.png" width="270">

**012-05-attach-sheet.png**

<img src="screenshots/012-05-attach-sheet.png" width="270">

**013-step25-tap-FAILED.png**

<img src="screenshots/013-step25-tap-FAILED.png" width="270">

**014-06-chat-menu.png**

<img src="screenshots/014-06-chat-menu.png" width="270">

**015-step28-tap-FAILED.png**

<img src="screenshots/015-step28-tap-FAILED.png" width="270">

**016-07-chat-info.png**

<img src="screenshots/016-07-chat-info.png" width="270">

**017-08-home-draft.png**

<img src="screenshots/017-08-home-draft.png" width="270">

**018-step37-long_press-FAILED.png**

<img src="screenshots/018-step37-long_press-FAILED.png" width="270">

**019-09-home-selection.png**

<img src="screenshots/019-09-home-selection.png" width="270">

**020-step40-tap-FAILED.png**

<img src="screenshots/020-step40-tap-FAILED.png" width="270">

**021-10-home-menu.png**

<img src="screenshots/021-10-home-menu.png" width="270">

**022-step42-tap-FAILED.png**

<img src="screenshots/022-step42-tap-FAILED.png" width="270">

**023-11-settings.png**

<img src="screenshots/023-11-settings.png" width="270">

**024-step45-scroll_to-FAILED.png**

<img src="screenshots/024-step45-scroll_to-FAILED.png" width="270">

**025-12-settings-advanced.png**

<img src="screenshots/025-12-settings-advanced.png" width="270">

**026-step47-scroll_to-FAILED.png**

<img src="screenshots/026-step47-scroll_to-FAILED.png" width="270">

**027-step48-tap-FAILED.png**

<img src="screenshots/027-step48-tap-FAILED.png" width="270">

**028-13-theme-sheet.png**

<img src="screenshots/028-13-theme-sheet.png" width="270">

**029-step51-tap-FAILED.png**

<img src="screenshots/029-step51-tap-FAILED.png" width="270">

**030-14-settings-dark.png**

<img src="screenshots/030-14-settings-dark.png" width="270">

**031-15-home-dark.png**

<img src="screenshots/031-15-home-dark.png" width="270">

**032-step57-tap-FAILED.png**

<img src="screenshots/032-step57-tap-FAILED.png" width="270">

**033-16-chat-dark.png**

<img src="screenshots/033-16-chat-dark.png" width="270">
