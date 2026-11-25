// בניית התוסף ל-Chrome ול-Firefox: node build.mjs [תיקיית פלט]
// יוצר dist/chrome ו-dist/firefox, ושני קובצי ZIP להתקנה. בלי תלויות חיצוניות.
import { readFileSync, writeFileSync, mkdirSync, rmSync, readdirSync, copyFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const here = dirname(fileURLToPath(import.meta.url));
const core = createRequire(import.meta.url)('./src/core.js');
const out = process.argv[2] ? process.argv[2] : join(here, 'dist');
const base = JSON.parse(readFileSync(join(here, 'manifest.base.json'), 'utf8'));
const version = base.version;

const targets = {
  chrome: { ...base, background: { service_worker: 'background.js' } },
  firefox: {
    ...base,
    background: { scripts: ['background.js'] },
    browser_specific_settings: {
      gecko: {
        id: 'mishnat-osef-download@londkirs-code',
        strict_min_version: '115.0',
        data_collection_permissions: { required: ['none'] }
      }
    }
  }
};

const files = ['vendor/qrcode.js', 'core.js', 'content.js', 'background.js'];
const icons = readdirSync(join(here, 'icons')).filter((f) => f.endsWith('.png')).sort();
mkdirSync(out, { recursive: true });

for (const [name, manifest] of Object.entries(targets)) {
  const dir = join(here, 'dist', name);
  rmSync(dir, { recursive: true, force: true });
  mkdirSync(join(dir, 'icons'), { recursive: true });
  mkdirSync(join(dir, 'vendor'), { recursive: true });
  writeFileSync(join(dir, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
  for (const f of files) copyFileSync(join(here, 'src', f), join(dir, f));
  for (const f of icons) copyFileSync(join(here, 'icons', f), join(dir, 'icons', f));
  const entries = [{ name: 'manifest.json', data: readFileSync(join(dir, 'manifest.json')) }, { name: 'vendor/', data: new Uint8Array(0) }];
  for (const f of files) entries.push({ name: f, data: readFileSync(join(dir, f)) });
  entries.push({ name: 'icons/', data: new Uint8Array(0) });
  for (const f of icons) entries.push({ name: 'icons/' + f, data: readFileSync(join(dir, 'icons', f)) });
  const zipName = `mishnat-osef-${name}-${version}.zip`;
  writeFileSync(join(out, zipName), core.zip(entries.map((e) => ({ name: e.name, data: new Uint8Array(e.data) }))));
  console.log('built', zipName);
}
console.log('version=' + version);
