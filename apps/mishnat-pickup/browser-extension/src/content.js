/*
 * משנת אוסף – הורדה לאפליקציה: כפתור וחלון באתר משנת יוסף, וכפתורים על כל הזמנה בעמוד היסטוריית ההזמנות.
 * בוחרים הזמנות ופורמט (TXT / JSON / ZIP עם תמונות) ומורידים קובץ שנפתח באפליקציה
 * גם במכשיר בלי אינטרנט: "רשימה חדשה" ← "ייבוא מקובץ".
 *
 * ההתחברות: הטוקן שהאתר עצמו שומר בדפדפן (localStorage "token"). הוא לא נשמר ולא נשלח לשום מקום
 * חוץ מהשרת של משנת יוסף. הקובץ מכיל רק מוצרים, כמויות, קטגוריות ותמונות - בלי פרטים אישיים.
 */
(function () {
  'use strict';
  if (window.__mishnatOsefDownload) return;
  window.__mishnatOsefDownload = true;

  const ext = typeof browser !== 'undefined' ? browser : chrome;
  // בפיירפוקס window של סקריפט התוכן הוא עטיפה של הדף, ו-core.js נרשם על globalThis.
  const core = globalThis.MishnatCore || window.MishnatCore;
  const API = 'https://v2.mishnatyosef.org/api';

  // ---------- API ----------

  function token() {
    let t = '';
    try { t = localStorage.getItem('token') || ''; } catch (e) { t = ''; }
    if (t.startsWith('"') && t.endsWith('"')) { try { t = JSON.parse(t); } catch (e) { /* כמו שהוא */ } }
    return t;
  }

  async function api(path) {
    const t = token();
    if (!t) throw new Error('לא נמצאה התחברות. התחברו לאתר משנת יוסף ונסו שוב.');
    const response = await fetch(API + path, { headers: { Authorization: 'Bearer ' + t, Accept: 'application/json' } });
    if (response.status === 401 || response.status === 403) throw new Error('ההתחברות לאתר פגה. התחברו מחדש ונסו שוב.');
    if (!response.ok) throw new Error('השרת של משנת יוסף לא זמין כרגע (' + response.status + ').');
    return response.json();
  }

  async function loadImage(url) {
    const result = await ext.runtime.sendMessage({ type: 'mishnat-fetch-image', url });
    if (!result || !result.ok) return null;
    const binary = atob(result.base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    return bytes;
  }

  // ---------- ממשק ----------

  const host = document.createElement('div');
  host.id = 'mishnat-osef-download';
  const shadow = host.attachShadow({ mode: 'closed' });
  shadow.innerHTML = `
    <style>
      :host { all: initial; }
      * { box-sizing: border-box; font-family: "Segoe UI", Arial, sans-serif; }
      .fab { position: fixed; left: 18px; bottom: 18px; z-index: 2147483646; direction: rtl;
        background: #5E3FA3; color: #fff; border: 0; border-radius: 999px; padding: 12px 18px;
        font-size: 15px; font-weight: 700; cursor: pointer; box-shadow: 0 6px 18px rgba(60,30,120,.35); }
      .fab:hover, .fab:focus-visible { background: #4A2F86; outline: 3px solid #C9B8F0; outline-offset: 2px; }
      .backdrop { position: fixed; inset: 0; z-index: 2147483646; background: rgba(28,16,52,.45); display: none; }
      .panel { position: fixed; z-index: 2147483647; direction: rtl; left: 50%; top: 50%; transform: translate(-50%,-50%);
        width: min(560px, calc(100vw - 24px)); max-height: calc(100vh - 32px); display: none; flex-direction: column;
        background: #FBF9FE; color: #2C1A4D; border-radius: 18px; box-shadow: 0 18px 50px rgba(30,10,70,.4); overflow: hidden; }
      .open .backdrop { display: block; } .open .panel { display: flex; }
      header { display: flex; align-items: center; gap: 10px; padding: 16px 18px 8px; }
      h2 { margin: 0; font-size: 19px; flex: 1; }
      .close { background: #EFE9FA; border: 0; border-radius: 10px; width: 36px; height: 36px; font-size: 18px; cursor: pointer; color: #4A2F86; }
      .hint { margin: 0 18px 10px; font-size: 13px; color: #6B5A8C; line-height: 1.5; }
      .orders { overflow: auto; padding: 0 12px; flex: 1; min-height: 120px; }
      .order { display: flex; align-items: center; gap: 10px; padding: 10px; margin: 4px 0; border-radius: 12px;
        background: #fff; border: 1px solid #E6DDF5; cursor: pointer; }
      .order:focus-within { outline: 3px solid #C9B8F0; }
      .order input { width: 20px; height: 20px; accent-color: #5E3FA3; flex: none; }
      .order .name { font-weight: 700; font-size: 15px; }
      .order .meta { font-size: 12px; color: #7A6A99; }
      .order.cancelled { opacity: .55; }
      .more { margin: 6px 12px; background: transparent; border: 1px dashed #B9A6E2; color: #5E3FA3; border-radius: 10px; padding: 8px; cursor: pointer; }
      .formats { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; padding: 12px 18px; }
      .qrview { display: none; flex-direction: column; align-items: center; gap: 10px; padding: 4px 18px 12px; overflow: auto; }
      .showqr .qrview { display: flex; } .showqr .orders, .showqr .formats, .showqr .hint { display: none; }
      .qrview img { width: min(380px, 80vw); height: auto; image-rendering: pixelated; background: #fff; border-radius: 12px; padding: 8px; }
      .qrcount { font-size: 18px; font-weight: 700; }
      .qrnav { display: flex; gap: 8px; width: 100%; }
      .qrnav button { flex: 1; border: 0; border-radius: 12px; padding: 10px; background: #EFE9FA; color: #2C1A4D; font-size: 15px; font-weight: 700; cursor: pointer; }
      .qrnav button:hover, .qrnav button:focus-visible { background: #5E3FA3; color: #fff; outline: none; }
      .qrnav button:disabled { opacity: .45; cursor: default; }
      .qrhelp { font-size: 13px; color: #6B5A8C; line-height: 1.55; margin: 0; }
      .formats button { border: 0; border-radius: 14px; padding: 12px 6px; background: #EFE9FA; color: #2C1A4D; cursor: pointer;
        font-size: 14px; font-weight: 700; display: flex; flex-direction: column; align-items: center; gap: 4px; }
      .formats button span { font-size: 24px; }
      .formats button:hover, .formats button:focus-visible { background: #5E3FA3; color: #fff; outline: none; }
      .formats button:disabled { opacity: .5; cursor: default; }
      .status { min-height: 22px; padding: 0 18px 14px; font-size: 13px; color: #4A2F86; }
      .status.error { color: #B3261E; }
      .disclaimer { padding: 0 18px 12px; font-size: 11px; color: #8E80A8; }
    </style>
    <button class="fab" type="button" title="הורדת הזמנות לאפליקציה משנת אוסף">📥 לאפליקציה</button>
    <div class="backdrop"></div>
    <section class="panel" role="dialog" aria-modal="true" aria-labelledby="t">
      <header><h2 id="t">הורדה לאפליקציה משנת אוסף</h2><button class="close" type="button" aria-label="סגירה">✕</button></header>
      <p class="hint">סמנו הזמנות ובחרו פורמט. את הקובץ מעבירים לטלפון (כבל, בלוטות' או כרטיס זיכרון) ופותחים באפליקציה:
        "רשימה חדשה" ← "ייבוא מקובץ". אין צורך באינטרנט בטלפון.</p>
      <div class="orders" role="group" aria-label="הזמנות"></div>
      <div class="formats">
        <button type="button" data-format="txt"><span>📄</span>טקסט (TXT)</button>
        <button type="button" data-format="json"><span>🗂️</span>JSON</button>
        <button type="button" data-format="zip"><span>🗜️</span>ZIP עם תמונות</button>
        <button type="button" data-format="qr"><span>📱</span>קוד QR לסריקה</button>
      </div>
      <div class="qrview" aria-live="polite">
        <div class="qrcount"></div>
        <img alt="קוד QR של רשימת האיסוף">
        <div class="qrnav">
          <button type="button" class="qrprev">→ הקודם</button>
          <button type="button" class="qrauto">▶ אוטומטי</button>
          <button type="button" class="qrnext">הבא ←</button>
        </div>
        <p class="qrhelp">באפליקציה: "רשימה חדשה" ← "סריקת קוד QR", ומחזיקים את הטלפון מול המסך. כשיש כמה קודים הם
          מתחלפים לבד, והאפליקציה אוספת אותם בכל סדר. בלי הסורק של האפליקציה: סורק המצלמה ← העתקה ← "הדבקת טקסט"
          (כמה קודים באותו חלון מתחברים לרשימה אחת). תמונות לא עוברות ב-QR, והן ייטענו כשיהיה אינטרנט.</p>
        <div class="qrnav"><button type="button" class="qrback">חזרה לבחירת הזמנות</button></div>
      </div>
      <div class="status" role="status" aria-live="polite"></div>
      <div class="disclaimer">תוסף לא רשמי. הקובץ מכיל רק מוצרים, כמויות וקטגוריות.</div>
    </section>`;

  const root = document.createElement('div');
  while (shadow.firstChild && shadow.firstChild !== root) root.appendChild(shadow.firstChild);
  shadow.appendChild(root);
  const $ = (s) => root.querySelector(s);
  const fab = $('.fab'), panel = $('.panel'), list = $('.orders'), status = $('.status');

  let page = 0, hasMore = false, loading = false, busy = false;
  const rowsById = new Map();

  function setStatus(text, error) {
    status.textContent = text || '';
    status.classList.toggle('error', !!error);
  }

  function currentOrderId() {
    const m = /\/order\/(\d+)/.exec(location.pathname);
    return m ? m[1] : '';
  }

  function formatDate(order) {
    const raw = core.first(order, 'created_at', 'createdAt', 'order_date', 'date');
    const d = raw ? new Date(raw.replace(' ', 'T')) : null;
    return d && !isNaN(d) ? d.toLocaleDateString('he-IL') : '';
  }

  function addOrderRow(order, checked) {
    const id = String(core.extractOrderId(order));
    if (id === '-1' || rowsById.has(id)) return;
    const cancelled = core.isCancelled(order);
    const label = document.createElement('label');
    label.className = 'order' + (cancelled ? ' cancelled' : '');
    const box = document.createElement('input');
    box.type = 'checkbox';
    box.value = id;
    box.checked = !!checked && !cancelled;
    const text = document.createElement('div');
    const name = document.createElement('div');
    name.className = 'name';
    name.textContent = core.orderTitle(order) + (cancelled ? ' (בוטלה)' : '');
    const meta = document.createElement('div');
    meta.className = 'meta';
    meta.textContent = ['הזמנה ' + id, formatDate(order)].filter(Boolean).join(' · ');
    text.append(name, meta);
    label.append(box, text);
    list.appendChild(label);
    rowsById.set(id, { order, box });
  }

  async function loadPage() {
    if (loading) return;
    loading = true;
    setStatus('טוען הזמנות…');
    try {
      const next = page + 1;
      const result = core.parseOrdersPage(await api('/orders?page=' + next), next);
      page = next;
      hasMore = result.hasMore;
      const current = currentOrderId();
      const firstPage = rowsById.size === 0;
      let preselected = false;
      result.orders.forEach((order) => {
        const id = String(core.extractOrderId(order));
        const select = current ? id === current : firstPage && !preselected && !core.isCancelled(order);
        if (select) preselected = true;
        addOrderRow(order, select);
      });
      // ההזמנה שבדף הנוכחי, גם אם היא לא בעמוד הראשון.
      if (firstPage && current && !rowsById.has(current)) {
        try { addOrderRow(core.unwrapOrder(await api('/orders/' + current)), true); } catch (e) { /* מתעלמים */ }
      }
      renderMore();
      setStatus(rowsById.size ? '' : 'לא נמצאו הזמנות בחשבון.');
    } catch (e) {
      setStatus(e.message || 'טעינת ההזמנות נכשלה.', true);
    } finally {
      loading = false;
    }
  }

  function renderMore() {
    const old = list.querySelector('.more');
    if (old) old.remove();
    if (!hasMore) return;
    const more = document.createElement('button');
    more.type = 'button';
    more.className = 'more';
    more.textContent = 'עוד הזמנות';
    more.addEventListener('click', loadPage);
    list.appendChild(more);
  }

  function selectedIds() {
    return [...rowsById.entries()].filter(([, r]) => r.box.checked).map(([id]) => id);
  }

  function fileName(ids, format) {
    const d = new Date();
    const date = d.getFullYear() + String(d.getMonth() + 1).padStart(2, '0') + String(d.getDate()).padStart(2, '0');
    const part = ids.length === 1 ? 'order-' + ids[0] : ids.length + '-orders';
    // שם באנגלית: לא משתבש בהעברה לטלפונים ישנים.
    return 'mishnat-osef-' + part + '-' + date + '.' + format;
  }

  function download(name, data, mime) {
    const blob = new Blob([data], { type: mime });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = name;
    a.style.display = 'none';
    root.appendChild(a);
    a.click();
    setTimeout(() => { URL.revokeObjectURL(url); a.remove(); }, 30000);
  }

  // ---------- QR ----------
  let qrChunks = [], qrIndex = 0, qrTimer = null;

  // החלפה אוטומטית כל 2.5 שניות: הסורק באפליקציה אוסף את הקודים בכל סדר.
  function setAuto(on) {
    if (qrTimer) { clearInterval(qrTimer); qrTimer = null; }
    if (on && qrChunks.length > 1) qrTimer = setInterval(() => { qrIndex = (qrIndex + 1) % qrChunks.length; renderQr(); }, 2500);
    $('.qrauto').textContent = qrTimer ? '⏸ עצירה' : '▶ אוטומטי';
    $('.qrauto').disabled = qrChunks.length < 2;
  }

  function renderQr() {
    const text = qrChunks[qrIndex];
    const qr = qrcode(0, 'L');
    qrcode.stringToBytes = qrcode.stringToBytesFuncs['UTF-8'];
    qr.addData(text, 'Byte');
    qr.make();
    const modules = qr.getModuleCount();
    const cell = Math.max(3, Math.floor(400 / (modules + 8)));
    $('.qrview img').src = qr.createDataURL(cell, 4);
    $('.qrcount').textContent = qrChunks.length > 1 ? 'קוד ' + (qrIndex + 1) + ' מתוך ' + qrChunks.length : 'קוד אחד';
    $('.qrprev').disabled = qrIndex === 0;
    $('.qrnext').disabled = qrIndex >= qrChunks.length - 1;
  }

  function showQr(chunks) {
    qrChunks = chunks; qrIndex = 0;
    root.classList.add('showqr');
    renderQr();
    setAuto(chunks.length > 1);
    setStatus(chunks.length > 1 ? 'ההזמנות חולקו ל-' + chunks.length + ' קודים, כדי שיהיה קל לסרוק במצלמה פשוטה.' : '');
    ($('.qrnext').disabled ? $('.qrback') : $('.qrnext')).focus();
  }

  function hideQr() {
    setAuto(false);
    root.classList.remove('showqr');
    setStatus('');
    const b = list.querySelector('input:checked') || list.querySelector('input');
    if (b) b.focus();
  }

  $('.qrprev').addEventListener('click', () => { setAuto(false); if (qrIndex > 0) { qrIndex--; renderQr(); } });
  $('.qrnext').addEventListener('click', () => { setAuto(false); if (qrIndex < qrChunks.length - 1) { qrIndex++; renderQr(); } });
  $('.qrauto').addEventListener('click', () => setAuto(!qrTimer));
  $('.qrback').addEventListener('click', hideQr);
  root.addEventListener('keydown', (e) => {
    if (!root.classList.contains('showqr')) return;
    if (e.key === 'ArrowLeft') { e.preventDefault(); $('.qrnext').click(); }
    else if (e.key === 'ArrowRight') { e.preventDefault(); $('.qrprev').click(); }
  });

  /**
   * ייצוא הזמנות. ids - מהחלון או מהכפתורים בעמוד היסטוריית ההזמנות; report - איפה להציג הודעות.
   */
  async function exportOrders(format, ids = selectedIds(), report = setStatus) {
    if (busy) { report('ההורדה הקודמת עוד לא הסתיימה.', true); return; }
    if (ids.length === 0) { report('סמנו לפחות הזמנה אחת.', true); return; }
    if (ids.length > 20) { report('אפשר להוריד עד 20 הזמנות בקובץ אחד.', true); return; }
    busy = true;
    setBusyButtons(true);
    try {
      const exports = [];
      for (let i = 0; i < ids.length; i++) {
        report('טוען הזמנה ' + (i + 1) + ' מתוך ' + ids.length + '…');
        const order = core.unwrapOrder(await api('/orders/' + ids[i]));
        const products = core.sortByCategory(core.parseProducts(order));
        const listed = rowsById.get(ids[i]);
        const title = core.saleName(order) || (listed ? core.orderTitle(listed.order) : 'הזמנה ' + ids[i]);
        exports.push({ title, products });
      }
      if (format === 'qr') {
        report('');
        if (!root.classList.contains('open')) { root.classList.add('open'); if (rowsById.size === 0) loadPage(); }
        showQr(core.toQrChunks(exports, 900));
        return;
      }
      if (format === 'txt') download(fileName(ids, 'txt'), core.toText(exports), 'text/plain;charset=utf-8');
      else if (format === 'json') download(fileName(ids, 'json'), JSON.stringify(core.toJson(exports), null, 2), 'application/json');
      else {
        let done = 0;
        const bytes = await core.toZip(exports, async (url) => {
          report('מוריד תמונות… ' + (++done));
          return loadImage(url);
        });
        download(fileName(ids, 'zip'), bytes, 'application/zip');
      }
      report('✅ הקובץ ירד. מעבירים אותו לטלפון ופותחים באפליקציה: "רשימה חדשה" ← "ייבוא מקובץ".');
    } catch (e) {
      report(e.message || 'ההורדה נכשלה.', true);
    } finally {
      busy = false;
      setBusyButtons(false);
    }
  }

  function setBusyButtons(on) {
    root.querySelectorAll('.formats button').forEach((b) => { b.disabled = on; });
    pageButtons.forEach((b) => { if (b.isConnected) b.disabled = on; });
  }

  // ---------- כפתורים על כל הזמנה בעמוד היסטוריית ההזמנות ----------
  // בכל שורת הזמנה: סימון לבחירה מרובה וכפתורי TXT / JSON / ZIP / QR להזמנה הזו.
  // כשמסמנים הזמנות מופיע פס בתחתית המסך להורדה של כולן יחד.

  const pageButtons = new Set();
  const pageSelected = new Set();
  const ROW_STYLE = `
    :host { all: initial; display: block; }
    * { box-sizing: border-box; font-family: "Segoe UI", Arial, sans-serif; }
    .bar { direction: rtl; display: flex; flex-wrap: wrap; align-items: center; gap: 6px; margin-top: 8px;
      padding: 6px 8px; border-radius: 10px; background: #F4EFFC; border: 1px solid #E2D8F5; color: #2C1A4D; }
    label { display: inline-flex; align-items: center; gap: 6px; font-size: 14px; font-weight: 700; cursor: pointer; margin-left: 6px; }
    input { width: 18px; height: 18px; accent-color: #5E3FA3; }
    button { border: 0; border-radius: 8px; padding: 5px 10px; background: #fff; color: #4A2F86; font-size: 13px; font-weight: 700;
      cursor: pointer; border: 1px solid #DCD0F2; }
    button:hover, button:focus-visible { background: #5E3FA3; color: #fff; outline: none; }
    button:disabled { opacity: .5; cursor: default; }
    .t { font-size: 12px; color: #7A6A99; }`;
  const FORMATS = [['txt', '📄 TXT'], ['json', '🗂️ JSON'], ['zip', '🗜️ ZIP'], ['qr', '📱 QR']];

  const tray = document.createElement('div');
  tray.id = 'mishnat-osef-tray';
  const trayShadow = tray.attachShadow({ mode: 'closed' });
  trayShadow.innerHTML = `<style>${ROW_STYLE}
    .tray { position: fixed; z-index: 2147483645; right: 50%; transform: translateX(50%); bottom: 16px; direction: rtl;
      display: none; flex-direction: column; gap: 6px; padding: 10px 14px; background: #FBF9FE; border-radius: 16px;
      box-shadow: 0 10px 30px rgba(30,10,70,.35); border: 1px solid #E2D8F5; max-width: calc(100vw - 24px); }
    .tray.show { display: flex; }
    .row { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; }
    .count { font-weight: 700; font-size: 15px; margin-left: 6px; }
    .msg { font-size: 13px; color: #4A2F86; min-height: 0; }
    .msg.error { color: #B3261E; }
  </style>
  <div class="tray" role="region" aria-label="הזמנות מסומנות">
    <div class="row"><span class="count"></span><span class="formats-b"></span><button type="button" class="clear">ניקוי סימון</button></div>
    <div class="msg" role="status" aria-live="polite"></div>
  </div>`;
  const trayBox = trayShadow.querySelector('.tray'), trayMsg = trayShadow.querySelector('.msg');
  FORMATS.forEach(([format, text]) => {
    const b = document.createElement('button');
    b.type = 'button'; b.textContent = text;
    b.addEventListener('click', () => exportOrders(format, [...pageSelected], trayReport));
    pageButtons.add(b);
    trayShadow.querySelector('.formats-b').appendChild(b);
  });
  trayShadow.querySelector('.clear').addEventListener('click', () => {
    pageSelected.clear();
    document.querySelectorAll('[data-mishnat-osef-check]').forEach((h) => { if (h.__mishnatBox) h.__mishnatBox.checked = false; });
    updateTray();
  });

  let trayHideTimer = null;
  function trayReport(text, error) {
    trayMsg.textContent = text || '';
    trayMsg.classList.toggle('error', !!error);
    if (text) trayBox.classList.add('show');
    if (trayHideTimer) clearTimeout(trayHideTimer);
    if (text && !busy && pageSelected.size === 0) trayHideTimer = setTimeout(() => { trayMsg.textContent = ''; updateTray(); }, 8000);
  }

  function updateTray() {
    const n = pageSelected.size;
    trayShadow.querySelector('.count').textContent = n === 1 ? 'הזמנה אחת מסומנת' : n + ' הזמנות מסומנות';
    trayShadow.querySelector('.formats-b').style.display = n ? '' : 'none';
    trayShadow.querySelector('.clear').style.display = n ? '' : 'none';
    trayShadow.querySelector('.count').style.display = n ? '' : 'none';
    trayBox.classList.toggle('show', n > 0 || !!trayMsg.textContent);
  }

  /** מספר ההזמנה משורה בעמוד: הטקסט שאחרי "מספר הזמנה". */
  function rowOrderId(card) {
    for (const p of card.querySelectorAll('p')) {
      if (p.textContent.trim() !== 'מספר הזמנה') continue;
      const value = p.nextElementSibling ? p.nextElementSibling.textContent.trim() : '';
      if (/^\d{3,}$/.test(value)) return value;
    }
    return '';
  }

  /** השורה של הזמנה: האב הקרוב של "לצפיה בהזמנה" שיש בו גם מספר הזמנה. */
  function rowFor(button) {
    let node = button.parentElement;
    for (let depth = 0; node && depth < 8; depth++, node = node.parentElement) {
      if (rowOrderId(node)) return node;
    }
    return null;
  }

  /**
   * שורת הזמנה: תיבת הסימון בהתחלה (בצד ימין, לפני "יום איסוף"),
   * והכפתורים ברווח שלפני "סה״כ" ו"לצפיה בהזמנה".
   */
  function decorateRow(card, id, viewButton) {
    const actions = viewButton.parentElement;
    const oldCheck = card.querySelector(':scope > [data-mishnat-osef-check]');
    const oldButtons = actions.querySelector(':scope > [data-mishnat-osef-row]');
    if (oldCheck && oldButtons && oldCheck.dataset.mishnatOsefCheck === id && oldButtons.dataset.mishnatOsefRow === id) return;
    if (oldCheck) oldCheck.remove();
    if (oldButtons) oldButtons.remove();

    const checkHost = document.createElement('div');
    checkHost.dataset.mishnatOsefCheck = id;
    checkHost.style.cssText = 'flex:none;display:flex;align-items:center;margin-inline-end:12px;';
    const checkShadow = checkHost.attachShadow({ mode: 'closed' });
    checkShadow.innerHTML = '<style>' + ROW_STYLE + ' input{width:22px;height:22px;margin:0;cursor:pointer;}</style>';
    const box = document.createElement('input');
    box.type = 'checkbox';
    box.title = 'סימון להורדה יחד עם הזמנות אחרות';
    box.setAttribute('aria-label', 'סימון הזמנה ' + id + ' להורדה לאפליקציה');
    box.checked = pageSelected.has(id);
    box.addEventListener('change', () => { if (box.checked) pageSelected.add(id); else pageSelected.delete(id); updateTray(); });
    checkShadow.appendChild(box);
    checkHost.__mishnatBox = box;
    card.insertBefore(checkHost, card.firstChild);

    const rowHost = document.createElement('div');
    rowHost.dataset.mishnatOsefRow = id;
    rowHost.style.cssText = 'flex:none;display:flex;align-items:center;';
    const rowShadow = rowHost.attachShadow({ mode: 'closed' });
    rowShadow.innerHTML = '<style>' + ROW_STYLE + ' .bar{margin:0;padding:0;background:none;border:0;gap:4px;flex-wrap:nowrap;}</style><div class="bar"></div>';
    const bar = rowShadow.querySelector('.bar');
    FORMATS.forEach(([format, text]) => {
      const b = document.createElement('button');
      b.type = 'button'; b.textContent = text; b.title = 'הורדת ההזמנה הזו (' + format.toUpperCase() + ')';
      b.disabled = busy;
      b.addEventListener('click', () => exportOrders(format, [id], trayReport));
      pageButtons.add(b);
      bar.appendChild(b);
    });
    actions.insertBefore(rowHost, actions.firstChild);
  }

  function decoratePage() {
    if (!/\/orders-history/.test(location.pathname)) return;
    const seen = new Set();
    document.querySelectorAll('button').forEach((button) => {
      if (!button.textContent.includes('לצפיה בהזמנה')) return;
      const card = rowFor(button);
      if (!card || seen.has(card)) return;
      seen.add(card);
      decorateRow(card, rowOrderId(card), button);
    });
    pageButtons.forEach((b) => { if (!b.isConnected) pageButtons.delete(b); });
  }

  let decorateTimer = null;
  const observer = new MutationObserver((mutations) => {
    // שינויים שלנו (בתוך הכפתורים שהוספנו) לא מפעילים סריקה חוזרת.
    if (mutations.every((m) => m.target && m.target.closest && m.target.closest('[data-mishnat-osef-row],[data-mishnat-osef-check]'))) return;
    if (decorateTimer) return;
    decorateTimer = setTimeout(() => { decorateTimer = null; decoratePage(); }, 300);
  });

  function open() {
    setAuto(false);
    root.classList.remove('showqr');
    root.classList.add('open');
    if (rowsById.size === 0) loadPage();
    setTimeout(() => { const b = list.querySelector('input:checked') || list.querySelector('input') || $('.close'); b.focus(); }, 0);
  }

  function close() {
    setAuto(false);
    root.classList.remove('open');
    fab.focus();
  }

  fab.addEventListener('click', open);
  $('.close').addEventListener('click', close);
  $('.backdrop').addEventListener('click', close);
  root.addEventListener('keydown', (e) => { if (e.key === 'Escape' && root.classList.contains('open')) { e.stopPropagation(); close(); } });
  root.querySelectorAll('.formats button').forEach((b) => b.addEventListener('click', () => exportOrders(b.dataset.format)));

  ext.runtime.onMessage.addListener((message) => {
    if (message && message.type === 'mishnat-toggle-panel') {
      if (root.classList.contains('open')) close(); else open();
    }
  });

  (document.body || document.documentElement).appendChild(host);
  (document.body || document.documentElement).appendChild(tray);
  updateTray();
  decoratePage();
  observer.observe(document.body || document.documentElement, { childList: true, subtree: true });
})();
