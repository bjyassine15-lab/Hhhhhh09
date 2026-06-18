package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@Entity(tableName = "debt_payments")
@JsonClass(generateAdapter = true)
data class DebtPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val amountPaid: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String? = null
)
