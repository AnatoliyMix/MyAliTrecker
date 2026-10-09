package com.example.myalitrecker.data.local.model

import androidx.room.Embedded
import androidx.room.Relation
import com.example.myalitrecker.data.local.entity.OrderItemEntity
import com.example.myalitrecker.data.local.entity.ParcelEntity

data class ParcelWithItems(
    @Embedded
    val parcel: ParcelEntity,

    @Relation(
        parentColumn = "trackingNumber",
        entityColumn = "trackingNumber"
    )
    val items: List<OrderItemEntity>
)
