package com.eastsea.badahannun

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.location.Location
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.eastsea.badahannun.data.*
import com.eastsea.badahannun.domain.CompassTracker
import com.eastsea.badahannun.domain.FishingLogic
import com.eastsea.badahannun.ui.WearFishingApp
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * One standalone Wear OS activity; no phone APK or proprietary API key required.
 */
class MainActivity : ComponentActivity() {
    private val catalog by lazy { GuideCatalog.fromAssets(this) }
    private val repository by lazy { ForecastRepository(applicationContext) }
    private val locationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val uiPreferences by lazy { getSharedPreferences("ui", MODE_PRIVATE) }
    private val compass by lazy { CompassTracker(this) { magneticHeading = it } }

    private var gpsLocation by mutableStateOf<Location?>(null)
    private var locationStatus by mutableStateOf("GPS 확인 전")
    private var manualPointId by mutableStateOf<String?>(null)
    private var report by mutableStateOf<SeaReport?>(null)
    private var refreshing by mutableStateOf(false)
    private var magneticHeading by mutableStateOf<Float?>(null)
    private var compassAvailable by mutableStateOf(true)
    private var compassWanted = false
    private var refreshJob: Job? = null
    private var refreshEpoch = 0
    private var locationToken: CancellationTokenSource? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manualPointId = uiPreferences.getString("manual_point", null)
            ?.takeIf { id -> catalog.points.any { it.id == id } }

        setContent {
            val requestPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                if (permissions.values.any { it }) requestLocation()
                else locationStatus = "위치 권한 없음 · 수동 포인트 사용"
            }

            LaunchedEffect(Unit) {
                loadData(force = false)
                if (hasLocationPermission()) requestLocation()
                else requestPermission.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }

            val point = selectedPoint()
            val distance = if (gpsLocation != null && manualPointId == null) {
                FishingLogic.distanceKm(
                    gpsLocation!!.latitude, gpsLocation!!.longitude,
                    point.latitude, point.longitude
                )
            } else null

            val trueHeading = magneticHeading?.let { magnetic ->
                val location = gpsLocation
                if (location == null) magnetic else {
                    val declination = GeomagneticField(
                        location.latitude.toFloat(), location.longitude.toFloat(),
                        location.altitude.toFloat(), System.currentTimeMillis()
                    ).declination
                    (magnetic + declination + 360f) % 360f
                }
            }

            WearFishingApp(
                catalog = catalog,
                point = point,
                report = report,
                refreshing = refreshing,
                gpsStatus = locationStatus,
                isGpsMode = manualPointId == null && gpsLocation != null,
                gpsDistanceKm = distance,
                heading = trueHeading,
                compassTrueNorth = gpsLocation != null,
                compassAvailable = compassAvailable,
                onRefresh = { loadData(force = true) },
                onLocate = {
                    if (hasLocationPermission()) requestLocation()
                    else requestPermission.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ))
                },
                onSelectPoint = { p ->
                    manualPointId = p.id
                    uiPreferences.edit().putString("manual_point", p.id).apply()
                    loadData(force = false)
                },
                onUseGps = {
                    manualPointId = null
                    uiPreferences.edit().remove("manual_point").apply()
                    if (gpsLocation == null) {
                        if (hasLocationPermission()) requestLocation()
                        else requestPermission.launch(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ))
                    } else loadData(force = false)
                },
                onCompassVisible = { setCompassVisible(it) }
            )
        }

        // No periodic GPS/network refresh while the activity is backgrounded.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    delay(15 * 60 * 1000L)
                    loadData(force = false)
                }
            }
        }
    }

    private fun selectedPoint(): FishingPoint {
        return catalog.points.firstOrNull { it.id == manualPointId }
            ?: gpsLocation?.let {
                FishingLogic.nearest(catalog.points, it.latitude, it.longitude)
            }
            ?: catalog.points.first { it.id == "ulsan-jeongja" }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestLocation() {
        if (!hasLocationPermission()) {
            locationStatus = "위치 권한 필요"
            return
        }
        locationStatus = "현위치 확인 중..."
        locationToken?.cancel()
        val token = CancellationTokenSource()
        locationToken = token
        try {
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .setMaxUpdateAgeMillis(5 * 60 * 1000L)
                .setDurationMillis(12_000L)
                .build()
            locationClient.getCurrentLocation(request, token.token)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        receiveLocation(location)
                    } else {
                        // Reject very old last-known locations.
                        locationClient.lastLocation
                            .addOnSuccessListener { last ->
                                if (last != null && System.currentTimeMillis() - last.time < 30 * 60 * 1000L)
                                    receiveLocation(last)
                                else locationStatus = "위치 확인 실패 · 포인트 수동 선택"
                            }
                            .addOnFailureListener {
                                locationStatus = "GPS 응답 없음 · 수동 선택 가능"
                            }
                    }
                }
                .addOnFailureListener {
                    locationStatus = "GPS 사용 불가 · 수동 선택 가능"
                }
        } catch (_: SecurityException) {
            locationStatus = "위치 권한 확인 필요"
        } catch (_: Exception) {
            locationStatus = "위치 서비스 확인 필요"
        }
    }

    private fun receiveLocation(location: Location) {
        gpsLocation = location
        locationStatus = "위치 확인됨 · 오차 약 ${location.accuracy.toInt()}m"
        if (manualPointId == null) loadData(force = false)
    }

    private fun loadData(force: Boolean) {
        val point = selectedPoint()
        val loc = gpsLocation
        val lat = if (manualPointId == null && loc != null) loc.latitude else point.latitude
        val lon = if (manualPointId == null && loc != null) loc.longitude else point.longitude
        refreshJob?.cancel()
        val epoch = ++refreshEpoch
        refreshing = true
        refreshJob = lifecycleScope.launch {
            try {
                val result = repository.load(point, lat, lon, force)
                if (epoch == refreshEpoch) report = result
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // No fabricated zero values; keep previous data if available.
                if (epoch == refreshEpoch && report == null) {
                    report = SeaReport(
                        null, null, emptyList(), null, null,
                        0L, stale = true, notice = "예보 요청 실패 · 인터넷 연결 확인"
                    )
                }
            } finally {
                if (epoch == refreshEpoch) refreshing = false
            }
        }
    }

    private fun setCompassVisible(visible: Boolean) {
        compassWanted = visible
        if (!visible) {
            compass.stop()
            magneticHeading = null
        } else if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            compassAvailable = compass.start()
        }
    }

    override fun onResume() {
        super.onResume()
        if (compassWanted) compassAvailable = compass.start()
        // When returning from ambient or another app, stale cache is rechecked.
        if (report == null || System.currentTimeMillis() - report!!.fetchedAtMs > 15 * 60 * 1000L)
            loadData(force = false)
    }

    override fun onPause() {
        compass.stop()
        // Keep the existing cached report visible on return; cancel foreground requests.
        refreshJob?.cancel()
        refreshing = false
        super.onPause()
    }

    override fun onDestroy() {
        locationToken?.cancel()
        refreshJob?.cancel()
        compass.stop()
        super.onDestroy()
    }
}
