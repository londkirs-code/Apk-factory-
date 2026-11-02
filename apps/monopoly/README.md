# מונופול לאנדרואיד

משחק מונופול מקומי בעברית, Kotlin + Jetpack Compose, ללא חיבור רשת.

## דרישות ובנייה
- Android מינימלי: 8.0 (API 26)
- Java: 17
- Gradle: 8.9
- אין הרשאות אינטרנט. הרשאת רטט נדרשת רק אם המשתמש מפעיל רטט בהגדרות.
- בדיקות ובניית Debug: `cd apps/monopoly && gradle testDebugUnitTest assembleDebug`
- APK חתום דרך APK Factory: הפעלת workflow `Build Android APKs` עם `app=monopoly`.
- חתימה: מפתח החתימה המשותף של APK Factory, כך שעדכונים עתידיים יוכלו להתקין מעל הגרסה הקודמת.

## קובץ APK קבוע
לאחר מיזוג לענף `main` והשלמת workflow הבנייה, הקובץ מתפרסם בכתובת הקבועה:
`https://raw.githubusercontent.com/londkirs-code/Apk-factory-/main/downloads/monopoly/app.apk`

## ארכיטקטורה
- `app/src/main/assets/monopoly_data.json` — כל נתוני 40 המשבצות וחפיסות הקלפים.
- `engine/` — מודלי מצב, חוקים, תנועה, שכירות, בנייה, משכון, כלא, חובות ומסחר; Kotlin טהור.
- `ai/` — שלוש רמות AI, הצעות מכרז והערכת עסקאות.
- `storage/` — שמירה וטעינה מקומית של מצב המשחק כ-JSON.
- `ui/` — מסכי Compose בעברית וב-RTL, לוח, הגדרות, ניהול נכסים, מכרזים, מסחר ויומן.

המשחק נשמר אוטומטית לאחר פעולות, וניתן להמשיך מאותה נקודה לאחר סגירת האפליקציה.
