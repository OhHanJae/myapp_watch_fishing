// Security and release-mode guard; CI also compiles and tests the Android app.
const fs = require('node:fs');
const assert = require('node:assert/strict');
const path = require('node:path');
const read = (file) => fs.readFileSync(path.join(__dirname, '..', file), 'utf8');
const w = read('.github/workflows/play-internal.yml');
const gradle = read('app/build.gradle.kts');
const guide = read('PLAY_INTERNAL_SETUP.md');
const ignore = read('.gitignore');

assert(w.includes('name: Wear OS - Google Play Internal'));
assert(w.includes('push:\n    branches: [master]'));
assert(w.includes("github.ref == 'refs/heads/master'"));
assert(w.includes("vars.PLAY_DEPLOY_ENABLED == 'true'"));
assert(w.includes('workflow_dispatch:'));
assert(w.includes('bundle_only') && w.includes('publish_internal'));
assert(w.includes('environment: google-play-internal'));
assert(w.includes('concurrency:'));
assert(w.includes('permissions:\n  contents: read'));
assert(/uses: r0adkll\/upload-google-play@[0-9a-f]{40}/.test(w));
assert(w.includes('tracks: internal') && !w.includes('tracks: production'));
assert(w.includes('status: completed') && w.includes('packageName: com.eastsea.badahannun'));
assert(w.includes("github.event_name == 'push' || inputs.mode == 'publish_internal'"));
assert(w.includes('serviceAccountJsonPlainText: ${{ secrets.PLAY_SERVICE_ACCOUNT_JSON }}'));
for (const name of [
  'PLAY_UPLOAD_KEYSTORE_B64', 'PLAY_UPLOAD_STORE_PASSWORD',
  'PLAY_UPLOAD_KEY_ALIAS', 'PLAY_UPLOAD_KEY_PASSWORD'
]) assert(w.includes('secrets.' + name));
assert(w.includes('base64 --decode') && w.includes('PLAY_UPLOAD_KEYSTORE_PATH'));
assert(w.includes('gradle :app:testDebugUnitTest :app:bundleRelease'));
assert(w.includes('jarsigner -verify'));
assert(w.includes('bada-hannun-play-signed-aab'));
assert(w.includes('rm -f "$RUNNER_TEMP/play-upload.jks"'));
assert(gradle.includes('versionCode = providers.gradleProperty("playVersionCode")'));
assert(gradle.includes('signingConfigs.getByName("playUpload")'));
assert(gradle.includes('providers.environmentVariable("PLAY_UPLOAD_KEYSTORE_PATH")'));
assert(ignore.includes('*.jks') && ignore.includes('*service-account*.json'));
assert(guide.includes('PLAY_DEPLOY_ENABLED') && guide.includes('Play App Signing'));
console.log('PASS: gated internal Google Play AAB release, secret-backed signing, and bootstrap mode');
