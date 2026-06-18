package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.PosDao
import com.example.data.entity.*

@Database(
    entities = [
        Product::class,
        Customer::class,
        Invoice::class,
        InvoiceItem::class,
        DebtPayment::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun posDao(): PosDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smart_pos_database"
                )
                    .fallbackToDestructiveMigration() // safe for rapid development iteration, easily handles schema changes
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
