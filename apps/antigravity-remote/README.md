# אנטיגרוויטי באנדרואיד (Antigravity Remote)

שתי דרכים להשתמש ב־Antigravity מהטלפון:
1. **ישירות בטלפון:** הרצת סוכן Antigravity ‏(`agy`) בטרמינל מובנה.
2. **מרחוק:** חיבור ל־Antigravity שרץ על המחשב.

## מבנה האפליקציה (4.0)
- **פתיחה ראשונה:** אשף הפעלה (`SetupActivity`): התאמת מכשיר ← התקנת agy ← הפעלה והתחברות ל־Google ← בחירת מודל ← שיחה ראשונה. כל צעד מתבצע בלחיצה ומציג מצב.
- **מסך ראשי:** רשימת שיחות בסגנון WhatsApp (`MainActivity`) עם צ'יפים לסינון (הכול, לא נקראו, מועדפים ורשימות משלכם), ארכיון, נעיצה, השתקה, טיוטות, החלקות, בחירה מרובה וחיפוש בתוך ההודעות.
- **שיחה:** `ChatActivity`, עם החלקה להשבה, כוכב, נעיצה, העברה, מדיה, הודעות קוליות, תבניות ופקודות `/`.
- **הגדרות:** מראה, צ'אט, התראות, עזרה, ו**מתקדם**: טרמינל, Shell, ממשק גרפי מלא, חיבור למחשב, עדכון ועצירת הסוכן.
- **עיצוב:** כל הרכיבים (דיאלוגים, תפריטים, גיליונות, הודעות, מתגים) מצוירים באפליקציה עצמה (`Ui.kt`, ‏`Theme.kt`), בפלטה שנגזרה מהאייקון, בבהיר ובכהה.

## צ'אט
- האפליקציה מפעילה ברקע את `agy --hub` (פורט 4400) ומדברת איתו ב־Connect-RPC JSON על ‏`/exa.language_server_pb.LanguageServerService/*`, עם טוקן CSRF.
- קריאות: ‏`StartCascade` (תיקיית עבודה `~/projects` או תיקייה שנבחרה לשיחה), ‏`SendUserCascadeMessage`, ‏`GetCascadeTrajectorySteps` (ריענון כל 1.2 שניות בזמן עבודה), ‏`HandleCascadeUserInteraction`, ‏`CancelCascadeInvocation`, ‏`SaveMediaAsArtifact`, ‏`GetSlashCommands`, ‏`Login` ו־`GetAuthStatus`.
- **מדיה מהסוכן:** תמונות שמגיעות inline (base64) נשמרות ב־`files/media`. קבצי תמונה וסרטון שמוזכרים בצעדים או בתשובה ונמצאים בטלפון מוצגים בבועה. צעדים שרק קוראים קבצים לא מוצגים כמדיה.
- **הודעות קוליות:** המודל לא שומע אודיו, ולכן נשלח תמלול. ב־Android 13 ומעלה ההקלטה נשמרת (WAV) ואפשר לנגן אותה. בגרסאות קודמות נשמר רק התמלול.
- **ברקע:** `ChatWatcher` רץ בתוך שירות הסוכן, מעדכן "לא נקראו" ושולח התראות עם תשובה, אישור ודחייה.
- **נתונים מקומיים:** כוכבים, נעיצות, "נמחק אצלי" והקלטות נשמרים רק בטלפון (`MsgMeta`). עותק אחרון של כל שיחה (`ChatCache`) משמש לתצוגה מיידית ולחיפוש.
- פרטי הפרוטוקול נלמדו מקוד פתוח של הקהילה (AntiGem, ‏RadnusD). המימוש כאן עצמאי ולא מעתיק קוד.

