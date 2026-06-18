package com.example.data.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.example.data.entity.Invoice
import com.example.data.entity.InvoiceItem

data class InvoiceWithItems(
    @Embedded val invoice: Invoice,
    @Relation(
        parentColumn = "id",
        entityColumn = "invoiceId"
    )
    val items: List<InvoiceItem>
)
