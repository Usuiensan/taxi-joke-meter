package jp.usuiensan.taxijokemeter.ui.shell

import android.app.Activity
import android.Manifest
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import jp.usuiensan.taxijokemeter.data.location.DutyMeasurementController
import jp.usuiensan.taxijokemeter.data.location.hasLocationPermission
import jp.usuiensan.taxijokemeter.data.location.MeasurementDebugStore
import jp.usuiensan.taxijokemeter.data.meter.MeterController
import jp.usuiensan.taxijokemeter.data.meter.MeterRuntimeStore
import jp.usuiensan.taxijokemeter.domain.measurement.OccupancyState
import jp.usuiensan.taxijokemeter.data.fare.FareRuleStore
import jp.usuiensan.taxijokemeter.data.fare.TripChargeSettings
import jp.usuiensan.taxijokemeter.data.fare.TripChargeSettingsStore
import jp.usuiensan.taxijokemeter.domain.model.FareRule
import jp.usuiensan.taxijokemeter.domain.model.FarePreset
import jp.usuiensan.taxijokemeter.domain.model.FarePresets
import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import jp.usuiensan.taxijokemeter.domain.location.LocationAccessState
import jp.usuiensan.taxijokemeter.domain.location.locationAccessState
import jp.usuiensan.taxijokemeter.ui.meter.MeterScreen
import jp.usuiensan.taxijokemeter.data.duty.DutyDatabase
import jp.usuiensan.taxijokemeter.data.receipt.ReceiptArtifactStore
import jp.usuiensan.taxijokemeter.data.receipt.ReceiptRepository
import jp.usuiensan.taxijokemeter.data.measurement.DrivingSampleRepository
import jp.usuiensan.taxijokemeter.data.safety.SafetyEventRepository
import jp.usuiensan.taxijokemeter.domain.report.ReceiptData
import jp.usuiensan.taxijokemeter.domain.report.ReceiptTextRenderer
import jp.usuiensan.taxijokemeter.domain.report.SafetyReportData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun AppShell() {
    var showMeter by rememberSaveable { mutableStateOf(true) }
    var showDebug by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showReissue by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = context as Activity
    val meterRuntime by MeterRuntimeStore.snapshot.collectAsState()
    val meterController = remember { MeterController(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val locationManager = remember { context.getSystemService(LocationManager::class.java) }
    val locationPreferences = remember { context.getSharedPreferences("location_access", Context.MODE_PRIVATE) }
    var locationAccessRevision by remember { mutableStateOf(0) }
    val locationAccess = remember(locationAccessRevision) {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        locationAccessState(fine, coarse, locationManager.isLocationEnabled, locationPreferences.getBoolean("location_permission_requested_once", false), ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION) || ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    val requestLocationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        locationAccessRevision++
        if (hasLocationPermission(context) && locationManager.isLocationEnabled) meterController.resumeLocation()
    }
    LaunchedEffect(showMeter) {
        if (showMeter && locationAccess == LocationAccessState.LOCATION_PERMISSION_NONE && !locationPreferences.getBoolean("location_permission_requested_once", false)) {
            locationPreferences.edit().putBoolean("location_permission_requested_once", true).apply()
            requestLocationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
    DisposableEffect(lifecycleOwner, showMeter) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                locationAccessRevision++
                if (showMeter && hasLocationPermission(context) && locationManager.isLocationEnabled) meterController.resumeLocation()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val openAppSettings = { context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))) }
    val openLocationSettings = { context.startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
    val openFareSettings = { showSettings = true; showMeter = false }
    DisposableEffect(showMeter) {
        activity.requestedOrientation = if (showMeter) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        if (showMeter) meterController.ensureMeasurementRunning()
        onDispose { }
    }
    BackHandler(enabled = true) {
        when {
            showSettings -> { showSettings = false; showMeter = true }
            showDebug -> { showDebug = false; showMeter = true }
            showReissue -> { showReissue = false; showMeter = true }
            showMeter -> activity.finish()
            else -> showMeter = true
        }
    }
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        when {
            showSettings -> FareRuleSettingsScreen(padding) { showSettings = false; showMeter = true }
            showDebug -> MeasurementDebugScreen(padding, { showMeter = true; showDebug = false }, openFareSettings)
            showReissue -> ReissueScreen(padding) { showReissue = false; showMeter = true }
            showMeter -> MeterScreen(padding, meterRuntime, meterController, openFareSettings, { showDebug = true; showMeter = false }, { showReissue = true; showMeter = false }, locationAccess, { locationPreferences.edit().putBoolean("location_permission_requested_once", true).apply(); requestLocationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }, openAppSettings, openLocationSettings)
            else -> MeterScreen(padding, meterRuntime, meterController)
        }
    }
}

@Composable
private fun ReissueScreen(contentPadding: androidx.compose.foundation.layout.PaddingValues, onBack: () -> Unit) {
    val context = LocalContext.current
    var date by remember { mutableStateOf(LocalDate.now(ZoneId.of("Asia/Tokyo"))) }
    var receipts by remember { mutableStateOf<List<ReceiptData>>(emptyList()) }
    var selected by remember { mutableStateOf<ReceiptData?>(null) }
    val artifacts = remember { ReceiptArtifactStore(context) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(date) {
        val db = DutyDatabase.build(context)
        try { receipts = ReceiptRepository(db).findForServiceDate(date.toString()); selected = null } finally { db.close() }
    }
    Column(Modifier.fillMaxSize().padding(contentPadding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { date = date.minusDays(1) }) { Text("前日") }
            Text(date.toString(), modifier = Modifier.padding(12.dp))
            Button(onClick = { date = date.plusDays(1) }) { Text("翌日") }
        }
        receipts.forEach { receipt ->
            Button(onClick = { selected = receipt }, modifier = Modifier.fillMaxWidth()) {
                Text("No.${receipt.tripNumber ?: "-"}  ${receipt.pickupAt.toString().substring(11, 16)} → ${receipt.dropoffAt?.toString()?.substring(11, 16) ?: "取消"}  ${receipt.totalFareYen}円")
            }
        }
        selected?.let { receipt ->
            Text(ReceiptTextRenderer.render(receipt))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { val file = artifacts.writeText(receipt); context.startActivity(Intent.createChooser(artifacts.share(file, "text/plain"), "TXT共有")) }) { Text("TXT共有") }
                Button(onClick = { scope.launch {
                    runCatching {
                        val file = withContext(Dispatchers.IO) {
                            val db = DutyDatabase.build(context)
                            try {
                                val trips = ReceiptRepository(db).findForSession(receipt.dutySessionId)
                                val samples = DrivingSampleRepository(db).findByDutySessionId(receipt.dutySessionId)
                                val events = samples.minOfOrNull { it.timestamp.toEpochMilli() }?.let { start -> samples.maxOfOrNull { it.timestamp.toEpochMilli() }?.let { end -> SafetyEventRepository(db).getEventsBetween(start, end + 1_000) } } ?: emptyList()
                                artifacts.writeSafetyReportPdf(SafetyReportData.from(java.time.Instant.now(), samples, events, trips))
                            } finally { db.close() }
                        }
                        context.startActivity(Intent.createChooser(artifacts.share(file, "application/pdf"), "安全運転レポートを共有"))
                    }
                } }) { Text("安全運転レポート") }
                Button(onClick = { scope.launch { runCatching { val file = withContext(Dispatchers.IO) { artifacts.writePdf(receipt) }; context.startActivity(Intent.createChooser(artifacts.share(file, "application/pdf"), "走行レシートを共有")) } } }) { Text("走行レシート") }
                Button(onClick = { context.startActivity(artifacts.view(artifacts.writePng(receipt))) }) { Text("PNG表示") }
                Button(onClick = { val file = artifacts.writePng(receipt); context.startActivity(Intent.createChooser(artifacts.share(file, "image/png"), "PNG共有")) }) { Text("PNG共有") }
            }
        }
        Button(onClick = onBack) { Text("戻る") }
    }
}

