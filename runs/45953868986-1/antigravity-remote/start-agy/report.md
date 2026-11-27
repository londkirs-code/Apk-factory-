# UI test — antigravity-remote (scenario)

**Result:** ❌ FAILED  
**Package:** `com.londkirs.antigravityremote`  
**Device:** sdk_gphone64_x86_64 · Android 13 (API 33) · 1080x2400  
**Duration:** 49.4s

## Crashes / ANRs

```
Crash: E/AndroidRuntime( 4799): Process: com.londkirs.antigravityremote, PID: 4799
--------- beginning of crash
E/AndroidRuntime( 4799): FATAL EXCEPTION: main
E/AndroidRuntime( 4799): Process: com.londkirs.antigravityremote, PID: 4799
E/AndroidRuntime( 4799): java.lang.RuntimeException: Unable to start activity ComponentInfo{com.londkirs.antigravityremote/com.londkirs.antigravityremote.TerminalActivity}: java.lang.NullPointerException: Attempt to read from field 'int com.termux.view.TerminalRenderer.mTextSize' on a null object reference in method 'void com.termux.view.TerminalView.setTypeface(android.graphics.Typeface)'
E/AndroidRuntime( 4799): 	at android.app.ActivityThread.performLaunchActivity(ActivityThread.java:3645)
E/AndroidRuntime( 4799): 	at android.app.ActivityThread.handleLaunchActivity(ActivityThread.java:3782)
E/AndroidRuntime( 4799): 	at android.app.servertransaction.LaunchActivityItem.execute(LaunchActivityItem.java:101)
E/AndroidRuntime( 4799): 	at android.app.servertransaction.TransactionExecutor.executeCallbacks(TransactionExecutor.java:138)
E/AndroidRuntime( 4799): 	at android.app.servertransaction.TransactionExecutor.execute(TransactionExecutor.java:95)
E/AndroidRuntime( 4799): 	at android.app.ActivityThread$H.handleMessage(ActivityThread.java:2307)
E/AndroidRuntime( 4799): 	at android.os.Handler.dispatchMessage(Handler.java:106)
E/AndroidRuntime( 4799): 	at android.os.Looper.loopOnce(Looper.java:201)
E/AndroidRuntime( 4799): 	at android.os.Looper.loop(Looper.java:288)
E/AndroidRuntime( 4799): 	at android.app.ActivityThread.main(ActivityThread.java:7924)
E/AndroidRuntime( 4799): 	at java.lang.reflect.Method.invoke(Native Method)
E/AndroidRuntime( 4799): 	at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:548)
E/AndroidRuntime( 4799): 	at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:936)
E/AndroidRuntime( 4799): Caused by: java.lang.NullPointerException: Attempt to read from field 'int com.termux.view.TerminalRenderer.mTextSize' on a null object reference in method 'void com.termux.view.TerminalView.setTypeface(android.graphics.Typeface)'
E/AndroidRuntime( 4799): 	at com.termux.view.TerminalView.setTypeface(TerminalView.java:454)
E/AndroidRuntime( 4799): 	at com.londkirs.antigravityremote.TerminalActivity.onCreate(TerminalActivity.kt:118)
E/AndroidRuntime( 4799): 	at android.app.Activity.performCreate(Activity.java:8342)
E/AndroidRuntime( 4799): 	at android.app.Activity.performCreate(Activity.java:8321)
E/AndroidRuntime( 4799): 	at android.app.Instrumentation.callActivityOnCreate(Instrumentation.java:1417)
E/AndroidRuntime( 4799): 	at android.app.ActivityThread.performLaunchActivity(ActivityThread.java:3626)
E/AndroidRuntime( 4799): 	... 12 more

--------- beginning of crash
```

## Steps

| # | Action | Detail | Result | Time |
|---|---|---|---|---|
| 1 | launch | clear_data=True | ✅ | 4.91s |
| 2 | tap | text='Wait' | ⚠️ — element not found within 10s: text='Wait' | 11.52s |
| 3 | shell | cmd='run-as com.londkirs.antigravityremote sh -c \'mkdir -p files/bin && echo "#!/system/b | ✅ | 0.16s |
| 4 | back |  | ✅ | 1.14s |
| 5 | launch |  | ✅ | 3.89s |
| 6 | tap | text='Wait' | ⚠️ — element not found within 10s: text='Wait' | 12.23s |
| 7 | wait_for | text='Start Antigravity' | ✅ | 2.72s |
| 8 | screenshot | name='home-installed' | ✅ | 3.14s |
| 9 | tap | text='Start Antigravity' | ✅ | 4.09s |

## Screenshots

**001-home-installed.png**

<img src="screenshots/001-home-installed.png" width="270">

**002-final.png**

<img src="screenshots/002-final.png" width="270">