## הרצה ישירות בטלפון
- Google מפרסמת גרסת Android רשמית של Antigravity CLI. זו הגרסה שה־`install.sh` הרשמי בוחר כשמריצים אותו ב־Termux (`uname -o` = Android, פלטפורמה `android_arm64`). זה קובץ ELF שנבנה ב־NDK ל־Android 24 ומעלה, עם `/system/bin/linker64`, והוא תלוי רק ב־`libc`/`libm`/`libdl`/`liblog` של המערכת. לכן לא צריך glibc, proot או את טלאי ה־VA39 שהקהילה השתמשה בו לפני שהגרסה הזו יצאה.
- בלחיצה על "התקנת agy" האפליקציה קוראת את ה־manifest הרשמי (`…run.app/manifests/android_arm64.json`), מורידה את ה־tar.gz מ־`storage.googleapis.com`, מאמתת SHA-512 מול ה־manifest ומחלצת את `antigravity` אל `files/bin/agy`. הקובץ לא נכלל ב־APK ולא מופץ מחדש.
- הטרמינל מבוסס על ספריות terminal-view/terminal-emulator של Termux (רישיון Apache-2.0, דרך JitPack): ‏PTY, אמולציית xterm, ושורת מקשים נוספים (ESC, ‏TAB, ‏CTRL, ‏ALT, חיצים, ‏^C, הדבקה).
- הסשנים רצים בתוך Foreground Service עם Wake Lock, כך שהסוכנים ממשיכים לרוץ גם כשהמסך כבוי.
- התחברות: ‏`BROWSER` מצביע על `files/bin/open-url`. הסקריפט כותב את הכתובת לקובץ שהאפליקציה מאזינה לו (FileObserver) ופותחת ב־Chrome. ה־callback ל־localhost חוזר ל־agy באותו מכשיר. כגיבוי, קישור התחברות של Google שמודפס בטרמינל מזוהה ומוצג בפס עם כפתור "פתיחה".
- **ממשק גרפי:** הכפתור מריץ `AGY_ENABLE_HUB=1 agy --hub --hub-port=4400`, בשיטה שבה משתמש RadnusD/Antigravity-for-Android ב־Termux, ומציג את http://localhost:4400 ב־WebView של האפליקציה. הפלט נשמר בסשן טרמינל כדי שאפשר יהיה לראות שגיאות.
- סביבה: ‏`HOME=files/home`, תיקיית עבודה `~/projects`, ‏`TMPDIR=files/tmp`, ‏`SHELL=/system/bin/sh`, ‏`TERM=xterm-256color`.
- `targetSdk 28`: בדיוק כמו ב־Termux. מ־API 29 אנדרואיד אוסר להריץ קבצים שהורדו לאחסון של האפליקציה (W^X).
- דרישות: טלפון arm64-v8a. עורך הקוד הגרפי (Electron) לא רץ על אנדרואיד, רק ה־CLI של הסוכן.

## מרחוק
- **חיבור רשמי:** מגיעה עם חיבור מוכן ל־Antigravity Remote Control (‏`https://antigravity.google/`). מתחברים עם אותו חשבון Google שמחובר במחשב.
- **חיבורים משלך:** אפשר להוסיף כל כתובת: code-server, ‏noVNC, גשר מובייל בקוד פתוח, או מנהרת Cloudflare/Tailscale. יש תמיכה גם בכתובות `http://` ברשת הביתית.
- **בתוך האפליקציה או ב־Chrome:** כל חיבור נפתח ב־WebView מסך מלא או בלשונית Chrome. Chrome מומלץ אם Google חוסם התחברות בתוך האפליקציה. כשמגיעים לדף ההתחברות של Google מוצג באנר עם כפתור "המשך ב־Chrome".
- **קישורי סשן ו־QR:** קישורי `antigravity.google` שנסרקים במצלמה או נפתחים מהודעה נפתחים ישר באפליקציה. אפשר גם לשתף קישור לאפליקציה או להשתמש ב"הדבקת קישור לסשן".
- העלאת קבצים, הורדות, מיקרופון להכתבה, מצב מחשב (Desktop UA), השארת המסך דולק ושמירת התחברות (cookies).
- ממשק בעברית ובאנגלית לפי שפת המכשיר.

## הפעלה במחשב
1. ב־Antigravity: ‏Settings (‏Ctrl+, או Cmd+,) ← מפעילים **Enable Remote Control** (בלשונית Application או Account, לפי הגרסה).
   ב־CLI: ‏`agy --remote-control` או `/remote-control` בתוך סשן.
2. המחשב צריך להישאר דולק ו־Antigravity פתוח.
3. בטלפון: פותחים את החיבור הרשמי, מתחברים ובוחרים את המחשב.

הבנייה נעשית ב־workflow הכללי של APK Factory (‏`app=antigravity-remote`). ה־APK מתפרסם ב־`downloads/antigravity-remote/app.apk`.
