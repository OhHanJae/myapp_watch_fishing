// Lightweight repository guard when an Android SDK is unavailable locally.
// The GitHub Actions job executes the real Android build and Kotlin unit tests.
const fs = require('node:fs');
const assert = require('node:assert/strict');
const path = require('node:path');
const project = path.join(__dirname, '..');
const read = file => fs.readFileSync(path.join(project, file), 'utf8');
const ui = read('app/src/main/java/com/eastsea/badahannun/ui/WearFishingApp.kt');
const main = read('app/src/main/java/com/eastsea/badahannun/MainActivity.kt');
const tile = read('app/src/main/java/com/eastsea/badahannun/tiles/MarineSummaryTileService.kt');
const repository = read('app/src/main/java/com/eastsea/badahannun/data/ForecastRepository.kt');
const math = read('app/src/main/java/com/eastsea/badahannun/domain/MarineTimelineMath.kt');
const manifest = read('app/src/main/AndroidManifest.xml');
const build = read('app/build.gradle.kts');
const catalog = JSON.parse(read('app/src/main/assets/guide_data.json'));
assert.equal(catalog.points.length, 20);
assert.deepEqual([...new Set(catalog.points.map(p => p.region))].sort(), ['경주','부산','울산','포항']);
assert(ui.includes('TransformingLazyColumn(') && ui.includes('ScreenScaffold(') && ui.includes('AppScaffold('));
assert(ui.includes('NavigationCard("내 위치로"'));
assert(ui.includes('onSelectPoint(candidate)'));
assert(ui.includes('screen = Screen.REGIONS') && ui.includes('screen = Screen.POINTS'));
assert(main.includes('manualPointId = null') && main.includes('manualPointId = p.id'));
assert(main.includes('TileSnapshotStore.write(') && main.includes('TileSnapshotStore.clear('));
assert(tile.includes('Material3TileService()') && tile.includes('setFreshnessIntervalMillis('));
assert(tile.includes('MAX_AGE_MS') && tile.includes('report.stale'));
assert(tile.includes('backgroundImage(') && tile.includes('MarineTileArtwork.create(snap)'));
assert(tile.includes('headingRecent') && tile.includes('이전/미수신 예보'));
const artwork = read('app/src/main/java/com/eastsea/badahannun/tiles/MarineTileArtwork.kt');
assert(artwork.includes('Bitmap.createBitmap(') &&
    artwork.includes('Bitmap.CompressFormat.PNG') &&
    artwork.includes('InlineImageResource.Builder()') &&
    artwork.includes('setInlineResource(image)'));
assert(artwork.includes('drawWind(') && artwork.includes('drawWaves('));
assert(artwork.includes('snapshot') || artwork.includes('snap'));
assert(tile.includes('windDir') && tile.includes('tidePhase') && tile.includes('seaTemp'));
assert(repository.includes('forecast = buildTimeline(') && repository.includes('(0..24).map'));
assert(repository.includes('wave_height,wave_period,wave_direction,sea_surface_temperature,sea_level_height_msl'));
assert(ui.includes('Screen.FORECAST') && ui.includes('Screen.TIME_DETAIL'));
assert(ui.includes('TrendChart(') && ui.includes('ForecastDetailCard('));
assert(ui.includes('FishLeaderChart(') && ui.includes('WatchVisualMath.dateTimeLabel(step.time)'));
assert(ui.includes('rememberInfiniteTransition(') && ui.includes('relativeWind('));
assert(ui.includes('onCompassVisible(screen == Screen.HOME)') && !ui.includes('Screen.COMPASS'));
assert(tile.includes('windBearing') && tile.includes('waveHeightM'));
assert(read('app/src/main/java/com/eastsea/badahannun/domain/WatchVisualMath.kt').includes('fun runs('));
assert(math.includes('return null') && math.includes('tideStage('));
assert(manifest.includes('androidx.wear.tiles.action.BIND_TILE_PROVIDER'));
assert(manifest.includes('android.hardware.type.watch') && manifest.includes('android:required="true"'));
assert(manifest.includes('com.google.android.wearable.standalone'));
const runConfig = read('.idea/runConfigurations/Wear_BadaHannun.xml');
assert(runConfig.includes('type="AndroidRunConfigurationType"'));
assert(runConfig.includes('BadaHannunWearOS.app.main'));
assert(runConfig.includes('value="default_activity"'));
assert(build.includes('androidx.wear.tiles:tiles:1.6.2'));
console.log('PASS: full-color tile wind/wave PNG registered inline, visible fallback, live home compass, forecast graphs');
