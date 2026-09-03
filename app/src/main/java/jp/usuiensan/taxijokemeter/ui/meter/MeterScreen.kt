package jp.usuiensan.taxijokemeter.ui.meter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import jp.usuiensan.taxijokemeter.data.meter.MeterController
import jp.usuiensan.taxijokemeter.data.meter.MeterRuntimeSnapshot
import jp.usuiensan.taxijokemeter.data.meter.ChargeProgressMode
import jp.usuiensan.taxijokemeter.domain.meter.DutyLifecycle
import jp.usuiensan.taxijokemeter.domain.meter.MeterAction
import jp.usuiensan.taxijokemeter.domain.meter.MeterMenu
import jp.usuiensan.taxijokemeter.domain.meter.MeterState

private val MeterBlack = Color(0xFF080B0E)
private val MeterBlue = Color(0xFF087FA5)
private val MeterWhite = Color(0xFFF0F1ED)
private val MeterRed = Color(0xFFE53B35)
private val DistanceGreen = Color(0xFF57B957)
private val TimeBlue = Color(0xFF2A9FD6)
private val IconBlack = Color(0xFF0A0A0A)
private val MeterLogicalWidth = 900.dp
private val MeterLogicalHeight = 400.dp

@Composable
fun MeterScreen(contentPadding: PaddingValues, runtime: MeterRuntimeSnapshot, controller: MeterController) {
    Box(
        modifier = Modifier.fillMaxSize().background(MeterBlack).padding(contentPadding).padding(10.dp),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val scale = minOf(
                maxWidth.value / MeterLogicalWidth.value,
                maxHeight.value / MeterLogicalHeight.value,
            )
            Box(
                modifier = Modifier.requiredSize(MeterLogicalWidth, MeterLogicalHeight).graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            ) {
                LandscapeMeterLayout(runtime, controller)
            }
        }
    }
}

@Composable
private fun LandscapeMeterLayout(runtime: MeterRuntimeSnapshot, controller: MeterController) {
    MeterReferenceLayout(runtime, controller)
}

@Composable
private fun MeterReferenceLayout(runtime: MeterRuntimeSnapshot, controller: MeterController) {
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
            BalancePanel(Modifier.height(228.dp), runtime, controller)
            Spacer(Modifier.weight(1f))
            BottomControls(runtime, controller)
        }

        Column(modifier = Modifier.width(84.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MeterButton("空車", MeterRed, Modifier.fillMaxWidth(), onClick = { controller.dispatch(MeterAction.EMPTY) })
            MeterButton("実車", Color(0xFF2C9ED9), Modifier.fillMaxWidth(), onClick = { controller.dispatch(MeterAction.OCCUPIED) })
            MeterButton("支払", MeterWhite, Modifier.fillMaxWidth(), Color(0xFF1C2023), onClick = { controller.dispatch(MeterAction.PAYMENT) })
            MeterButton("合計", MeterWhite, Modifier.fillMaxWidth(), Color(0xFF1C2023))
        }
    }
}

@Composable
private fun BalancePanel(modifier: Modifier, runtime: MeterRuntimeSnapshot, controller: MeterController) {
    Box(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (runtime.dutyLifecycle == DutyLifecycle.BEFORE_DEPARTURE) {
                MeterButton("出庫", MeterWhite, Modifier.fillMaxWidth(), Color(0xFF1C2023), onClick = { controller.dispatch(MeterAction.DEPART) })
            } else if (runtime.menu == MeterMenu.EMPTY_MENU) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Text("メニュー", color = MeterWhite)
                    Text("戻る", color = MeterWhite, modifier = Modifier.clickable { controller.dispatch(MeterAction.BACK) })
                }
            } else if (runtime.meterState == MeterState.EMPTY) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Text("メニュー", color = MeterWhite, modifier = Modifier.clickable { controller.dispatch(MeterAction.MENU) })
                    Text("入庫", color = MeterWhite, modifier = Modifier.clickable { controller.dispatch(MeterAction.ARRIVE) })
                }
            } else if (runtime.meterState == MeterState.PAYMENT) {
                Column(Modifier.fillMaxWidth()) {
                    MeterRow("合計", "${runtime.fareYen.formatYen()}円", 22.sp)
                    MeterRow("運賃料金計", "${runtime.fareYen.formatYen()}円", 22.sp)
                    MeterRow("通行料他", "0円", 22.sp)
                }
            } else if (runtime.meterState == MeterState.OCCUPIED) {
                Column(Modifier.fillMaxWidth()) {
                    Text("実車", color = MeterWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    runtime.chargeProgress?.let { ChargeProgressRail(it.mode, it.fraction) }
                    Box(
                        modifier = Modifier.fillMaxWidth().height(128.dp)
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    0.0f to Color.Transparent,
                                    0.55f to Color(0xFF07111B),
                                    1.0f to Color(0xFF075A9C),
                                ),
                            ),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Text(
                            "運賃",
                            color = MeterWhite,
                            fontSize = 20.sp,
                            modifier = Modifier.align(Alignment.BottomStart).padding(start = 4.dp, bottom = 8.dp),
                        )
                        val fare = formatFareAmount(runtime.fareYen)
                        Row(
                            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text(
                                fare.number,
                                color = MeterWhite,
                                style = TextStyle(
                                    fontSize = 76.sp,
                                    lineHeight = 82.sp,
                                    fontWeight = FontWeight.Light,
                                    platformStyle = PlatformTextStyle(includeFontPadding = true),
                                ),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip,
                            )
                            Text(
                                fare.unit,
                                color = MeterWhite,
                                style = TextStyle(
                                    fontSize = 26.sp,
                                    lineHeight = 32.sp,
                                    platformStyle = PlatformTextStyle(includeFontPadding = true),
                                ),
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp).fillMaxWidth().background(MeterWhite))
        }
    }
}

