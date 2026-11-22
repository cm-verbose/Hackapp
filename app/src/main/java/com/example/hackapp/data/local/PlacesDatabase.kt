package com.example.hackapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Read-only reference database built by the ETL (etl/) and shipped in
 * src/main/assets/databases/places.db.
 *
 * To ship new data: re-run the ETL, copy the new places.db, bump [VERSION] here AND
 * DB_VERSION in etl/mtl_food_etl/pipeline.py. With fallbackToDestructiveMigration Room
 * deletes the old copy and re-copies the new asset on the next launch.
 *
 * Do not store user data (favourites, saved trips) here: it would be wiped on every data
 * update. Use a separate database for that.
 */
@Database(
    entities = [
        PlaceEntity::class, PlaceCuisineEntity::class, PlaceDietaryEntity::class,
        AreaEntity::class, CuisineGroupEntity::class, CuisineGroupMemberEntity::class,
        PriceBracketEntity::class, VenueTypeSlotEntity::class,
    ],
    version = PlacesDatabase.VERSION,
    exportSchema = true,
)
abstract class PlacesDatabase : RoomDatabase() {
    abstract fun placeDao(): PlaceDao
    abstract fun referenceDao(): ReferenceDao

    companion object {
        const val VERSION = 4
        private const val ASSET = "databases/places.db"

        @Volatile private var instance: PlacesDatabase? = null

        fun get(context: Context): PlacesDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, PlacesDatabase::class.java, "places.db")
                    .createFromAsset(ASSET)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
