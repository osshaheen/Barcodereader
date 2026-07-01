package com.example.multibarcode.data

/**
 * Plain data models stored in Firestore. Ids are Firestore document ids (String).
 * All fields have defaults so Firestore can deserialize them, but we map manually
 * in [AppRepository] to keep control over number/id handling.
 */
data class Product(
    val id: String = "",
    val barcode: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val createdAt: Long = 0,
    /** Google Drive file id of the product image, if any. */
    val imageFileId: String? = null,
)

data class Customer(
    val id: String = "",
    val name: String = "",
    val phone: String? = null,
    val note: String? = null,
    val createdAt: Long = 0,
)

/** One line inside an order (stored as a map in the order document). */
data class OrderItem(
    val productId: String? = null,
    val barcode: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val quantity: Int = 0,
    val lineTotal: Double = 0.0,
)

data class Order(
    val id: String = "",
    val customerId: String? = null,
    val total: Double = 0.0,
    val itemCount: Int = 0,
    val note: String? = null,
    val createdAt: Long = 0,
    val items: List<OrderItem> = emptyList(),
)

data class Payment(
    val id: String = "",
    val customerId: String = "",
    val amount: Double = 0.0,
    val note: String? = null,
    val createdAt: Long = 0,
)

/** Projection: a customer together with its computed outstanding balance. */
data class CustomerRow(
    val id: String,
    val name: String,
    val phone: String?,
    val note: String?,
    val createdAt: Long,
    val balance: Double,
)

/** A pending login request from a non-allowlisted user, shown to the super admin. */
data class AccessRequest(
    val email: String,
    val requestedAt: Long,
)

/** An allowlisted user; [admin] means they are a super admin who can manage others. */
data class AllowedUser(
    val email: String,
    val admin: Boolean,
)

/** Projection: an order together with its customer's name (null for a cash sale). */
data class OrderRow(
    val id: String,
    val customerName: String?,
    val total: Double,
    val itemCount: Int,
    val createdAt: Long,
)
