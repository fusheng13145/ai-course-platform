import fs from 'node:fs';
import path from 'node:path';
import { parse } from '@vue/compiler-sfc';

const root = process.argv[2] || '.';
const errs = [];

function walk(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === 'node_modules' || entry.name === 'dist' || entry.name === '.git') continue;
      walk(full);
    } else if (entry.name.endsWith('.vue')) {
      const src = fs.readFileSync(full, 'utf-8');
      try {
        const { errors } = parse(src, { filename: full });
        if (errors && errors.length) {
          for (const e of errors) {
            errs.push(`${full}\n    ${e.message}`);
          }
        }
      } catch (e) {
        errs.push(`${full}\n    THROW ${e.message}`);
      }
    }
  }
}

walk(root);
console.log(errs.length ? (`FOUND ${errs.length} error(s):\n` + errs.join('\n')) : 'ALL_OK');
