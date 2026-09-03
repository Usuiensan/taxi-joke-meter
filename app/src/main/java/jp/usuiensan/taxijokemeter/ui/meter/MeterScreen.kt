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
import androidx.compose.ui.text.style.TextAlign
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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MeterBlack = Color(0xFF080B0E)
private val MeterBlue = Color(0xFF087FA5)
private val MeterWhite = Color(0xFFF0F1ED)
private val MeterRed = Color(0xFFE53B35)
private val DistanceGreen = Color(0xFF57B957)
private val TimeBlue = Color(0xFF2A9FD6)
private val IconBlack = Color(0xFF0A0A0A)
private val MeterLogicalWidth = 900.dp
private val MeterLogicalHeight = 400.dp
private val MeterSoftKeyHeight = 50.dp

@Composable
fun MeterScreen(contentPadding: PaddingValues, runtime: MeterRuntimeSnapshot, controller: MeterController, onOpenFareSettings: () -> Unit = {}) {
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
                LandscapeMeterLayout(runtime, controller, onOpenFareSettings)
            }
        }
    }
}

@Composable
private fun LandscapeMeterLayout(runtime: MeterRuntimeSnapshot, controller: MeterController, onOpenFareSettings: () -> Unit) {
    MeterReferenceLayout(runtime, controller, onOpenFareSettings)
}

@Composable
private fun MeterReferenceLayout(runtime: MeterRuntimeSnapshot, controller: MeterController, onOpenFareSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.width(84.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MeterStateLabel(stateLabel(runtime.meterState))
            Spacer(Modifier.weight(1f))
        }

        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (showsDateTime(runtime.meterState, runtime.dutyLifecycle == DutyLifecycle.BEFORE_DEPARTURE)) {
                MeterDateHeader()
            }
            BalancePanel(Modifier.height(228.dp), runtime, controller)
            Spacer(Modifier.weight(1f))
            BottomControls(runtime, controller, onOpenFareSettings)
        }

        Column(modifier = Modifier.width(84.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MeterButton("空車", MeterRed, Modifier.fillMaxWidth(), onClick = if (runtime.meterState == MeterState.DEADHEAD) null else ({ controller.dispatch(MeterAction.EMPTY) }))
            MeterButton("実車", Color(0xFF2C9ED9), Modifier.fillMaxWidth(), onClick = if (runtime.meterState == MeterState.DEADHEAD) null else ({ controller.dispatch(MeterAction.OCCUPIED) }))
            MeterButton("支払", MeterWhite, Modifier.fillMaxWidth(), Color(0xFF1C2023), onClick = if (runtime.meterState == MeterState.DEADHEAD) null else ({ controller.dispatch(MeterAction.PAYMENT) }))
            MeterButton("合計", MeterWhite, Modifier.fillMaxWidth(), Color(0xFF1C2023), onClick = if (runtime.meterState == MeterState.DEADHEAD) null else ({ controller.dispatch(MeterAction.TOTAL) }))
        }
    }
}

@Composable
private fun BalancePanel(modifier: Modifier, runtime: MeterRuntimeSnapshot, controller: MeterController) {
    Box(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (runtime.meterState == MeterState.DEADHEAD) {
                Box(Modifier.fillMaxWidth().height(160.dp)) {
                    Text("回送中", color = MeterWhite, fontSize = 36.sp, modifier = Modifier.align(Alignment.Center))
                }
            } else if (runtime.dutyLifecycle == DutyLifecycle.BEFORE_DEPARTURE) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MeterButton("メニュー", MeterWhite, Modifier.width(128.dp), Color(0xFF1C2023), onClick = { controller.dispatch(MeterAction.MENU) })
                    MeterButton("出庫", MeterWhite, Modifier.width(128.dp), Color(0xFF1C2023), onClick = { controller.dispatch(MeterAction.DEPART) })
                }
            } else if (runtime.menu == MeterMenu.EMPTY_MENU) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MeterButton("回送", Color(0xFF2C9ED9), Modifier.width(128.dp), Color(0xFF1C2023), onClick = { controller.dispatch(MeterAction.DEADHEAD) })
                    MeterButton("戻る", MeterWhite, Modifier.width(128.dp), Color(0xFF1C2023), onClick = { controller.dispatch(MeterAction.BACK) })
                }
            } else if (runtime.meterState == MeterState.EMPTY) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MeterButton("メニュー", MeterWhite, Modifier.width(128.dp), Color(0xFF1C2023), onClick = { controller.dispatch(MeterAction.MENU) })
                    MeterButton(if (runtime.dutyLifecycle == DutyLifecycle.BEFORE_DEPARTURE) "出庫" else "入庫", MeterWhite, Modifier.width(128.dp), Color(0xFF1C2023), onClick = { controller.dispatch(if (runtime.dutyLifecycle == DutyLifecycle.BEFORE_DEPARTURE) MeterAction.DEPART else MeterAction.ARRIVE) })
                }
            } else if (runtime.meterState == MeterState.PAYMENT || runtime.meterState == MeterState.TOTAL) {
                PaymentSummaryPanel(runtime)
            } else if (runtime.meterState == MeterState.OCCUPIED) {
                Column(Modifier.fillMaxWidth()) {
                    if (showsChargeProgressRail(runtime.meterState)) {
                        runtime.chargeProgress?.let { ChargeProgressRail(it.mode, it.fraction) }
                    }
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
            if (showsChargeProgressRail(runtime.meterState)) {
                Spacer(Modifier.height(6.dp).fillMaxWidth().background(MeterWhite))
            }
        }
    }
}

