# APK Bridge

אפליקציית Android עצמאית להתקנת APK באמצעות Android Wireless Debugging / ADB, ללא Root, מחשב או Termux לאחר ההתקנה.

- Android 11 ומעלה (API 30+).
- זיווג חד־פעמי באמצעות קוד Wireless Debugging ו־TLS/SPAKE2.
- שמירת מפתח ADB באחסון הפרטי של האפליקציה.
- גילוי שירות החיבור באמצעות mDNS או הזנת פורט ידנית.
- קבלת APK דרך פתיחה/שיתוף ובחירה מתוך מנהל הקבצים.
- התקנה דרך ADB Package Manager ובדיקת installerPackageName לאחר ההתקנה.

## מקור התקנה

האפליקציה מבקשת מ־Package Manager לרשום `com.android.vending` כ־installer באמצעות `-i`, ואז בודקת את התוצאה. זו אינה התקנה שבוצעה בפועל מתוך Google Play: Android עשוי להציג את ה־installer הרשום כך, אך initiating package נשאר ADB והאפליקציה אינה מקבלת רישוי, רכישה או אימות Play.

## בנייה

הפרויקט נבנה ב־APK Factory עם מפתח החתימה הקבוע של המאגר. ה־workflow מאמת שה־APK יחיד, לא ריק, חתום, ושה־manifest תואם לגרסה לפני פרסום.
