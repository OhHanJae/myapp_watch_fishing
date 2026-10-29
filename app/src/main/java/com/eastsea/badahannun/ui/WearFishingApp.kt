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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
private enum class Screen { HOME, FORECAST, TIME_DETAIL, FISH, REGIONS, POINTS }
private enum class Metric(val title: String) {
    WAVE("파고"), WIND("바람"), AIR("기온"), SEA("수온"), LEVEL("조위")
}

private val Background = Color(0xFF050D16)
private val CardBg = Color(0xFF102333)
private val Accent = Color(0xFF63DFD4)
private val White = Color(0xFFF0F8FF)
private val Gray = Color(0xFFA1B6C5)
private val Yellow = Color(0xFFFFCD82)
private val Red = Color(0xFFFF858B)

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
                                item {
                                    PageHeading("바다한눈")
                                }
                                item {
                                    NavigationCard(
                                        title = point.name,
                                        caption = when {
                                            !isGpsMode -> "직접 선택 · ${point.region}"
                                            gpsDistanceKm == null -> "GPS 확인 중 · 임시 포인트"
                                            else -> "내 위치 기준 · ${point.region}"
                                        },
                                        onClick = { selectedRegion = point.region; screen = Screen.REGIONS }
                                    )
                                }
                                item {
    val leader = FishingLogic.topFish(catalog.fish, point,
        report?.takeUnless { it.stale }, now).firstOrNull()?.fish?.name ?: "--"
    SeaAtGlance(report, now, refreshing, heading, compassTrueNorth,
        compassAvailable, leader)
}
                                item {
                                    val condition = if (report?.stale == true) "이전 예보" else if (report == null) "예보 대기" else "모델 예보"
                                    val distanceWarning = isGpsMode && gpsDistanceKm != null && gpsDistanceKm > 30.0
                                    Text(
                                        if (distanceWarning) "가까운 포인트와 ${display(gpsDistanceKm, 0)}km · 현장 확인" else condition + " · 공식 관측값 아님",
                                        color = if (distanceWarning || report?.stale == true) Yellow else Gray,
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
                                    NavigationCard("추천 어종 TOP 3", "계절 · 시간 · 바다 상태", onClick = { screen = Screen.FISH })
                                }
                                item {
                                    NavigationCard("지역 · 포인트", "내 위치로 / 직접 선택", onClick = {
                                        selectedRegion = point.region
                                        screen = Screen.REGIONS
                                    })
                                }
                                item {
                                    WatchButton(if (refreshing) "갱신 중..." else "예보 새로고침", !refreshing, onRefresh)
                                }
                            }

                            Screen.FORECAST -> {
                                val leads = report?.forecast?.map { step ->
                                    val model = report.takeUnless { it.stale }
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
                .background(if (selected == option) Color(0xFF235C65) else CardBg)
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
                drawLine(Color(0xFF294454), Offset(left, y), Offset(right, y), strokeWidth = 1f)
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
                    drawLine(Accent, xy(index - 1, a), xy(index, b), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                }
            }
            for (index in values.indices step 6) {
                values[index]?.let { drawCircle(Yellow, radius = 2.5.dp.toPx(), center = xy(index, it)) }
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
                    val color = if (run.name == "--") Gray.copy(alpha = 0.25f)
                        else colors[(species.indexOf(run.name).coerceAtLeast(0)) % colors.size]
                    drawRect(color, topLeft = Offset(width * run.startIndex, 3.dp.toPx()),
                        size = Size(width * (run.endExclusive - run.startIndex), 19.dp.toPx()))
                }
                for (at in listOf(0, count / 2, count - 1)) {
                    val x = (at + 0.5f) * width
                    drawLine(White.copy(alpha = 0.72f),
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
@Composable
private fun SeaAtGlance(
    report: SeaReport?,
    now: ZonedDateTime,
    refreshing: Boolean,
    heading: Float?,
    compassTrueNorth: Boolean,
    compassAvailable: Boolean,
    topFish: String
) {
    val w = report?.weather
    val ocean = report?.ocean
    val wave = (ocean?.waveM ?: 0.0).coerceIn(0.0, 3.5).toFloat()
    val speed = (w?.windMs ?: 0.0).coerceIn(0.0, 20.0).toFloat()
    val motion = rememberInfiniteTransition(label = "marine-wind-waves")
    val phase by motion.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4700, easing = LinearEasing)),
        label = "wind-and-wave-phase"
    )
    val bearing = WatchVisualMath.relativeWind(w?.windDirectionDegrees, heading)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(CardBg).padding(horizontal = 9.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth().height(210.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val radius = size.minDimension * 0.48f
                drawCircle(Color(0xFF2A5063), radius, style =
                    androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                // A ring rotating *with the real foreground sensor heading*.
                if (heading != null && compassAvailable) {
                    for (index in 0 until 36) {
                        val radians = Math.toRadians(index * 10.0 - heading.toDouble() - 90.0)
                        val x = cos(radians).toFloat()
                        val y = sin(radians).toFloat()
                        val inner = if (index % 9 == 0) radius * 0.81f else radius * 0.88f
                        drawLine(if (index == 0) Accent else Gray.copy(alpha = 0.44f),
                            Offset(center.x + inner * x, center.y + inner * y),
                            Offset(center.x + radius * 0.97f * x,
                                center.y + radius * 0.97f * y),
                            strokeWidth = if (index == 0) 3.dp.toPx() else 1.dp.toPx(),
                            cap = StrokeCap.Round)
                    }
                }
                // Forecast wind blows in the corresponding compass-relative direction.
                if (bearing != null) {
                    val radians = Math.toRadians((bearing + 90f).toDouble())
                    val dx = cos(radians).toFloat()
                    val dy = sin(radians).toFloat()
                    val px = -dy
                    val py = dx
                    for (i in -2..2) {
                        val travel = ((phase * (0.5f + speed / 14f) + (i + 2) * 0.19f) % 1f - 0.5f)
                        val offset = travel * radius * 0.92f
                        val side = i * radius * 0.21f
                        val mx = center.x + dx * offset + px * side
                        val my = center.y + dy * offset + py * side
                        val length = (8f + speed * 1.8f).dp.toPx()
                        drawLine(Color(0xFF9AD8F3).copy(alpha = 0.13f + speed / 115f),
                            Offset(mx - dx * length / 2, my - dy * length / 2),
                            Offset(mx + dx * length / 2, my + dy * length / 2),
                            strokeWidth = 1.4.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
                // Model height controls the wave amplitude, while the tide phase
                // controls its shade. This is decorative, not a tidal animation.
                val amplitude = (1.8f + wave * 2.7f).dp.toPx()
                val rising = (ocean?.tideFlow ?: "").contains("들물")
                for (row in 0..2) {
                    val path = Path()
                    for (i in 0..44) {
                        val t = i / 44f
                        val x = center.x + (t - 0.5f) * radius * 1.22f
                        val y = center.y + radius * 0.50f + row * 7.dp.toPx() +
                            sin((t * 3.5f + phase + row * 0.2f) * Math.PI * 2)
                                .toFloat() * amplitude
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, if (rising) Accent.copy(alpha = 0.17f)
                        else Color(0xFF6AAEDE).copy(alpha = 0.18f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.6.dp.toPx()))
                }
                // Fixed top reference; the north tick moves beneath it.
                drawCircle(Accent, 3.2.dp.toPx(),
                    center = Offset(center.x, center.y - radius * 1.02f))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (w?.temperatureC == null) "날씨 --"
                    else "${display(w.temperatureC, 0)}°  ${FishingLogic.weatherLabel(w.weatherCode)}",
                    color = White, fontSize = 11.sp
                )
                Spacer(Modifier.height(6.dp))
                Text("파고 ${display(ocean?.waveM)}m",
                    color = Accent, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("${FishingLogic.windDirection(w?.windDirectionDegrees)} ${display(w?.windMs)}m/s",
                    color = White, fontSize = 11.sp)
                Text("수온 ${display(ocean?.surfaceTemperatureC)}° · ${TideCalculator.onDate(now.toLocalDate()).name}",
                    color = White, fontSize = 11.sp)
                Text(ocean?.tideFlow ?: "물 흐름 --", color = Yellow, fontSize = 10.sp)
                Text("추천 $topFish", color = White, fontSize = 10.sp, maxLines = 1)
                Text(
                    if (!compassAvailable || heading == null) "나침반 확인 중"
                    else "${if (compassTrueNorth) "진북" else "자북"} ${heading.toInt()}°",
                    color = Gray, fontSize = 9.sp
                )
            }
        }
        Text(
            when {
                refreshing -> "예보 갱신 중"
                report == null -> "예보 확인 중"
                report.stale -> "이전 예보 · 갱신 필요"
                else -> "기상·파도는 수치모델 예보 · 물때는 간이"
            }, color = if (report?.stale == true) Yellow else Gray,
            textAlign = TextAlign.Center, fontSize = 9.sp,
            modifier = Modifier.fillMaxWidth()
        )
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
            .background(if (enabled) Color(0xFF234B5D) else CardBg)
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