@Composable
private fun MeterRow(label: String, value: String, valueSize: androidx.compose.ui.unit.TextUnit = 32.sp) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF4D5C64)).padding(horizontal = 8.dp, vertical = 0.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MeterWhite, fontSize = 18.sp)
        Text(value, color = MeterWhite, fontSize = valueSize, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
    }
}

@Composable
private fun ChargeProgressRail(mode: ChargeProgressMode, fraction: Float) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(56.dp).background(MeterBlack),
        contentAlignment = Alignment.CenterStart,
    ) {
        val iconSize = 46.dp
        val iconBodySize = 32.dp
        val travel = (maxWidth - iconSize).coerceAtLeast(0.dp)
        val iconOffset = travel * fraction.coerceIn(0f, 1f)
        Canvas(Modifier.fillMaxWidth().height(28.dp)) {
            val y = size.height / 2
            val progressColor = if (mode == ChargeProgressMode.TIME) TimeBlue else DistanceGreen
            val radius = 14f
            drawRoundRect(MeterWhite, topLeft = Offset(0f, y - radius), size = androidx.compose.ui.geometry.Size(size.width, radius * 2), cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius))
            val filledWidth = maxOf(size.width * fraction.coerceIn(0f, 1f), iconSize.toPx() / 2f)
            drawRoundRect(progressColor, topLeft = Offset(0f, y - radius), size = androidx.compose.ui.geometry.Size(filledWidth, radius * 2), cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius))
            repeat(11) { index ->
                val x = size.width * index / 10f
                drawLine(Color(0xFF647078), Offset(x, y - 8f), Offset(x, y + 8f), strokeWidth = 2f)
            }
        }
        Box(
            Modifier.requiredSize(iconSize)
                .graphicsLayer { translationX = iconOffset.toPx() }
                .background(if (mode == ChargeProgressMode.TIME) TimeBlue else DistanceGreen, CircleShape)
                .border(2.dp, MeterBlack, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(if (mode == ChargeProgressMode.TIME) jp.usuiensan.taxijokemeter.R.drawable.charge_time_clock else jp.usuiensan.taxijokemeter.R.drawable.charge_distance_wheel),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(IconBlack),
                modifier = Modifier.requiredSize(iconBodySize),
            )
        }
    }
}

internal data class FareAmountParts(val number: String, val unit: String)

internal fun formatFareAmount(yen: Long) = FareAmountParts(yen.formatYen(), "円")

private fun Long.formatYen() = "%,d".format(this)

@Composable
private fun BottomControls(runtime: MeterRuntimeSnapshot, controller: MeterController) {
    val labels = if (runtime.meterState == MeterState.PAYMENT) listOf("明細", "置換", "立替", "チケット", "定額", "高速") else listOf("早朝", "高割", "障割", "予約", "経由地", "高速")
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { index, label ->
            MeterButton(label, if (index == 0) Color(0xFFB6D744) else if (index == 1) Color(0xFF1AA4D1) else MeterWhite, Modifier.weight(1f), if (index < 2) Color(0xFF16200C) else Color(0xFF1C2023), onClick = if (index == 5) controller::shortRoadModePress else null)
        }
    }
}

@Composable
private fun MeterButton(text: String, background: Color, modifier: Modifier = Modifier, textColor: Color = MeterWhite, size: androidx.compose.ui.unit.TextUnit = 17.sp, onClick: (() -> Unit)? = null) {
    Box(modifier = modifier.then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)).background(background).border(1.dp, Color(0xFF273238)).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(text, color = textColor, fontWeight = FontWeight.Bold, fontSize = size, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
    }
}
