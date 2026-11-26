# חולין • עמוד יומי (Android, Java)

אפליקציה מקורית לקריאה ולשינון של מסכתות הבבלי עם תוספות, בלי דפדפן.

## הרצת בדיקות הליבה (בלי Android)
    javac -d out -encoding UTF-8 app/src/main/java/com/londkirs/chullin/core/*.java app/src/main/java/com/londkirs/chullin/model/*.java app/src/test/java/com/londkirs/chullin/CoreTest.java
    java -cp out com.londkirs.chullin.CoreTest

## הוספת מסכתות (נתוני התוספות)
ב-`tools/build_masechtot.py` (דורש אינטרנט, מומלץ להריץ במחשב):
    python3 tools/build_masechtot.py                 # כל 37 המסכתות מספריא
    python3 tools/build_masechtot.py --only Berakhot
    python3 tools/build_masechtot.py --input-dir DIR  # קבצי TXT שהורדתם ידנית מספריא
הסקריפט כותב `app/src/main/assets/masechtot/<Key>.json` ומעדכן את `catalog.json`. מסכת שהקובץ שלה פגום או חסר נדחית עם הודעה ולא נכתבת.

## מבנה
- `core/`: לוח עברי, בניית המסכת, כללי גמר וחזרות, עמוד יומי. ללא Android.
- `model/`: עמוד, רשומת התקדמות, מידע על מסכת.
- `data/`: קטלוג, שמירה (SharedPreferences), הגדרות.
- `ui/MainActivity.java`: מסך הלימוד.
