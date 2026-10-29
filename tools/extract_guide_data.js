#!/usr/bin/env node
// Run only on your own trusted HTML source; the selected JS section is evaluated.
const fs = require('fs');
const vm = require('vm');
const path = require('path');
const base = path.resolve(__dirname, '..');
const source = process.argv[2] || path.join(base, 'source/lure_guide.html');
const output = path.join(base, 'app/src/main/assets/guide_data.json');
const html = fs.readFileSync(source, 'utf8');
const start = html.indexOf('const GEARS=');
const end = html.indexOf('function attackTimeText', start);
if (start < 0 || end < 0) throw new Error('Source HTML catalogue not found');
const code = html.slice(start, end);
const catalog = JSON.parse(vm.runInNewContext(code + '\nJSON.stringify({gears:GEARS,fish:FISH,points:POINTS})', {}));
if (!Array.isArray(catalog.points) || !Array.isArray(catalog.fish) || !Array.isArray(catalog.gears))
  throw new Error('Invalid source catalogue');
fs.writeFileSync(output, JSON.stringify(catalog, null, 2) + '\n', 'utf8');
console.log(`Exported ${catalog.points.length} points, ${catalog.fish.length} fish, ${catalog.gears.length} gear sets.`);
