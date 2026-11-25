/*
 * משנת אוסף – הורדה לאפליקציה: ליבה משותפת (בדפדפן ובבדיקות Node).
 *
 * 1. פענוח הזמנה מהשרת של משנת יוסף - העתק מדויק של OrderParser ו-OrderHistoryPage באפליקציה.
 * 2. ייצוא בפורמטים שהאפליקציה מייבאת (ImportParser) - העתק מדויק של ListExporter באפליקציה:
 *    JSON (mishnat-pickup-list), TXT ("## שם" לכל רשימה, "שם | כמות | | הערה | קטגוריה") ו-ZIP (list.json + images/).
 * 3. כותב ZIP קטן (בלי דחיסה), בלי ספריות חיצוניות.
 *
 * כל שינוי בפורמט חייב להתעדכן גם באפליקציה. בדיקת ExtensionCompatibilityTest באפליקציה
 * מייבאת את הקבצים שבתיקייה test/fixtures, כך ששבירת התאימות מכשילה את הבנייה.
 */
(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.MishnatCore = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';

  const FORMAT = 'mishnat-pickup-list';
  const VERSION = 1;
  const MAX_LISTS = 20;
  const IMAGE_BASE = 'https://images.mishnatyosef.org/images/items/';
  const CATEGORY_KEYS = ['category', 'category_name', 'categoryName', 'cat_name', 'catName',
    'category_title', 'department', 'department_name', 'dept', 'group_name'];

  // ---------- עזרים ----------

  function isObject(v) { return v !== null && typeof v === 'object' && !Array.isArray(v); }

  /** הערך הראשון שאינו ריק (מחרוזת או מספר). אובייקטים ומערכים מדולגים. */
  function first(obj, ...keys) {
    if (!isObject(obj)) return '';
    for (const key of keys) {
      const value = obj[key];
      if (value === null || value === undefined || typeof value === 'object') continue;
      const text = String(value).trim();
      if (text && text.toLowerCase() !== 'null') return text;
    }
    return '';
  }

  function limit(value, max) {
    const v = value === null || value === undefined ? '' : String(value).trim();
    return v.length > max ? v.substring(0, max) : v;
  }

  /** "2,5" ו-"2.5" הם 2.5; כמות לא תקינה היא 0 (כמו Quantities.parse). */
  function parseQuantity(value) {
    const n = Number(String(value === null || value === undefined ? '' : value).trim().replace(',', '.'));
    return Number.isFinite(n) && n > 0 ? n : 0;
  }

  /** 3 ← "3", ‏1.5 ← "1.5" (כמו Quantities.format). */
  function formatQuantity(value) {
    if (Math.abs(value - Math.round(value)) < 0.000001) return String(Math.round(value));
    let s = value.toFixed(3);
    while (s.includes('.') && s.endsWith('0')) s = s.substring(0, s.length - 1);
    if (s.endsWith('.')) s = s.substring(0, s.length - 1);
    return s;
  }

  // ---------- פענוח הזמנה ----------

  function unwrapOrder(response) {
    if (!isObject(response)) return {};
    if (isObject(response.order)) return response.order;
    if (isObject(response.data)) return isObject(response.data.order) ? response.data.order : response.data;
    return response;
  }

  function extractOrderId(order) {
    if (!isObject(order)) return -1;
    for (const key of ['id', 'order_id', 'orderID']) {
      const n = Number(order[key]);
      if (Number.isFinite(n) && n > 0) return n;
    }
    if (isObject(order.order)) { const id = extractOrderId(order.order); if (id > 0) return id; }
    if (isObject(order.data)) { const id = extractOrderId(order.data); if (id > 0) return id; }
    return -1;
  }

  function findCategoryIn(source, depth) {
    if (!isObject(source) || depth > 2) return '';
    for (const key of CATEGORY_KEYS) {
      const value = source[key];
      if (isObject(value)) {
        const nested = first(value, 'name', 'title', 'category_name', 'week');
        if (nested) return nested;
      } else if (typeof value === 'string') {
        const text = value.trim();
        // מזהה מספרי אינו שם קטגוריה.
        if (text && text.toLowerCase() !== 'null' && !/^\d+$/.test(text)) return text;
      }
    }
    for (const key of Object.keys(source)) {
      if (isObject(source[key])) {
        const nested = findCategoryIn(source[key], depth + 1);
        if (nested) return nested;
      }
    }
    return '';
  }

  function findCategory(...sources) {
    for (const source of sources) {
      const found = findCategoryIn(source, 0);
      if (found) return found;
    }
    return '';
  }

  function findMatchingItem(items, product) {
    if (!Array.isArray(items)) return null;
    let id = first(product, 'item_salesID', 'item_sales_id', 'itemID', 'id');
    if (!id) id = first(product && product.data, 'item_salesID', 'item_sales_id', 'itemID', 'id');
    for (const item of items) if (isObject(item) && id === first(item, 'item_salesID', 'item_sales_id', 'itemID', 'id')) return item;
    return null;
  }

  function nestedProduct(item) {
    return isObject(item) && isObject(item.item_sale) && isObject(item.item_sale.product) ? item.item_sale.product : null;
  }

  /** המוצרים בהזמנה: {key, name, imageUrl, amount, note, category, categoryOrder}. */
  function parseProducts(order) {
    const result = [];
    const orderItems = Array.isArray(order.items) ? order.items : null;
    const rows = Array.isArray(order.products) ? order.products : orderItems;
    if (!rows) return result;
    rows.forEach((row, i) => {
      if (!isObject(row)) return;
      const data = isObject(row.data) ? row.data : null;
      const item = findMatchingItem(orderItems, row);
      const saleProduct = nestedProduct(item);
      let catalogId = first(saleProduct, 'id', 'product_id', 'productID');
      if (!catalogId) catalogId = first(item && item.item_sale, 'product_id', 'productID');
      let lineId = first(row, 'item_salesID', 'item_sales_id', 'itemID', 'id');
      if (!lineId && data) lineId = first(data, 'item_salesID', 'item_sales_id', 'itemID', 'id');
      const key = catalogId || (lineId || 'line') + '_' + i;
      let name = first(data, 'full_name', 'name', 'product_name');
      if (!name) name = first(row, 'full_name', 'name', 'product_name');
      if (!name) name = first(saleProduct, 'full_name', 'name', 'title');
      if (!name) name = 'מוצר ללא שם';
      let image = first(data, 'featured_image', 'image', 'image_url');
      if (!image) image = first(row, 'featured_image', 'image', 'image_url');
      if (!image) image = first(saleProduct, 'featured_image', 'image', 'image_url');
      if (image && !/^https?:\/\//.test(image)) image = IMAGE_BASE + image;
      let amount = first(row, 'amount', 'quantity', 'qty');
      if (!amount && data) amount = first(data, 'amount', 'quantity', 'qty');
      if (!amount) amount = '1';
      const note = first(saleProduct, 'note');
      const category = findCategory(saleProduct, data, row, item && item.item_sale, item);
      const categoryObject = saleProduct && isObject(saleProduct.category) ? saleProduct.category : null;
      const categoryOrder = categoryObject ? (Number(categoryObject.order) || 0) : 0;
      result.push({ key, name, imageUrl: image, amount, note, category, categoryOrder });
    });
    return result;
  }

  /** מיון לפי קטגוריות (category.order), ואז לפי א-ב. מוצרים בלי קטגוריה בסוף. סדר יציב. */
  function sortByCategory(rows) {
    const indexed = rows.map((row, index) => ({ row, index }));
    indexed.sort((x, y) => {
      const a = x.row, b = y.row;
      const ae = !a.category, be = !b.category;
      if (ae || be) return ae === be ? x.index - y.index : (ae ? 1 : -1);
      if (a.categoryOrder > 0 && b.categoryOrder > 0 && a.categoryOrder !== b.categoryOrder) return a.categoryOrder - b.categoryOrder;
      if (a.category === b.category) return x.index - y.index;
      const c = a.category.localeCompare(b.category, 'he');
      return c !== 0 ? c : x.index - y.index;
    });
    return indexed.map((e) => e.row);
  }

  function candidates(order) {
    if (!isObject(order)) return [];
    const inner = isObject(order.order) ? order.order : null;
    const data = isObject(order.data) ? order.data : null;
    const dataOrder = data && isObject(data.order) ? data.order : null;
    return [order, inner, data, dataOrder].filter(Boolean);
  }

  function path(root, keys) {
    let current = root;
    for (let i = 0; i < keys.length - 1 && current; i++) current = isObject(current[keys[i]]) ? current[keys[i]] : null;
    return first(current, keys[keys.length - 1]);
  }

  /** שם המכירה (למשל "וישלח תשפ״ז"), כמו OrderHistoryPage.saleName. */
  function saleName(order) {
    const list = candidates(order);
    const paths = [['sale', 'parent', 'week'], ['parent', 'week'], ['sale', 'week'], ['sale', 'name'], ['sale', 'title'],
      ['sale', 'custom_name'], ['sale', 'parent', 'name'], ['sale', 'parent', 'title'], ['parent', 'name'], ['parent', 'title']];
    for (const p of paths) for (const c of list) { const v = path(c, p); if (v) return v; }
    for (const key of ['week', 'parasha', 'parasha_name', 'sale_name', 'order_name', 'name', 'title'])
      for (const c of list) { const v = first(c, key); if (v) return v; }
    return '';
  }

  function orderTitle(order) {
    const name = saleName(order);
    if (name) return name;
    let id = '';
    for (const c of candidates(order)) { id = first(c, 'id', 'order_id', 'orderID'); if (id) break; }
    return id ? 'הזמנה ' + id : 'הזמנה';
  }

  function isCancelled(order) {
    for (const c of candidates(order)) {
      const status = first(c, 'status', 'order_status', 'state', 'status_name').toLowerCase();
      if (/cancel|בוטל|ביטול|rejected|deleted/.test(status)) return true;
      if (isObject(c.status) && /cancel|בוטל|ביטול/.test(first(c.status, 'name', 'key', 'slug', 'title').toLowerCase())) return true;
      if (c.cancelled === true || c.canceled === true || c.is_cancelled === true || c.is_canceled === true) return true;
      if (first(c, 'cancelled_at', 'canceled_at')) return true;
    }
    return false;
  }

  /** תשובת /api/orders?page=N: {orders, hasMore}. */
  function parseOrdersPage(response, requestedPage) {
    if (Array.isArray(response)) return { orders: response, hasMore: false };
    if (!isObject(response)) throw new Error('תשובת היסטוריית ההזמנות אינה תקינה.');
    const dataObject = isObject(response.data) ? response.data : null;
    let rows = Array.isArray(response.data) ? response.data : Array.isArray(response.orders) ? response.orders
      : Array.isArray(response.items) ? response.items : null;
    if (!rows && dataObject) rows = Array.isArray(dataObject.orders) ? dataObject.orders
      : Array.isArray(dataObject.items) ? dataObject.items : Array.isArray(dataObject.data) ? dataObject.data : null;
    if (!rows) throw new Error('השרת החזיר היסטוריה במבנה לא מוכר.');
    const meta = isObject(response.meta) ? response.meta : isObject(response.pagination) ? response.pagination
      : dataObject && isObject(dataObject.meta) ? dataObject.meta : {};
    const num = (o, k, f) => { const n = Number(o && o[k]); return Number.isFinite(n) && n > 0 ? n : f; };
    const current = num(response, 'current_page', num(meta, 'current_page', requestedPage));
    const last = num(response, 'last_page', num(meta, 'last_page', current));
    const next = first(response, 'next_page_url') || first(meta, 'next_page_url') || (dataObject ? first(dataObject, 'next_page_url') : '');
    return { orders: rows, hasMore: current < last || !!next };
  }

  // ---------- ייצוא (כמו ListExporter באפליקציה) ----------

  function oneLine(value) { return value === null || value === undefined ? '' : String(value).replace(/[\r\n]/g, ' ').trim(); }
  function cell(value) { return oneLine(value).replace(/\|/g, '/'); }

  function imageExtension(url) {
    const m = /\.(jpe?g|png|webp)(?:$|[?#])/i.exec(url || '');
    return m ? '.' + m[1].toLowerCase() : '.jpg';
  }

  function buildItems(products, listIndex, images) {
    const items = [];
    const ids = new Set();
    (products || []).forEach((p, i) => {
      if (!p) return;
      const name = limit(p.name, 200);
      if (!name) return;
      let id = limit(p.key, 64) || 'item' + i;
      const base = id; let suffix = 2;
      while (ids.has(id)) id = base + '_' + (suffix++);
      ids.add(id);
      let quantity = parseQuantity(p.amount);
      if (quantity <= 0) quantity = 1;
      const item = { id, name, quantity };
      const note = limit(p.note, 300), category = limit(p.category, 100), url = p.imageUrl || '';
      if (note) item.note = note;
      if (category) item.category = category;
      if (/^https:\/\//i.test(url) && url.length <= 2048) item.imageUrl = url;
      if (images && item.imageUrl) {
        const imageName = 'img_' + listIndex + '_' + i + imageExtension(url);
        images.push({ name: imageName, url: item.imageUrl });
        item.image = 'images/' + imageName;
      }
      items.push(item);
    });
    return items;
  }

  /**
   * exports: [{title, products}]. options.now: זמן קבוע (לבדיקות). options.images: מערך שמתמלא ב-{name,url}
   * לכל תמונה שתיכנס ל-ZIP.
   */
  function toJson(exports, options) {
    const opts = options || {};
    if (!Array.isArray(exports) || exports.length === 0) throw new Error('לא נבחרו הזמנות להורדה.');
    if (exports.length > MAX_LISTS) throw new Error('אפשר להוריד עד ' + MAX_LISTS + ' הזמנות בקובץ אחד.');
    const stamp = opts.now || Date.now();
    const lists = [];
    exports.forEach((e, l) => {
      const items = buildItems(e.products, l, opts.images || null);
      if (items.length === 0) return;
      const id = 'web-' + stamp + '-' + l;
      lists.push({
        account: { id, name: 'הורדה מהאתר' },
        order: { id, name: limit(oneLine(e.title), 200) || 'רשימה משותפת' },
        items
      });
    });
    if (lists.length === 0) throw new Error('אין בהזמנות שנבחרו מוצרים.');
    return { format: FORMAT, version: VERSION, lists };
  }

  function toText(exports, options) {
    const json = toJson(exports, options);
    let out = '# משנת אוסף: רשימות איסוף. אפשר לייבא את הקובץ הזה באפליקציה.\n';
    for (const list of json.lists) {
      out += '\n## ' + oneLine(list.order.name || 'רשימה') + '\n';
      for (const item of list.items) {
        out += cell(item.name) + ' | ' + formatQuantity(item.quantity) + ' | | ' + cell(item.note || '') + ' | ' + cell(item.category || '') + '\n';
      }
    }
    return out;
  }

  // ---------- ZIP (בלי דחיסה) ----------

  const CRC_TABLE = (() => {
    const table = new Uint32Array(256);
    for (let n = 0; n < 256; n++) {
      let c = n;
      for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1;
      table[n] = c >>> 0;
    }
    return table;
  })();

  function crc32(bytes) {
    let crc = 0xFFFFFFFF;
    for (let i = 0; i < bytes.length; i++) crc = CRC_TABLE[(crc ^ bytes[i]) & 0xFF] ^ (crc >>> 8);
    return (crc ^ 0xFFFFFFFF) >>> 0;
  }

  function utf8(text) { return new TextEncoder().encode(text); }

  /** entries: [{name, data: Uint8Array}] (שם שמסתיים ב-"/" הוא תיקייה). מחזיר Uint8Array. תאריך קבוע: הקובץ זהה בכל פעם. */
  function zip(entries) {
    const parts = [], central = [];
    let offset = 0;
    const DOS_TIME = 0, DOS_DATE = (1 << 5) | 1; // 1980-01-01
    for (const entry of entries) {
      const name = utf8(entry.name);
      const data = entry.data || new Uint8Array(0);
      const crc = crc32(data);
      const local = new DataView(new ArrayBuffer(30));
      local.setUint32(0, 0x04034b50, true); local.setUint16(4, 20, true); local.setUint16(6, 0x0800, true);
      local.setUint16(8, 0, true); local.setUint16(10, DOS_TIME, true); local.setUint16(12, DOS_DATE, true);
      local.setUint32(14, crc, true); local.setUint32(18, data.length, true); local.setUint32(22, data.length, true);
      local.setUint16(26, name.length, true); local.setUint16(28, 0, true);
      parts.push(new Uint8Array(local.buffer), name, data);
      const dir = new DataView(new ArrayBuffer(46));
      dir.setUint32(0, 0x02014b50, true); dir.setUint16(4, 20, true); dir.setUint16(6, 20, true); dir.setUint16(8, 0x0800, true);
      dir.setUint16(10, 0, true); dir.setUint16(12, DOS_TIME, true); dir.setUint16(14, DOS_DATE, true);
      dir.setUint32(16, crc, true); dir.setUint32(20, data.length, true); dir.setUint32(24, data.length, true);
      dir.setUint16(28, name.length, true); dir.setUint16(30, 0, true); dir.setUint16(32, 0, true);
      dir.setUint16(34, 0, true); dir.setUint16(36, 0, true); dir.setUint32(38, entry.name.endsWith('/') ? 0x10 : 0, true);
      dir.setUint32(42, offset, true);
      central.push(new Uint8Array(dir.buffer), name);
      offset += 30 + name.length + data.length;
    }
    const centralSize = central.reduce((s, p) => s + p.length, 0);
    const end = new DataView(new ArrayBuffer(22));
    end.setUint32(0, 0x06054b50, true); end.setUint16(8, entries.length, true); end.setUint16(10, entries.length, true);
    end.setUint32(12, centralSize, true); end.setUint32(16, offset, true);
    const all = parts.concat(central, [new Uint8Array(end.buffer)]);
    const out = new Uint8Array(all.reduce((s, p) => s + p.length, 0));
    let pos = 0;
    for (const p of all) { out.set(p, pos); pos += p.length; }
    return out;
  }

  /**
   * קובץ ZIP לאפליקציה: list.json ותיקיית images.
   * loadImage(url) מחזיר Promise של Uint8Array, או null כשההורדה נכשלה (אז נשאר רק קישור התמונה).
   */
  async function toZip(exports, loadImage, options) {
    const images = [];
    const json = toJson(exports, Object.assign({}, options, { images }));
    const files = [];
    const loaded = new Map();
    for (const image of images) {
      let bytes = null;
      try { bytes = loadImage ? await loadImage(image.url) : null; } catch (e) { bytes = null; }
      if (bytes && bytes.length > 0 && bytes.length <= 5 * 1024 * 1024) loaded.set(image.name, bytes);
    }
    // מוצר שהתמונה שלו לא ירדה נשאר עם קישור בלבד.
    for (const list of json.lists) for (const item of list.items) {
      if (item.image && !loaded.has(item.image.substring(7))) delete item.image;
    }
    files.push({ name: 'list.json', data: utf8(JSON.stringify(json)) });
    if (loaded.size > 0) {
      files.push({ name: 'images/', data: new Uint8Array(0) });
      for (const [name, bytes] of loaded) files.push({ name: 'images/' + name, data: bytes });
    }
    return zip(files);
  }

  // ---------- QR: טקסט קומפקטי בכמה קודים ----------

  function utf8Length(text) { return new TextEncoder().encode(text).length; }

  /**
   * מחלק את ההזמנות לקודי QR. כל קוד הוא טקסט שהאפליקציה מייבאת ("הדבקת טקסט"):
   *   #משנת אוסף 1/3          (הערה: מספר הקוד)
   *   ## שם ההזמנה
   *   ### קטגוריה            (חלה על המוצרים שאחריה)
   *   שם | כמות | | הערה
   * maxBytes: גודל מרבי לקוד (UTF-8). קוד קטן קל יותר לסריקה במצלמה פשוטה.
   */
  function toQrChunks(exports, maxBytes) {
    const limitBytes = maxBytes || 900;
    const json = toJson(exports, {});
    const chunks = [];
    let lines = [], size = 0;
    const flush = () => { if (lines.length) chunks.push(lines.join('\n')); lines = []; size = 0; };
    const push = (line) => { lines.push(line); size += utf8Length(line) + 1; };
    for (const list of json.lists) {
      const title = '## ' + oneLine(list.order.name || 'רשימה');
      let category = null;
      if (size + utf8Length(title) + 40 > limitBytes) flush();
      push(title);
      for (const item of list.items) {
        const itemCategory = oneLine(item.category || '');
        let line = cell(item.name) + ' | ' + formatQuantity(item.quantity);
        if (item.note) line += ' | | ' + cell(item.note);
        const catLineFor = (current) => itemCategory !== (current || '') ? '### ' + (itemCategory || '-') : null;
        let catLine = catLineFor(category);
        const need = utf8Length(line) + 1 + (catLine ? utf8Length(catLine) + 1 : 0);
        if (size + need > limitBytes - 24) {
          // קוד חדש: מתחיל בשם ההזמנה ובקטגוריה של המוצר הבא.
          flush(); category = null; push(title);
          catLine = catLineFor(null);
        }
        if (catLine) { category = itemCategory || null; push(catLine); }
        push(line);
      }
    }
    flush();
    // כותרת מספור בראש כל קוד (שורת הערה, האפליקציה מדלגת עליה).
    return chunks.map((c, i) => '#משנת אוסף ' + (i + 1) + '/' + chunks.length + '\n' + c);
  }

  return {
    toQrChunks, FORMAT, VERSION, first, parseQuantity, formatQuantity, unwrapOrder, extractOrderId, parseProducts,
    sortByCategory, saleName, orderTitle, isCancelled, parseOrdersPage, toJson, toText, toZip, zip, crc32
  };
});
