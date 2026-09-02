package jp.usuiensan.taxijokemeter.ui.shell

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import jp.usuiensan.taxijokemeter.ui.meter.MeterScreen

@Composable
fun AppShell() {
    Scaffold(modifier = Modifier.fillMaxSize()) { padding -> MeterScreen(padding) }
}
