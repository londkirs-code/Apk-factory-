# הוראות עבודה לפרויקט — APK מוכן להורדה

המסמך הזה הוא הוראת עבודה מחייבת לעוזר AI שעובד בפרויקט. מטרת המשתמש היא שכאשר הוא מבקש APK, העוזר יתחיל מיד בנייה אמיתית, יפרסם קובץ APK עצמאי בתוך GitHub, ויחזיר קישור ישיר ויציב שעובד גם לאחר שתוקף Artifacts זמניים פג.

## כלל פעולה
- ביטויים כמו "תעשה את הסקיל", "תבנה APK", "תכין APK", "תעדכן APK" או "יש APK?" בהקשר של בקשת בנייה מחייבים לבצע את השלבים בפועל, לא רק להסביר אותם.
- אין להסתפק בפתיחת Workflow, ביצירת Artifact זמני, בקובץ sandbox, או בהודעת הצלחה כללית.
- אין לדווח הצלחה לפני שה־Workflow הסתיים, הקובץ נכתב למאגר, וה־metadata ב־main מאמת שהוא שייך לריצה הנוכחית.
- אם משהו נכשל, לבדוק את ה־step הראשון שנכשל ואת הלוג שלו, לתקן את הסיבה ולנסות שוב. לא לבצע ניסיונות עיוורים ולא למסור APK ישן כאילו הוא חדש.

## נץ החמה
- מקור: https://github.com/londkirs-code/- — branch `main`.
- Skill מחייב: `skills/build-netzhachama-apk/SKILL.md`.
- Workflow: `.github/workflows/build-netzhachama-signed.yml`.
- קובץ APK יציב בתוך Git: `downloads/NetzHachama.apk`.
- קישור ישיר: https://raw.githubusercontent.com/londkirs-code/Apk-factory-/main/downloads/NetzHachama.apk
- Metadata: `downloads/NetzHachama-build-info.txt`.
- טריגר חלופי לבנייה: תגובה מדויקת `/build-netzhachama` ל־issue #1: https://github.com/londkirs-code/Apk-factory-/issues/1
- המקור הוא המאגר האישי של המשתמש; לא נדרשת סנכרון upstream נפרד.

## NotifAI
- Skill מחייב: `skills/build-notifai-apk/SKILL.md`.
- לפני בנייה, לבצע את סנכרון upstream שהסקיל מגדיר ולוודא שהסנכרון הצליח לפני תחילת הבנייה.
- APK יציב: `downloads/NotifAI.apk`; Metadata: `downloads/NotifAI-build-info.txt`.
- קישור ישיר: https://raw.githubusercontent.com/londkirs-code/Apk-factory-/main/downloads/NotifAI.apk
- טריגר חלופי: `/build-notifai` ב־issue #1.

## חתימה ותאימות עדכון
- תמיד להשתמש במפתח החתימה הקיים של APK Factory ובסודות Actions שהוגדרו עבורו. אין לייצר מפתח חדש, להחליף מפתח, או לחשוף ערכי secrets.
- לשמור על package/application ID ולוודא שה־versionCode עולה.
- בנייה, חתימה, העלאת Artifact, פרסום קובץ קבוע ואימות metadata הם שלבים נפרדים. כולם חייבים להצליח.
- לעולם לא להשתמש ב־force-push. בפרסום יש לבצע fetch/rebase/push בטוח עם מספר ניסיונות מוגבל; אם יש קונפליקט אמיתי, לעצור ולבדוק.
- לא לטעון שהתקנה במכשיר נבדקה אם לא בוצעה בדיקה פיזית.

## אימות חובה לפני מסירת קישור
1. נמצאה ריצה חדשה שנוצרה בעקבות הבקשה הנוכחית.
2. הריצה הסתיימה ב־`success`, ושלבי Release build, חתימה, Artifact ופרסום קובץ קבוע הצליחו.
3. הקובץ `downloads/<App>.apk` קיים ב־main של GitHub.
4. קובץ ה־metadata נקרא מחדש מ־main, ו־`workflow_run_id`, `source_sha`, `version_code` ו־`sha256` תואמים לריצה הנוכחית ולתוצר שפורסם.
5. רק אז למסור את הקישור הישיר ואת קישור הריצה. אם אחד מהתנאים לא מתקיים, לומר במפורש מה לא אומת ולא להציג קישור ישן כאילו עודכן.
