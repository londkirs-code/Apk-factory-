# משנת אוסף - כללי R8.
# כל המחלקות של האפליקציה נקראות ישירות מהקוד או מה-Manifest, ולכן אין צורך בכללי שמירה מיוחדים.
# ZXing: משתמשים רק ב-QRCodeReader; R8 מסיר את שאר הפורמטים.
-dontwarn com.google.zxing.**
# שמירת מספרי שורות לדיווחי קריסה קריאים.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
