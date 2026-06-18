package com.example.data.model

import com.example.data.entity.Product

data class CartItem(
    val product: Product,
    val quantity: Int
) {
    val totalAmount: Double
        get() = product.salePrice * quantity
}
