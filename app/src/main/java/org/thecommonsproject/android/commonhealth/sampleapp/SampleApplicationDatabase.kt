package org.thecommonsproject.android.commonhealth.sampleapp

import androidx.room.Database
import androidx.room.RoomDatabase
import org.thecommonsproject.android.common.keyvaluestore.room.KeyValueEntry
import org.thecommonsproject.android.common.keyvaluestore.room.KeyValueEntryDao

@Database(entities = [
    KeyValueEntry::class
], version = 1, exportSchema = false)
abstract class SampleApplicationDatabase : RoomDatabase() {
    abstract fun keyValueEntryDao(): KeyValueEntryDao
}