package com.EarthWatch.myapp.data.local.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "satellite_group_cache")
public class SatelliteCacheEntity {
    @PrimaryKey
    @NonNull
    public String groupCode;
    @NonNull
    public String recordsJson;
    public long fetchedAtMillis;

    public SatelliteCacheEntity(@NonNull String groupCode, @NonNull String recordsJson, long fetchedAtMillis) {
        this.groupCode = groupCode;
        this.recordsJson = recordsJson;
        this.fetchedAtMillis = fetchedAtMillis;
    }
}
