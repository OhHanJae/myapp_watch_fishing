package com.eastsea.badahannun.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.eastsea.badahannun.data.*
import com.eastsea.badahannun.domain.FishingLogic
import com.eastsea.badahannun.domain.TideCalculator
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val OceanBg = Color(0xFF06131F)
private val Surface = Color(0xFF0E2435)
private val SurfaceLight = Color(0xFF123248)
private val PrimaryText = Color(0xFFEAF7FF)
private val MutedText = Color(0xFF98B7CA)
private val Aqua = Color(0xFF53DFD8)
private val Blue = Color(0xFF6ABEFF)
private val Amber = Color(0xFFFFCA79)
private val Danger = Color(0xFFFF858B)
private val Good = Color(0xFF7BDEA7)

private enum class WatchPage { NOW, FISH, COMPASS, POINTS }

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
    onLocate: () -> Unit,
    onSelectPoint: (FishingPoint) -> Unit,
    onUseGps: () -> Unit,
    onCompassVisible: (Boolean) -> Unit
) {
    var page by remember { mutableStateOf(WatchPage.NOW) }
    var selectedFish by remember { mutableStateOf<FishSpecies?>(null) }
    var now by remember { mutableStateOf(ZonedDateTime.now(FishingLogic.KST)) }
    var pointRegion by remember { mutableStateOf(point.region) }

    LaunchedEffect(point.region) {
        if (page != WatchPage.POINTS) pointRegion = point.region
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            now = ZonedDateTime.now(FishingLogic.KST)
        }
    }

    DisposableEffect(page) {
        onCompassVisible(page == WatchPage.COMPASS)
        onDispose {
            if (page == WatchPage.COMPASS) onCompassVisible(false)
        }
    }

    BackHandler(page != WatchPage.NOW || selectedFish != null) {
        if (selectedFish != null) selectedFish = null
        else page = WatchPage.NOW
    }

    val isSmall = LocalConfiguration.current.screenWidthDp <= 220
    val hPadding = if (isSmall) 22.dp else 29.dp
    val topFish = remember(catalog, point, report, now) {
        FishingLogic.topFish(catalog.fish, point, report?.takeUnless { it.stale }, now)
    }

    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(OceanBg, Color(0xFF0A2031))))
                .padding(horizontal = hPadding)
        ) {
            Spacer(Modifier.height(25.dp))
            Text(
                text = "바다한눈  ·  ${now.format(DateTimeFormatter.ofPattern("HH:mm"))}",
                fontSize = 11.sp, fontWeight = FontWeight.Bold,
                color = Aqua,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = "${point.region}  ${point.name}",
                fontSize = 15.sp, color = PrimaryText, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                NavChip("현황", page == WatchPage.NOW && selectedFish == null, Modifier.weight(1f)) {
                    selectedFish = null
                    page = WatchPage.NOW
                }
                NavChip("TOP 3", page == WatchPage.FISH, Modifier.weight(1f)) {
                    selectedFish = null
                    page = WatchPage.FISH
                }
                NavChip("나침반", page == WatchPage.COMPASS, Modifier.weight(1f)) {
                    selectedFish = null
                    page = WatchPage.COMPASS
                }
            }
            Spacer(Modifier.height(8.dp))

            key(page, selectedFish?.id) {
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when {
                        selectedFish != null ->
                            FishDetail(
                                fish = selectedFish!!,
                                gear = catalog.gears.firstOrNull { it.id == selectedFish!!.mainGear },
                                onBack = { selectedFish = null }
                            )
                        page == WatchPage.NOW -> NowPage(
                            report = report,
                            refreshing = refreshing,
                            point = point,
                            gpsStatus = gpsStatus,
                            isGpsMode = isGpsMode,
                            gpsDistanceKm = gpsDistanceKm,
                            topFish = topFish,
                            now = now,
                            onRefresh = onRefresh,
                            onLocate = onLocate,
                            onPoints = { page = WatchPage.POINTS },
                            onFish = { page = WatchPage.FISH }
                        )
                        page == WatchPage.FISH -> FishPage(
                            topFish = topFish,
                            point = point,
                            report = report,
                            catalog = catalog,
                            onDetails = { selectedFish = it }
                        )
                        page == WatchPage.COMPASS ->
                            CompassPage(heading, compassTrueNorth, compassAvailable)
                        page == WatchPage.POINTS ->
                            PointsPage(
                                catalog.points, pointRegion, { pointRegion = it },
                                isGpsMode, gpsStatus,
                                onLocate = {
                                    onUseGps()
                                    page = WatchPage.NOW
                                },
                                onSelect = {
                                    onSelectPoint(it)
                                    page = WatchPage.NOW
                                }
                            )
                    }
                    Spacer(Modifier.height(25.dp))
                }
            }
        }
    }
}