@Composable
private fun MeasurementDebugScreen(contentPadding: androidx.compose.foundation.layout.PaddingValues, showMeter: () -> Unit, showSettings: () -> Unit) {
    val context = LocalContext.current
    val runtime by MeterRuntimeStore.snapshot.collectAsState()
    val controller = remember { DutyMeasurementController(context) }
    var permissionGranted by remember {
        mutableStateOf(hasLocationPermission(context))
    }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted -> permissionGranted = granted.values.any { it } }
    val fareRule = FareRuleStore(context).load()

    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    Column(Modifier.fillMaxSize().padding(contentPadding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("GPS計測デバッグ")
        Text("位置情報権限: ${if (permissionGranted) "許可" else "未許可"}")
        if (!permissionGranted) Button(onClick = { requestPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) { Text("権限を許可") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = permissionGranted && runtime.dutyLifecycle != jp.usuiensan.taxijokemeter.domain.meter.DutyLifecycle.ACTIVE, onClick = { controller.start(RoadMode.GENERAL, OccupancyState.EMPTY, fareRule) }) { Text("計測開始") }
            Button(enabled = runtime.dutyLifecycle == jp.usuiensan.taxijokemeter.domain.meter.DutyLifecycle.ACTIVE, onClick = controller::stop) { Text("計測停止") }
        }
        Text("GPS health: ${runtime.gpsHealth}")
        Text("最新accuracy: ${runtime.latestAccuracyMeters?.let { "%.1f m".format(it) } ?: "未取得"}")
        Text("最新reported speed: ${runtime.latestSpeedMps?.let { "%.1f m/s".format(it) } ?: "不明"}")
        Text("保存sample件数: ${runtime.savedSampleCount}")
        Text("RoadMode: ${runtime.roadMode}")
        if (isLandscape) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoadMode.entries.forEach { mode -> Button(enabled = runtime.dutyLifecycle == jp.usuiensan.taxijokemeter.domain.meter.DutyLifecycle.ACTIVE, onClick = { controller.updateState(mode, if (runtime.meterState == jp.usuiensan.taxijokemeter.domain.meter.MeterState.OCCUPIED) OccupancyState.OCCUPIED else OccupancyState.EMPTY) }) { Text(mode.name, maxLines = 1, softWrap = false) } }
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RoadMode.entries.forEach { mode -> Button(enabled = runtime.dutyLifecycle == jp.usuiensan.taxijokemeter.domain.meter.DutyLifecycle.ACTIVE, onClick = { controller.updateState(mode, if (runtime.meterState == jp.usuiensan.taxijokemeter.domain.meter.MeterState.OCCUPIED) OccupancyState.OCCUPIED else OccupancyState.EMPTY) }) { Text(mode.name, maxLines = 1, softWrap = false) } }
            }
        }
        Button(onClick = showMeter) { Text("メーター表示") }
        Button(onClick = showSettings) { Text("運賃設定") }
    }
}

