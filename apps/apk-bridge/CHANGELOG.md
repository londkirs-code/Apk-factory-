# APK Bridge — יומן שינויים

## 2.0.0 — 2026-11-25
**הבקשה:** לתקן את APK Bridge ולשדרג אותו למקסימום.

### תיקונים
- **ספריית ADB:** `app/build.gradle` — שדרוג `adb-kt` מ־v1.0.6 ל־v1.0.13. גרסאות 1.0.7–1.0.9 מתקנות `ClassCastException` במפתח RSA ב־Android 8–11 והתנגשות ספק BouncyCastle שגרמה לכשלי זיווג/חיבור. קובץ המפתח הקיים (`filesDir/adbkey`) נשאר באותו מקום ונקרא גם בגרסה החדשה, כך שזיווג קיים נשמר. coroutines עודכן ל־1.11.0 בהתאם לספרייה.
- **קריסות mDNS:** בגרסה הקודמת `onResolveFailed`/`onStartDiscoveryFailed` עדכנו את המסך מ־thread רקע (קריסה), `stopServiceDiscovery` נקרא על listener שלא נרשם (קריסה), ולחיצה חוזרת על "אתר חיבור" רשמה את אותו listener פעמיים (קריסה). הגילוי נכתב מחדש ב־`Bridge.discover` עם timeout, תור resolve, listener חדש לכל resolve ו־MulticastLock.
- **ביטול התקנה בסיבוב מסך:** העבודה רצה עכשיו ב־scope ברמת התהליך (`Bridge.scope`) ולא ב־scope של ה־Activity.
- **Edge-to-edge ב־Android 15:** עם targetSdk 35 התוכן הוסתר מתחת לשורת הסטטוס; נוסף טיפול ב־WindowInsets (כולל מקלדת).
- **אימות מקור ההתקנה:** `pm list packages -i` החזיר גם חבילות ששמן מתחיל באותו prefix; עכשיו נבחרת רק השורה המדויקת של החבילה.
- **זיווג בלתי אפשרי באותו מכשיר:** חלון "התאמת מכשיר עם קוד" נסגר כשעוברים לאפליקציה אחרת, ולכן הזנת הקוד באפליקציה עצמה כמעט לא עבדה. נוסף זיווג דרך התראה (ראו למטה).

### יכולות חדשות
- `PairingService.kt` — שירות חזית (connectedDevice) שמאתר את פורט הזיווג ב־mDNS ומבקש את הקוד בהתראה עם RemoteInput; `PairingReplyReceiver` מעביר את הקוד לזיווג. ניסיון חוזר אם הקוד שגוי.
- `Bridge.kt` — מנוע ADB משותף: חיבור ל־127.0.0.1, שמירת הפורט האחרון שעבד ואיתור מחדש אוטומטי כשהוא משתנה, הודעות שגיאה בעברית לשגיאות `INSTALL_FAILED_*` נפוצות.
- התקנת כמה קבצים יחד, Split APKs, וחבילות APKS/XAPK/APKM דרך `installMultiple`; העתקת OBB מ־XAPK ל־`/sdcard/Android/obb/<package>`; סינון חלקי ABI לא תואמים.
- אפשרויות התקנה (נשמרות): `-d`, `-g`, `-t`, `--bypass-low-target-sdk-block` (Android 14+), ורישום Google Play כ־installer (ברירת מחדל: מסומן, כמו בגרסה הקודמת).
- כלים: הסרת אפליקציה (`pm uninstall [-k]`), פתיחת האפליקציה שהותקנה, הענקת `WRITE_SECURE_SETTINGS` דרך ADB כדי שהאפליקציה תדליק את ניפוי הבאגים האלחוטי בעצמה.
- מסך חדש: כרטיסים, מצב חיבור (זיווג, Wi‑Fi, ניפוי באגים), פס התקדמות, כפתור ביטול, יומן עם העתקה, תמיכה ב־RTL, אייקון אפליקציה ואייקון התראה.
- קבלת קבצים גם ב־`SEND_MULTIPLE` ובסוגי MIME של APKS/XAPK/APKM; הוסר מסנן `file://` שאינו שמיש מ־Android 7.

### מה לא השתנה
- `applicationId` (`com.londkirs.apkbridge`), מפתח החתימה, מיקום קובץ מפתח ה־ADB, minSdk 30 ו־targetSdk 35.

### בדיקות
- קומפילציית Kotlin של כל קובצי האפליקציה יחד עם קוד המקור של adb-kt v1.0.13 מול Android API 35 (Robolectric android-all) — עברה ללא שגיאות.
- בדיקת תקינות XML של ה־manifest והמשאבים.
- בנייה מלאה וחתימה מתבצעות ב־GitHub Actions. **לא בוצעה בדיקה על מכשיר פיזי.**

## 1.0.0
- יצירת אפליקציה עצמאית להתקנת APK באמצעות Android Wireless Debugging.
- זיווג TLS/SPAKE2, שמירת מפתח ADB, גילוי mDNS ובחירת קובץ APK.
- בדיקת מקור ההתקנה המדווח על ידי Android לאחר התקנה.
