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
import android.util.Log
import androidx.core.app.NotificationCompat
import jp.usuiensan.taxijokemeter.MainActivity
import jp.usuiensan.taxijokemeter.data.duty.DutyDatabase
import jp.usuiensan.taxijokemeter.data.measurement.DrivingSampleRepository
import jp.usuiensan.taxijokemeter.domain.measurement.DrivingSample
import jp.usuiensan.taxijokemeter.domain.measurement.MeasurementQualityPolicy
import jp.usuiensan.taxijokemeter.domain.measurement.OccupancyState
import jp.usuiensan.taxijokemeter.domain.measurement.TripMeasurementAccumulator
import jp.usuiensan.taxijokemeter.domain.model.FareRule
import jp.usuiensan.taxijokemeter.domain.model.RoadMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

class LocationMeasurementService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var locationManager: LocationManager
    private lateinit var database: DutyDatabase
    private lateinit var repository: DrivingSampleRepository
    private lateinit var accumulator: TripMeasurementAccumulator
    private lateinit var mapper: LocationSampleMapper
    private var sampleState = DrivingSampleState("", RoadMode.GENERAL, OccupancyState.EMPTY)
    private var previous: DrivingSample? = null
    private var savedCount = 0

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val sample = mapper.map(location, sampleState, Instant.now())
            val accumulation = previous?.let { accumulator.add(it, sample) }
            previous = sample
            scope.launch {
                repository.save(sample)
                savedCount += 1
                Log.i(TAG, "GPS状態=${sample.measurementQuality} 速度=${sample.speedMps ?: "不明"}m/s accuracy=${sample.accuracyMeters}m RoadMode=${sample.roadMode} 累積課金距離=${accumulation?.chargeableDistanceMeters ?: 0}m 累積低速時間=${accumulation?.lowSpeedSeconds ?: 0}秒 保存件数=$savedCount")
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
        accumulator = TripMeasurementAccumulator(FareRule(1_000, 500, 200, 100, 10, 75))
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) {
            previous = null
            savedCount = 0
            sampleState = DrivingSampleState(
                intent.getStringExtra(EXTRA_SESSION_ID) ?: return START_NOT_STICKY,
                RoadMode.valueOf(intent.getStringExtra(EXTRA_ROAD_MODE) ?: RoadMode.GENERAL.name),
                OccupancyState.valueOf(intent.getStringExtra(EXTRA_OCCUPANCY_STATE) ?: OccupancyState.EMPTY.name),
            )
            startForegroundCompat()
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "GPS状態=権限なし")
                stopSelf()
                return START_NOT_STICKY
            }
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0f, listener, Looper.getMainLooper())
            Log.i(TAG, "GPS状態=計測開始 sessionId=${sampleState.dutySessionId}")
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        locationManager.removeUpdates(listener)
        scope.cancel()
        database.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

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

    companion object {
        const val ACTION_START = "jp.usuiensan.taxijokemeter.action.START_MEASUREMENT"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_ROAD_MODE = "road_mode"
        const val EXTRA_OCCUPANCY_STATE = "occupancy_state"
        private const val CHANNEL_ID = "duty_measurement"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "LocationMeasurement"
    }
}
