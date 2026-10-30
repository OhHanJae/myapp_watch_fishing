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
assert(tile.includes('backgroundImage(') && tile.includes('MarineTileArtwork.create(snap, resolvedTheme)'));
assert(!tile.includes('headingRecent') && !tile.includes('headingDegrees'));
assert(!tile.includes('N↑ 고정 · 풍향은 북 기준'));
assert(tile.includes('text("\\u00A0".layoutString') &&
    tile.includes('이전/미수신 예보'));
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
// Wind and waves are drawn inside MarineTileArtwork, not in the old ArcLine tree.
assert(artwork.includes('relativeWind(snap.windDegrees?.toDouble(), null)') &&
    artwork.includes('snap.waveHeightM'));
assert(artwork.includes('drawCompass(canvas, brush, palette)'));
const geometry = read('app/src/main/java/com/eastsea/badahannun/domain/MarineWaveGeometry.kt');
// Exactly one wave path in the tile and app; no center rectangle box.
assert(geometry.includes('fun sample(') && geometry.includes('centerX + (2f * t - 1f) * radius'));
assert(!artwork.includes('drawRoundRect(') && !artwork.includes('for (row in 0..2)'));
assert(artwork.includes('MarineWaveGeometry.sample(') && artwork.includes('canvas.drawPath(wavePath, brush)'));
assert(ui.includes('val wavePoints = MarineWaveGeometry.sample('));
assert(!ui.includes('for (row in 0..2)') && /drawPath\(\s*wavePath/.test(ui));
// No cardinal letter labels in either renderer; colored ticks instead.
assert(!ui.includes('nativeCanvas.drawText('));
assert(!artwork.includes('canvas.drawText(label'));
assert(ui.includes('0 -> Color(0xFFDF2934)') &&
    ui.includes('18 -> Color(0xFF246ADC)'));
assert(ui.includes('9, 27 -> Color.Black'));
assert(artwork.includes('0 -> Color.rgb(223, 41, 52)') &&
    artwork.includes('18 -> Color.rgb(36, 106, 220)') &&
    artwork.includes('9, 27 -> Color.BLACK'));
// Full viewport is the FIRST HOME item, with location reachable by scrolling.
const home = ui.slice(ui.indexOf('Screen.HOME -> {'), ui.indexOf('Screen.FORECAST -> {'));
assert(home.indexOf('SeaAtGlance(') > -1 &&
    home.indexOf('SeaAtGlance(') < home.indexOf('title = "지역 · 포인트"'));
assert(!home.includes('PageHeading("바다한눈")'));
assert(ui.includes('val homeDialHeight = (LocalConfiguration.current.screenHeightDp - 12)'));
assert(ui.includes('Modifier.fillMaxWidth().height(viewportHeight)'));
// Bright wind trails are drawn as real strokes and clipped to the compass.
assert(ui.includes('clipPath(clipCircle)') &&
    ui.includes('windColor.copy(alpha = opacity)'));
assert(ui.includes('strokeWidth = 6.dp.toPx()') &&
    ui.includes('windTrail = Color(0xFF8BE8FF)'));
assert(read('app/src/test/java/com/eastsea/badahannun/domain/MarineWaveGeometryTest.kt')
    .includes('waveTouchesLeftAndRightCompassEdges'));
assert(artwork.includes('radians(bearing)') &&
    !artwork.includes('radians(bearing - heading)'));
assert(read('app/src/main/java/com/eastsea/badahannun/domain/WatchVisualMath.kt').includes('fun runs('));
assert(math.includes('return null') && math.includes('tideStage('));
assert(manifest.includes('androidx.wear.tiles.action.BIND_TILE_PROVIDER'));
assert(manifest.includes('android.hardware.type.watch') && manifest.includes('android:required="true"'));
assert(manifest.includes('com.google.android.wearable.standalone'));
const runConfig = read('.idea/runConfigurations/Wear_BadaHannun.xml');
assert(runConfig.includes('type="AndroidRunConfigurationType"'));
assert(runConfig.includes('BadaHannunWearOS.app.main'));
assert(runConfig.includes('value="default_activity"'));
const pointOrder = read('app/src/main/java/com/eastsea/badahannun/domain/PointSelection.kt');
assert(pointOrder.includes('listOf("울산", "경주", "포항", "부산")'));
assert(pointOrder.includes('listOf("ulsan-seuldo", "ulsan-jujeon", "ulsan-dangsa")'));
const appearance = read('app/src/main/java/com/eastsea/badahannun/domain/AppearanceSettings.kt');
assert(appearance.includes('FOLLOW_APP("follow_app", "앱 설정에 따름")'));
assert(appearance.includes('fun resolveTileTheme('));
assert(main.includes('onAppColorModeChange =') && main.includes('onTileColorModeChange ='));
assert(main.includes('TileSnapshotStore.requestUpdate(this)'));
assert(ui.includes('Screen.SETTINGS') && ui.includes('CompositionLocalProvider('));
assert(ui.includes('LightMarinePalette') && ui.includes('DarkMarinePalette'));
assert(build.includes('androidx.wear.tiles:tiles:1.6.2'));
console.log('PASS: clean tile legend, fullscreen scroll-first compass, red/blue/black cardinal ticks, visible wind trails');
