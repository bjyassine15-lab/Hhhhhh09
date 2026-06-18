package com.example.data.util

import com.example.data.entity.*
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BackupData(
    val products: List<Product> = emptyList(),
    val customers: List<Customer> = emptyList(),
    val invoices: List<Invoice> = emptyList(),
    val invoiceItems: List<InvoiceItem> = emptyList(),
    val debtPayments: List<DebtPayment> = emptyList()
)
