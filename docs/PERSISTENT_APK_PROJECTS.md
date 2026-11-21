# מנהל פרויקטים קבועים – APK Factory

כל פרויקט שחולץ ונרשם נשמר תחת `apps/reverse-engineered/<slug>/`.

## שמירת פרויקט חדש

1. להריץ את **Reverse Engineer Android APK** ולוודא שהריצה הסתיימה בהצלחה.
2. לפתוח Actions → **Save Reverse-Engineered APK as Persistent Project**.
3. להזין את מספר הריצה המדויק ואת אותו project slug.
4. לאשר בעלות/הרשאה. המאגר הנוכחי ציבורי, ולכן חובה להבין שגם קוד מפוענח ומשאבים יהפכו לציבוריים.
5. לאשר את הפרסום הציבורי. התהליך נכשל אם תיקיית הפרויקט כבר קיימת; הוא לא מוחק או דורס עבודה קיימת.

## מה נשמר

- `apktool-project/` – מקור העריכה העיקרי: Smali, Manifest, משאבים ונכסים.
- `jadx-gradle-export/` – ייצוא Java/Gradle לקריאה ולהבנת הלוגיקה; אינו המקור המקורי.
- `analysis/` – דוחות טקסטואליים ומידע על החבילה והחתימה.
- `logs/` – יומני פענוח ובנייה.
- `project.json` – מטא-נתונים, מזהה החבילה, מקור הניתוח ומספר ריצת המקור.

קובצי APK מקוריים, OBB, APK שנבנו, ZIP וקובצי ספריות Native בינאריים אינם מועתקים לתיקייה הקבועה. הם נשארים ב־Artifact של הניתוח לזמן השמירה שלו, או במקור פרטי אחר לפי הצורך. אין להכניס מפתחות חתימה או סיסמאות למאגר.

## פקודות ניהול

```bash
python3 tools/reverse-engineering/project-manager.py list
python3 tools/reverse-engineering/project-manager.py show <slug>
python3 tools/reverse-engineering/project-manager.py validate <slug>
```

## בנייה מחדש

השתמש ב־**Build Reconstructed APK** והגדר את:
- Source repository: `londkirs-code/Apk-factory-`
- Ref: `main`
- Project path: `apps/reverse-engineered/<slug>/apktool-project`

הבנייה ממשיכה להשתמש בתהליך הקיים: Apktool, zipalign, העלאת versionCode בעותק זמני, ואז חתימה ב־job נפרד עם מפתח APK Factory הקיים. השוואת טביעת האצבע מול החתימה המקורית נדרשת לפני שמצהירים על תאימות לעדכון.

## פרטיות ומפתחות

המאגר `londkirs-code/Apk-factory-` ציבורי. כל מקור מפוענח שמתחייב אליו נגיש לציבור. לא לשמור בו אפליקציות סודיות או קנייניות. לשימוש פרטי יש ליצור עותק פרטי של APK Factory ולהעביר אליו את התהליך. קובצי keystore, סיסמאות וטוקנים נשמרים רק ב־GitHub Secrets.