@Composable
private fun NavChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(50))
            .background(if (selected) Color(0xFF1D5264) else Surface)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, fontSize = 10.sp,
            color = if (selected) Aqua else MutedText,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun NowPage(
    report: SeaReport?,
    refreshing: Boolean,
    point: FishingPoint,
    gpsStatus: String,
    isGpsMode: Boolean,
    gpsDistanceKm: Double?,
    topFish: List<FishingLogic.ScoredFish>,
    now: ZonedDateTime,
    onRefresh: () -> Unit,
    onLocate: () -> Unit,
    onPoints: () -> Unit,
    onFish: () -> Unit
) {
    val weather = report?.weather
    val sea = report?.ocean
    val label = FishingLogic.weatherLabel(weather?.weatherCode)
    val risk = if (report?.stale == true) {
        FishingLogic.Safety(FishingLogic.Risk.UNKNOWN, "이전 예보", "실시간 상태가 아닙니다")
    } else FishingLogic.safety(report)

    Text(
        text = if (report == null || (weather == null && sea == null)) "예보 연결 대기"
            else "${num(weather?.temperatureC, 0)}°  $label",
        fontSize = if (weather?.temperatureC != null) 27.sp else 17.sp,
        fontWeight = FontWeight.Bold, color = PrimaryText,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(3.dp))
    Text(
        text = when {
            isGpsMode && gpsDistanceKm != null ->
                "GPS 근접 포인트 · ${num(gpsDistanceKm, 1)} km"
            else -> "선택 포인트 기준 · 현위치 아님"
        },
        fontSize = 10.sp, color = MutedText,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
    )
    if (gpsDistanceKm != null && gpsDistanceKm >= 30) {
        Spacer(Modifier.height(4.dp))
        Text("포인트와 거리가 멀어 추천 정확도 제한", fontSize = 9.sp, color = Amber,
            textAlign = TextAlign.Center)
    }
    Spacer(Modifier.height(8.dp))

    Panel {
        Text("지금 바다", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Aqua)
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            NumberTile("수온", num(sea?.surfaceTemperatureC) + "°", "모델 SST", Modifier.weight(1f))
            NumberTile("파고", num(sea?.waveM) + "m", "파주기 ${num(sea?.wavePeriodSeconds)}s", Modifier.weight(1f))
        }
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            NumberTile("바람", num(weather?.windMs),
                "m/s", Modifier.weight(1f))
            NumberTile("돌풍", num(weather?.gustMs),
                "m/s", Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
        Text("풍향 ${FishingLogic.windDirection(weather?.windDirectionDegrees)}",
            fontSize = 10.sp, color = MutedText)
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            NumberTile("비 확률", numberPercent(weather?.rainProbability), "예보", Modifier.weight(1f))
            NumberTile("습도", numberPercent(weather?.relativeHumidity), "상대습도", Modifier.weight(1f))
        }
    }

    Spacer(Modifier.height(8.dp))
    val tide = TideCalculator.onDate(now.toLocalDate())
    Panel {
        Text("물때 · 조위", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Aqua)
        Spacer(Modifier.height(5.dp))
        Text("${tide.name}  ·  ${tide.band}", fontSize = 20.sp,
            fontWeight = FontWeight.Bold, color = PrimaryText)
        Text("음력 기준 간이 추정 · 공식 물때표 아님", fontSize = 9.sp, color = MutedText)
        Spacer(Modifier.height(5.dp))
        Text("조위 흐름  ${sea?.tideFlow ?: "--"}", fontSize = 11.sp, color = Blue)
        if (report?.tideEvents?.isNotEmpty() == true) {
            Spacer(Modifier.height(4.dp))
            report.tideEvents.take(2).forEach { turn ->
                Text("${turn.label} ${turn.time.format(DateTimeFormatter.ofPattern("MM/dd HH:mm"))} (모델)",
                    color = PrimaryText, fontSize = 11.sp)
            }
        } else {
            Text("만·간조 예측값 없음", color = MutedText, fontSize = 10.sp)
        }
    }
    Spacer(Modifier.height(8.dp))

    Panel(color = when (risk.risk) {
        FishingLogic.Risk.GOOD -> Color(0xFF13372F)
        FishingLogic.Risk.CAUTION -> Color(0xFF3A2B21)
        FishingLogic.Risk.BAD -> Color(0xFF3A202A)
        FishingLogic.Risk.UNKNOWN -> Surface
    }) {
        Text(
            text = "출조 판단 · ${risk.title}",
            color = when (risk.risk) {
                FishingLogic.Risk.GOOD -> Good
                FishingLogic.Risk.CAUTION -> Amber
                FishingLogic.Risk.BAD -> Danger
                FishingLogic.Risk.UNKNOWN -> MutedText
            },
            fontSize = 12.sp, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(2.dp))
        Text(risk.detail, fontSize = 10.sp, color = PrimaryText)
    }

    Spacer(Modifier.height(8.dp))
    Panel(modifier = Modifier.clickable(onClick = onFish)) {
        Text("추천 TOP 3   ›", fontSize = 12.sp, color = Aqua, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        topFish.forEachIndexed { index, result ->
            Text("${index + 1}. ${result.fish.name}  ${result.score}점",
                fontSize = 12.sp, color = PrimaryText, fontWeight = FontWeight.Medium)
        }
    }

    Spacer(Modifier.height(7.dp))
    if (report?.sunrise != null || report?.sunset != null) {
        Text("일출 ${formatTime(report.sunrise)} · 일몰 ${formatTime(report.sunset)}",
            fontSize = 10.sp, color = MutedText, textAlign = TextAlign.Center)
    }
    Text(
        when {
            report == null -> "예보 요청 중"
            report.fetchedAtMs <= 0 -> report.notice ?: "예보 없음"
            report.stale -> "오프라인/부분 실패 · 참고 예보 (${ageMinutes(report.fetchedAtMs)}분 전)"
            else -> "Open-Meteo 모델 · ${ageMinutes(report.fetchedAtMs)}분 전 갱신"
        },
        fontSize = 9.sp, color = if (report?.stale == true) Amber else MutedText,
        textAlign = TextAlign.Center
    )
    report?.notice?.let { Text(it, fontSize = 9.sp, color = Amber, textAlign = TextAlign.Center) }

    Spacer(Modifier.height(10.dp))
    Action("새로고침${if (refreshing) " 중..." else ""}", enabled = !refreshing, onClick = onRefresh)
    Spacer(Modifier.height(5.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Action("GPS 재검색", Modifier.weight(1f), onClick = onLocate)
        Action("포인트 변경", Modifier.weight(1f), onClick = onPoints)
    }
    Spacer(Modifier.height(6.dp))
    Text(
        "GPS: $gpsStatus\n위험도는 단순 수치 참고용. 현장 기상특보·출입통제 확인",
        color = MutedText, fontSize = 9.sp,
        textAlign = TextAlign.Center, lineHeight = 12.sp
    )
    Spacer(Modifier.height(5.dp))
    Text(
        "출처: Open-Meteo · Météo-France/ECMWF/NOAA/DWD/Copernicus 해양 모델",
        color = MutedText, fontSize = 8.sp, textAlign = TextAlign.Center,
        lineHeight = 11.sp
    )
}

@Composable
private fun FishPage(
    topFish: List<FishingLogic.ScoredFish>,
    point: FishingPoint,
    report: SeaReport?,
    catalog: GuideCatalog,
    onDetails: (FishSpecies) -> Unit
) {
    Text("현위치 추천 대상어", color = PrimaryText, fontSize = 16.sp,
        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Spacer(Modifier.height(4.dp))
    Text("${point.name} 기준 · 계절/시간/예보", fontSize = 10.sp,
        color = MutedText, textAlign = TextAlign.Center)
    if (report == null || report.stale || report.weather == null || report.ocean == null) {
        Text("현재 기상 반영 제한 · 계절·시간 우선", fontSize = 9.sp,
            color = Amber, textAlign = TextAlign.Center)
    }
    if (FishingLogic.safety(report).risk == FishingLogic.Risk.BAD && report?.stale != true) {
        Spacer(Modifier.height(5.dp))
        Text("출조 비추천 조건 · 순위는 안전 판단과 별개",
            fontSize = 10.sp, color = Danger, textAlign = TextAlign.Center)
    }
    Spacer(Modifier.height(9.dp))
    topFish.forEachIndexed { index, item ->
        val mainGear = catalog.gears.firstOrNull { it.id == item.fish.mainGear }
        Panel(modifier = Modifier.clickable { onDetails(item.fish) }) {
            Text(
                "${index + 1}위  ${item.fish.name}",
                color = if (index == 0) Aqua else PrimaryText,
                fontSize = 17.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("조건 적합도", color = MutedText, fontSize = 10.sp,
                    modifier = Modifier.weight(1f))
                Text("${item.score} / 100", color = Blue, fontSize = 12.sp,
                    fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(4.dp)
                .clip(RoundedCornerShape(99.dp)).background(Color(0xFF244255))) {
                Box(Modifier.fillMaxWidth(item.score / 100f).fillMaxHeight()
                    .background(Aqua, RoundedCornerShape(99.dp)))
            }
            Spacer(Modifier.height(7.dp))
            Text("장비  ${mainGear?.shortName ?: "--"}",
                fontSize = 11.sp, color = PrimaryText)
            Text("루어  ${item.fish.lures.firstOrNull() ?: "--"}",
                fontSize = 10.sp, color = MutedText, lineHeight = 14.sp)
            Text("자세히 보기  ›", fontSize = 10.sp, color = Aqua)
        }
        Spacer(Modifier.height(7.dp))
    }
    Text("추천 점수는 조과 확률이 아닌 가이드 적합도",
        color = MutedText, fontSize = 9.sp, textAlign = TextAlign.Center)
}

@Composable
private fun FishDetail(
    fish: FishSpecies,
    gear: FishingGear?,
    onBack: () -> Unit
) {
    Action("‹ 추천 목록", onClick = onBack)
    Spacer(Modifier.height(9.dp))
    Text(fish.name, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Aqua)
    Spacer(Modifier.height(8.dp))
    DetailRow("메인 장비", gear?.fullName ?: "${fish.mainGear}번")
    DetailRow("추천 루어", fish.lures.joinToString(" / "))
    DetailRow("추천 색상", fish.colors.joinToString(" · "))
    DetailRow("공략 수심", fish.water)
    DetailRow("운용", fish.action)
    DetailRow("포인트 노트", fish.note)
    Spacer(Modifier.height(5.dp))
    Text("기존 HTML의 어종·루어·장비 가이드", color = MutedText, fontSize = 9.sp)
}

@Composable
private fun DetailRow(label: String, value: String) {
    Panel {
        Text(label, fontSize = 10.sp, color = Aqua, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(value, fontSize = 11.sp, color = PrimaryText, lineHeight = 16.sp)
    }
    Spacer(Modifier.height(7.dp))
}

@Composable
private fun CompassPage(
    heading: Float?,
    trueNorth: Boolean,
    available: Boolean
) {
    Text("나침반", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
    Spacer(Modifier.height(4.dp))
    Text(
        if (heading == null) {
            if (available) "센서 교정 또는 방향 확인 중" else "방위 센서를 사용할 수 없음"
        } else "${heading.roundToInt()}° · ${FishingLogic.compassName(heading)}",
        fontSize = if (heading != null) 19.sp else 11.sp,
        fontWeight = FontWeight.Bold,
        color = Aqua,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(8.dp))
    CompassDial(heading)
    Spacer(Modifier.height(8.dp))
    Text(
        if (trueNorth) "진북 기준 · GPS 자기편각 보정"
        else "자북 기준 · GPS 미확보",
        fontSize = 10.sp, color = MutedText, textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(5.dp))
    Text(
        "워치를 수평으로 들고 방향을 확인하세요.\n오차가 크면 금속 물체에서 떨어져 교정하세요.",
        fontSize = 10.sp, lineHeight = 14.sp,
        color = MutedText, textAlign = TextAlign.Center
    )
}

@Composable
private fun CompassDial(heading: Float?) {
    Canvas(modifier = Modifier.size(154.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.435f
        drawCircle(Color(0xFF143346), radius, center)
        drawCircle(Color(0xFF426B80), radius, center, style = Stroke(width = 2.dp.toPx()))
        rotate(-(heading ?: 0f), center) {
            for (index in 0 until 36) {
                val angle = Math.toRadians(index * 10.0 - 90.0)
                val outer = Offset(
                    center.x + cos(angle).toFloat() * radius,
                    center.y + sin(angle).toFloat() * radius
                )
                val length = if (index % 9 == 0) 17.dp.toPx()
                    else if (index % 3 == 0) 11.dp.toPx() else 6.dp.toPx()
                val inner = Offset(
                    center.x + cos(angle).toFloat() * (radius - length),
                    center.y + sin(angle).toFloat() * (radius - length)
                )
                drawLine(
                    if (index == 0) Danger
                    else if (index % 9 == 0) Blue else MutedText,
                    inner, outer, strokeWidth = if (index % 9 == 0) 3.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
        // Stationary upward pointer is the direction the top of the watch faces.
        val arrow = Path().apply {
            moveTo(center.x, center.y - 31.dp.toPx())
            lineTo(center.x - 10.dp.toPx(), center.y + 13.dp.toPx())
            lineTo(center.x, center.y + 7.dp.toPx())
            lineTo(center.x + 10.dp.toPx(), center.y + 13.dp.toPx())
            close()
        }
        drawPath(arrow, Aqua)
        drawCircle(OceanBg, 5.dp.toPx(), center)
        drawCircle(Aqua, 3.dp.toPx(), center)
    }
}

@Composable
private fun PointsPage(
    points: List<FishingPoint>,
    region: String,
    onRegionChange: (String) -> Unit,
    isGpsMode: Boolean,
    gpsStatus: String,
    onLocate: () -> Unit,
    onSelect: (FishingPoint) -> Unit
) {
    Text("포인트 선택", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
    Spacer(Modifier.height(7.dp))
    Action("GPS 자동 선택", onClick = onLocate)
    Text(if (isGpsMode) "현재 GPS 기준 자동 추천 중" else gpsStatus,
        fontSize = 9.sp, color = MutedText, textAlign = TextAlign.Center)
    Spacer(Modifier.height(9.dp))

    val regions = listOf("포항", "경주", "울산", "부산")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        regions.forEach { name ->
            NavChip(name, region == name, Modifier.weight(1f)) { onRegionChange(name) }
        }
    }
    Spacer(Modifier.height(8.dp))
    points.filter { it.region == region }.forEach { point ->
        Panel(modifier = Modifier.clickable { onSelect(point) }) {
            Text(point.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Blue)
            Text(point.types.joinToString(" · "), fontSize = 9.sp, color = MutedText)
        }
        Spacer(Modifier.height(5.dp))
    }
    Text("현장 출입통제·낚시 가능 구역 확인 필수", fontSize = 9.sp,
        color = MutedText, textAlign = TextAlign.Center)
}

@Composable
private fun Panel(
    modifier: Modifier = Modifier,
    color: Color = Surface,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(color).then(modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        content = content
    )
}

@Composable
private fun NumberTile(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(12.dp))
            .background(SurfaceLight)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Text(label, fontSize = 10.sp, color = MutedText, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(
            value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryText,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(1.dp))
        Text(sub, fontSize = 8.sp, color = MutedText,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Action(
    title: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (enabled) Color(0xFF1D4A65) else Surface)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(title, fontSize = 11.sp, color = if (enabled) PrimaryText else MutedText,
            fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

private fun num(value: Double?, digits: Int = 1): String =
    if (value == null || !value.isFinite()) "--"
    else String.format(Locale.KOREA, "%.${digits}f", value)

private fun numberPercent(value: Double?): String =
    if (value == null || !value.isFinite()) "--"
    else "${value.roundToInt()}%"

private fun formatTime(time: LocalTime?): String =
    time?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "--:--"

private fun ageMinutes(timestamp: Long): Long =
    ((System.currentTimeMillis() - timestamp) / 60_000L).coerceAtLeast(0)
