package com.example.myalitrecker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "parcels")
data class ParcelEntity(
    @PrimaryKey
    val trackingNumber: String,
    val status: String = "Обрабатывается",
    val carrier: String = "AliExpress",
    val lastUpdated: Long = System.currentTimeMillis(),
    val lastLocation: String? = null
)
