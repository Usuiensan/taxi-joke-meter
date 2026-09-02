package jp.usuiensan.taxijokemeter.data.duty

import androidx.room.TypeConverter
import java.time.Instant

class DutyConverters {
    @TypeConverter fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
}
