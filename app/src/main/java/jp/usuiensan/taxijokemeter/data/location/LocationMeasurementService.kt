package jp.usuiensan.taxijokemeter.data.location

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.os.Looper
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import jp.usuiensan.taxijokemeter.MainActivity
import jp.usuiensan.taxijokemeter.data.duty.DutyDatabase
import jp.usuiensan.taxijokemeter.data.duty.DutyRecordRepository
import jp.usuiensan.taxijokemeter.data.fare.FareRuleStore
import jp.usuiensan.taxijokemeter.data.measurement.DrivingSampleRepository
import jp.usuiensan.taxijokemeter.data.safety.SafetyEventRepository
import jp.usuiensan.taxijokemeter.data.meter.AudioCuePlayer
import jp.usuiensan.taxijokemeter.data.meter.MeterAudioCue
import jp.usuiensan.taxijokemeter.data.meter.MeterAudioResources
import jp.usuiensan.taxijokemeter.data.meter.MediaPlayerAudioCueSink
import jp.usuiensan.taxijokemeter.data.meter.toMeterAudioCue
import jp.usuiensan.taxijokemeter.domain.measurement.DrivingSample
import jp.usuiensan.taxijokemeter.domain.measurement.MeasurementQualityPolicy
import jp.usuiensan.taxijokemeter.domain.measurement.OccupancyState
import jp.usuiensan.taxijokemeter.domain.measurement.MeasurementQuality
import jp.usuiensan.taxijokemeter.domain.location.ReverseGeocoder
import jp.usuiensan.taxijokemeter.domain.location.reportAddressForAccuracy
import jp.usuiensan.taxijokemeter.domain.location.PRECISE_MAX_METERS
import jp.usuiensan.taxijokemeter.domain.location.TOWN_MAX_METERS
import jp.usuiensan.taxijokemeter.domain.measurement.RestDistanceAccumulator
import jp.usuiensan.taxijokemeter.domain.model.FareRule
import jp.usuiensan.taxijokemeter.domain.model.totalFareYen
import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import jp.usuiensan.taxijokemeter.domain.safety.SafetyEventDetector
import jp.usuiensan.taxijokemeter.domain.safety.SafetyEventSignal
import jp.usuiensan.taxijokemeter.data.location.ImuMotionTracker
import jp.usuiensan.taxijokemeter.data.meter.MeterRuntimeStore
import jp.usuiensan.taxijokemeter.data.meter.toMeterRuntimeState
import jp.usuiensan.taxijokemeter.data.meter.safetyWarningText
import jp.usuiensan.taxijokemeter.data.meter.ChargeProgressMapper
import jp.usuiensan.taxijokemeter.data.meter.ChargeProgressMode
import jp.usuiensan.taxijokemeter.data.meter.ChargeProgressModeController
import jp.usuiensan.taxijokemeter.data.meter.CurrentLocationDisplayState
import jp.usuiensan.taxijokemeter.data.meter.CurrentLocationStatus
import jp.usuiensan.taxijokemeter.data.meter.shouldRefreshCurrentLocation
import jp.usuiensan.taxijokemeter.domain.fare.FareCalculator
import jp.usuiensan.taxijokemeter.domain.meter.MeterAction
import jp.usuiensan.taxijokemeter.domain.meter.MeterStateMachine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

