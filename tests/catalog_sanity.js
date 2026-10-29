#!/usr/bin/env node
const assert = require('assert');
const fs = require('fs');
const path = require('path');
const catalog = JSON.parse(fs.readFileSync(path.join(__dirname, '../app/src/main/assets/guide_data.json'), 'utf8'));
assert.strictEqual(catalog.points.length, 20);
assert.strictEqual(catalog.fish.length, 15);
assert.strictEqual(catalog.gears.length, 3);
assert.strictEqual(new Set(catalog.points.map(p => p.id)).size, 20);
assert.strictEqual(new Set(catalog.fish.map(f => f.id)).size, 15);
assert.deepStrictEqual([...new Set(catalog.points.map(p => p.region))], ['포항','경주','울산','부산']);
for (const point of catalog.points) {
  assert(point.lat >= 33 && point.lat <= 39 && point.lon >= 124 && point.lon <= 133);
  assert(Array.isArray(point.fish) && point.fish.length > 0);
}
console.log('PASS - source catalogue has 20 points, 15 species, and 3 gear sets');
