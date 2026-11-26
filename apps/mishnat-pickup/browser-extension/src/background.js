/*
 * משנת אוסף – הורדה לאפליקציה: רקע (Chrome: service worker, Firefox: background script).
 * 1. הורדת תמונות מוצרים לקובץ ה-ZIP (דף האתר לא יכול להוריד אותן בעצמו בגלל הגבלות דפדפן).
 *    רק מהשרת של התמונות של משנת יוסף, ורק בכתובת https.
 * 2. לחיצה על סמל התוסף פותחת או סוגרת את חלון ההורדה באתר, ובאתר אחר פותחת את היסטוריית ההזמנות.
 * אין כאן שום איסוף נתונים או שליחה לשרת אחר.
 */
const ext = typeof browser !== 'undefined' ? browser : chrome;

const IMAGE_HOSTS = ['images.mishnatyosef.org'];
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;

function allowedImage(url) {
  try {
    const u = new URL(url);
    return u.protocol === 'https:' && IMAGE_HOSTS.includes(u.hostname);
  } catch (e) {
    return false;
  }
}

function toBase64(buffer) {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) binary += String.fromCharCode.apply(null, bytes.subarray(i, i + chunk));
  return btoa(binary);
}

async function fetchImage(url) {
  if (!allowedImage(url)) return { ok: false };
  const response = await fetch(url, { credentials: 'omit' });
  if (!response.ok) return { ok: false };
  const buffer = await response.arrayBuffer();
  if (buffer.byteLength === 0 || buffer.byteLength > MAX_IMAGE_BYTES) return { ok: false };
  return { ok: true, base64: toBase64(buffer) };
}

ext.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (!message || message.type !== 'mishnat-fetch-image') return false;
  fetchImage(message.url).then(sendResponse, () => sendResponse({ ok: false }));
  return true; // תשובה אסינכרונית
});

ext.action.onClicked.addListener(async (tab) => {
  if (!tab || tab.id == null) return;
  try {
    await ext.tabs.sendMessage(tab.id, { type: 'mishnat-toggle-panel' });
  } catch (e) {
    // לא באתר משנת יוסף: פותחים את היסטוריית ההזמנות.
    ext.tabs.create({ url: 'https://mishnatyosef.org/orders-history' });
  }
});
