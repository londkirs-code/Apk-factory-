# APK Factory

תשתית ליצירת אפליקציות Android באמצעות GitHub Actions, בלי Android Studio או Android SDK במחשב המקומי.

## מה כלול?
- פרויקט Android בסיסי בשורש המאגר.
- .github/workflows/build-apk.yml: בנייה אוטומטית בענן.
- downloads: תיקייה לשמירת ה־APK האחרון במאגר.

## בניית APK
1. פותחים את לשונית Actions.
2. בוחרים Build Android APK.
3. לוחצים Run workflow.
4. לאחר הצלחה, מורידים את [downloads/app-debug.apk](downloads/app-debug.apk).
5. אפשר גם להוריד את ה־Artifact מתוך ריצת Actions.

## התאמה אישית
הקוד נמצא בתיקיית app/src/main/java/com/example/apkfactory/MainActivity.java. ערכו אותו כדי לשנות את מסך האפליקציה. הרשאות והגדרות נמצאות ב־app/src/main/AndroidManifest.xml וב־app/build.gradle.

## הרשאות
ב־Settings → Actions → General → Workflow permissions יש לבחור Read and write permissions כדי לאפשר ל־workflow לשמור APK במאגר. אם אין הרשאת כתיבה, עדיין אפשר להוריד את ה־Artifact של הריצה.

## הערות
זוהי תשתית התחלתית עם אפליקציית דוגמה, לא מחולל AI אוטומטי. קובץ debug מיועד לבדיקה והתקנה ידנית. להפצה קבועה יש להגדיר חתימת release ולשמור את המפתח הפרטי ב־GitHub Secrets, לא במאגר.
