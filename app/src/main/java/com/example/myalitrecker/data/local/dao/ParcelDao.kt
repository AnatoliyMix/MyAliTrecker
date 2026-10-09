package com.example.myalitrecker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.myalitrecker.data.local.entity.OrderItemEntity
import com.example.myalitrecker.data.local.entity.ParcelEntity
import com.example.myalitrecker.data.local.model.ParcelWithItems
import kotlinx.coroutines.flow.Flow

@Dao
interface ParcelDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParcel(parcel: ParcelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<OrderItemEntity>)

    @Transaction
    suspend fun insertParcelWithItems(parcel: ParcelEntity, items: List<OrderItemEntity>) {
        insertParcel(parcel)
        insertItems(items)
    }

    @Transaction
    @Query("SELECT * FROM parcels ORDER BY lastUpdated DESC")
    fun getParcelsWithItems(): Flow<List<ParcelWithItems>>

    @Transaction
    @Query("SELECT * FROM parcels WHERE trackingNumber = :trackingNumber")
    fun getParcelWithItems(trackingNumber: String): Flow<ParcelWithItems?>

    @Query("DELETE FROM parcels WHERE trackingNumber = :trackingNumber")
    suspend fun deleteParcel(trackingNumber: String)

    @Query("DELETE FROM order_items WHERE orderId = :orderId AND trackingNumber = :trackingNumber")
    suspend fun deleteOrderItem(orderId: String, trackingNumber: String)
}
