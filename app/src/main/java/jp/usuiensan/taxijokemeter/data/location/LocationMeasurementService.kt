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
import android.os.IBinder
import android.os.Looper
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import jp.usuiensan.taxijokemeter.MainActivity
import jp.usuiensan.taxijokemeter.data.duty.DutyDatabase
import jp.usuiensan.taxijokemeter.data.fare.FareRuleStore
import jp.usuiensan.taxijokemeter.data.measurement.DrivingSampleRepository
import jp.usuiensan.taxijokemeter.domain.measurement.DrivingSample
import jp.usuiensan.taxijokemeter.domain.measurement.MeasurementQualityPolicy
import jp.usuiensan.taxijokemeter.domain.measurement.OccupancyState
import jp.usuiensan.taxijokemeter.domain.model.FareRule
import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import jp.usuiensan.taxijokemeter.data.meter.MeterRuntimeStore
import jp.usuiensan.taxijokemeter.data.meter.ChargeProgressMapper
import jp.usuiensan.taxijokemeter.data.meter.ChargeProgressMode
import jp.usuiensan.taxijokemeter.domain.fare.FareCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

class LocationMeasurementService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var locationManager: LocationManager
    private lateinit var database: DutyDatabase
    private lateinit var repository: DrivingSampleRepository
    private lateinit var mapper: LocationSampleMapper
    private lateinit var fareRule: FareRule
    private var fareMeasurement: FareMeasurementSession? = null
    private var runtimeState = MeasurementRuntimeState(DrivingSampleState("", RoadMode.GENERAL, OccupancyState.EMPTY))
    private val savedSampleCount = AtomicInteger()
    private val healthTracker = LocationHealthTracker()
    private val healthHandler = Handler(Looper.getMainLooper())
    private var lastLoggedHealth: LocationHealth? = null

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            location.elapsedRealtimeNanos.takeIf { it > 0L }?.let(healthTracker::recordFix)
            val sample = mapper.map(location, runtimeState.sampleState, Instant.now())
            val accumulation = fareMeasurement?.add(sample)
            runtimeState = runtimeState.copy(previousSample = sample)
            MeasurementDebugStore.update { it.copy(health = healthTracker.healthAt(SystemClock.elapsedRealtimeNanos()), latestAccuracyMeters = sample.accuracyMeters, latestSpeedMps = sample.speedMps, roadMode = sample.roadMode.name) }
            MeterRuntimeStore.update { current -> current.copy(
                roadMode = sample.roadMode,
                gpsHealth = healthTracker.healthAt(SystemClock.elapsedRealtimeNanos()),
                latestAccuracyMeters = sample.accuracyMeters,
                latestSpeedMps = sample.speedMps,
                chargeableDistanceMeters = accumulation?.chargeableDistanceMeters ?: current.chargeableDistanceMeters,
                lowSpeedSeconds = accumulation?.lowSpeedSeconds ?: current.lowSpeedSeconds,
                fareYen = accumulation?.let { FareCalculator.calculate(fareRule, it.chargeableDistanceMeters, it.lowSpeedSeconds) } ?: current.fareYen,
                chargeProgress = if (sample.isValid) {
                    val values = accumulation ?: jp.usuiensan.taxijokemeter.domain.measurement.MeasurementAccumulation(current.chargeableDistanceMeters, current.lowSpeedSeconds)
                    val mode = sample.speedMps?.let { speed ->
                        if (speed * 3.6 < fareRule.lowSpeedLimitKmh) ChargeProgressMode.TIME else ChargeProgressMode.DISTANCE
                    } ?: current.chargeProgress?.mode ?: ChargeProgressMode.DISTANCE
                    jp.usuiensan.taxijokemeter.data.meter.ChargeProgressUiState(mode, ChargeProgressMapper.fraction(fareRule, values.chargeableDistanceMeters, values.lowSpeedSeconds))
                } else current.chargeProgress,
            ) }
            scope.launch {
                repository.save(sample)
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
        database = DutyDatabase.build(this)
        repository = DrivingSampleRepository(database)
        mapper = LocationSampleMapper(MeasurementQualityPolicy(20.0, Duration.ofSeconds(5)))
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) {
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
            savedSampleCount.set(0)
            MeasurementDebugStore.update(MeasurementDebugSnapshot(running = true, roadMode = runtimeState.sampleState.roadMode.name))
            MeterRuntimeStore.update { it.copy(dutyLifecycle = jp.usuiensan.taxijokemeter.domain.meter.DutyLifecycle.ACTIVE, meterState = jp.usuiensan.taxijokemeter.domain.meter.MeterState.EMPTY, roadMode = runtimeState.sampleState.roadMode) }
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "GPS状態=権限なし")
                stopSelf()
                return START_NOT_STICKY
            }
            startForegroundCompat()
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0f, listener, Looper.getMainLooper())
            healthHandler.post(healthWatchdog)
            Log.i(TAG, "GPS状態=計測開始 sessionId=${runtimeState.sampleState.dutySessionId}")
        } else if (intent?.action == ACTION_UPDATE_STATE) {
            if (!MeasurementDebugStore.snapshot.value.running) return START_NOT_STICKY
            runtimeState = runtimeState.updateState(
                RoadMode.valueOf(intent.getStringExtra(EXTRA_ROAD_MODE) ?: runtimeState.sampleState.roadMode.name),
                OccupancyState.valueOf(intent.getStringExtra(EXTRA_OCCUPANCY_STATE) ?: runtimeState.sampleState.occupancyState.name),
            )
            fareMeasurement?.setOccupancyState(runtimeState.sampleState.occupancyState)
            MeasurementDebugStore.update { it.copy(roadMode = runtimeState.sampleState.roadMode.name) }
            MeterRuntimeStore.update { it.copy(roadMode = runtimeState.sampleState.roadMode) }
        } else if (intent?.action == ACTION_BEGIN_FARE) {
            val session = ensureFareMeasurementInitialized() ?: return START_NOT_STICKY
            runtimeState = runtimeState.updateState(runtimeState.sampleState.roadMode, OccupancyState.OCCUPIED)
            session.begin(OccupancyState.OCCUPIED)
            session.setOccupancyState(OccupancyState.OCCUPIED)
            MeterRuntimeStore.update { it.copy(meterState = jp.usuiensan.taxijokemeter.domain.meter.MeterState.OCCUPIED) }
            Log.i(TAG, "料金計測=開始")
        } else if (intent?.action == ACTION_END_FARE) {
            val result = fareMeasurement?.end()
            result?.let { accumulation -> fareRuleOrNull()?.let { rule -> MeterRuntimeStore.update { state -> state.copy(fareYen = FareCalculator.calculate(rule, accumulation.chargeableDistanceMeters, accumulation.lowSpeedSeconds), chargeableDistanceMeters = accumulation.chargeableDistanceMeters, lowSpeedSeconds = accumulation.lowSpeedSeconds) } } }
            Log.i(TAG, "料金計測=終了 結果=${result ?: "未開始"}")
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        locationManager.removeUpdates(listener)
        healthHandler.removeCallbacks(healthWatchdog)
        scope.cancel()
        database.close()
        MeasurementDebugStore.update { it.copy(running = false) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
            healthHandler.postDelayed(this, 1_000L)
        }
    }

    companion object {
        const val ACTION_START = "jp.usuiensan.taxijokemeter.action.START_MEASUREMENT"
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
        private const val TAG = "LocationMeasurement"
    }
}
