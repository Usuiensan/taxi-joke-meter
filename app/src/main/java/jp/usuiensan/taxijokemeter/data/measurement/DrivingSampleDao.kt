package jp.usuiensan.taxijokemeter.data.measurement

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DrivingSampleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sample: DrivingSampleEntity)

    @Query("SELECT * FROM driving_samples ORDER BY timestampEpochSecond ASC, timestampNano ASC")
    suspend fun findAll(): List<DrivingSampleEntity>
}
