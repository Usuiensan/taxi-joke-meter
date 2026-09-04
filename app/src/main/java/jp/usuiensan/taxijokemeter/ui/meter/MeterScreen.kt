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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import jp.usuiensan.taxijokemeter.data.meter.MeterController
import jp.usuiensan.taxijokemeter.data.meter.MeterRuntimeSnapshot
import jp.usuiensan.taxijokemeter.data.meter.CurrentLocationStatus
import jp.usuiensan.taxijokemeter.data.meter.ChargeProgressMode
import jp.usuiensan.taxijokemeter.domain.meter.DutyLifecycle
import jp.usuiensan.taxijokemeter.domain.meter.MeterAction
import jp.usuiensan.taxijokemeter.domain.meter.MeterMenu
import jp.usuiensan.taxijokemeter.domain.meter.MeterState
import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import jp.usuiensan.taxijokemeter.domain.model.FareAdjustmentType
import jp.usuiensan.taxijokemeter.domain.location.LocationAccessState
import jp.usuiensan.taxijokemeter.data.meter.SafetyWarning
import jp.usuiensan.taxijokemeter.R
import java.time.LocalDateTime
import java.time.Duration
import java.time.Instant
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
private val MeterSoftKeyHeight = 56.dp
private val MeterFontFamily = FontFamily(
    Font(R.font.biz_udpgothic_regular),
    Font(R.font.biz_udpgothic_bold, FontWeight.Bold),
)

