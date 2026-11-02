# מונופול לאנדרואיד

פרויקט Kotlin + Jetpack Compose עצמאי, מיועד לבנייה מתוך APK Factory.

- Android מינימלי: 8.0 (API 26)
- Java: 17
- Gradle: 8.9
- מצב אופליין; אין הרשאות רשת.
- בנייה מקומית: `cd apps/monopoly && gradle assembleDebug`
- APK חתום ב-APK Factory: הפעלת workflow `Build Android APKs` עם `app=monopoly`.

נתוני הלוח והקלפים נמצאים ב-`app/src/main/assets/monopoly_data.json`; מנוע המשחק נמצא ב-`engine/` ואינו תלוי באנדרואיד.
