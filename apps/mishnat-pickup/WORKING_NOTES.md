# הערות עבודה — רשימת איסוף (משנת יוסף)

מסמך זה נועד לקרוא בתחילת כל סשן עבודה, כדי לא לחזור על הגדרות ובדיקות שכבר נעשו.

## מיקומים
- מאגר: `londkirs-code/Apk-factory-` (ענף ברירת המחדל: `main`).
- קוד המקור הקבוע: `apps/mishnat-pickup/` בלבד. אין לערוך קבצים בתיקיות אחרות לצורך האפליקציה הזו.
- חבילת Android: `org.mishnatyosef.pickup`. יש לשמור על השם.
- Gradle: `app/build.gradle` (גרסה נוכחית: `versionCode 97`, `versionName 1.97`, `minSdk 19`, `targetSdk 35`).
- קוד Java: `app/src/main/java/org/mishnatyosef/pickup/` (13 קבצים, כ־5,900 שורות).
- ממשק המשתמש, עיצוב ואייקונים: `Ui.java` (אייקונים וקטוריים ב-`Ui.IconDrawable`, כפתורי אייקון ב-`Ui.iconButton`). מחרוזות "מה חדש" (`showSettingsScreen`) מתעדכנות יחד עם כל שינוי שנראה למשתמש.
- ניווט: `MainActivity.screen` (enum `Screen`) הוא מקור האמת למסך הנוכחי. כל מסך מוצג דרך `presentScreen`. מקש חזרה מטופל ב-`onBackPressed` לפי המסך.
- ה־APK שפורסם: `downloads/mishnat-pickup/app.apk`, מטא־דאטה ב־`downloads/mishnat-pickup/build-info.txt`.
- workflow לבנייה: `.github/workflows/build-mishnat-pickup.yml`.
- Skill הבנייה הקיים: `apps/mishnat-pickup/skills/build-mishnat-pickup-apk/SKILL.md`.

## כללים לעבודה
- לקבל קוד מלא לקובץ שמשתנה, ולא רק diff או קטע, כפי שהוגדר בהעדפות.
- כל שינוי שנראה למשתמש: רשומה חדשה בראש `CHANGELOG.md` בעברית, עם מספר גרסה מעודכן, ועדכון "מה חדש" באפליקציה.
- לא ליצור ולא להחליף keystore. לא לחשוף סודות חתימה.
- לא לשמור או להדפיס סיסמה, קוד SMS או טוקן גישה. הטוקן נשמר מוצפן ב־Android Keystore בלבד.
- לא לשלוח סיסמה או קוד SMS בצ'אט.
- כל ענף עבודה נפרד. שינויים נכנסים ל־`main` דרך PR.

## בנייה ופרסום
- כל PR שנוגע ב-`apps/mishnat-pickup/` נבנה אוטומטית (`.github/workflows/build-mishnat-pickup-pr.yml`). ה-APK נשמר בענף תחת `downloads/mishnat-pickup-pr/<PR>/app.apk`, ותגובה עם קישור הורדה נוספת ל-PR (מגיעה במייל). אם `versionCode` לא גבוה מהגרסה שפורסמה, הבנייה מעלה אותו אוטומטית כדי שההתקנה תעבוד מעל הגרסה הקיימת.
- אין SDK של Android בסביבת העבודה של Claude, ולכן הקומפילציה נבדקת רק דרך הבנייה ב-PR.
- בנייה ידנית: הודעה בדיוק `/build-mishnat-pickup` ב־issue מספר 1 של המאגר. יש לוודא שנוצרה ריצה חדשה ולחכות לסיומה.
- ה־workflow אמור לוודא: בנייה Release, שם החבילה והגרסה ב־APK, חתימה, והמשכיות חתימה מול ה־APK הקודם, כדי שהתקנה מעל גרסה קודמת תעבוד.
- פרסום מוצלח: הקובץ נכנס ל־`downloads/mishnat-pickup/app.apk` ו־`build-info.txt` תואם לו. הקישור הישיר מתפרסם רק אחרי אישור הפרסום.
- כל בנייה שמורה ב־`downloads/mishnat-pickup/history`.
- בנייה שהצליחה לא מוכיחה שההתחברות לחשבון אמיתי או זרימת האיסוף עובדות. צריך לציין את זה בכל דיווח.

