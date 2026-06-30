package com.example.multibarcode.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A product that can be matched by its barcode while scanning an order. */
@Entity(
    tableName = "products",
    indices = [Index(value = ["barcode"], unique = true)],
)
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val barcode: String,
    val name: String,
    val price: Double,
    val createdAt: Long = 0,
)

/** A customer who can owe money (debts from orders) and make payments. */
@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String? = null,
    val note: String? = null,
    val createdAt: Long = 0,
)

/** A saved order (basket). Its [total] becomes part of the customer's debt. */
@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long?,
    val total: Double,
    val itemCount: Int,
    val note: String? = null,
    val createdAt: Long = 0,
)

/** One line in an order. Stores a snapshot of name/price so history is stable. */
@Entity(
    tableName = "order_items",
    indices = [Index(value = ["orderId"])],
)
data class OrderItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val productId: Long?,
    val barcode: String,
    val name: String,
    val price: Double,
    val quantity: Int,
    val lineTotal: Double,
)

/** A payment made by a customer, reducing their outstanding balance. */
@Entity(
    tableName = "payments",
    indices = [Index(value = ["customerId"])],
)
data class Payment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val amount: Double,
    val note: String? = null,
    val createdAt: Long = 0,
)

/** Projection: a customer row together with its computed outstanding balance. */
data class CustomerRow(
    val id: Long,
    val name: String,
    val phone: String?,
    val note: String?,
    val createdAt: Long,
    val balance: Double,
)

/** Projection: an order together with its customer's name (null for a cash sale). */
data class OrderRow(
    val id: Long,
    val customerName: String?,
    val total: Double,
    val itemCount: Int,
    val createdAt: Long,
)
