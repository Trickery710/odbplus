package com.odbplus.app.data.db.entity

import androidx.room.Entity

@Entity(tableName = "ecu_modules", primaryKeys = ["vin", "moduleId"])
data class EcuModuleEntity(
    val vin: String,
    val moduleId: String,
    val moduleName: String,
    val protocol: String? = null,
    val lastSeen: Long = System.currentTimeMillis()
)