@Composable
private fun FareRuleSettingsScreen(contentPadding: androidx.compose.foundation.layout.PaddingValues, onBack: () -> Unit) {
    val context = LocalContext.current
    val current = FareRuleStore(context).load()
    val chargeSettings = TripChargeSettingsStore(context).load()
    var initialDistance by remember(current) { mutableStateOf(current.initialDistanceMeters.toString()) }
    var initialFare by remember(current) { mutableStateOf(current.initialFareYen.toString()) }
    var distanceIncrement by remember(current) { mutableStateOf(current.distanceIncrementMeters.toString()) }
    var incrementFare by remember(current) { mutableStateOf(current.distanceIncrementFareYen.toString()) }
    var lowSpeedLimit by remember(current) { mutableStateOf(current.lowSpeedLimitKmh.toString()) }
    var lowSpeedSeconds by remember(current) { mutableStateOf(current.lowSpeedTimeIncrementSeconds.toString()) }
    var pickupFee by remember(chargeSettings) { mutableStateOf(chargeSettings.pickupFeeYen.toString()) }
    var reservationFee by remember(chargeSettings) { mutableStateOf(chargeSettings.reservationFeeYen.toString()) }
    var surchargePercent by remember(chargeSettings) { mutableStateOf(chargeSettings.surchargePercent.toString()) }
    var discountPercent by remember(chargeSettings) { mutableStateOf(chargeSettings.discountPercent.toString()) }
    var selectedPresetId by remember(current, chargeSettings) {
        mutableStateOf(FarePresets.BUILT_IN.firstOrNull { preset ->
            preset.rule == current && preset.pickupFeeYen == chargeSettings.pickupFeeYen &&
                preset.reservationFeeYen == chargeSettings.reservationFeeYen &&
                preset.surchargePercent == chargeSettings.surchargePercent &&
                preset.discountPercent == chargeSettings.discountPercent
        }?.id)
    }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().padding(contentPadding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("運賃設定")
        Text("選択中: ${selectedPresetId?.let { id -> FarePresets.BUILT_IN.first { it.id == id }.label } ?: "CUSTOM"}")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FarePresets.BUILT_IN.forEach { preset ->
                Button(onClick = {
                    initialDistance = preset.rule.initialDistanceMeters.toString()
                    initialFare = preset.rule.initialFareYen.toString()
                    distanceIncrement = preset.rule.distanceIncrementMeters.toString()
                    incrementFare = preset.rule.distanceIncrementFareYen.toString()
                    lowSpeedLimit = preset.rule.lowSpeedLimitKmh.toString()
                    lowSpeedSeconds = preset.rule.lowSpeedTimeIncrementSeconds.toString()
                    pickupFee = preset.pickupFeeYen.toString()
                    reservationFee = preset.reservationFeeYen.toString()
                    surchargePercent = preset.surchargePercent.toString()
                    discountPercent = preset.discountPercent.toString()
                    selectedPresetId = preset.id
                    saveFarePreset(context, preset)
                }) { Text(preset.label) }
            }
        }
        FareRuleField("初乗り距離 (m)", initialDistance) { selectedPresetId = null; initialDistance = it }
        FareRuleField("初乗り運賃 (円)", initialFare) { selectedPresetId = null; initialFare = it }
        FareRuleField("加算距離 (m)", distanceIncrement) { selectedPresetId = null; distanceIncrement = it }
        FareRuleField("加算運賃 (円)", incrementFare) { selectedPresetId = null; incrementFare = it }
        FareRuleField("低速判定速度 (km/h)", lowSpeedLimit) { selectedPresetId = null; lowSpeedLimit = it }
        FareRuleField("低速時間加算 (秒)", lowSpeedSeconds) { selectedPresetId = null; lowSpeedSeconds = it }
        FareRuleField("迎車料金 (円)", pickupFee) { selectedPresetId = null; pickupFee = it }
        FareRuleField("予約料金 (円)", reservationFee) { selectedPresetId = null; reservationFee = it }
        FareRuleField("割増率 (%)", surchargePercent) { selectedPresetId = null; surchargePercent = it }
        FareRuleField("割引率 (%)", discountPercent) { selectedPresetId = null; discountPercent = it }
        Text("タクシー系プリセットは参考値です")
        error?.let { Text(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onBack) { Text("戻る") }
            Button(onClick = {
                try {
                    saveFareSettings(context, initialDistance, initialFare, distanceIncrement, incrementFare, lowSpeedLimit, lowSpeedSeconds, pickupFee, reservationFee, surchargePercent, discountPercent)
                    error = null
                    onBack()
                } catch (_: NumberFormatException) {
                    error = "すべて整数で入力してください"
                } catch (_: IllegalArgumentException) {
                    error = "入力値を確認してください"
                }
            }) { Text("保存") }
        }
    }
}

internal fun saveFareSettings(
    context: Context,
    initialDistance: String,
    initialFare: String,
    distanceIncrement: String,
    incrementFare: String,
    lowSpeedLimit: String,
    lowSpeedSeconds: String,
    pickupFee: String,
    reservationFee: String,
    surchargePercent: String = "0",
    discountPercent: String = "0",
) {
    val rule = FareRule(initialDistance.toLong(), initialFare.toLong(), distanceIncrement.toLong(), incrementFare.toLong(), lowSpeedLimit.toInt(), lowSpeedSeconds.toInt())
    val charges = TripChargeSettings(pickupFee.toLong(), reservationFee.toLong(), surchargePercent.toInt(), discountPercent.toInt())
    FareRuleStore(context).save(rule)
    TripChargeSettingsStore(context).save(charges)
}

internal fun saveFarePreset(context: Context, preset: FarePreset) {
    FareRuleStore(context).save(preset.rule)
    TripChargeSettingsStore(context).save(
        TripChargeSettings(preset.pickupFeeYen, preset.reservationFeeYen, preset.surchargePercent, preset.discountPercent),
    )
}

@Composable
private fun FareRuleField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(value, onValueChange, label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
}
