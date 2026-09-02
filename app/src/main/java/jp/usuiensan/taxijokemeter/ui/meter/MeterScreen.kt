package jp.usuiensan.taxijokemeter.ui.meter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val MeterBlack = Color(0xFF080B0E)
private val MeterBlue = Color(0xFF087FA5)
private val MeterWhite = Color(0xFFF0F1ED)
private val MeterRed = Color(0xFFE53B35)

@Composable
fun MeterScreen(contentPadding: PaddingValues) {
    Column(
        modifier = Modifier.fillMaxSize().background(MeterBlack).padding(contentPadding).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MeterLabel("合計", MeterBlue, Modifier.weight(1f))
            MeterLabel("空車", MeterRed, Modifier.weight(1f))
        }
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).border(2.dp, MeterBlue).background(Color(0xFF06131A)).padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.End) {
                Text("燃料実費", color = MeterRed, fontSize = 20.sp, modifier = Modifier.fillMaxWidth())
                Text("0", color = MeterWhite, fontSize = 72.sp, fontWeight = FontWeight.Light)
                Text("円", color = MeterWhite, fontSize = 24.sp)
            }
        }
        MeterRow("走行距離", "0 m")
        MeterRow("低速時間", "0 秒")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("実車", "支払", "迎車", "高速", "割増").forEach { MeterLabel(it, MeterBlue, Modifier.weight(1f)) }
        }
    }
}

@Composable private fun MeterRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF25343D)).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MeterWhite, fontSize = 18.sp)
        Text(value, color = MeterWhite, fontSize = 18.sp)
    }
}

@Composable private fun MeterLabel(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(color).padding(vertical = 9.dp), contentAlignment = Alignment.Center) { Text(text, color = MeterWhite, fontWeight = FontWeight.Bold, fontSize = 17.sp) }
}
