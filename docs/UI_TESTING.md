# בדיקת ממשק (UI) אוטומטית — אמולטור, ניווט, לחיצות וצילומי מסך

יכולת **אופציונלית** שמשותפת לכל המאגר. אפשר להשתמש בה לכל אפליקציה תחת `apps/`, וגם ל־APK חיצוני (NotifAI, נץ החמה או כל קישור HTTPS). היא מפעילה אמולטור Android בתוך GitHub Actions, מתקינה את ה־APK, מנווטת באפליקציה (לחיצות, הקלדה, גלילה, חזרה), מצלמת מסכים, מזהה קריסות ו־ANR ומפרסמת דוח עם צילומי מסך.

**היא לא רצה בבנייה רגילה.** בניית APK נשארת מהירה כמו שהייתה. הבדיקה רצה רק כשמבקשים אותה במפורש, או אחרי בנייה אם הפעלתם אותה ידנית (ראו בהמשך). ריצה טיפוסית לוקחת 4–8 דקות: כ־2–4 דקות לאתחול האמולטור (בפעם הראשונה לכל רמת API, ומהר יותר אחר כך בזכות snapshot שנשמר ב־cache), ועוד הזמן של התרחיש עצמו.

## רכיבים
| קובץ | תפקיד |
|---|---|
| `.github/workflows/ui-test-apk.yml` | Workflow: אמולטור, התקנה, הרצה ופרסום |
| `tools/ui-testing/ui_driver.py` | הדרייבר. Python טהור מעל `adb`, ועובד גם מול טלפון אמיתי |
| `tools/ui-testing/run-ci.sh` | בחירת APK ותרחישים לכל אפליקציה וסיכום |
| `tools/ui-testing/publish-results.sh` | פרסום התוצאות לענף `ui-test-results` |
| `tools/ui-testing/scenarios/example-smoke.json` | תבנית לתרחיש |
| `apps/<app>/ui-tests/*.json` או `ui-tests/<app>/*.json` | תרחישים לכל אפליקציה |

## שלוש דרכים להפעלה
1. **Actions → UI test APK on emulator → Run workflow**
   - `app`: מזהה אפליקציה, למשל `monopoly` או `NotifAI`. אפשר כמה מזהים מופרדים ברווח.
   - `mode`:
     - `auto`: מריץ תרחישים אם יש, ואם אין מריץ סריקה אוטומטית.
     - `scenario`: מריץ תרחישים בלבד.
     - `explore`: מריץ סריקה אוטומטית בלבד.
     - `both`: מריץ את שניהם.
   - `scenario`: נתיב לתרחיש מסוים (לא חובה).
   - `explore_actions`: מספר הלחיצות המקסימלי בסריקה.
   - `apk_url`: לבדוק APK מקישור HTTPS במקום הקובץ שב־`downloads/`.
   - `api_level`: ברירת המחדל היא 33.
   - `locale`: למשל `he-IL` לבדיקה בעברית.
