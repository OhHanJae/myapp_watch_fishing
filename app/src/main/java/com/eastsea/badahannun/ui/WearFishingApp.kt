package com.eastsea.badahannun.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.eastsea.badahannun.data.FishingPoint
import com.eastsea.badahannun.data.ForecastStep
import com.eastsea.badahannun.data.GuideCatalog
import com.eastsea.badahannun.data.SeaReport
import com.eastsea.badahannun.domain.AppColorMode
import com.eastsea.badahannun.domain.TileColorMode
import com.eastsea.badahannun.domain.MarineWaveGeometry
import com.eastsea.badahannun.domain.FishingLogic
import com.eastsea.badahannun.domain.PointSelection
import com.eastsea.badahannun.domain.TideCalculator
import com.eastsea.badahannun.domain.WatchVisualMath
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.max
import kotlin.math.sin

/** Glanceable watch screens, with a separate chart/timeline destination. */
private enum class Screen { HOME, FORECAST, TIME_DETAIL, FISH, REGIONS, POINTS, SETTINGS }
private enum class Metric(val title: String) {
    WAVE("파고"), WIND("바람"), AIR("기온"), SEA("수온"), LEVEL("조위")
}

/** Scoped colors switch every watch screen without changing other app components. */
private data class MarinePalette(
    val background: Color, val card: Color, val accent: Color,
    val foreground: Color, val secondary: Color, val warning: Color,
    val error: Color, val selected: Color, val button: Color,
    val compassRing: Color, val graphGrid: Color,
    val windTrail: Color, val windHalo: Color
)
private val DarkMarinePalette = MarinePalette(
    background = Color(0xFF050D16), card = Color(0xFF102333),
    accent = Color(0xFF63DFD4), foreground = Color(0xFFF0F8FF),
    secondary = Color(0xFFA1B6C5), warning = Color(0xFFFFCD82),
    error = Color(0xFFFF858B), selected = Color(0xFF235C65),
    button = Color(0xFF234B5D), compassRing = Color(0xFF2A5063),
    graphGrid = Color(0xFF294454),
    windTrail = Color(0xFF8BE8FF), windHalo = Color(0xFF12394B)
)
private val LightMarinePalette = MarinePalette(
    background = Color(0xFFF6FBFF), card = Color(0xFFE3F0F6),
    accent = Color(0xFF006D73), foreground = Color(0xFF173344),
    secondary = Color(0xFF536D7E), warning = Color(0xFF875000),
    error = Color(0xFFA61F37), selected = Color(0xFFB8E4E8),
    button = Color(0xFFC6E5F0), compassRing = Color(0xFF86B5C6),
    graphGrid = Color(0xFFB0C9D3),
    windTrail = Color(0xFF0073A9), windHalo = Color(0xFFF5FCFF)
)
private val LocalMarinePalette = compositionLocalOf { DarkMarinePalette }
private val Background: Color
    @Composable get() = LocalMarinePalette.current.background
private val CardBg: Color
    @Composable get() = LocalMarinePalette.current.card
private val Accent: Color
    @Composable get() = LocalMarinePalette.current.accent
private val White: Color
    @Composable get() = LocalMarinePalette.current.foreground
private val Gray: Color
    @Composable get() = LocalMarinePalette.current.secondary
private val Yellow: Color
    @Composable get() = LocalMarinePalette.current.warning
private val Red: Color
    @Composable get() = LocalMarinePalette.current.error
private val SelectedChip: Color
    @Composable get() = LocalMarinePalette.current.selected
private val ButtonBg: Color
    @Composable get() = LocalMarinePalette.current.button

