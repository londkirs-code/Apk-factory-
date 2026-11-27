# אנטיגרוויטי באנדרואיד (Antigravity Remote)

Antigravity של Google היא אפליקציית מחשב ואי אפשר להריץ אותה ישירות על אנדרואיד. האפליקציה הזו מתחברת מהטלפון ל־Antigravity שרץ על המחשב שלך.

## מה יש באפליקציה
- **חיבור רשמי:** מגיעה עם חיבור מוכן ל־Antigravity Remote Control (‏`https://antigravity.google/`). מתחברים עם אותו חשבון Google שמחובר במחשב.
- **חיבורים משלך:** אפשר להוסיף כל כתובת: code-server, ‏noVNC, גשר מובייל בקוד פתוח, או מנהרת Cloudflare/Tailscale. יש תמיכה גם בכתובות `http://` ברשת הביתית.
- **בתוך האפליקציה או ב־Chrome:** כל חיבור נפתח ב־WebView מסך מלא או בלשונית Chrome. Chrome מומלץ אם Google חוסם התחברות בתוך האפליקציה. כשמגיעים לדף ההתחברות של Google מוצג באנר עם כפתור "המשך ב־Chrome".
- **קישורי סשן ו־QR:** קישורי `antigravity.google` שנסרקים במצלמה או נפתחים מהודעה נפתחים ישר באפליקציה. אפשר גם לשתף קישור לאפליקציה או להשתמש ב"הדבקת קישור לסשן".
- העלאת קבצים, הורדות, מיקרופון להכתבה, מצב מחשב (Desktop UA), השארת המסך דולק ושמירת התחברות (cookies).
- ממשק בעברית ובאנגלית לפי שפת המכשיר.

## הפעלה במחשב
1. ב־Antigravity: ‏Settings (‏Ctrl+, או Cmd+,) ← מפעילים **Enable Remote Control** (בלשונית Application או Account, לפי הגרסה).
   ב־CLI: ‏`agy --remote-control` או `/remote-control` בתוך סשן.
2. המחשב צריך להישאר דולק ו־Antigravity פתוח.
3. בטלפון: פותחים את החיבור הרשמי, מתחברים ובוחרים את המחשב.

הבנייה נעשית ב־workflow הכללי של APK Factory (‏`app=antigravity-remote`). ה־APK מתפרסם ב־`downloads/antigravity-remote/app.apk`.
