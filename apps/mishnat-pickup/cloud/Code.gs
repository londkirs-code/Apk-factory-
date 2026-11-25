/**
 * @OnlyCurrentDoc
 * ההערה למעלה מגבילה את הסקריפט לגיליון שהוא מחובר אליו בלבד. בלי ההערה גוגל מבקשת גישה לכל הגיליונות.
 */

/**
 * משנת אוסף - שרת שיתוף רשימות (Google Apps Script + Google Sheets).
 *
 * כל רשימה שמועלית מקבלת מספר אקראי פנוי בין 1 ל-1000 ונשמרת 7 ימים.
 * כל אחד יכול להביא רשימה לפי המספר. העלאה תמיד יוצרת מספר חדש, כך שאי אפשר לדרוס רשימה של מישהו אחר.
 * נשמרים רק המוצרים (שם, כמות, הערה, מחלקה, קישור לתמונה). שמות חשבונות ומשפחות לא נשלחים לשרת.
 *
 * התקנה: ראו README.md בתיקייה הזו.
 */

var SLOT_COUNT = 1000;                       // מספרים 1..1000
var KEEP_MS = 7 * 24 * 60 * 60 * 1000;       // תוקף: 7 ימים
var MAX_ITEMS = 1000;                        // מוצרים לרשימה
var MAX_PAYLOAD = 180000;                    // תווים לרשימה (בערך 180KB)
var CHUNK = 45000;                           // תווים לתא (מגבלת תא בגוגל שיט: 50,000)
var CHUNK_COLUMNS = 4;                       // עמודות G..J לתוכן הרשימה
var UPLOADS_PER_WINDOW = 15;                 // העלאות למכשיר...
var WINDOW_SECONDS = 6 * 60 * 60;            // ...בכל 6 שעות
var MAX_ACTIVE_PER_DEVICE = 3;               // רשימות פעילות (בתוקף) לכל משתמש
var SHEET_NAME = 'lists';

// עמודות: A מספר | B נוצר (ms) | C פג תוקף (ms) | D שם | E מוצרים | F מכשיר (גיבוב) | G..J תוכן
var COL_CREATED = 2, COL_EXPIRES = 3, COL_TITLE = 4, COL_COUNT = 5, COL_DEVICE = 6, COL_DATA = 7;

function doGet(e) {
  var number = e && e.parameter ? parseInt(e.parameter.n, 10) : NaN;
  if (!isNaN(number)) return json_(getList_(number));
  return json_({ ok: true, service: 'mishnat-pickup-cloud', version: 1, slots: SLOT_COUNT });
}

function doPost(e) {
  try {
    var body = JSON.parse((e && e.postData && e.postData.contents) || '{}');
    if (body.action === 'upload') return json_(upload_(body));
    if (body.action === 'get') return json_(getList_(parseInt(body.number, 10)));
    return json_({ ok: false, error: 'פעולה לא מוכרת.' });
  } catch (err) {
    return json_({ ok: false, error: 'בקשה לא תקינה.' });
  }
}

function upload_(body) {
  var list = body.list;
  if (!list || list.format !== 'mishnat-pickup-list' || !list.lists || list.lists.length !== 1)
    return { ok: false, error: 'מבנה הרשימה אינו תקין.' };
  var items = list.lists[0].items;
  if (!items || !items.length) return { ok: false, error: 'הרשימה ריקה.' };
  if (items.length > MAX_ITEMS) return { ok: false, error: 'אפשר לשתף עד ' + MAX_ITEMS + ' מוצרים.' };

  // ניקוי: רק השדות של המוצרים נשמרים. שם החשבון מוחלף בשם כללי.
  var order = list.lists[0].order || {};
  var title = String(order.name || 'רשימה משותפת').substring(0, 80);
  var clean = {
    format: 'mishnat-pickup-list', version: 1,
    lists: [{
      account: { id: 'cloud', name: 'רשימה מהענן' },
      order: { id: 'cloud', name: title },
      items: items.map(function (it, i) {
        var q = Number(it.quantity);
        var url = String(it.imageUrl || '');
        return {
          id: String(it.id || ('item' + i)).substring(0, 64),
          name: String(it.name || '').substring(0, 200),
          quantity: q > 0 && isFinite(q) ? q : 1,
          note: String(it.note || '').substring(0, 300),
          category: String(it.category || '').substring(0, 100),
          imageUrl: url.indexOf('https://') === 0 ? url.substring(0, 2048) : ''
        };
      }).filter(function (it) { return it.name; })
    }]
  };
  var payload = JSON.stringify(clean);
  if (payload.length > MAX_PAYLOAD) return { ok: false, error: 'הרשימה גדולה מדי לשיתוף.' };

  var device = hash_(String(body.device || 'unknown'));
  if (!allowUpload_(device)) return { ok: false, error: 'יותר מדי העלאות מהמכשיר הזה. נסו שוב בעוד כמה שעות.' };

  var lock = LockService.getScriptLock();
  if (!lock.tryLock(15000)) return { ok: false, error: 'השרת עמוס. נסו שוב בעוד רגע.' };
  try {
    var sheet = sheet_();
    var now = Date.now();
    var expires = sheet.getRange(2, COL_EXPIRES, SLOT_COUNT, 1).getValues();
    var free = [];
    for (var i = 0; i < SLOT_COUNT; i++) {
      var ex = Number(expires[i][0]) || 0;
      if (ex <= now) free.push(i + 1);
    }
    if (!free.length) return { ok: false, error: 'כל 1000 המספרים תפוסים כרגע. נסו שוב מאוחר יותר.' };
    var devices = sheet.getRange(2, COL_DEVICE, SLOT_COUNT, 1).getValues(), active = 0;
    for (var d = 0; d < SLOT_COUNT; d++)
      if (String(devices[d][0]) === device && (Number(expires[d][0]) || 0) > now) active++;
    if (active >= MAX_ACTIVE_PER_DEVICE)
      return { ok: false, error: 'אפשר להחזיק עד ' + MAX_ACTIVE_PER_DEVICE + ' רשימות בענן בו-זמנית. מספר מתפנה אחרי 7 ימים.' };
    var number = free[Math.floor(Math.random() * free.length)];

    var row = [number, now, now + KEEP_MS, title, clean.lists[0].items.length, device];
    for (var c = 0; c < CHUNK_COLUMNS; c++) row.push(payload.substring(c * CHUNK, (c + 1) * CHUNK));
    sheet.getRange(number + 1, 1, 1, row.length).setValues([row]);
    SpreadsheetApp.flush();
    return { ok: true, number: number, expiresAt: now + KEEP_MS };
  } finally {
    lock.releaseLock();
  }
}

