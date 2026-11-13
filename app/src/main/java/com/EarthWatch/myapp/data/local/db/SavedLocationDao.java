package com.EarthWatch.myapp.data.local.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface SavedLocationDao {
    @Query("SELECT * FROM saved_locations ORDER BY createdAtMillis DESC")
    List<SavedLocationEntity> getAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(SavedLocationEntity location);

    @Query("DELETE FROM saved_locations WHERE locationId = :locationId")
    void delete(String locationId);
}
