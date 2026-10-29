package com.eastsea.badahannun.ui

import androidx.activity.compose.BackHandler
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
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.max
import kotlin.math.sin

/** Glanceable watch screens, with a separate chart/timeline destination. */
private enum class Screen { HOME, FORECAST, TIME_DETAIL, FISH, COMPASS, REGIONS, POINTS }
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
        onCompassVisible(screen == Screen.COMPASS)
        onDispose { if (screen == Screen.COMPASS) onCompassVisible(false) }
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
                                item { SeaAtGlance(report, now, refreshing) }
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
                                    NavigationCard("나침반", "실시간 방위 확인", onClick = { screen = Screen.COMPASS })
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
                                item { PageHeading("12시간 예보", "30분 간격 · 모델 예측") }
                                if (report == null || report.forecast.isEmpty()) {
                                    item { Text("예보를 아직 받지 못했어요", color = Yellow, fontSize = 12.sp) }
                                    item { WatchButton("새로고침", !refreshing, onRefresh) }
                                } else {
                                    item { MetricTabs(metric) { metric = it } }
                                    item { TrendChart(report.forecast, metric) }
                                    item {
                                        Text("30분 값은 시간별 예보 보간 · 조위/물 흐름은 모델 추정",
                                            color = Gray, fontSize = 9.sp, textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                                    }
                                    items(report.forecast.size) { index ->
                                        val step = report.forecast[index]
                                        val best = FishingLogic.topFish(
                                            catalog.fish, point,
                                            report.takeUnless { it.stale }?.copy(weather = step.weather, ocean = step.ocean),
                                            step.time.atZone(FishingLogic.KST)
                                        ).firstOrNull()?.fish?.name ?: "--"
                                        ForecastRow(step, best, index == 0) {
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

                            Screen.COMPASS -> {
                                item { PageHeading("나침반", if (compassTrueNorth) "진북 기준" else "자북 기준") }
                                item { CompassFace(heading, compassAvailable) }
                                item {
                                    Text(
                                        if (compassAvailable) "손목을 수평으로 유지하세요" else "방위 센서를 사용할 수 없음",
                                        color = Gray, fontSize = 11.sp, textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
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
            Text("현재", fontSize = 9.sp, color = Gray)
            Text("+6시간", fontSize = 9.sp, color = Gray)
            Text("+12시간", fontSize = 9.sp, color = Gray)
        }
    }
}

@Composable
private fun ForecastRow(step: ForecastStep, topFish: String, current: Boolean, onClick: () -> Unit) {
    val weather = step.weather
    val sea = step.ocean
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)
        .clip(RoundedCornerShape(17.dp)).background(CardBg).clickable(onClick = onClick)
        .padding(horizontal = 13.dp, vertical = 9.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${if (current) "지금 · " else ""}${step.time.format(DateTimeFormatter.ofPattern("HH:mm"))}",
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

@Composable
private fun SeaAtGlance(report: SeaReport?, now: ZonedDateTime, refreshing: Boolean) {
    val weather = report?.weather
    val sea = report?.ocean
    val risk = if (report == null || report.stale) null else FishingLogic.safety(report)
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(23.dp))
            .background(CardBg).padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (weather?.temperatureC == null) "날씨 --"
                else "${display(weather.temperatureC, 0)}° · ${FishingLogic.weatherLabel(weather.weatherCode)}",
                color = White, fontSize = 12.sp, maxLines = 1
            )
            Text(
                when (risk?.risk) {
                    FishingLogic.Risk.GOOD -> "양호"
                    FishingLogic.Risk.CAUTION -> "주의"
                    FishingLogic.Risk.BAD -> "위험"
                    else -> if (refreshing) "갱신 중" else "미확인"
                },
                color = when (risk?.risk) {
                    FishingLogic.Risk.GOOD -> Accent
                    FishingLogic.Risk.BAD -> Red
                    else -> Yellow
                }, fontSize = 11.sp, fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(10.dp))
        Text("파고", fontSize = 12.sp, color = Gray)
        Text(
            if (sea?.waveM == null) "-- m" else "${display(sea.waveM)} m",
            fontSize = 32.sp, color = Accent, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            CompactMetric("바람", "${display(weather?.windMs)}m/s")
            CompactMetric("수온", "${display(sea?.surfaceTemperatureC)}°")
            CompactMetric("물때*", TideCalculator.onDate(now.toLocalDate()).name)
        }
        Spacer(Modifier.height(5.dp))
        Text("${FishingLogic.windDirection(weather?.windDirectionDegrees)} · ${sea?.tideFlow ?: "물 흐름 예보 없음"} (모델)",
            fontSize = 10.sp, color = Gray)
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

@Composable
private fun CompassFace(heading: Float?, available: Boolean) {
    val diameter = (LocalConfiguration.current.screenWidthDp * 0.7f).dp
    val value = heading ?: 0f
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(diameter)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2f
                drawCircle(Color(0xFF213F52), radius = radius * 0.95f)
                drawCircle(Background, radius = radius * 0.90f)
                for (index in 0 until 24) {
                    val rad = Math.toRadians(index * 15.0 - value - 90.0)
                    val len = if (index % 6 == 0) radius * 0.14f else radius * 0.07f
                    val x = cos(rad).toFloat()
                    val y = sin(rad).toFloat()
                    drawLine(
                        if (index == 0) Accent else Color(0xFF638396),
                        start = Offset(center.x + (radius * 0.76f - len) * x,
                            center.y + (radius * 0.76f - len) * y),
                        end = Offset(center.x + radius * 0.76f * x,
                            center.y + radius * 0.76f * y),
                        strokeWidth = if (index == 0) 6f else 3f, cap = StrokeCap.Round
                    )
                }
                // North pointer rotates against the measured device heading.
                val north = Math.toRadians(-value.toDouble() - 90.0)
                drawLine(
                    Accent, center,
                    Offset(center.x + cos(north).toFloat() * radius * 0.54f,
                        center.y + sin(north).toFloat() * radius * 0.54f),
                    strokeWidth = 8f, cap = StrokeCap.Round
                )
                drawCircle(Accent, radius = 8f)
            }
            Box(
                modifier = Modifier.fillMaxSize().graphicsLayer(rotationZ = -value),
                contentAlignment = Alignment.TopCenter
            ) {
                Text("N", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp))
            }
        }
        Text(
            if (!available || heading == null) "--°" else "${value.toInt()}°",
            color = White, fontSize = 27.sp, fontWeight = FontWeight.Bold
        )
    }
}

private fun display(value: Double?, decimals: Int = 1): String =
    if (value == null || !value.isFinite()) "--"
    else String.format(Locale.KOREA, "%.${decimals}f", value)
