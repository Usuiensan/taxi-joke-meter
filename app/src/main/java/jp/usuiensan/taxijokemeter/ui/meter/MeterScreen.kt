package jp.usuiensan.taxijokemeter.ui.meter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalConfiguration
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
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    Box(
        modifier = Modifier.fillMaxSize().background(MeterBlack).padding(contentPadding).padding(10.dp),
    ) {
        if (isLandscape) LandscapeMeterLayout() else PortraitMeterLayout()
    }
}

@Composable
private fun PortraitMeterLayout() {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BalancePanel(Modifier.fillMaxWidth().heightIn(min = 128.dp).weight(1f))
        MeterRow("運賃料金計", "14,850円")
        MeterRow("通行料他", "730円")
        MeterRow("決済合計", "-15,580円")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MeterButton("合計", MeterBlue, Modifier.fillMaxWidth())
                MeterButton("1/3", MeterWhite, Modifier.fillMaxWidth(), Color(0xFF1C2023), 20.sp)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MeterButton("空車", MeterRed, Modifier.fillMaxWidth())
                MeterButton("実車", Color(0xFF2C9ED9), Modifier.fillMaxWidth())
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MeterButton("支払", MeterWhite, Modifier.fillMaxWidth(), Color(0xFF1C2023))
                MeterButton("合計", MeterWhite, Modifier.fillMaxWidth(), Color(0xFF1C2023))
            }
        }
        PortraitBottomControls()
    }
}

@Composable
private fun LandscapeMeterLayout() {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.width(84.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MeterButton("合計", MeterBlue, Modifier.fillMaxWidth())
            Spacer(Modifier.weight(1f))
            MeterButton("1/3", MeterWhite, Modifier.fillMaxWidth(), Color(0xFF1C2023), 20.sp)
        }

        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BalancePanel(Modifier.height(180.dp))
            MeterRow("運賃料金計", "14,850円")
            MeterRow("通行料他", "730円")
            MeterRow("決済合計", "-15,580円")
            Spacer(Modifier.weight(1f))
            BottomControls()
        }

        Column(modifier = Modifier.width(84.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MeterButton("空車", MeterRed, Modifier.fillMaxWidth())
            MeterButton("実車", Color(0xFF2C9ED9), Modifier.fillMaxWidth())
            MeterButton("支払", MeterWhite, Modifier.fillMaxWidth(), Color(0xFF1C2023))
            MeterButton("合計", MeterWhite, Modifier.fillMaxWidth(), Color(0xFF1C2023))
        }
    }
}

@Composable
private fun BalancePanel(modifier: Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().border(2.dp, MeterBlue).background(Color(0xFF056B9B)).padding(12.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("残金", color = MeterRed, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("0", color = MeterWhite, fontSize = 76.sp, fontWeight = FontWeight.Light)
                    Text("円", color = MeterWhite, fontSize = 24.sp, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
            Spacer(Modifier.height(6.dp).fillMaxWidth().background(MeterWhite))
        }
    }
}

@Composable
private fun MeterRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF4D5C64)).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MeterWhite, fontSize = 18.sp)
        Text(value, color = MeterWhite, fontSize = 32.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
    }
}

@Composable
private fun BottomControls() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MeterButton("明細", Color(0xFFB6D744), Modifier.weight(1f), Color(0xFF16200C))
        MeterButton("人員", Color(0xFF1AA4D1), Modifier.weight(1f))
        MeterButton("経由地", MeterWhite, Modifier.weight(1f), Color(0xFF1C2023))
        MeterButton("障割", Color(0xFF98A853), Modifier.weight(1f), Color(0xFF1C2023))
        MeterButton("迎車", Color(0xFF1AA4D1), Modifier.weight(1f))
        MeterButton("高速", MeterWhite, Modifier.weight(1f), Color(0xFF1C2023))
    }
}

@Composable
private fun PortraitBottomControls() {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MeterButton("明細", Color(0xFFB6D744), Modifier.weight(1f), Color(0xFF16200C))
            MeterButton("人員", Color(0xFF1AA4D1), Modifier.weight(1f))
            MeterButton("経由地", MeterWhite, Modifier.weight(1f), Color(0xFF1C2023))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MeterButton("障割", Color(0xFF98A853), Modifier.weight(1f), Color(0xFF1C2023))
            MeterButton("迎車", Color(0xFF1AA4D1), Modifier.weight(1f))
            MeterButton("高速", MeterWhite, Modifier.weight(1f), Color(0xFF1C2023))
        }
    }
}

@Composable
private fun MeterButton(text: String, background: Color, modifier: Modifier = Modifier, textColor: Color = MeterWhite, size: androidx.compose.ui.unit.TextUnit = 17.sp) {
    Box(modifier = modifier.background(background).border(1.dp, Color(0xFF273238)).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(text, color = textColor, fontWeight = FontWeight.Bold, fontSize = size)
    }
}