@Composable
private fun PaymentSummaryPanel(runtime: MeterRuntimeSnapshot) {
    Column(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(108.dp).background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0.0f to MeterBlack,
                    0.68f to MeterBlack,
                    1.0f to Color(0xFF075A9C),
                ),
            ),
        ) {
            Text("合計", color = MeterWhite, fontSize = 20.sp, modifier = Modifier.align(Alignment.BottomStart).padding(start = 4.dp, bottom = 10.dp))
            val fare = formatFareAmount(runtime.fareYen)
            Row(
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    fare.number,
                    color = MeterWhite,
                    style = TextStyle(fontSize = 68.sp, lineHeight = 74.sp, fontWeight = FontWeight.Light, platformStyle = PlatformTextStyle(includeFontPadding = true)),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
                Text(
                    fare.unit,
                    color = MeterWhite,
                    style = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, platformStyle = PlatformTextStyle(includeFontPadding = true)),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        paymentSummaryRows(runtime.fareYen).forEach { (label, value) -> PaymentDetailRow(label, value) }
    }
}

@Composable
private fun PaymentDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF4D5C64)).padding(horizontal = 8.dp, vertical = 0.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MeterWhite, fontSize = 18.sp)
        Text(value, color = MeterWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
    }
}

@Composable
private fun MeterStateLabel(text: String) {
    Box(Modifier.fillMaxWidth().background(stateLabelColor(text)).padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MeterWhite, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

internal fun stateLabel(state: MeterState) = when (state) {
    MeterState.EMPTY -> "空車"
    MeterState.OCCUPIED -> "賃走"
    MeterState.PAYMENT -> "支払"
    MeterState.TOTAL -> "合計"
    MeterState.DEADHEAD -> "回送"
}

internal fun showsDateTime(state: MeterState, beforeDeparture: Boolean) =
    beforeDeparture || state == MeterState.EMPTY || state == MeterState.DEADHEAD

private fun stateLabelColor(text: String) = when (text) {
    "空車" -> MeterRed
    "賃走", "回送" -> Color(0xFF2C9ED9)
    "支払" -> Color(0xFF35C75A)
    else -> MeterBlue
}

@Composable
private fun MeterDateHeader() {
    val now = LocalDateTime.now()
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth()) {
            Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), color = MeterWhite, fontSize = 24.sp, modifier = Modifier.align(Alignment.TopEnd))
        }
        Text(now.format(DateTimeFormatter.ofPattern("yyyy年 MM月 dd日（E）", Locale.JAPAN)), color = MeterWhite, fontSize = 25.sp)
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
private fun BottomControls(runtime: MeterRuntimeSnapshot, controller: MeterController, onOpenFareSettings: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        softKeySlots(runtime.meterState).forEachIndexed { index, label ->
            val slotModifier = Modifier.weight(1f).height(MeterSoftKeyHeight)
            if (label == null) {
                Spacer(slotModifier)
            } else {
                MeterButton(label, if (index == 0) Color(0xFFB6D744) else if (index == 1) Color(0xFF1AA4D1) else MeterWhite, slotModifier, if (index < 2) Color(0xFF16200C) else Color(0xFF1C2023), onClick = when {
                runtime.meterState == MeterState.EMPTY && label == "運賃制" -> onOpenFareSettings
                runtime.meterState == MeterState.DEADHEAD && label == "解除" -> ({ controller.dispatch(MeterAction.RELEASE) })
                runtime.meterState == MeterState.PAYMENT || runtime.meterState == MeterState.TOTAL -> ({})
                else -> null
                })
            }
        }
    }
}

internal fun softKeySlots(state: MeterState): List<String?> = when (state) {
    MeterState.EMPTY -> listOf("累計", "運賃制", null, null, "高速")
    MeterState.OCCUPIED -> listOf("早朝", "高割", "障割", "予約", "経由地")
    MeterState.PAYMENT, MeterState.TOTAL -> listOf("明細", "置換", "立替", "チケット", "定額")
    MeterState.DEADHEAD -> listOf(null, null, null, null, "解除")
}

internal fun softKeyLabels(state: MeterState) = softKeySlots(state).filterNotNull()

internal fun paymentSummaryTitle(state: MeterState) = when (state) {
    MeterState.PAYMENT -> "支払"
    MeterState.TOTAL -> "合計"
    else -> ""
}

internal fun paymentSummaryRows(fareYen: Long) = listOf(
    "運賃料金計" to "${fareYen.formatYen()}円",
    "通行料他" to "0円",
)

internal fun showsChargeProgressRail(state: MeterState) = state == MeterState.OCCUPIED

@Composable
private fun MeterButton(text: String, background: Color, modifier: Modifier = Modifier, textColor: Color = MeterWhite, size: androidx.compose.ui.unit.TextUnit = 17.sp, onClick: (() -> Unit)? = null) {
    Box(modifier = modifier.then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)).background(background).border(1.dp, Color(0xFF273238)).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(text, color = textColor, fontWeight = FontWeight.Bold, fontSize = size, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
    }
}
