# APK Factory — תשתית לאוסף אפליקציות Android

המאגר הזה נועד להחזיק הרבה אפליקציות, לבנות אותן בענן ולהעמיד APK להורדה בלי Android Studio ובלי להתקין Android SDK במחשב.

## מבנה המאגר
- apps/<app-id>/ — פרויקט Android נפרד לכל אפליקציה.
- .github/workflows/build-apk.yml — בניית אפליקציה לפי בחירה או כל האפליקציות.
- downloads/<app-id>/ — קובץ APK אחרון לכל אפליקציה.
- scripts/new-app.sh — יצירת שלד לאפליקציה חדשה מתוך תבנית.
- templates/android-basic/ — אפליקציית Java מינימלית לשכפול.

## יצירת אפליקציה חדשה
שכפלו את templates/android-basic לתוך apps/my-app, התאימו את package והקוד, ואז בצעו Commit. אפשר להריץ מקומית scripts/new-app.sh my-app com.example.myapp אחרי שכפול המאגר. הבנייה מתבצעת בענן דרך GitHub Actions.

## בניית APK
פתחו Actions → Build Android APKs → Run workflow. הזינו מזהה אפליקציה (למשל example-app) או all. שינויים בתיקיית apps/ מפעילים גם בנייה אוטומטית.

## הורדת APK
לאחר הצלחת הבנייה, קובץ האפליקציה נשמר ב-downloads/<app-id>/app-debug.apk וגם כ-Artifact בריצת Actions. דף ההורדות: https://github.com/londkirs-code/Apk-factory-/tree/main/downloads

## הרשאות GitHub
ב-Settings → Actions → General → Workflow permissions יש לבחור Read and write permissions כדי שה-workflow יוכל לבצע Commit של קובצי APK. אם הכתיבה חסומה, ה-Artifact עדיין ייווצר.

## הערות
כל אפליקציה היא פרויקט עצמאי תחת apps/. תהליך הבנייה משתמש ב-Gradle וב-JDK 17. קובצי debug מיועדים לבדיקה והתקנה ידנית. אין לשמור מפתחות חתימה או סודות בקוד.

## עדכונים לאפליקציה קיימת — חובה להגדיר פעם אחת

תהליך הבנייה לא יפיק APK אם מפתח החתימה הקבוע לא הוגדר. זו הגנה מכוונת: APK שנחתם במפתח אחר עלול לא להתקין כעדכון מעל האפליקציה הקיימת.

### הגדרה חד־פעמית של מפתח חתימה

1. במחשב עם Java מותקן, צרו מפתח פרטי. שמרו אותו במקום בטוח ואל תעלו אותו למאגר Git:
   ```bash
   keytool -genkeypair -v -keystore apk-factory-release.jks -alias apkfactory -keyalg RSA -keysize 2048 -validity 10000
   ```
   זכרו את סיסמת ה־keystore ואת סיסמת המפתח. אפשר לבחור אותה סיסמה לשתיהן.
2. קודדו את הקובץ ל־Base64:
   - Linux / Termux: `base64 -w 0 apk-factory-release.jks`
   - Windows PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("apk-factory-release.jks"))`
3. פתחו [Settings → Secrets and variables → Actions](https://github.com/londkirs-code/Apk-factory-/settings/secrets/actions) וצרו את ארבעת ה־Repository secrets הבאים:
   - `APK_SIGNING_KEY_BASE64` — כל מחרוזת ה־Base64 שהתקבלה.
   - `APK_SIGNING_STORE_PASSWORD` — סיסמת ה־keystore.
   - `APK_SIGNING_KEY_ALIAS` — `apkfactory` (או הכינוי שבחרתם).
   - `APK_SIGNING_KEY_PASSWORD` — סיסמת המפתח.
4. שמרו עותק גיבוי של קובץ המפתח והסיסמאות במקום מאובטח. **בלי המפתח הזה אי אפשר לחתום עדכונים תואמים בעתיד.** אין להעלות אותו ל־GitHub או לשלוח אותו בצ'אט.

### מה התשתית עושה אוטומטית

- כל בנייה מגדילה את `versionCode` בכל אפליקציה שנבחרה.
- כל APK נבנה כ־Release ונחתם במפתח הקבוע.
- ה־workflow נעצר עם הודעת שגיאה ברורה אם אחד מסודות החתימה חסר, במקום לפרסם APK שעלול לא לעדכן התקנה קיימת.
- מזהה החבילה (`applicationId`) חייב להישאר זהה בין הגרסאות של אותה אפליקציה.
- ה־APK האחרון נשמר תחת `downloads/<app-id>/app.apk`.
- בכל בנייה מוצלחת נשמר גם עותק ממוספר שאינו נדרס תחת `downloads/<app-id>/history/`, בשם הכולל את `versionName` ואת `versionCode`. כך אפשר להוריד גרסה קודמת ישירות מהמאגר.

### כללי עבודה למפתחים

- לא משנים `applicationId` של אפליקציה קיימת.
- לא מייצרים מפתח חתימה חדש עבור עדכון.
- לא מאפסים את `versionCode`; תהליך הבנייה מגדיל אותו אוטומטית.
- לפני הפצה, בודקים שה־GitHub Actions הסתיים בהצלחה.
- מעבר מאפליקציה קיימת שנחתמה במפתח אחר למפתח הקבוע עלול לדרוש התקנה מחדש חד־פעמית. לאחר מכן, העדכונים יכולים להתקין מעל הגרסה הקיימת.

