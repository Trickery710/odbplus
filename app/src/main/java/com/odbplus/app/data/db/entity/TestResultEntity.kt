package com.odbplus.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "test_results", indices = [Index("vin")])
data class TestResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vin: String,
    val testName: String,
    val result: String,
    val notes: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
