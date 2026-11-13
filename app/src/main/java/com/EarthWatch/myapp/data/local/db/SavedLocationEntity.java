package com.EarthWatch.myapp.data.local.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "saved_locations")
public class SavedLocationEntity {
    @PrimaryKey
    @NonNull
    public String locationId;
    @NonNull
    public String name;
    public double latitudeDeg;
    public double longitudeDeg;
    public long createdAtMillis;

    public SavedLocationEntity(@NonNull String locationId, @NonNull String name, double latitudeDeg, double longitudeDeg, long createdAtMillis) {
        this.locationId = locationId;
        this.name = name;
        this.latitudeDeg = latitudeDeg;
        this.longitudeDeg = longitudeDeg;
        this.createdAtMillis = createdAtMillis;
    }
}