@Composable
fun MeterScreen(contentPadding: PaddingValues, runtime: MeterRuntimeSnapshot, controller: MeterController, onOpenFareSettings: () -> Unit = {}, onOpenDebug: () -> Unit = {}, onOpenReissue: () -> Unit = {}, locationAccess: LocationAccessState = LocationAccessState.LOCATION_READY, onRequestLocationPermission: () -> Unit = {}, onOpenAppSettings: () -> Unit = {}, onOpenLocationSettings: () -> Unit = {}) {
    CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = MeterFontFamily)) {
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
                    LandscapeMeterLayout(runtime, controller, onOpenFareSettings, onOpenDebug, onOpenReissue)
                    if (locationAccess == LocationAccessState.LOCATION_PERMISSION_NONE || locationAccess == LocationAccessState.PERMISSION_SETTINGS_REQUIRED || locationAccess == LocationAccessState.LOCATION_SERVICE_OFF) {
                        LocationRecoveryPanel(locationAccess, onRequestLocationPermission, onOpenAppSettings, onOpenLocationSettings, Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LandscapeMeterLayout(runtime: MeterRuntimeSnapshot, controller: MeterController, onOpenFareSettings: () -> Unit, onOpenDebug: () -> Unit, onOpenReissue: () -> Unit) {
    MeterReferenceLayout(runtime, controller, onOpenFareSettings, onOpenDebug, onOpenReissue)
}

@Composable
private fun MeterReferenceLayout(runtime: MeterRuntimeSnapshot, controller: MeterController, onOpenFareSettings: () -> Unit, onOpenDebug: () -> Unit, onOpenReissue: () -> Unit) {
    var passengerControlsVisible by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.width(84.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MeterStateLabel(stateLabel(runtime.meterState))
            Spacer(Modifier.weight(1f))
        }

        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (showsDateTime(runtime.meterState, false)) {
                MeterDateHeader()
            }
            BalancePanel(Modifier.weight(1f).fillMaxWidth(), runtime, controller, passengerControlsVisible)
            BottomControls(runtime, controller, onOpenFareSettings, onOpenDebug, onOpenReissue) { passengerControlsVisible = !passengerControlsVisible }
        }

        Column(modifier = Modifier.width(92.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                Triple("空車", MeterRed, MeterAction.EMPTY),
                Triple("実車", Color(0xFF2C9ED9), MeterAction.OCCUPIED),
                Triple("支払", MeterWhite, MeterAction.PAYMENT),
                Triple("合計", MeterWhite, MeterAction.TOTAL),
            ).forEachIndexed { index, (label, color, action) ->
                val enabled = rightKeyEnabled(runtime.meterState)[index]
                if (enabled) MeterButton(
                    label, color, Modifier.weight(1f).fillMaxWidth(),
                    if (color == MeterWhite) Color(0xFF1C2023) else MeterWhite,
                    size = 28.sp,
                    onClick = { controller.dispatch(action) },
                ) else Spacer(Modifier.weight(1f).fillMaxWidth())
            }
            roadModeButtonModes().forEach { mode ->
                MeterButton(
                    roadModeLabel(mode),
                    if (runtime.roadMode == mode) Color(0xFF35C75A) else Color(0xFF1C2023),
                    Modifier.weight(1f).fillMaxWidth(),
                    size = 26.sp,
                    onClick = { controller.setRoadMode(mode) },
                )
            }
        }
    }
}

@Composable
private fun BalancePanel(modifier: Modifier, runtime: MeterRuntimeSnapshot, controller: MeterController, passengerControlsVisible: Boolean) {
    Box(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (runtime.meterState == MeterState.DEADHEAD) {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    Text("回送中", color = MeterWhite, fontSize = 36.sp, modifier = Modifier.align(Alignment.Center))
                }
            } else if (runtime.meterState == MeterState.REST) {
                RestDurationPanel(runtime.restStartedAt)
            } else if (runtime.meterState == MeterState.PAYMENT || runtime.meterState == MeterState.TOTAL) {
                PaymentSummaryPanel(runtime)
            } else if (runtime.meterState == MeterState.OCCUPIED) {
                Column(Modifier.fillMaxWidth()) {
                    if (showsChargeProgressRail(runtime.meterState)) {
                        runtime.chargeProgress?.let { ChargeProgressRail(it.mode, it.fraction) }
                    }
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth().weight(1f)
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    0.0f to Color.Transparent,
                                    0.55f to Color(0xFF07111B),
                                    1.0f to Color(0xFF075A9C),
                                ),
                            ),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        val fare = formatFareAmount(runtime.fareYen)
                        val sizing = fareDisplaySizing(MeterState.OCCUPIED, maxHeight.value, fare.number)
                        Text(
                            "運賃",
                            color = MeterWhite,
                            fontSize = 20.sp,
                            modifier = Modifier.align(Alignment.BottomStart).padding(start = 4.dp, bottom = 8.dp),
                        )
                        Row(
                            modifier = Modifier.align(Alignment.BottomEnd).fillMaxWidth().padding(end = 8.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.End,
                        ) {
                            AutoSizeMeterText(
                                fare.number,
                                modifier = Modifier.weight(1f),
                                color = MeterWhite,
                                style = TextStyle(
                                    fontFamily = MeterFontFamily,
                                    fontSize = sizing.numberSp.sp,
                                    lineHeight = sizing.numberSp.sp * 1.08f,
                                    fontWeight = FontWeight.Light,
                                    fontFeatureSettings = "tnum",
                                    platformStyle = PlatformTextStyle(includeFontPadding = true),
                                ),
                                textAlign = TextAlign.End,
                            )
                            Text(
                                fare.unit,
                                color = MeterWhite,
                                style = TextStyle(
                                    fontFamily = MeterFontFamily,
                                    fontSize = sizing.unitSp.sp,
                                    lineHeight = sizing.unitSp.sp * 1.08f,
                                    fontFeatureSettings = "tnum",
                                    platformStyle = PlatformTextStyle(includeFontPadding = true),
                                ),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip,
                            )
                        }
                        if (passengerControlsVisible) PassengerControls(runtime, controller, Modifier.align(Alignment.TopStart).padding(8.dp))
                        else CurrentLocationDisplay(runtime.currentLocation, Modifier.align(Alignment.TopStart).padding(8.dp))
                        Column(
                            Modifier.align(Alignment.TopEnd).padding(8.dp),
                            horizontalAlignment = Alignment.End,
                        ) {
                            Text("経由地: ${runtime.waypointCount}件", color = MeterWhite, fontSize = 22.sp)
                            if (runtime.pickupFeeApplied) Text("迎車料金: 適用済", color = MeterWhite, fontSize = 22.sp)
                        }
                        runtime.safetyWarning?.takeIf { showsSafetyWarning(runtime.meterState) }?.let { SafetyWarningOverlay(it, Modifier.align(Alignment.TopCenter)) }
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
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().height(150.dp).background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0.0f to MeterBlack,
                    0.68f to MeterBlack,
                    1.0f to Color(0xFF075A9C),
                ),
            ),
        ) {
            Text("合計", color = MeterWhite, fontSize = 20.sp, modifier = Modifier.align(Alignment.BottomStart).padding(start = 4.dp, bottom = 10.dp))
            val fare = formatFareAmount(runtime.fareYen)
            val sizing = fareDisplaySizing(runtime.meterState, maxHeight.value, fare.number)
            Row(
                modifier = Modifier.align(Alignment.BottomEnd).fillMaxWidth().padding(end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.End,
            ) {
                AutoSizeMeterText(
                    fare.number,
                    modifier = Modifier.weight(1f),
                    color = MeterWhite,
                    style = TextStyle(fontFamily = MeterFontFamily, fontSize = sizing.numberSp.sp, lineHeight = sizing.numberSp.sp * 1.08f, fontWeight = FontWeight.Light, fontFeatureSettings = "tnum", platformStyle = PlatformTextStyle(includeFontPadding = true)),
                    textAlign = TextAlign.End,
                )
                Text(
                    fare.unit,
                    color = MeterWhite,
                    style = TextStyle(fontFamily = MeterFontFamily, fontSize = sizing.unitSp.sp, lineHeight = sizing.unitSp.sp * 1.08f, fontFeatureSettings = "tnum", platformStyle = PlatformTextStyle(includeFontPadding = true)),
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
        modifier = Modifier.fillMaxWidth().height(paymentSummaryRowHeightDp().dp).background(Color(0xFF4D5C64)).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MeterWhite, fontSize = paymentSummaryLabelFontSizeSp().sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        AutoSizeMeterText(
            value,
            modifier = Modifier.weight(1f),
            color = MeterWhite,
            style = TextStyle(fontSize = paymentSummaryValueFontSizeSp().sp, fontWeight = FontWeight.Bold),
            minFontSize = 18.sp,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun AutoSizeMeterText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    minFontSize: TextUnit = 48.sp,
    textAlign: TextAlign? = null,
) {
    var fontSize by remember(text, style.fontSize) { mutableStateOf(style.fontSize) }
    Text(
        text,
        modifier = modifier,
        color = color,
        style = style.copy(fontSize = fontSize),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        textAlign = textAlign,
        onTextLayout = { layoutResult ->
            if (layoutResult.hasVisualOverflow && fontSize > minFontSize) {
                fontSize = (fontSize.value - 1f).coerceAtLeast(minFontSize.value).sp
            }
        },
    )
}

@Composable
private fun MeterStateLabel(text: String) {
    val shape = RoundedCornerShape(3.dp)
    val color = stateLabelColor(text)
    Box(
        Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 2.dp)
            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.92f), color)), shape)
            .border(1.dp, Color(0xFFB9E8F4), shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = MeterWhite, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

internal fun stateLabel(state: MeterState) = when (state) {
    MeterState.EMPTY -> "空車"
    MeterState.OCCUPIED -> "賃走"
    MeterState.PAYMENT -> "支払"
    MeterState.TOTAL -> "合計"
    MeterState.DEADHEAD -> "回送"
    MeterState.REST -> "休憩"
    MeterState.PICKUP -> "迎車"
}

internal fun emptyButtonAction(state: MeterState) = MeterAction.EMPTY

internal fun emptySoftKeyAction(label: String) = when (label) {
    "休憩" -> MeterAction.REST
    "迎車" -> MeterAction.PICKUP
    "回送" -> MeterAction.DEADHEAD
    else -> null
}

@Composable
private fun LocationRecoveryPanel(state: LocationAccessState, requestPermission: () -> Unit, openAppSettings: () -> Unit, openLocationSettings: () -> Unit, modifier: Modifier) {
    val (message, label, action) = when (state) {
        LocationAccessState.LOCATION_PERMISSION_NONE -> Triple("位置情報が必要です", "位置情報を許可", requestPermission)
        LocationAccessState.PERMISSION_SETTINGS_REQUIRED -> Triple("位置情報の許可が必要です", "設定を開く", openAppSettings)
        LocationAccessState.LOCATION_SERVICE_OFF -> Triple("位置情報がオフです", "位置情報をオン", openLocationSettings)
        else -> return
    }
    Column(modifier.width(560.dp).background(Color(0xEE101417), RoundedCornerShape(4.dp)).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(message, color = MeterWhite, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        MeterButton(label, Color(0xFF2C9ED9), Modifier.fillMaxWidth().height(64.dp), size = 28.sp, onClick = action)
    }
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
        val iconRadius = iconSize / 2
        val trackStart = iconRadius
        val trackEnd = (maxWidth - iconRadius).coerceAtLeast(trackStart)
        val geometry = chargeRailGeometry(trackStart.value, (trackEnd - trackStart).value, fraction)
        val density = LocalDensity.current
        Canvas(Modifier.fillMaxWidth().height(28.dp)) {
            val y = size.height / 2
            val progressColor = if (mode == ChargeProgressMode.TIME) TimeBlue else DistanceGreen
            val radius = 14f
            val trackStartX = with(density) { trackStart.toPx() }
            val trackEndX = with(density) { trackEnd.toPx() }
            val knobCenterX = with(density) { geometry.knobCenterX.dp.toPx() }
            drawRoundRect(MeterWhite, topLeft = Offset(trackStartX, y - radius), size = androidx.compose.ui.geometry.Size(trackEndX - trackStartX, radius * 2), cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius))
            drawRoundRect(progressColor, topLeft = Offset(trackStartX, y - radius), size = androidx.compose.ui.geometry.Size(with(density) { geometry.coloredProgressEndX.dp.toPx() } - trackStartX, radius * 2), cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius))
            drawRoundRect(MeterWhite, topLeft = Offset(with(density) { geometry.whiteRemainderStartX.dp.toPx() }, y - radius), size = androidx.compose.ui.geometry.Size(trackEndX - knobCenterX, radius * 2), cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius))
            for (index in 1 until 10) {
                val x = trackStartX + (trackEndX - trackStartX) * index / 10f
                drawLine(Color(0xFF647078), Offset(x, y - 8f), Offset(x, y + 8f), strokeWidth = 2f, cap = StrokeCap.Butt)
            }
        }
        Box(
            Modifier.requiredSize(iconSize)
                .graphicsLayer { translationX = with(density) { geometry.knobCenterX.dp.toPx() - iconRadius.toPx() } }
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

internal data class ChargeRailGeometry(
    val knobCenterX: Float,
    val coloredProgressEndX: Float,
    val whiteRemainderStartX: Float,
)

internal fun chargeRailGeometry(trackStartX: Float, trackWidth: Float, fraction: Float): ChargeRailGeometry {
    val knobCenterX = trackStartX + trackWidth.coerceAtLeast(0f) * fraction.coerceIn(0f, 1f)
    return ChargeRailGeometry(knobCenterX, knobCenterX, knobCenterX)
}

internal data class FareAmountParts(val number: String, val unit: String)

internal fun formatFareAmount(yen: Long) = FareAmountParts(yen.formatYen(), "円")

internal data class FareDisplaySizing(val numberSp: Float, val unitSp: Float)

internal fun fareDisplaySizing(state: MeterState, availableHeightDp: Float, number: String): FareDisplaySizing {
    val base = (availableHeightDp * if (state == MeterState.OCCUPIED) 0.55f else 0.70f)
        .coerceIn(if (state == MeterState.OCCUPIED) 120f else 96f, if (state == MeterState.OCCUPIED) 150f else 132f)
    val digitScale = when {
        number.length <= 3 -> 1f
        number.length <= 5 -> 0.92f
        else -> 0.82f
    }
    val numberSp = base * digitScale
    return FareDisplaySizing(numberSp, numberSp * 0.4f)
}

internal fun roadModeButtonModes() = listOf(RoadMode.GENERAL, RoadMode.HIGHWAY1, RoadMode.HIGHWAY2)

internal fun roadModeLabel(mode: RoadMode) = when (mode) {
    RoadMode.GENERAL -> "一般"
    RoadMode.HIGHWAY1 -> "専用道"
    RoadMode.HIGHWAY2 -> "高速"
}

internal fun showsSafetyWarning(state: MeterState) = true

@Composable
private fun SafetyWarningOverlay(warning: SafetyWarning, modifier: Modifier) {
    Box(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 30.dp)
            .background(Color(0xCC101417), RoundedCornerShape(4.dp)).padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(warning.text, color = MeterWhite, fontSize = 26.sp, textAlign = TextAlign.Center, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
    }
}

private fun Long.formatYen() = "%,d".format(this)

@Composable
private fun PassengerControls(runtime: MeterRuntimeSnapshot, controller: MeterController, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("人員", color = MeterWhite, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        val canDecrease = runtime.passengerCount > 1
        MeterButton(
            "−",
            if (canDecrease) Color(0xFF2C9ED9) else Color(0xFF4D5C64),
            Modifier.width(60.dp).height(60.dp),
            size = 40.sp,
            onClick = if (canDecrease) ({ controller.changePassengerCount(runtime.passengerCount - 1) }) else null,
        )
        Text("${runtime.passengerCount}", color = MeterWhite, fontSize = 56.sp, fontWeight = FontWeight.Bold)
        val canIncrease = runtime.passengerCount < 9
        MeterButton(
            "+",
            if (canIncrease) Color(0xFF2C9ED9) else Color(0xFF4D5C64),
            Modifier.width(60.dp).height(60.dp),
            size = 40.sp,
            onClick = if (canIncrease) ({ controller.changePassengerCount(runtime.passengerCount + 1) }) else null,
        )
    }
}

@Composable
private fun CurrentLocationDisplay(location: jp.usuiensan.taxijokemeter.data.meter.CurrentLocationDisplayState, modifier: Modifier) {
    Column(modifier) {
        location.roadName?.let { Text(it, color = MeterWhite, fontSize = 26.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        location.areaText?.let { Text(it, color = MeterWhite, fontSize = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        location.facilityName?.takeIf { it != location.areaText && it != location.roadName }?.let { Text(it, color = MeterWhite, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        if (location.roadName == null && location.areaText == null && location.facilityName == null) Text(
            when (location.status) {
                CurrentLocationStatus.GPS_LOADING -> "位置情報を取得中…"
                CurrentLocationStatus.GEOCODING -> "住所を取得中…"
                CurrentLocationStatus.GEOCODE_UNAVAILABLE -> "住所を取得できません"
                CurrentLocationStatus.GPS_UNAVAILABLE -> "位置情報を取得できません"
                CurrentLocationStatus.AVAILABLE -> "住所を取得できません"
            },
            color = MeterWhite, fontSize = 22.sp,
        )
    }
}

@Composable
private fun RestDurationPanel(startedAt: Instant?) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(startedAt) {
        while (startedAt != null) {
            now = Instant.now()
            kotlinx.coroutines.delay(60_000L)
        }
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("休憩時間", color = MeterWhite, fontSize = 30.sp)
        Text(formatRestDuration(startedAt, now), color = MeterWhite, fontSize = 52.sp, fontWeight = FontWeight.Bold)
    }
}

internal fun formatRestDuration(startedAt: Instant?, now: Instant): String {
    val minutes = startedAt?.let { Duration.between(it, now).toMinutes().coerceAtLeast(0) } ?: 0
    return "%d時間%02d分".format(minutes / 60, minutes % 60)
}

@Composable
private fun BottomControls(runtime: MeterRuntimeSnapshot, controller: MeterController, onOpenFareSettings: () -> Unit, onOpenDebug: () -> Unit, onOpenReissue: () -> Unit, onTogglePassengerControls: () -> Unit) {
    val rows = if (runtime.meterState == MeterState.EMPTY) emptyMenuRows() else listOf(softKeySlots(runtime.meterState, runtime.menu == MeterMenu.EMPTY_MENU))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEachIndexed { index, label ->
                    val isRestRelease = runtime.meterState == MeterState.REST && label == "解除"
                    val slotModifier = Modifier.weight(1f).height(if (isRestRelease) 72.dp else MeterSoftKeyHeight)
                    if (label == null) {
                        Spacer(slotModifier)
                    } else {
                        val adjustmentOn = (label == "割増" && runtime.fareAdjustmentType == FareAdjustmentType.SURCHARGE) || (label == "割引" && runtime.fareAdjustmentType == FareAdjustmentType.DISCOUNT)
                        MeterButton(
                            when {
                                runtime.meterState == MeterState.EMPTY && label == "予約" -> reservationLabel(runtime.pendingReservation)
                                runtime.meterState in setOf(MeterState.OCCUPIED, MeterState.PAYMENT) && label == "迎車" -> pickupFeeLabel(runtime.pickupFeeApplied)
                                adjustmentOn -> "$label ✓"
                                else -> label
                            },
                            if (index == 0) Color(0xFFB6D744) else if (index == 1) Color(0xFF1AA4D1) else MeterWhite,
                            slotModifier,
                            if (index < 2) Color(0xFF16200C) else Color(0xFF1C2023),
                            size = if (isRestRelease) 28.sp else 26.sp,
                            onClick = when {
                                runtime.meterState == MeterState.EMPTY && emptySoftKeyAction(label) != null -> ({ controller.dispatch(emptySoftKeyAction(label)!!) })
                                runtime.meterState == MeterState.EMPTY && label == "予約" -> ({ controller.toggleReservation() })
                                runtime.meterState == MeterState.EMPTY && label == "回送" -> ({ controller.dispatch(MeterAction.DEADHEAD) })
                                runtime.meterState == MeterState.EMPTY && label == "デバッグ" -> ({ controller.dispatch(MeterAction.BACK); onOpenDebug() })
                                runtime.meterState == MeterState.EMPTY && label == "再発行" -> onOpenReissue
                                runtime.meterState == MeterState.EMPTY && label == "運賃制" -> onOpenFareSettings
                                runtime.meterState == MeterState.OCCUPIED && label == "人員" -> onTogglePassengerControls
                                runtime.meterState == MeterState.OCCUPIED && label == "経由地" -> ({ controller.addWaypoint() })
                                runtime.meterState == MeterState.OCCUPIED && label == "迎車" -> ({ controller.togglePickupFee() })
                                runtime.meterState == MeterState.OCCUPIED && label == "割増" -> ({ controller.toggleFareAdjustment(FareAdjustmentType.SURCHARGE) })
                                runtime.meterState == MeterState.OCCUPIED && label == "割引" -> ({ controller.toggleFareAdjustment(FareAdjustmentType.DISCOUNT) })
                                runtime.meterState == MeterState.REST && label == "解除" -> ({ controller.dispatch(MeterAction.REST_RELEASE) })
                                runtime.meterState == MeterState.DEADHEAD && label == "解除" -> ({ controller.dispatch(MeterAction.RELEASE) })
                                else -> null
                            },
                        )
                    }
                }
            }
        }
    }
}

internal fun emptyMenuRows(): List<List<String?>> = listOf(
    listOf("休憩", "迎車", "予約", "回送", null),
    listOf("レポート", "再発行", "運賃制", "デバッグ", null),
)

internal fun reservationLabel(pending: Boolean) = if (pending) "予約 ✓" else "予約"
internal fun pickupFeeLabel(applied: Boolean) = if (applied) "迎車 ✓" else "迎車"

internal fun softKeySlots(state: MeterState, emptyMenu: Boolean = false): List<String?> = when {
    state == MeterState.EMPTY -> emptyMenuRows().flatten()
    else -> when (state) {
    MeterState.OCCUPIED -> listOf("人員", "経由地", "迎車", "割増", "割引")
    MeterState.PAYMENT -> listOf("迎車", null, null, null, null)
    MeterState.TOTAL -> listOf(null, null, null, null, null)
    MeterState.DEADHEAD -> listOf(null, null, null, null, "解除")
    MeterState.REST -> listOf(null, null, null, null, "解除")
    MeterState.PICKUP -> listOf(null, null, null, null, null)
    MeterState.EMPTY -> error("unreachable")
    }
}

internal fun softKeyLabels(state: MeterState) = softKeySlots(state).filterNotNull()

internal fun softKeyAction(state: MeterState, label: String) = when {
    state == MeterState.REST && label == "解除" -> MeterAction.REST_RELEASE
    state == MeterState.DEADHEAD && label == "解除" -> MeterAction.RELEASE
    else -> null
}

internal fun rightKeyEnabled(state: MeterState): List<Boolean> = when (state) {
    MeterState.EMPTY -> listOf(false, true, false, false, true, true, true)
    MeterState.OCCUPIED -> listOf(true, false, true, false, true, true, true)
    MeterState.PAYMENT -> listOf(false, true, false, true, true, true, true)
    MeterState.PICKUP -> listOf(true, true, false, false, true, true, true)
    MeterState.REST -> listOf(false, true, false, false, true, true, true)
    MeterState.TOTAL -> listOf(true, true, false, false, true, true, true)
    MeterState.DEADHEAD -> listOf(false, false, false, false, true, true, true)
}

internal fun paymentSummaryTitle(state: MeterState) = when (state) {
    MeterState.PAYMENT -> "支払"
    MeterState.TOTAL -> "合計"
    else -> ""
}

internal fun paymentSummaryRows(fareYen: Long) = listOf(
    "運賃料金計" to "${fareYen.formatYen()}円",
    "通行料他" to "0円",
)

internal fun paymentSummaryRowHeightDp() = 60
internal fun paymentSummaryLabelFontSizeSp() = 30
internal fun paymentSummaryValueFontSizeSp() = 34

internal fun showsChargeProgressRail(state: MeterState) = state == MeterState.OCCUPIED

@Composable
private fun MeterButton(text: String, background: Color, modifier: Modifier = Modifier, textColor: Color = MeterWhite, size: androidx.compose.ui.unit.TextUnit = 17.sp, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(2.dp)
    val highlight = Color(
        red = background.red + (1f - background.red) * 0.35f,
        green = background.green + (1f - background.green) * 0.35f,
        blue = background.blue + (1f - background.blue) * 0.35f,
        alpha = background.alpha,
    )
    val shade = Color(background.red * 0.55f, background.green * 0.55f, background.blue * 0.55f, background.alpha)
    Box(modifier = modifier.then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)).shadow(8.dp, shape).background(Brush.verticalGradient(listOf(highlight, background, shade)), shape).border(2.dp, Color(0xFF273238), shape).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(text, color = textColor, fontWeight = FontWeight.Bold, fontSize = size, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
    }
}