function getList_(number) {
  if (isNaN(number) || number < 1 || number > SLOT_COUNT)
    return { ok: false, error: 'המספר צריך להיות בין 1 ל-' + SLOT_COUNT + '.' };
  var values = sheet_().getRange(number + 1, 1, 1, COL_DATA - 1 + CHUNK_COLUMNS).getValues()[0];
  var expires = Number(values[COL_EXPIRES - 1]) || 0;
  if (expires <= Date.now()) return { ok: false, error: 'לא נמצאה רשימה בתוקף במספר ' + number + '.' };
  var payload = '';
  for (var c = 0; c < CHUNK_COLUMNS; c++) payload += String(values[COL_DATA - 1 + c] || '');
  return {
    ok: true, number: number,
    title: String(values[COL_TITLE - 1] || ''),
    itemCount: Number(values[COL_COUNT - 1]) || 0,
    createdAt: Number(values[COL_CREATED - 1]) || 0,
    expiresAt: expires,
    list: JSON.parse(payload)
  };
}

/** מגבלת העלאות לכל מכשיר, כדי שאף אחד לא יציף את השרת. */
function allowUpload_(device) {
  var cache = CacheService.getScriptCache();
  var key = 'up_' + device;
  var count = Number(cache.get(key) || 0);
  if (count >= UPLOADS_PER_WINDOW) return false;
  cache.put(key, String(count + 1), WINDOW_SECONDS);
  return true;
}

/** הגיליון נוצר אוטומטית בפעם הראשונה, עם שורה לכל מספר. */
function sheet_() {
  var book = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = book.getSheetByName(SHEET_NAME);
  if (sheet && sheet.getLastRow() >= SLOT_COUNT + 1) return sheet;
  if (!sheet) sheet = book.insertSheet(SHEET_NAME);
  var header = ['מספר', 'נוצר', 'פג תוקף', 'שם', 'מוצרים', 'מכשיר'];
  for (var c = 0; c < CHUNK_COLUMNS; c++) header.push('תוכן ' + (c + 1));
  sheet.getRange(1, 1, 1, header.length).setValues([header]).setFontWeight('bold');
  var rows = [];
  for (var i = 1; i <= SLOT_COUNT; i++) rows.push([i]);
  sheet.getRange(2, 1, SLOT_COUNT, 1).setValues(rows);
  sheet.setFrozenRows(1);
  return sheet;
}

/** ניקוי יומי של רשימות שפג תוקפן (אופציונלי: ראו README). המספרים מתפנים גם בלי זה. */
function cleanupExpired() {
  var lock = LockService.getScriptLock();
  if (!lock.tryLock(30000)) return;
  try {
    var sheet = sheet_();
    var width = COL_DATA - 1 + CHUNK_COLUMNS;
    var values = sheet.getRange(2, 1, SLOT_COUNT, width).getValues();
    var now = Date.now(), changed = false;
    for (var i = 0; i < SLOT_COUNT; i++) {
      var ex = Number(values[i][COL_EXPIRES - 1]) || 0;
      if (ex && ex <= now) {
        values[i] = [i + 1];
        while (values[i].length < width) values[i].push('');
        changed = true;
      }
    }
    if (changed) sheet.getRange(2, 1, SLOT_COUNT, width).setValues(values);
  } finally {
    lock.releaseLock();
  }
}

/** הפעלה ידנית פעם אחת מהעורך: יוצר את הגיליון ומתזמן ניקוי יומי. */
function setup() {
  sheet_();
  ScriptApp.getProjectTriggers().forEach(function (t) {
    if (t.getHandlerFunction() === 'cleanupExpired') ScriptApp.deleteTrigger(t);
  });
  ScriptApp.newTrigger('cleanupExpired').timeBased().everyDays(1).atHour(3).create();
}

function hash_(text) {
  var bytes = Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, text);
  return Utilities.base64EncodeWebSafe(bytes).substring(0, 16);
}

function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}
