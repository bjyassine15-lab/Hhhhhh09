package com.example.data.relation

data class CustomerWithDebt(
    val id: Long,
    val name: String,
    val phone: String?,
    val totalDebt: Double
)
