package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@Entity(tableName = "invoices")
@JsonClass(generateAdapter = true)
data class Invoice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceNumber: String,
    val timestamp: Long = System.currentTimeMillis(),
    val totalAmount: Double,
    val isDebt: Boolean,
    val customerId: Long? = null,
    val paidAmount: Double = 0.0
)
