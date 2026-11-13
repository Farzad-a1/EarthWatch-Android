package com.EarthWatch.myapp.data.local.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {SavedLocationEntity.class, SatelliteCacheEntity.class}, version = 1, exportSchema = false)
public abstract class EarthWatchDatabase extends RoomDatabase {
    private static volatile EarthWatchDatabase INSTANCE;

    public abstract SavedLocationDao savedLocationDao();
    public abstract SatelliteCacheDao satelliteCacheDao();

    public static EarthWatchDatabase getInstance(@NonNull Context context) {
        EarthWatchDatabase database = INSTANCE;
        if (database == null) {
            synchronized (EarthWatchDatabase.class) {
                database = INSTANCE;
                if (database == null) {
                    database = Room.databaseBuilder(context.getApplicationContext(), EarthWatchDatabase.class, "earthwatch.db").build();
                    INSTANCE = database;
                }
            }
        }
        return database;
    }
}
