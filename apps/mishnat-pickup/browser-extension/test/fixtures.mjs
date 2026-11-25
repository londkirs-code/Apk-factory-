// קבצי דוגמה שהתוסף מייצר, מנתוני הזמנה קבועים. הבדיקה ב-Node מוודאת שהפלט לא השתנה בלי כוונה,
// ו-ExtensionCompatibilityTest באפליקציה מוודאת שהאפליקציה מייבאת אותם.
// יצירה מחדש אחרי שינוי מכוון בפורמט: node test/fixtures.mjs --write
import { readFileSync, writeFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const here = dirname(fileURLToPath(import.meta.url));
export const core = createRequire(import.meta.url)('../src/core.js');
export const dir = join(here, 'fixtures');
export const NOW = 1700000000000;

/** תמונה "שהורדה": בתים קבועים לכל קישור מהשרת של משנת יוסף; קישור אחר נכשל. */
export async function fakeImage(url) {
  if (!url.startsWith('https://images.mishnatyosef.org/')) return null;
  const bytes = new Uint8Array(16);
  bytes.set([0xFF, 0xD8, 0xFF, 0xE0]);
  for (let i = 4; i < 16; i++) bytes[i] = url.charCodeAt(i % url.length) & 0xFF;
  return bytes;
}

export function exportsFromFixture() {
  const order = core.unwrapOrder(JSON.parse(readFileSync(join(dir, 'order_detail.json'), 'utf8')));
  const products = core.sortByCategory(core.parseProducts(order));
  // שתי הזמנות בקובץ אחד, כדי לבדוק גם ריבוי רשימות. השם השני עם | וירידת שורה.
  return [
    { title: 'וישלח תשפ"ז', products },
    { title: 'הזמנה | שנייה\nלבדיקה', products: products.slice(0, 2) }
  ];
}

export async function build() {
  const exports = exportsFromFixture();
  return {
    'export.txt': Buffer.from(core.toText(exports, { now: NOW }), 'utf8'),
    'export.json': Buffer.from(JSON.stringify(core.toJson(exports, { now: NOW }), null, 2), 'utf8'),
    'export.zip': Buffer.from(await core.toZip(exports, fakeImage, { now: NOW }))
  };
}

if (process.argv.includes('--write')) {
  for (const [name, data] of Object.entries(await build())) {
    writeFileSync(join(dir, name), data);
    console.log('wrote', name, data.length);
  }
}