2. **תגובה ב־[issue #1](https://github.com/londkirs-code/Apk-factory-/issues/1)** (רק בעל המאגר):
   ```
   /ui-test monopoly
   /ui-test mishnat-yosef explore 50
   /ui-test hagaha apps/hagaha/ui-tests/login.json
   ```
   ה־workflow מגיב ב־issue עם קישור לריצה, ובסיום עם קישור לדוח.
3. **אוטומטית אחרי כל בנייה מוצלחת** (כבוי כברירת מחדל): ב־Settings → Secrets and variables → Actions → Variables מגדירים:
   - `UI_TEST_AFTER_BUILD` = `true`
   - `UI_TEST_APPS` = רשימת אפליקציות (לא חובה). אם לא מגדירים, ייבדקו רק אפליקציות שיש להן תרחישים.

   הבדיקה משתמשת ב־APK מתוך ה־Artifact של אותה בנייה.

שימוש מתוך workflow אחר: `uses: ./.github/workflows/ui-test-apk.yml` עם `app` ו־`apk_artifact`.

## איפה רואים את התוצאות
- **ענף `ui-test-results`**: `runs/<run-id>-<attempt>/summary.md`, ולכל אפליקציה ותרחיש `report.md`. GitHub מציג את ה־report עם צילומי המסך בתוך הדף. הקובץ `latest/<app>.md` מצביע תמיד על הריצה האחרונה. הענף שומר רק את 40 הריצות האחרונות, והוא נפרד מ־`main`, כך שלא מתווספות תמונות להיסטוריה של הקוד.
- **Job summary** של הריצה: טבלה עם קישורים.
- **Artifact** `ui-test-results-*`: עותק מלא שכולל `logcat.txt`, dump של הממשק (`ui/*.xml`) ו־`index.html` (גלריה).

## כתיבת תרחיש
קובץ JSON עם רשימת `steps`. לכל צעד יש `action`, ואפשר לאתר רכיב באחת מהדרכים הבאות:
- `text`: התאמה חלקית ללא תלות ברישיות.
- `id`: למשל `settings` או `pkg:id/settings`.
- `desc`: התאמה ל־content-desc.
- `any`: התאמה לטקסט, ל־desc או ל־id.
- `regex`
- `class`

אפשר להוסיף `exact: true` להתאמה מדויקת, ו־`index` כשיש כמה התאמות.

```json
{
  "name": "smoke",
  "grant_permissions": true,
  "steps": [
    {"action": "launch", "clear_data": true},
    {"action": "wait_for", "text": "ברוכים הבאים", "timeout": 15},
    {"action": "screenshot", "name": "home"},
    {"action": "tap", "any": "הגדרות"},
    {"action": "assert_text", "text": "גודל גופן"},
    {"action": "scroll_to", "text": "אודות", "direction": "up"},
    {"action": "tap", "x": 0.5, "y": 0.9},
    {"action": "type", "id": "search", "value": "hello", "enter": true},
    {"action": "back"},
    {"action": "assert_not_visible", "text": "שגיאה"},
    {"action": "explore", "max_actions": 15}
  ]
}
```

| פעולה | פרמטרים |
|---|---|
| `launch` | `clear_data`, `restart` (ברירת מחדל true), `wait_for` |
| `tap` / `long_press` / `double_tap` | בורר רכיב או `x`,`y` (פיקסלים, או שבר בין 0 ל־1 כמו `0.5`), `settle` |
| `type` | `value`, בורר שדה (לא חובה), `clear`, `enter`, `hide_keyboard` |
| `wait_for` / `wait_gone` | בורר רכיב, `timeout` |
| `assert_text` / `assert_visible` / `assert_not_visible` / `assert_foreground` | בורר רכיב |
| `swipe` | `direction` (up/down/left/right) או `x1,y1,x2,y2` |
| `scroll_to` | בורר רכיב, `direction`, `max_swipes` |
| `back`, `home`, `key` | `key`: BACK/HOME/ENTER/TAB/DEL או keycode |
| `screenshot` | `name` |
| `wait` | `seconds` |
| `rotate` | `to`: portrait/landscape |
| `open_url` | `url` (deep link) |
| `stop`, `clear_data`, `grant`, `dump`, `shell` | פעולות עזר |
| `explore` | `max_actions`: סריקה אוטומטית מהמסך הנוכחי |

אפשרויות לכל צעד:
- `"optional": true`: אם הצעד נכשל, הוא מסומן אזהרה ולא כישלון.
- `"screenshot": true`: מצלם מסך אחרי הצעד.

אפשרויות ברמת התרחיש:
- `screenshot_every_step`
- `stop_on_failure` (ברירת מחדל true)
- `avoid`: ביטוי רגולרי לרכיבים שהסריקה האוטומטית לא תלחץ עליהם.

כל צעד שנכשל מצולם אוטומטית.

## הסריקה האוטומטית (explore)
הסריקה מפעילה את האפליקציה ולוחצת על כל רכיב לחיץ שעוד לא נלחץ במסך הנוכחי, לפי הסדר: קודם רכיבים עם טקסט, ומלמעלה למטה. כל מסך חדש מצולם. אם האפליקציה יוצאת לאפליקציה אחרת, הסריקה לוחצת Back או מפעילה אותה מחדש. היא נעצרת מיד אם יש קריסה.

כברירת מחדל הסריקה **לא לוחצת** על כפתורים שהטקסט שלהם כולל מילים כמו מחק, הסר, התנתק, שלח, תשלום, רכישה, איפוס, delete, send, pay, logout ודומיהן. זה נועד למנוע פעולות אמיתיות מול שרתים. אפשר לשנות את הרשימה עם `avoid`.

## הרצה מקומית מול טלפון אמיתי
כשיש מחשב עם `adb` וטלפון מחובר (או אמולטור מקומי):
```bash
python3 tools/ui-testing/ui_driver.py --apk downloads/monopoly/app.apk --scenario apps/monopoly/ui-tests/smoke.json --out /tmp/ui
python3 tools/ui-testing/ui_driver.py --package com.londkirs.monopoly --explore 40 --out /tmp/ui   # בלי התקנה
```

## מגבלות ידועות
- **הקלדת עברית:** `adb input text` תומך רק ב־ASCII. לטקסט בעברית צריך להתקין במכשיר את המקלדת ADBKeyBoard (`com.android.adbkeyboard`). הדרייבר משתמש בה אוטומטית כשהיא מותקנת, ואחרת הצעד נכשל עם הסבר ברור. לחיצה על כפתורים וזיהוי טקסט בעברית עובדים בלי מגבלה.
- **WebView ו־Canvas:** משחקים או מסכים שמצוירים ב־Canvas לא חושפים רכיבים ל־uiautomator. במסכים כאלה משתמשים בלחיצה לפי קואורדינטות ובצילומי מסך.
- **התחברות:** כניסה עם Google, קוד SMS או שירותי Play לא זמינה באמולטור. כדי להיכנס צריך חשבון בדיקה או מצב demo באפליקציה.
- **בדיקה באמולטור אינה בדיקה במכשיר פיזי.** בדיווח צריך לכתוב "נבדק באמולטור API xx". אסור לכתוב "נבדק במכשיר".
- המאגר ציבורי, ולכן גם צילומי המסך ציבוריים. אין לבדוק כאן מסכים עם מידע אישי או סודי.
