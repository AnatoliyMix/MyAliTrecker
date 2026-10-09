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

    @Query("SELECT * FROM order_items WHERE trackingNumber = :trackingNumber")
    suspend fun getItemsByTracking(trackingNumber: String): List<OrderItemEntity>

    @Query("SELECT * FROM order_items WHERE orderId = :orderId")
    suspend fun getItemsByOrderId(orderId: String): List<OrderItemEntity>

    @Query("UPDATE order_items SET trackingNumber = :newTrackingNumber WHERE orderId = :orderId")
    suspend fun updateTrackingNumberForOrder(orderId: String, newTrackingNumber: String)

    @Query("UPDATE parcels SET isConsolidated = :isConsolidated WHERE trackingNumber = :trackingNumber")
    suspend fun updateConsolidatedStatus(trackingNumber: String, isConsolidated: Boolean)

    @Query("DELETE FROM parcels WHERE trackingNumber LIKE 'PENDING_%' AND trackingNumber NOT IN (SELECT DISTINCT trackingNumber FROM order_items)")
    suspend fun deleteEmptyPendingParcels()

    @Transaction
    suspend fun insertOrConsolidateParcel(parcel: ParcelEntity, items: List<OrderItemEntity>, orderIdsToConsolidate: List<String>) {
        insertParcel(parcel)

        // 1. Move any items previously stored under pending or previous tracking numbers for these order IDs
        for (orderId in orderIdsToConsolidate) {
            updateTrackingNumberForOrder(orderId, parcel.trackingNumber)
        }

        // 2. Insert any new items found in this email
        if (items.isNotEmpty()) {
            insertItems(items)
        }

        // 3. Check if this parcel now contains multiple distinct order IDs -> mark as consolidated
        val allItems = getItemsByTracking(parcel.trackingNumber)
        val distinctOrders = allItems.map { it.orderId }.distinct()
        if (distinctOrders.size > 1 || parcel.isConsolidated) {
            updateConsolidatedStatus(parcel.trackingNumber, true)
        }

        // 4. Remove empty pending placeholder parcels
        deleteEmptyPendingParcels()
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