## מה כבר קיים (לא לבנות מחדש)
- התחברות לאתר משנת יוסף עם מייל וסיסמה (`POST /api/login`). נשמר רק טוקן. כניסה ב-SMS מוזכרת בתיעוד ישן אבל אינה ממומשת בקוד.
- טעינת ההזמנה האחרונה שאינה מבוטלת, ואחריה פרטיה.
- סימון איסוף מלא וחלקי לפי הזמנה ומוצר. הסימון לא משנה את האתר.
- ארכיון רשימות שמורות לפי מקור, חשבון והזמנה. מיזוג, ייצוא TXT, יצירת רשימה חדשה.
- ייבוא JSON, TXT או ZIP. TXT תומך בעמודות כמות, יחידה, הערה וקטגוריה.
- היסטוריית הזמנות עם דפדוף דרך `GET /api/orders?page=N`.
- שש רמות תצוגה, מצב רשת, ניווט מלא במקשים, וקיצורי מקשים.
- צליל ורטט לפי הגדרות נפרדות. מנגינת הניצחון נטענת מקומית מ־`victory_fanfare.mp3`.

## מבנה הזמנה מהשרת (נבדק מול תשובה אמיתית של GET /api/orders)
- שם המכירה נמצא ב-`sale.parent.week` (למשל "וישלח תשפ״ז"). `OrderHistoryPage.saleName` בודק אותו ראשון.
- פרטי האיסוף: `sale` (שעות `startHour`/`endHour`, `supply_begin` = תאריך האיסוף, `msg`, `special_msg_txt`, `prep_note`), `sale.site` (שם נקודה, `street`, `city`, `lat`/`lng`, `admin_name`, `admin_phone`, `note`, `distribution_day`), `sale.parent` (ימי השבוע של המכירה).
- מחלקת מוצר (נבדק מול GET /api/orders/{id}): `order.items[].item_sale.product.category` = `{name, order, main_category.name}`. המיון לפי `category.order`.
- `PickupInfo.extract` שומר רשימה לבנה בלבד של שדות. לעולם לא לשמור `card`, `tokef`, `tranzila_token` או שדות תשלום.

## פתוח / לבדוק
- עד גרסה 1.94 חסר `android:supportsRtl="true"` במניפסט, ולכן כל `setLayoutDirection(RTL)` לא פעל והמסכים הוצגו הפוך (שמאל-לימין). תוקן ב-1.95. לבדוק כל מסך אחרי ההיפוך.
- בדיקה על מכשיר אמיתי לא בוצעה. כל שינוי ממשקי צריך אישור על מכשיר.
- בדיקת חשבון אמיתי לא בוצעה בסביבת העבודה.
- אין בדיקות אוטומטיות (unit tests). יש רק lint עם `abortOnError true`.
- `BUILD_TRIGGER` מציין גרסה 1.81. הוא מפעיל בנייה ל-`main` רק כשהוא משתנה.
- כותרת ההזמנה, אחרי פענוח `sale.week` ו־`parent.week`: לאמת מול חשבון אמיתי.
- ממשק בחירת יעד למיזוג רשימות: מופיע ב־1.82 כיצירת רשימה ומיזוג עם אישור. לוודא שהזרימה מלאה ונבדקה.
- מסך ההיסטוריה: לאמת את מעטפת התגובה של `GET /api/orders` בהפעלה אמיתית, כי התיעוד עדיין מציין זאת.

## תיעוד נוסף בתיקייה
- `README.md` — תיאור מפורט של ההתנהגות.
- `CHANGELOG.md` — היסטוריית גרסאות בעברית, החדש ראשון.
- `LIST_WORKSPACE_ARCHITECTURE.md` — תכנון מערכת הרשימות והיסטוריית ההזמנות.
- `AUDIO_LICENSE.md` — רישיון צליל הניצחון.
