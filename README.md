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
