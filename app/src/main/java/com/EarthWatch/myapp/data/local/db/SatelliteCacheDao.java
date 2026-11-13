package com.EarthWatch.myapp.data.local.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface SatelliteCacheDao {
    @Query("SELECT * FROM satellite_group_cache WHERE groupCode = :groupCode LIMIT 1")
    SatelliteCacheEntity get(String groupCode);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void put(SatelliteCacheEntity record);
}