class LocationMeasurementService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @OptIn(ExperimentalCoroutinesApi::class)
    private val sampleProcessingDispatcher = Dispatchers.IO.limitedParallelism(1)
    private lateinit var locationManager: LocationManager
    private lateinit var database: DutyDatabase
    private lateinit var repository: DrivingSampleRepository
    private lateinit var safetyEventRepository: SafetyEventRepository
    private val safetyEventDetector = SafetyEventDetector()
    private val imuMotionTracker = ImuMotionTracker()
    private lateinit var sensorManager: SensorManager
    private lateinit var audioCuePlayer: AudioCuePlayer
    private lateinit var mapper: LocationSampleMapper
    private lateinit var fareRule: FareRule
    private var fareMeasurement: FareMeasurementSession? = null
    private var runtimeState = MeasurementRuntimeState(DrivingSampleState("", RoadMode.GENERAL, OccupancyState.EMPTY))
    private val savedSampleCount = AtomicInteger()
    private val healthTracker = LocationHealthTracker()
    private val healthHandler = Handler(Looper.getMainLooper())
    private var lastLoggedHealth: LocationHealth? = null
    private var lastGeocodedAtMillis: Long? = null
    private var lastGeocodedLatitude: Double? = null
    private var lastGeocodedLongitude: Double? = null
    private val geocodeInFlight = java.util.concurrent.atomic.AtomicBoolean(false)
    private var hasValidGpsFix = false
    private var latestGpsLocation: Location? = null
    private lateinit var currentLocationGeocoder: ReverseGeocoder
    private lateinit var placesFacilitySearcher: GooglePlacesFacilitySearcher
    private var lastPlacesAtMillis: Long? = null
    private var lastPlacesLatitude: Double? = null
    private var lastPlacesLongitude: Double? = null
    private var chargeModeController: ChargeProgressModeController? = null

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val nowElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            if (location.provider == LocationManager.NETWORK_PROVIDER && shouldIgnoreNetworkFix(
                    latestGpsLocation?.elapsedRealtimeNanos?.takeIf { it > 0L },
                    latestGpsLocation?.accuracy?.toDouble()?.takeIf { latestGpsLocation?.hasAccuracy() == true },
                    nowElapsedRealtimeNanos,
                )) return
            val isGpsFix = location.provider == LocationManager.GPS_PROVIDER
            if (isGpsFix) latestGpsLocation = location
            location.elapsedRealtimeNanos.takeIf { it > 0L }?.let(healthTracker::recordFix)
            val sample = mapper.map(location, runtimeState.sampleState, Instant.now()).let {
                if (isGpsFix) it else it.copy(measurementQuality = MeasurementQuality.POOR_ACCURACY)
            }
            if (isGpsFix && sample.isDisplayValid) hasValidGpsFix = true
            if (sample.isDisplayValid && shouldRefreshCurrentLocation(lastGeocodedAtMillis, lastGeocodedLatitude, lastGeocodedLongitude, SystemClock.elapsedRealtime(), sample.latitude, sample.longitude) && geocodeInFlight.compareAndSet(false, true)) {
                MeterRuntimeStore.update { it.copy(currentLocation = it.currentLocation.copy(status = CurrentLocationStatus.GEOCODING)) }
                val latitude = sample.latitude
                val longitude = sample.longitude
                scope.launch {
                    try {
                        val result = try { currentLocationGeocoder.reverseGeocode(latitude, longitude) } catch (exception: CancellationException) { throw exception } catch (_: Exception) { null }
                        lastGeocodedAtMillis = SystemClock.elapsedRealtime()
                        lastGeocodedLatitude = latitude
                        lastGeocodedLongitude = longitude
                        MeterRuntimeStore.update { state ->
                            state.copy(currentLocation = if (result == null || (result.roadName == null && result.areaText == null && result.facilityName == null)) {
                                state.currentLocation.copy(status = CurrentLocationStatus.GEOCODE_UNAVAILABLE)
                            } else if (sample.accuracyMeters > TOWN_MAX_METERS) {
                                state.currentLocation.copy(status = CurrentLocationStatus.AVAILABLE)
                            } else {
                                val area = reportAddressForAccuracy(result, sample.accuracyMeters)
                                CurrentLocationDisplayState(
                                    roadName = result.roadName.takeIf { sample.accuracyMeters <= PRECISE_MAX_METERS } ?: state.currentLocation.roadName,
                                    areaText = area ?: state.currentLocation.areaText,
                                    facilityName = result.facilityName.takeIf { sample.accuracyMeters <= PRECISE_MAX_METERS } ?: state.currentLocation.facilityName,
                                    status = CurrentLocationStatus.AVAILABLE,
                                )
                            })
                        }
                    } finally {
                        geocodeInFlight.set(false)
                    }
                }
            }
            if (isGpsFix && sample.isDisplayValid && sample.accuracyMeters <= PRECISE_MAX_METERS && shouldRefreshPlaces(lastPlacesAtMillis, lastPlacesLatitude, lastPlacesLongitude, SystemClock.elapsedRealtime(), sample.latitude, sample.longitude)) {
                lastPlacesAtMillis = SystemClock.elapsedRealtime()
                lastPlacesLatitude = sample.latitude
                lastPlacesLongitude = sample.longitude
                placesFacilitySearcher.search(sample.latitude, sample.longitude, sample.accuracyMeters) { facilityName ->
                    if (facilityName != null) MeterRuntimeStore.update { state ->
                        state.copy(currentLocation = state.currentLocation.copy(facilityName = facilityName))
                    }
                }
            }
            val accumulation = if (isGpsFix && sample.occupancyState == OccupancyState.OCCUPIED) fareMeasurement?.add(sample) else null
            if (isGpsFix) runtimeState = runtimeState.advance(sample)
            val restId = MeterRuntimeStore.snapshot.value.restIntervalId.takeIf { sample.occupancyState == OccupancyState.REST }
            val restDistance = runtimeState.restDistanceMeters.takeIf { restId != null }
            MeasurementDebugStore.update { it.copy(health = healthTracker.healthAt(SystemClock.elapsedRealtimeNanos()), latestAccuracyMeters = sample.accuracyMeters, latestSpeedMps = sample.speedMps, roadMode = sample.roadMode.name) }
            MeterRuntimeStore.update { current -> current.copy(
                roadMode = sample.roadMode,
                gpsHealth = healthTracker.healthAt(SystemClock.elapsedRealtimeNanos()),
                latestAccuracyMeters = sample.accuracyMeters,
                latestSpeedMps = sample.speedMps,
                chargeableDistanceMeters = accumulation?.chargeableDistanceMeters ?: current.chargeableDistanceMeters,
                actualDistanceMeters = accumulation?.actualDistanceMeters ?: current.actualDistanceMeters,
                lowSpeedSeconds = accumulation?.lowSpeedSeconds ?: current.lowSpeedSeconds,
                meteredFareYen = accumulation?.let { FareCalculator.calculate(fareRule, it.chargeableDistanceMeters, it.lowSpeedSeconds) } ?: current.meteredFareYen,
                fareYen = accumulation?.let { totalFareYen(FareCalculator.calculate(fareRule, it.chargeableDistanceMeters, it.lowSpeedSeconds), current.fareAdjustmentType, current.fareAdjustmentPercent, current.fareChargesYen) } ?: current.fareYen,
                restDistanceMeters = restDistance?.toLong() ?: current.restDistanceMeters,
                chargeProgress = if (sample.isFareContinuable) {
                    val values = accumulation ?: jp.usuiensan.taxijokemeter.domain.measurement.MeasurementAccumulation(current.chargeableDistanceMeters, current.lowSpeedSeconds, current.actualDistanceMeters)
                    val mode = chargeModeController?.update(sample.speedMps, sample.timestamp.toEpochMilli()) ?: current.chargeProgress?.mode ?: ChargeProgressMode.DISTANCE
                    jp.usuiensan.taxijokemeter.data.meter.ChargeProgressUiState(mode, ChargeProgressMapper.fraction(fareRule, values.chargeableDistanceMeters, values.lowSpeedSeconds))
                } else current.chargeProgress,
            ) }
            scope.launch(sampleProcessingDispatcher) {
                repository.save(sample)
                if (restId != null && restDistance != null) {
                    val records = DutyRecordRepository(database)
                    records.updateRestDistance(restId, restDistance.toLong())
                    if (restDistance >= REST_DISTANCE_LIMIT_METERS) {
                        records.finishRestInterval(restId, Instant.now(), "AUTO_DISTANCE_1KM")
                        healthHandler.post { finishRestAutomatically(restId, restDistance) }
                    }
                }
                val imuAcceleration = imuMotionTracker.longitudinalAcceleration(sample.bearingDegrees)
                if (isGpsFix) safetyEventDetector.consume(sample, imuAcceleration, imuMotionTracker.state == ImuMountState.STABLE).forEach { signal ->
                    when (signal) {
                        is SafetyEventSignal.Started -> {
                            Log.d(TAG, "[SAFETY-DETECT] type=${signal.type} detectionId=${signal.detectionId} detectedAt=${signal.locationTimestampMillis} currentSpeedMps=${signal.currentSpeedMps} previousSpeedMps=${signal.previousSpeedMps} accelerationMps2=${signal.accelerationMps2} threshold=${signal.threshold} locationTimestamp=${signal.locationTimestampMillis} elapsedRealtime=${signal.elapsedRealtimeMillis}")
                            audioCuePlayer.enqueue(signal.type.toMeterAudioCue(), "LocationMeasurementService.SafetyEventSignal.Started")
                            MeterRuntimeStore.showSafetyWarning(
                                safetyWarningText(signal.type, signal.accelerationMps2, signal.speedKmh),
                                SystemClock.elapsedRealtime(),
                            )
                        }
                        is SafetyEventSignal.Completed -> {
                            Log.d(TAG, "[SAFETY-DETECT] completed type=${signal.event.type} eventId=${signal.event.id} detectedAt=${signal.event.startedAtEpochMillis} currentSpeed=unknown previousSpeed=unknown acceleration=unknown threshold=unknown locationTimestamp=${signal.event.endedAtEpochMillis} elapsedRealtime=${signal.event.startedAtElapsedRealtimeMillis}")
                            safetyEventRepository.insert(signal.event)
                        }
                    }
                }
                val count = savedSampleCount.incrementAndGet()
                healthHandler.post {
                    MeasurementDebugStore.update { it.copy(savedSampleCount = count) }
                    MeterRuntimeStore.update { it.copy(savedSampleCount = count) }
                }
                Log.i(TAG, "GPS状態=${healthTracker.healthAt(SystemClock.elapsedRealtimeNanos())} 測位品質=${sample.measurementQuality} 速度=${sample.speedMps ?: "不明"}m/s accuracy=${sample.accuracyMeters}m RoadMode=${sample.roadMode} 累積課金距離=${accumulation?.chargeableDistanceMeters ?: 0}m 累積低速時間=${accumulation?.lowSpeedSeconds ?: 0}秒 保存件数=$count")
            }
        }

        override fun onProviderEnabled(provider: String) { Log.i(TAG, "GPS状態=有効 provider=$provider") }
        override fun onProviderDisabled(provider: String) { Log.w(TAG, "GPS状態=無効 provider=$provider") }
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LocationManager::class.java)
        sensorManager = getSystemService(SensorManager::class.java)
        database = DutyDatabase.build(this)
        repository = DrivingSampleRepository(database)
        safetyEventRepository = SafetyEventRepository(database)
        audioCuePlayer = AudioCuePlayer({ cue -> MeterAudioResources.resourceId(this, cue) }, MediaPlayerAudioCueSink(this), safetyPlaybackGuard = jp.usuiensan.taxijokemeter.data.meter.ProcessSafetyPlaybackGuard)
        mapper = LocationSampleMapper(MeasurementQualityPolicy(20.0, Duration.ofSeconds(5)))
        currentLocationGeocoder = GoogleReverseGeocoder(this)
        placesFacilitySearcher = GooglePlacesFacilitySearcher(this)
        createNotificationChannel()
        registerImuSensors()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Measurement commands must run on the main looper" }
        val action = intent?.action ?: return START_NOT_STICKY
        if (action == ACTION_START) {
            runtimeState = MeasurementRuntimeState(DrivingSampleState(
                intent.getStringExtra(EXTRA_SESSION_ID) ?: return START_NOT_STICKY,
                RoadMode.valueOf(intent.getStringExtra(EXTRA_ROAD_MODE) ?: RoadMode.GENERAL.name),
                OccupancyState.valueOf(intent.getStringExtra(EXTRA_OCCUPANCY_STATE) ?: OccupancyState.EMPTY.name),
            ))
            fareRule = FareRule(
                intent.getLongExtra(EXTRA_INITIAL_DISTANCE_METERS, 0L),
                intent.getLongExtra(EXTRA_INITIAL_FARE_YEN, 0L),
                intent.getLongExtra(EXTRA_DISTANCE_INCREMENT_METERS, 0L),
                intent.getLongExtra(EXTRA_DISTANCE_INCREMENT_FARE_YEN, 0L),
                intent.getIntExtra(EXTRA_LOW_SPEED_LIMIT_KMH, 0),
                intent.getIntExtra(EXTRA_LOW_SPEED_TIME_INCREMENT_SECONDS, 0),
            )
            fareMeasurement = FareMeasurementSession(fareRule)
            chargeModeController = ChargeProgressModeController(fareRule.lowSpeedLimitKmh.toDouble()).also { it.reset() }
            savedSampleCount.set(0)
            hasValidGpsFix = false
            latestGpsLocation = null
            MeterRuntimeStore.update { it.copy(currentLocation = CurrentLocationDisplayState(status = CurrentLocationStatus.GPS_LOADING)) }
            MeasurementDebugStore.update(MeasurementDebugSnapshot(running = true, sessionId = runtimeState.sampleState.dutySessionId, roadMode = runtimeState.sampleState.roadMode.name))
            MeterRuntimeStore.update { it.copy(dutyLifecycle = jp.usuiensan.taxijokemeter.domain.meter.DutyLifecycle.ACTIVE, roadMode = runtimeState.sampleState.roadMode) }
            if (!hasLocationPermission(this)) {
                Log.w(TAG, "GPS状態=権限なし")
                MeasurementDebugStore.update { it.copy(running = false) }
                stopSelf()
                return START_NOT_STICKY
            }
            startForegroundCompat()
            registerLocationProviders()
            healthHandler.post(healthWatchdog)
            Log.i(TAG, "GPS状態=計測開始 sessionId=${runtimeState.sampleState.dutySessionId}")
        } else if (action == ACTION_RESTART_LOCATION) {
            if (!MeasurementDebugStore.snapshot.value.running || !hasLocationPermission(this)) return START_NOT_STICKY
            registerLocationProviders()
            Log.i(TAG, "GPS状態=provider再開 sessionId=${runtimeState.sampleState.dutySessionId}")
        } else if (action == ACTION_UPDATE_STATE) {
            if (!MeasurementDebugStore.snapshot.value.running) return START_NOT_STICKY
            runtimeState = runtimeState.updateState(
                RoadMode.valueOf(intent.getStringExtra(EXTRA_ROAD_MODE) ?: runtimeState.sampleState.roadMode.name),
                OccupancyState.valueOf(intent.getStringExtra(EXTRA_OCCUPANCY_STATE) ?: runtimeState.sampleState.occupancyState.name),
            )
            fareMeasurement?.setOccupancyState(runtimeState.sampleState.occupancyState)
            MeasurementDebugStore.update { it.copy(roadMode = runtimeState.sampleState.roadMode.name) }
            MeterRuntimeStore.update { it.copy(roadMode = runtimeState.sampleState.roadMode) }
        } else if (action == ACTION_BEGIN_FARE) {
            if (fareMeasurement?.isActive == true) return START_NOT_STICKY
            val rule = intent.fareRuleOrNull() ?: fareRuleOrNull() ?: return START_NOT_STICKY
            fareRule = rule
            val session = FareMeasurementSession(rule)
            fareMeasurement = session
            runtimeState = runtimeState.updateState(runtimeState.sampleState.roadMode, OccupancyState.OCCUPIED)
            session.begin(OccupancyState.OCCUPIED)
            session.setOccupancyState(OccupancyState.OCCUPIED)
            MeterRuntimeStore.update { it.copy(meterState = jp.usuiensan.taxijokemeter.domain.meter.MeterState.OCCUPIED) }
            Log.i(TAG, "料金計測=開始")
        } else if (action == ACTION_END_FARE) {
            val result = fareMeasurement?.end()
            result?.let { accumulation -> fareRuleOrNull()?.let { rule ->
                val fare = FareCalculator.calculate(rule, accumulation.chargeableDistanceMeters, accumulation.lowSpeedSeconds)
                MeterRuntimeStore.update { state -> state.copy(fareYen = totalFareYen(fare, state.fareAdjustmentType, state.fareAdjustmentPercent, state.fareChargesYen), meteredFareYen = fare, actualDistanceMeters = accumulation.actualDistanceMeters, chargeableDistanceMeters = accumulation.chargeableDistanceMeters, lowSpeedSeconds = accumulation.lowSpeedSeconds) }
                MeterRuntimeStore.snapshot.value.currentTripId?.let { tripId -> scope.launch { DutyRecordRepository(database).updateTripMeasurement(tripId, accumulation.actualDistanceMeters, accumulation.chargeableDistanceMeters, accumulation.lowSpeedSeconds, fare) } }
            } }
            Log.i(TAG, "料金計測=終了 結果=${result ?: "未開始"}")
        } else return START_NOT_STICKY
        MeasurementDebugStore.update { it.copy(processedCommands = it.processedCommands + MeasurementCommandDebug(
            action,
            runtimeState.sampleState.occupancyState,
            fareMeasurement?.isActive == true,
        )) }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(imuListener)
        locationManager.removeUpdates(listener)
        healthHandler.removeCallbacks(healthWatchdog)
        scope.cancel()
        database.close()
        MeasurementDebugStore.update { it.copy(running = false) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun finishRestAutomatically(restId: String, restDistance: Double) {
        if (runtimeState.sampleState.occupancyState != OccupancyState.REST || MeterRuntimeStore.snapshot.value.restIntervalId != restId) return
        runtimeState = runtimeState.updateState(runtimeState.sampleState.roadMode, OccupancyState.EMPTY)
        MeterRuntimeStore.update { current ->
            val after = MeterStateMachine(current.toMeterRuntimeState()).dispatch(MeterAction.REST_RELEASE)
            current.copy(
                meterState = after.meterState,
                menu = after.menu,
                restIntervalId = null,
                restStartedAt = null,
                restDistanceMeters = restDistance.toLong(),
            )
        }
    }

    private fun ensureFareMeasurementInitialized(): FareMeasurementSession? {
        if (fareMeasurement == null) {
            val rule = fareRuleOrNull() ?: run {
                Log.e(TAG, "料金計測=開始不可 FareRule未初期化")
                return null
            }
            fareRule = rule
            fareMeasurement = FareMeasurementSession(rule)
        }
        return fareMeasurement
    }

    private fun fareRuleOrNull(): FareRule? = if (::fareRule.isInitialized) fareRule else FareRuleStore(this).load()

    private fun Intent.fareRuleOrNull(): FareRule? {
        if (!hasExtra(EXTRA_INITIAL_DISTANCE_METERS) || !hasExtra(EXTRA_INITIAL_FARE_YEN) ||
            !hasExtra(EXTRA_DISTANCE_INCREMENT_METERS) || !hasExtra(EXTRA_DISTANCE_INCREMENT_FARE_YEN) ||
            !hasExtra(EXTRA_LOW_SPEED_LIMIT_KMH) || !hasExtra(EXTRA_LOW_SPEED_TIME_INCREMENT_SECONDS)
        ) return null
        return FareRule(
            getLongExtra(EXTRA_INITIAL_DISTANCE_METERS, 0L),
            getLongExtra(EXTRA_INITIAL_FARE_YEN, 0L),
            getLongExtra(EXTRA_DISTANCE_INCREMENT_METERS, 0L),
            getLongExtra(EXTRA_DISTANCE_INCREMENT_FARE_YEN, 0L),
            getIntExtra(EXTRA_LOW_SPEED_LIMIT_KMH, 0),
            getIntExtra(EXTRA_LOW_SPEED_TIME_INCREMENT_SECONDS, 0),
        )
    }

    private fun startForegroundCompat() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("乗務計測中")
            .setContentText("GPS走行計測を継続しています")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .setOngoing(true)
            .build()
        if (android.os.Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        else startForeground(NOTIFICATION_ID, notification)
    }

    private val imuListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) = imuMotionTracker.onSensorChanged(event)
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    private fun registerImuSensors() {
        listOf(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_ROTATION_VECTOR).forEach { type ->
            sensorManager.getDefaultSensor(type)?.let { sensorManager.registerListener(imuListener, it, SensorManager.SENSOR_DELAY_GAME) }
        }
    }

    private fun registerLocationProviders() {
        if (!locationManager.isLocationEnabled) {
            Log.w(TAG, "GPS状態=端末位置情報OFF")
            return
        }
        val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val providers = buildList {
            if (fine && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
        }
        providers.forEach { provider ->
            locationManager.requestLocationUpdates(provider, 500L, 0f, listener, Looper.getMainLooper())
            Log.i(TAG, "Location provider started provider=$provider permissionFine=$fine")
        }
        if (providers.isEmpty()) Log.w(TAG, "Location provider unavailable")
    }

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "乗務計測", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private val healthWatchdog = object : Runnable {
        override fun run() {
            val health = healthTracker.healthAt(SystemClock.elapsedRealtimeNanos())
            if (health != lastLoggedHealth) {
                Log.i(TAG, "GPS状態=$health")
                lastLoggedHealth = health
            }
            MeasurementDebugStore.update { it.copy(health = health) }
            if (!hasValidGpsFix && health == LocationHealth.UNAVAILABLE) {
                MeterRuntimeStore.update { it.copy(currentLocation = it.currentLocation.copy(status = CurrentLocationStatus.GPS_UNAVAILABLE)) }
            }
            healthHandler.postDelayed(this, 1_000L)
        }
    }

    companion object {
        const val ACTION_START = "jp.usuiensan.taxijokemeter.action.START_MEASUREMENT"
        const val ACTION_RESTART_LOCATION = "jp.usuiensan.taxijokemeter.action.RESTART_LOCATION"
        const val ACTION_UPDATE_STATE = "jp.usuiensan.taxijokemeter.action.UPDATE_MEASUREMENT_STATE"
        const val ACTION_BEGIN_FARE = "jp.usuiensan.taxijokemeter.action.BEGIN_FARE_MEASUREMENT"
        const val ACTION_END_FARE = "jp.usuiensan.taxijokemeter.action.END_FARE_MEASUREMENT"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_ROAD_MODE = "road_mode"
        const val EXTRA_OCCUPANCY_STATE = "occupancy_state"
        const val EXTRA_INITIAL_DISTANCE_METERS = "initial_distance_meters"
        const val EXTRA_INITIAL_FARE_YEN = "initial_fare_yen"
        const val EXTRA_DISTANCE_INCREMENT_METERS = "distance_increment_meters"
        const val EXTRA_DISTANCE_INCREMENT_FARE_YEN = "distance_increment_fare_yen"
        const val EXTRA_LOW_SPEED_LIMIT_KMH = "low_speed_limit_kmh"
        const val EXTRA_LOW_SPEED_TIME_INCREMENT_SECONDS = "low_speed_time_increment_seconds"
        private const val CHANNEL_ID = "duty_measurement"
        private const val NOTIFICATION_ID = 1001
        private const val REST_DISTANCE_LIMIT_METERS = 1_000L
        private const val TAG = "LocationMeasurement"
    }
}
