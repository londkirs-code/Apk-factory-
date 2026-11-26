// בדיקות לליבת התוסף: node --test test/
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { core, dir, build, exportsFromFixture, NOW } from './fixtures.mjs';

test('פענוח הזמנה: מוצרים, כמויות, קטגוריות ותמונות', () => {
  const order = core.unwrapOrder(JSON.parse(readFileSync(join(dir, 'order_detail.json'), 'utf8')));
  const rows = core.parseProducts(order);
  assert.equal(rows.length, 4);
  const oats = rows.find((r) => r.key === '421');
  assert.equal(oats.name, 'קווקר הכנה מהירה');
  assert.equal(oats.amount, '2');
  assert.equal(oats.category, 'קטניות ושימורים');
  assert.equal(oats.categoryOrder, 25);
  assert.equal(oats.imageUrl, 'https://images.mishnatyosef.org/images/items/421_a.jpeg');
  // מיון לפי category.order: ירקות(14) קטניות(25) טיפוח(31) ביצים(200)
  assert.deepEqual(core.sortByCategory(rows).map((r) => r.category), ['ירקות', 'קטניות ושימורים', 'טיפוח ונקיון', 'ביצים']);
});

test('שם מכירה לפי sale.parent.week, ואחרת "הזמנה" ומספר', () => {
  assert.equal(core.saleName({ sale: { parent: { week: 'וישלח' } }, name: 'x' }), 'וישלח');
  assert.equal(core.orderTitle({ id: 55 }), 'הזמנה 55');
  assert.equal(core.isCancelled({ status: 'cancelled' }), true);
  assert.equal(core.isCancelled({ status: 'paid' }), false);
});

test('עמוד היסטוריה: מבנה Laravel ועמוד נוסף', () => {
  const page = core.parseOrdersPage({ data: [{ id: 1 }], current_page: 1, last_page: 3 }, 1);
  assert.equal(page.orders.length, 1);
  assert.equal(page.hasMore, true);
});

test('טקסט: "##" לכל רשימה, ו-| וירידות שורה לא שוברים עמודות', () => {
  const text = core.toText(exportsFromFixture(), { now: NOW });
  assert.match(text, /^## וישלח תשפ"ז$/m);
  assert.match(text, /^## הזמנה \| שנייה לבדיקה$/m);
  assert.match(text, /^קווקר הכנה מהירה \| 2 \| \| {2}\| קטניות ושימורים$/m);
});

test('JSON לפי הפורמט של האפליקציה', () => {
  const json = core.toJson(exportsFromFixture(), { now: NOW });
  assert.equal(json.format, 'mishnat-pickup-list');
  assert.equal(json.lists.length, 2);
  assert.notEqual(json.lists[0].account.id, json.lists[1].account.id);
  assert.equal(typeof json.lists[0].items[0].quantity, 'number');
});

test('ZIP: תקין, ותמונה שלא ירדה נשארת רק כקישור', async () => {
  const exports = [{ title: 'א', products: [
    { key: '1', name: 'עם תמונה', amount: '1', imageUrl: 'https://images.mishnatyosef.org/images/items/1.png' },
    { key: '2', name: 'בלי', amount: '1', imageUrl: 'https://other.example/2.jpg' }] }];
  const bytes = await core.toZip(exports, async (u) => u.includes('mishnatyosef') ? new Uint8Array([1, 2, 3]) : null, { now: NOW });
  assert.equal(bytes[0], 0x50); assert.equal(bytes[1], 0x4B);
  const text = Buffer.from(bytes).toString('latin1');
  assert.ok(text.includes('images/img_0_0.png'));
  assert.ok(!text.includes('img_0_1'));
});

test('QR: כל קוד קטן מהמגבלה, ממוספר, ונבנה בהצלחה', async () => {
  const { createRequire } = await import('node:module');
  const qrcode = createRequire(import.meta.url)('../src/vendor/qrcode.js');
  qrcode.stringToBytes = qrcode.stringToBytesFuncs['UTF-8'];
  const many = [];
  for (let i = 0; i < 120; i++) many.push({ key: 'k' + i, name: 'מוצר בדיקה ארוך מספר ' + i, amount: String(1 + (i % 4)), category: 'קטגוריה ' + (i % 6) });
  const chunks = core.toQrChunks([{ title: 'הזמנה גדולה', products: many }], 900);
  assert.ok(chunks.length > 1);
  chunks.forEach((c, i) => {
    assert.ok(c.startsWith('#משנת אוסף ' + (i + 1) + '/' + chunks.length + '\n'));
    assert.ok(Buffer.byteLength(c, 'utf8') <= 900 + 40, 'קוד ' + (i + 1) + ' גדול מדי');
    assert.ok(c.includes('\n## הזמנה גדולה\n'));
    const qr = qrcode(0, 'L'); qr.addData(c, 'Byte'); qr.make();
    assert.ok(qr.getModuleCount() <= 105, 'קוד צפוף מדי למצלמה פשוטה');
  });
  const lines = chunks.join('\n').split('\n').filter((l) => l && !l.startsWith('#'));
  assert.equal(lines.length, 120);
});

test('קובצי הדוגמה תואמים לפלט הנוכחי (אחרי שינוי מכוון: node test/fixtures.mjs --write)', async () => {
  for (const [name, data] of Object.entries(await build())) {
    assert.ok(readFileSync(join(dir, name)).equals(data), name + ' השתנה');
  }
});
