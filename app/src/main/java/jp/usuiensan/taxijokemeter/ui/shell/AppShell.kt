package jp.usuiensan.taxijokemeter.ui.shell

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import jp.usuiensan.taxijokemeter.data.location.DutyMeasurementController
import jp.usuiensan.taxijokemeter.data.location.MeasurementDebugStore
import jp.usuiensan.taxijokemeter.domain.measurement.OccupancyState
import jp.usuiensan.taxijokemeter.domain.model.EnergyCostProfile
import jp.usuiensan.taxijokemeter.domain.model.EnergyEfficiency
import jp.usuiensan.taxijokemeter.domain.model.FuelType
import jp.usuiensan.taxijokemeter.domain.model.Powertrain
import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import jp.usuiensan.taxijokemeter.domain.fare.FuelCostFareFactory
import jp.usuiensan.taxijokemeter.ui.meter.MeterScreen

@Composable
fun AppShell() {
    var showMeter by remember { mutableStateOf(false) }
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        if (showMeter) MeterScreen(padding)
        else MeasurementDebugScreen(padding) { showMeter = true }
    }
}

@Composable
private fun MeasurementDebugScreen(contentPadding: androidx.compose.foundation.layout.PaddingValues, showMeter: () -> Unit) {
    val context = LocalContext.current
    val snapshot by MeasurementDebugStore.snapshot.collectAsState()
    val controller = remember { DutyMeasurementController(context) }
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> permissionGranted = granted }
    val fareRule = remember { FuelCostFareFactory.create(EnergyCostProfile(FuelType.REGULAR, Powertrain.ICE, 180.0, EnergyEfficiency.KmPerLiter(15.0)), 1_000, 200, 10) }

    Column(Modifier.fillMaxSize().padding(contentPadding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("GPS計測デバッグ")
        Text("位置情報権限: ${if (permissionGranted) "許可" else "未許可"}")
        if (!permissionGranted) Button(onClick = { requestPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION) }) { Text("権限を許可") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = permissionGranted && !snapshot.running, onClick = { controller.start(RoadMode.GENERAL, OccupancyState.EMPTY, fareRule) }) { Text("計測開始") }
            Button(enabled = snapshot.running, onClick = controller::stop) { Text("計測停止") }
        }
        Text("GPS health: ${snapshot.health}")
        Text("最新accuracy: ${snapshot.latestAccuracyMeters?.let { "%.1f m".format(it) } ?: "未取得"}")
        Text("最新reported speed: ${snapshot.latestSpeedMps?.let { "%.1f m/s".format(it) } ?: "不明"}")
        Text("保存sample件数: ${snapshot.savedSampleCount}")
        Text("RoadMode: ${snapshot.roadMode}")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoadMode.entries.forEach { mode -> Button(enabled = snapshot.running, onClick = { controller.updateState(mode, OccupancyState.EMPTY) }) { Text(mode.name) } }
        }
        Button(onClick = showMeter) { Text("メーター表示") }
    }
}
