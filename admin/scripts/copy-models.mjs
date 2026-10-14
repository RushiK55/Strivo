// Copies the 3D body (models/anatomy at the repository root) into public/models/anatomy, where Next serves it from. It runs
// before `npm run dev` and `npm run build`, so the dashboard always ships the models the repository holds; the copy itself is
// not committed (public/models is ignored). The models are not duplicated in git.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const from = path.join(here, '..', '..', 'models', 'anatomy');
const to = path.join(here, '..', 'public', 'models', 'anatomy');
const FILES = ['male.glb', 'female.glb', 'muscles.json', 'ATTRIBUTION.md'];

const missing = FILES.filter((file) => !fs.existsSync(path.join(from, file)));
if (missing.length) {
  console.error(`The 3D models are missing from ${from}: ${missing.join(', ')}. Build them (tools/anatomy) or restore them from git.`);
  process.exit(1);
}
fs.rmSync(to, { recursive: true, force: true });
fs.mkdirSync(to, { recursive: true });
for (const file of FILES) fs.copyFileSync(path.join(from, file), path.join(to, file));
console.log(`copied ${FILES.length} model files to public/models/anatomy`);