@Composable
fun WearFishingApp(
    catalog: GuideCatalog,
    point: FishingPoint,
    report: SeaReport?,
    refreshing: Boolean,
    gpsStatus: String,
    isGpsMode: Boolean,
    gpsDistanceKm: Double?,
    heading: Float?,
    compassTrueNorth: Boolean,
    compassAvailable: Boolean,
    appColorMode: AppColorMode,
    tileColorMode: TileColorMode,
    onAppColorModeChange: (AppColorMode) -> Unit,
    onTileColorModeChange: (TileColorMode) -> Unit,
    onRefresh: () -> Unit,
    onSelectPoint: (FishingPoint) -> Unit,
    onUseGps: () -> Unit,
    onCompassVisible: (Boolean) -> Unit
) {
    var screen by remember { mutableStateOf(Screen.HOME) }
    var selectedRegion by remember { mutableStateOf("울산") }
    var metric by remember { mutableStateOf(Metric.WAVE) }
    var selectedStep by remember { mutableStateOf(0) }
    var now by remember { mutableStateOf(ZonedDateTime.now(FishingLogic.KST)) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = ZonedDateTime.now(FishingLogic.KST)
        }
    }
    DisposableEffect(screen) {
        onCompassVisible(screen == Screen.HOME)
        onDispose { if (screen == Screen.HOME) onCompassVisible(false) }
    }
    BackHandler(screen != Screen.HOME) {
        screen = when (screen) {
            Screen.POINTS -> Screen.REGIONS
            Screen.TIME_DETAIL -> Screen.FORECAST
            else -> Screen.HOME
        }
    }

    // The same saved app palette is used when the tile option follows app settings.
    val palette = if (appColorMode == AppColorMode.LIGHT) LightMarinePalette else DarkMarinePalette
    // The HOME's FIRST list item occupies the visible watch viewport. The
    // location selector only appears after swiping down, not above the dial.
    val homeDialHeight = (LocalConfiguration.current.screenHeightDp - 12)
        .coerceAtLeast(174).dp
    CompositionLocalProvider(LocalMarinePalette provides palette) {
    // Keep list state per destination. TransformingLazyColumn handles Wear rotary/bezel
    // events with ScreenScaffold and AppScaffold (including scroll indicator and focus).
    MaterialTheme {
        AppScaffold(modifier = Modifier.background(Background)) {
            key(screen, selectedRegion) {
                val state = rememberTransformingLazyColumnState()
                ScreenScaffold(scrollState = state) { padding ->
                    TransformingLazyColumn(
                        state = state,
                        contentPadding = padding,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        when (screen) {
                            Screen.HOME -> {
                                // Deliberately no header/card above the full-watch dial.
                                item {
                                    val leader = FishingLogic.topFish(catalog.fish, point,
                                        report?.takeUnless { it.stale }, now)
                                        .firstOrNull()?.fish?.name ?: "--"
                                    SeaAtGlance(
                                        report = report,
                                        now = now,
                                        heading = heading,
                                        compassTrueNorth = compassTrueNorth,
                                        compassAvailable = compassAvailable,
                                        topFish = leader,
                                        pointName = point.name,
                                        viewportHeight = homeDialHeight
                                    )
                                }
                                // The very next item is the location selector: swipe down.
                                item {
                                    NavigationCard(
                                        title = "지역 · 포인트",
                                        caption = when {
                                            !isGpsMode -> "${point.name} · 직접 선택"
                                            gpsDistanceKm == null -> "${point.name} · GPS 확인 중"
                                            else -> "${point.name} · 내 위치"
                                        },
                                        onClick = {
                                            selectedRegion = point.region
                                            screen = Screen.REGIONS
                                        }
                                    )
                                }
                                item {
                                    val condition = if (report?.stale == true) "이전 예보"
                                        else if (report == null) "예보 대기" else "모델 예보"
                                    val farFromPoint = isGpsMode && gpsDistanceKm != null &&
                                        gpsDistanceKm > 30.0
                                    Text(
                                        if (farFromPoint) "가까운 포인트와 ${display(gpsDistanceKm, 0)}km · 현장 확인"
                                        else "$condition · 공식 관측값 아님",
                                        color = if (farFromPoint || report?.stale == true)
                                            Yellow else Gray,
                                        textAlign = TextAlign.Center,
                                        fontSize = 10.sp,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                    )
                                }
                                item {
                                    NavigationCard("12시간 예보 · 그래프", "30분마다 · 바람 / 파고 / 물 흐름", onClick = {
                                        metric = Metric.WAVE
                                        screen = Screen.FORECAST
                                    })
                                }
                                item {
                                    NavigationCard("추천 어종 TOP 3", "계절 · 시간 · 바다 상태",
                                        onClick = { screen = Screen.FISH })
                                }
                                item {
                                    WatchButton(if (refreshing) "갱신 중..." else "예보 새로고침",
                                        !refreshing, onRefresh)
                                }
                                item {
                                    NavigationCard("화면 · 타일 테마",
                                        "앱 ${appColorMode.label} · 타일 ${tileColorMode.label}",
                                        onClick = { screen = Screen.SETTINGS })
                                }
                            }

                            Screen.FORECAST -> {
                                val leads = report?.forecast?.map { step ->
                                    val model = report?.takeUnless { it.stale }
                                        ?.copy(weather = step.weather, ocean = step.ocean)
                                    FishingLogic.topFish(catalog.fish, point, model,
                                        step.time.atZone(FishingLogic.KST))
                                        .firstOrNull()?.fish?.name ?: "--"
                                }.orEmpty()
                                item { PageHeading("12시간 예보", "한국시간 · 30분 간격") }
                                if (report == null || report.forecast.isEmpty()) {
                                    item { Text("예보를 아직 받지 못했어요", color = Yellow, fontSize = 12.sp) }
                                    item { WatchButton("새로고침", !refreshing, onRefresh) }
                                } else {
                                    item { MetricTabs(metric) { metric = it } }
                                    item { TrendChart(report.forecast, metric) }
                                    item { FishLeaderChart(report.forecast, leads) }
                                    item {
                                        Text("30분 값은 시간별 예보 보간 · 조위/물 흐름은 모델 추정",
                                            color = Gray, fontSize = 9.sp, textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                                    }
                                    items(report.forecast.size) { index ->
                                        val step = report.forecast[index]
                                        ForecastRow(step, leads.getOrElse(index) { "--" }) {
                                            selectedStep = index
                                            screen = Screen.TIME_DETAIL
                                        }
                                    }
                                }
                                item { WatchButton("홈으로", onClick = { screen = Screen.HOME }) }
                            }

                            Screen.TIME_DETAIL -> {
                                val step = report?.forecast?.getOrNull(selectedStep)
                                if (step == null) {
                                    item { PageHeading("예보 없음") }
                                } else {
                                    item { PageHeading(step.time.format(DateTimeFormatter.ofPattern("MM/dd HH:mm")), "시간별 모델 · 30분 보간") }
                                    item { ForecastDetailCard(step) }
                                    val top = FishingLogic.topFish(
                                        catalog.fish, point,
                                        report?.takeUnless { it.stale }?.copy(weather = step.weather, ocean = step.ocean),
                                        step.time.atZone(FishingLogic.KST)
                                    )
                                    item {
                                        NavigationCard("추천 ${top.joinToString(" · ") { it.fish.name }}",
                                            "포인트·계절·시간·예보 기준", enabled = false, onClick = {})
                                    }
                                    item { Text("※ 물때는 간이 계산, 조위·흐름은 모델 추정입니다.",
                                        color = Gray, fontSize = 9.sp, textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()) }
                                }
                                item { WatchButton("12시간 목록", onClick = { screen = Screen.FORECAST }) }
                            }

                            Screen.FISH -> {
                                item { PageHeading("어종 TOP 3", point.name) }
                                val scored = FishingLogic.topFish(
                                    catalog.fish, point,
                                    report?.takeUnless { it.stale }, now
                                )
                                items(scored.size) { index ->
                                    val entry = scored[index]
                                    val lure = entry.fish.lures.firstOrNull().orEmpty()
                                    NavigationCard(
                                        title = "${index + 1}  ${entry.fish.name}",
                                        caption = if (lure.isNotBlank()) "추천 루어 · $lure" else "시즌·시간 기준 추천",
                                        onClick = { /* display-only: this screen is intentionally one glance */ },
                                        enabled = false
                                    )
                                }
                                item {
                                    Text(
                                        "추천은 예상 조과가 아닌 조건 적합도",
                                        color = Gray, fontSize = 10.sp, textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                                    )
                                }
                                item { WatchButton("홈으로", onClick = { screen = Screen.HOME }) }
                            }

                            Screen.REGIONS -> {
                                item { PageHeading("어디서 낚시해?", "현재 위치가 기본값") }
                                item {
                                    NavigationCard("내 위치로", gpsStatus, onClick = {
                                        onUseGps()
                                        screen = Screen.HOME
                                    })
                                }
                                val regions = PointSelection.regions(catalog.points)
                                items(regions.size) { index ->
                                    val region = regions[index]
                                    NavigationCard(
                                        title = region,
                                        caption = "포인트 ${catalog.points.count { it.region == region }}곳 선택",
                                        onClick = { selectedRegion = region; screen = Screen.POINTS }
                                    )
                                }
                                item { WatchButton("돌아가기", onClick = { screen = Screen.HOME }) }
                            }

                            Screen.POINTS -> {
                                item { PageHeading("$selectedRegion 포인트", "원하는 장소를 누르세요") }
                                item {
                                    WatchButton("내 위치로", onClick = {
                                        onUseGps()
                                        screen = Screen.HOME
                                    })
                                }
                                val points = PointSelection.inRegion(catalog.points, selectedRegion)
                                items(points.size) { index ->
                                    val candidate = points[index]
                                    NavigationCard(
                                        title = if (candidate.id == point.id && !isGpsMode)
                                            "✓ ${candidate.name}" else candidate.name,
                                        caption = candidate.types.take(2).joinToString(" · "),
                                        onClick = {
                                            onSelectPoint(candidate)
                                            screen = Screen.HOME
                                        }
                                    )
                                }
                                item { WatchButton("지역 목록", onClick = { screen = Screen.REGIONS }) }
                            }

                            Screen.SETTINGS -> {
                                item { PageHeading("테마 설정", "앱 화면과 타일 색상을 따로 선택") }
                                item {
                                    Text("앱 화면", color = Accent, fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                                }
                                items(AppColorMode.entries.size) { index ->
                                    val choice = AppColorMode.entries[index]
                                    NavigationCard(
                                        title = if (choice == appColorMode) "✓ ${choice.label}" else choice.label,
                                        caption = "앱 전체 화면에 적용",
                                        onClick = { onAppColorModeChange(choice) }
                                    )
                                }
                                item {
                                    Text("워치 타일", color = Accent, fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                                }
                                items(TileColorMode.entries.size) { index ->
                                    val choice = TileColorMode.entries[index]
                                    NavigationCard(
                                        title = if (choice == tileColorMode) "✓ ${choice.label}" else choice.label,
                                        caption = when (choice) {
                                            TileColorMode.FOLLOW_APP -> "앱 화면 테마 변경 시 같이 적용"
                                            TileColorMode.LIGHT -> "타일만 밝은 색상"
                                            TileColorMode.DARK -> "타일만 어두운 색상"
                                        },
                                        onClick = { onTileColorModeChange(choice) }
                                    )
                                }
                                item {
                                    Text("타일 나침반은 N↑ 고정 · 풍향만 북쪽 기준",
                                        color = Gray, fontSize = 10.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                                }
                                item { WatchButton("홈으로", onClick = { screen = Screen.HOME }) }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun PageHeading(title: String, subtitle: String? = null) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = White)
        if (subtitle != null) {
            Text(subtitle, fontSize = 11.sp, color = Gray,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MetricTabs(selected: Metric, onSelect: (Metric) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Metric.entries.forEach { option ->
            Box(Modifier.weight(1f).height(32.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(if (selected == option) SelectedChip else CardBg)
                .clickable { onSelect(option) }, contentAlignment = Alignment.Center) {
                Text(option.title, fontSize = 9.sp,
                    color = if (selected == option) Accent else White)
            }
        }
    }
}

private fun metricValue(step: ForecastStep, metric: Metric): Double? = when (metric) {
    Metric.WAVE -> step.ocean?.waveM
    Metric.WIND -> step.weather?.windMs
    Metric.AIR -> step.weather?.temperatureC
    Metric.SEA -> step.ocean?.surfaceTemperatureC
    Metric.LEVEL -> step.ocean?.seaLevelMslM
}

@Composable
private fun TrendChart(steps: List<ForecastStep>, metric: Metric) {
    val values = steps.map { metricValue(it, metric)?.takeIf(Double::isFinite) }
    val available = values.filterNotNull()
    val unit = when (metric) {
        Metric.WAVE, Metric.LEVEL -> "m"
        Metric.WIND -> "m/s"
        Metric.AIR, Metric.SEA -> "°C"
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)
        .clip(RoundedCornerShape(18.dp)).background(CardBg)
        .padding(horizontal = 9.dp, vertical = 11.dp)) {
        Text("${metric.title} · ${if (available.isEmpty()) "예보 없음" else "${display(available.minOrNull())}~${display(available.maxOrNull())}$unit"}",
            color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        val plotAccent = Accent
        val plotWarning = Yellow
        val plotGrid = LocalMarinePalette.current.graphGrid
        Canvas(Modifier.fillMaxWidth().height(102.dp)) {
            val left = 8f
            val right = size.width - 8f
            val top = 8f
            val bottom = size.height - 8f
            val low = (available.minOrNull() ?: 0.0).toFloat()
            val high = (available.maxOrNull() ?: 1.0).toFloat()
            val spread = (high - low).coerceAtLeast(0.25f)
            val minimum = low - spread * 0.16f
            val maximum = high + spread * 0.16f
            // Light horizontal guides, no minuscule axis labels on a round screen.
            for (fraction in listOf(0f, 0.5f, 1f)) {
                val y = top + fraction * (bottom - top)
                drawLine(plotGrid, Offset(left, y), Offset(right, y), strokeWidth = 1f)
            }
            fun xy(index: Int, value: Double): Offset = Offset(
                left + (right - left) * index / max(1, values.lastIndex),
                bottom - ((value.toFloat() - minimum) / (maximum - minimum)) * (bottom - top)
            )
            // Do not connect gaps across missing model values.
            for (index in 1 until values.size) {
                val a = values[index - 1]
                val b = values[index]
                if (a != null && b != null) {
                    drawLine(plotAccent, xy(index - 1, a), xy(index, b), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                }
            }
            for (index in values.indices step 6) {
                values[index]?.let { drawCircle(plotWarning, radius = 2.5.dp.toPx(), center = xy(index, it)) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(steps.firstOrNull()?.time?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "--",
                fontSize = 9.sp, color = Gray)
            Text(steps.getOrNull(steps.size / 2)?.time?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "--",
                fontSize = 9.sp, color = Gray)
            Text(steps.lastOrNull()?.time?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "--",
                fontSize = 9.sp, color = Gray)
        }
        Text(WatchVisualMath.dateRangeLabel(steps.map { it.time }),
            color = Gray, fontSize = 9.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
    }
}


/**
 * Categorical 30-minute TOP-1 timeline. The colored segments indicate the
 * leading species, not a measured bite probability or catch likelihood.
 */
@Composable
private fun FishLeaderChart(steps: List<ForecastStep>, leaders: List<String>) {
    val colors = listOf(Accent, Yellow, Color(0xFF7DABFF),
        Color(0xFFFF93A3), Color(0xFFA9C88A), Color(0xFFD2A2ED))
    val mutedPlot = Gray
    val tickPlot = White
    val species = leaders.filter { it != "--" }.distinct()
    val runs = WatchVisualMath.runs(leaders)
    Column(
        Modifier.fillMaxWidth().padding(vertical = 5.dp)
            .clip(RoundedCornerShape(18.dp)).background(CardBg)
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Text("시간별 추천 어종 1순위", color = White,
            fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("30분별 순위 변화 · 조과 확률 아님",
            color = Gray, fontSize = 9.sp)
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(36.dp)) {
            val count = leaders.size
            if (count > 0) {
                val width = size.width / count
                runs.forEach { run ->
                    val color = if (run.name == "--") mutedPlot.copy(alpha = 0.25f)
                        else colors[(species.indexOf(run.name).coerceAtLeast(0)) % colors.size]
                    drawRect(color, topLeft = Offset(width * run.startIndex, 3.dp.toPx()),
                        size = Size(width * (run.endExclusive - run.startIndex), 19.dp.toPx()))
                }
                for (at in listOf(0, count / 2, count - 1)) {
                    val x = (at + 0.5f) * width
                    drawLine(tickPlot.copy(alpha = 0.72f),
                        Offset(x, 0f), Offset(x, 25.dp.toPx()), strokeWidth = 1.dp.toPx())
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(steps.firstOrNull()?.time?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "--",
                color = Gray, fontSize = 9.sp)
            Text(steps.getOrNull(steps.size / 2)?.time?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "--",
                color = Gray, fontSize = 9.sp)
            Text(steps.lastOrNull()?.time?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "--",
                color = Gray, fontSize = 9.sp)
        }
        species.forEachIndexed { i, name ->
            Row(Modifier.fillMaxWidth().padding(top = 3.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(colors[i % colors.size], CircleShape))
                Text("  $name", color = White, fontSize = 10.sp)
            }
        }
        Text(WatchVisualMath.dateRangeLabel(steps.map { it.time }),
            color = Gray, fontSize = 9.sp,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

@Composable
private fun ForecastRow(step: ForecastStep, topFish: String, onClick: () -> Unit) {
    val weather = step.weather
    val sea = step.ocean
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)
        .clip(RoundedCornerShape(17.dp)).background(CardBg).clickable(onClick = onClick)
        .padding(horizontal = 13.dp, vertical = 9.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(WatchVisualMath.dateTimeLabel(step.time),
                color = Accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text("${display(weather?.temperatureC, 0)}° ${FishingLogic.weatherLabel(weather?.weatherCode)}",
                color = White, fontSize = 11.sp, maxLines = 1)
        }
        Spacer(Modifier.height(3.dp))
        Text("파고 ${display(sea?.waveM)}m  ·  ${FishingLogic.windDirection(weather?.windDirectionDegrees)} ${display(weather?.windMs)}m/s",
            color = White, fontSize = 10.sp, maxLines = 1)
        Text("수온 ${display(sea?.surfaceTemperatureC)}° · ${step.tidePhase ?: "물 흐름 --"} · $topFish",
            color = Gray, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ForecastDetailCard(step: ForecastStep) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
        .background(CardBg).padding(horizontal = 13.dp, vertical = 12.dp)) {
        val w = step.weather
        val o = step.ocean
        ForecastValue("날씨 · 기온", "${FishingLogic.weatherLabel(w?.weatherCode)} ${display(w?.temperatureC)}°C")
        ForecastValue("수온", "${display(o?.surfaceTemperatureC)}°C (모델)")
        ForecastValue("바람", "${FishingLogic.windDirection(w?.windDirectionDegrees)} ${display(w?.windMs)}m/s")
        ForecastValue("파고", "${display(o?.waveM)}m · ${display(o?.wavePeriodSeconds)}초 주기")
        ForecastValue("물때*", TideCalculator.onDate(step.time.toLocalDate()).name)
        ForecastValue("물 흐름", step.tidePhase?.let { "$it (모델)" } ?: "예보 부족")
    }
}

@Composable
private fun ForecastValue(title: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Gray, fontSize = 10.sp, modifier = Modifier.weight(0.44f))
        Text(value, color = White, fontSize = 11.sp, modifier = Modifier.weight(0.56f),
            textAlign = TextAlign.End)
    }
}


/**
 * Home's tile-style live compass. The sensor updates only while Home is in
 * foreground; waves and wind are visual effects driven by model values.
 * Wind from-angle is converted to relative watch bearing.
 */
/**
 * First HOME viewport: full-width, dial-sized information without an
 * introductory title or a surrounding card. Region selection is the next
 * scroll item. Wind only animates when the model actually supplies direction
 * and non-calm speed; waves remain model-driven, never an observed animation.
 */
@Composable
private fun SeaAtGlance(
    report: SeaReport?,
    now: ZonedDateTime,
    heading: Float?,
    compassTrueNorth: Boolean,
    compassAvailable: Boolean,
    topFish: String,
    pointName: String,
    viewportHeight: Dp
) {
    val weather = report?.weather
    val ocean = report?.ocean
    val speed = (weather?.windMs ?: 0.0).coerceIn(0.0, 20.0).toFloat()
    val motion = rememberInfiniteTransition(label = "visible-wind-and-one-wave")
    val phase by motion.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing)),
        label = "marine-animation-phase"
    )
    val bearing = WatchVisualMath.relativeWind(weather?.windDirectionDegrees, heading)
    val palette = LocalMarinePalette.current
    val windColor = palette.windTrail
    val windHalo = palette.windHalo
    val ringColor = palette.compassRing
    val minorTick = Gray
    val waveRising = Accent

    Box(
        modifier = Modifier.fillMaxWidth().height(viewportHeight),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension * 0.475f

            // Large, high-contrast wind trails in the ring, moving towards
            // the downwind bearing, with a soft contrasting outline.
            if (bearing != null && speed > 0.3f) {
                val radians = Math.toRadians((bearing + 90f).toDouble())
                val dx = cos(radians).toFloat()
                val dy = sin(radians).toFloat()
                val px = -dy
                val py = dx
                val clipCircle = Path().apply {
                    addOval(Rect(
                        center.x - radius * 0.93f, center.y - radius * 0.93f,
                        center.x + radius * 0.93f, center.y + radius * 0.93f
                    ))
                }
                clipPath(clipCircle) {
                    for (lane in -3..3) {
                        val p = (phase * (0.70f + speed / 12f) +
                            (lane + 3) * 0.145f) % 1f
                        val along = (p * 2f - 1f) * radius * 0.82f
                        val side = lane * radius * 0.19f
                        val mx = center.x + dx * along + px * side
                        val my = center.y + dy * along + py * side
                        val trailLength = (18f + speed * 1.5f).dp.toPx()
                            .coerceAtMost(radius * 0.49f)
                        val from = Offset(
                            mx - dx * trailLength / 2f,
                            my - dy * trailLength / 2f
                        )
                        val to = Offset(
                            mx + dx * trailLength / 2f,
                            my + dy * trailLength / 2f
                        )
                        drawLine(
                            windHalo.copy(alpha = 0.76f), from, to,
                            strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round
                        )
                        val opacity = (0.78f + speed * 0.009f -
                            kotlin.math.abs(lane) * 0.035f).coerceIn(0.65f, 0.94f)
                        drawLine(
                            windColor.copy(alpha = opacity), from, to,
                            strokeWidth = (2.8f + speed * 0.065f).dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            // Single continuous wave, touching both horizontal ring edges.
            val wavePoints = MarineWaveGeometry.sample(
                centerX = center.x, centerY = center.y,
                radius = radius * 0.99f, waveHeightM = ocean?.waveM,
                phaseTurns = phase
            )
            val wavePath = Path().apply {
                moveTo(wavePoints.first().x, wavePoints.first().y)
                wavePoints.drop(1).forEach { point -> lineTo(point.x, point.y) }
            }
            drawPath(
                wavePath,
                if ((ocean?.tideFlow ?: "").contains("들물"))
                    waveRising.copy(alpha = 0.8f)
                else Color(0xFF418FD6).copy(alpha = 0.8f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 2.6.dp.toPx(), cap = StrokeCap.Round
                )
            )

            // Cardinal accent TICKS: no N/E/S/W letters or round letter badges.
            // A white under-stroke makes BLACK E/W legible in dark mode.
            drawCircle(
                ringColor, radius,
                style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx())
            )
            val sensorHeading = heading?.takeIf { compassAvailable } ?: 0f
            for (index in 0 until 36) {
                val radians = Math.toRadians(
                    index * 10.0 - sensorHeading.toDouble() - 90.0
                )
                val x = cos(radians).toFloat()
                val y = sin(radians).toFloat()
                val cardinal = index % 9 == 0
                val inner = if (cardinal) radius * 0.69f else radius * 0.88f
                val from = Offset(center.x + inner * x, center.y + inner * y)
                val to = Offset(
                    center.x + radius * 0.97f * x,
                    center.y + radius * 0.97f * y
                )
                if (index == 9 || index == 27) {
                    drawLine(
                        Color.White, from, to,
                        strokeWidth = 7.5.dp.toPx(), cap = StrokeCap.Round
                    )
                }
                val tickColor = when (index) {
                    0 -> Color(0xFFDF2934)   // N red
                    18 -> Color(0xFF246ADC) // S blue
                    9, 27 -> Color.Black    // E/W black
                    else -> minorTick.copy(alpha = 0.42f)
                }
                drawLine(
                    tickColor, from, to,
                    strokeWidth = if (cardinal) 4.6.dp.toPx()
                        else 1.1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            // Fixed heading reference at the watch's physical top.
            drawCircle(Color(0xFFFFF8E1), 2.6.dp.toPx(),
                center = Offset(center.x, center.y - radius * 1.015f))
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(pointName, color = Gray, fontSize = 10.sp, maxLines = 1)
            Text(
                if (weather?.temperatureC == null) "날씨 --"
                else "${display(weather.temperatureC, 0)}°  ${FishingLogic.weatherLabel(weather.weatherCode)}",
                color = White, fontSize = 10.sp, maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text("파고 ${display(ocean?.waveM)}m",
                color = Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("${FishingLogic.windDirection(weather?.windDirectionDegrees)} ${display(weather?.windMs)}m/s",
                color = White, fontSize = 10.sp, maxLines = 1)
            Text("수온 ${display(ocean?.surfaceTemperatureC)}° · ${TideCalculator.onDate(now.toLocalDate()).name}",
                color = White, fontSize = 10.sp, maxLines = 1)
            Text(ocean?.tideFlow ?: "물 흐름 --", color = Yellow, fontSize = 10.sp)
            Text("추천 $topFish", color = White, fontSize = 10.sp, maxLines = 1)
            Text(
                if (!compassAvailable || heading == null) "방위 확인 중"
                else "${if (compassTrueNorth) "진북" else "자북"} ${heading.toInt()}°",
                color = Gray, fontSize = 9.sp, maxLines = 1
            )
        }
    }
}

@Composable
private fun CompactMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = Gray)
        Text(value, fontSize = 13.sp, color = White, fontWeight = FontWeight.SemiBold,
            maxLines = 1)
    }
}

@Composable
private fun NavigationCard(
    title: String,
    caption: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
            .clip(RoundedCornerShape(24.dp)).background(CardBg)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .height(58.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = White,
                fontWeight = FontWeight.SemiBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text(caption, fontSize = 10.sp, color = Gray,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (enabled) Text("›", fontSize = 22.sp, color = Accent)
    }
}

@Composable
private fun WatchButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 10.dp)
            .clip(CircleShape)
            .background(if (enabled) ButtonBg else CardBg)
            .clickable(enabled = enabled, onClick = onClick)
            .height(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (enabled) White else Gray, fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp)
    }
}

private fun display(value: Double?, decimals: Int = 1): String =
    if (value == null || !value.isFinite()) "--"
    else String.format(Locale.KOREA, "%.${decimals}f", value)
