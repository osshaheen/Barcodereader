package com.example.multibarcode.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** A single product line to persist when saving an order. */
data class NewOrderItem(
    val productId: String?,
    val barcode: String,
    val name: String,
    val price: Double,
    val quantity: Int,
)

/**
 * Firestore-backed data access, scoped to the signed-in user:
 * `users/{uid}/{products|customers|orders|payments}`.
 *
 * Firestore's on-device cache is enabled by default, so reads/writes keep working offline
 * and sync automatically when the connection returns. Filtering, pagination and balance
 * computation are done in memory by the ViewModels over these snapshot flows.
 */
class AppRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun col(name: String): CollectionReference? =
        auth.currentUser?.uid?.let { db.collection("users").document(it).collection(name) }

    // ---- Generic snapshot flow ------------------------------------------
    private fun <T> collectionFlow(name: String, map: (DocumentSnapshot) -> T?): Flow<List<T>> =
        callbackFlow {
            val c = col(name)
            if (c == null) {
                trySend(emptyList())
                awaitClose { }
                return@callbackFlow
            }
            val registration = c.addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                } else {
                    trySend(snapshot.documents.mapNotNull(map))
                }
            }
            awaitClose { registration.remove() }
        }

    // ---- Products ---------------------------------------------------------
    fun productsFlow(): Flow<List<Product>> = collectionFlow("products", ::toProduct)

    suspend fun findProductByBarcode(barcode: String): Product? {
        val c = col("products") ?: return null
        return c.whereEqualTo("barcode", barcode).limit(1).get().await()
            .documents.firstOrNull()?.let(::toProduct)
    }

    suspend fun upsertProduct(product: Product): String {
        val c = col("products") ?: return ""
        val data = mapOf(
            "barcode" to product.barcode,
            "name" to product.name,
            "price" to product.price,
            "createdAt" to product.createdAt,
            "imageFileId" to product.imageFileId,
        )
        return if (product.id.isBlank()) {
            c.add(data).await().id
        } else {
            c.document(product.id).set(data).await(); product.id
        }
    }

    suspend fun deleteProduct(product: Product) {
        col("products")?.document(product.id)?.delete()?.await()
    }

    /** Save many products at once (used by the bulk-scan flow). */
    suspend fun addProducts(products: List<Product>) {
        val c = col("products") ?: return
        val batch = db.batch()
        products.forEach { p ->
            batch.set(
                c.document(),
                mapOf(
                    "barcode" to p.barcode,
                    "name" to p.name,
                    "price" to p.price,
                    "createdAt" to p.createdAt,
                    "imageFileId" to p.imageFileId,
                ),
            )
        }
        batch.commit().await()
    }

    /** True if [email] is present in the top-level `allowlist` collection (login gate). */
    suspend fun isEmailAllowed(email: String): Boolean {
        val doc = db.collection("allowlist").document(email.trim().lowercase()).get().await()
        return doc.exists()
    }

    // ---- Access control (super-admin) ------------------------------------
    private fun <T> topFlow(query: Query, map: (DocumentSnapshot) -> T?): Flow<List<T>> =
        callbackFlow {
            val reg = query.addSnapshotListener { snap, err ->
                if (err != null || snap == null) trySend(emptyList())
                else trySend(snap.documents.mapNotNull(map))
            }
            awaitClose { reg.remove() }
        }

    fun allowlistFlow(): Flow<List<String>> =
        topFlow(db.collection("allowlist")) { it.id }

    fun accessRequestsFlow(): Flow<List<AccessRequest>> =
        topFlow(db.collection("accessRequests")) {
            AccessRequest(email = it.id, requestedAt = it.getLong("requestedAt") ?: 0)
        }

    suspend fun addAllowed(email: String) {
        db.collection("allowlist").document(email.trim().lowercase())
            .set(mapOf("addedAt" to System.currentTimeMillis())).await()
    }

    suspend fun removeAllowed(email: String) {
        db.collection("allowlist").document(email.trim().lowercase()).delete().await()
    }

    /** Record a login attempt by a non-allowlisted user so the admin can approve it. */
    suspend fun createAccessRequest(email: String) {
        val e = email.trim().lowercase()
        db.collection("accessRequests").document(e)
            .set(mapOf("email" to e, "requestedAt" to System.currentTimeMillis())).await()
    }

    suspend fun approveRequest(email: String) {
        addAllowed(email)
        db.collection("accessRequests").document(email.trim().lowercase()).delete().await()
    }

    suspend fun denyRequest(email: String) {
        db.collection("accessRequests").document(email.trim().lowercase()).delete().await()
    }

    // ---- Customers --------------------------------------------------------
    fun customersFlow(): Flow<List<Customer>> = collectionFlow("customers", ::toCustomer)

    suspend fun upsertCustomer(customer: Customer): String {
        val c = col("customers") ?: return ""
        val data = mapOf(
            "name" to customer.name,
            "phone" to customer.phone,
            "note" to customer.note,
            "createdAt" to customer.createdAt,
        )
        return if (customer.id.isBlank()) {
            c.add(data).await().id
        } else {
            c.document(customer.id).set(data).await(); customer.id
        }
    }

    suspend fun deleteCustomer(customer: Customer) {
        col("customers")?.document(customer.id)?.delete()?.await()
    }

    // ---- Orders -----------------------------------------------------------
    fun ordersFlow(): Flow<List<Order>> = collectionFlow("orders", ::toOrder)

    suspend fun saveOrder(
        customerId: String?,
        note: String?,
        items: List<NewOrderItem>,
        now: Long,
    ): String {
        val c = col("orders") ?: return ""
        val itemMaps = items.map {
            mapOf(
                "productId" to it.productId,
                "barcode" to it.barcode,
                "name" to it.name,
                "price" to it.price,
                "quantity" to it.quantity,
                "lineTotal" to it.price * it.quantity,
            )
        }
        val data = mapOf(
            "customerId" to customerId,
            "total" to items.sumOf { it.price * it.quantity },
            "itemCount" to items.sumOf { it.quantity },
            "note" to note,
            "createdAt" to now,
            "items" to itemMaps,
        )
        return c.add(data).await().id
    }

    // ---- Payments ---------------------------------------------------------
    fun paymentsFlow(): Flow<List<Payment>> = collectionFlow("payments", ::toPayment)

    suspend fun addPayment(payment: Payment): String {
        val c = col("payments") ?: return ""
        val data = mapOf(
            "customerId" to payment.customerId,
            "amount" to payment.amount,
            "note" to payment.note,
            "createdAt" to payment.createdAt,
        )
        return c.add(data).await().id
    }

    suspend fun deletePayment(payment: Payment) {
        col("payments")?.document(payment.id)?.delete()?.await()
    }

    // ---- Mapping ----------------------------------------------------------
    private fun toProduct(d: DocumentSnapshot) = Product(
        id = d.id,
        barcode = d.getString("barcode") ?: "",
        name = d.getString("name") ?: "",
        price = d.getDouble("price") ?: 0.0,
        createdAt = d.getLong("createdAt") ?: 0,
        imageFileId = d.getString("imageFileId"),
    )

    private fun toCustomer(d: DocumentSnapshot) = Customer(
        id = d.id,
        name = d.getString("name") ?: "",
        phone = d.getString("phone"),
        note = d.getString("note"),
        createdAt = d.getLong("createdAt") ?: 0,
    )

    private fun toPayment(d: DocumentSnapshot) = Payment(
        id = d.id,
        customerId = d.getString("customerId") ?: "",
        amount = d.getDouble("amount") ?: 0.0,
        note = d.getString("note"),
        createdAt = d.getLong("createdAt") ?: 0,
    )

    @Suppress("UNCHECKED_CAST")
    private fun toOrder(d: DocumentSnapshot): Order {
        val rawItems = d.get("items") as? List<Map<String, Any?>> ?: emptyList()
        val items = rawItems.map { m ->
            OrderItem(
                productId = m["productId"] as? String,
                barcode = m["barcode"] as? String ?: "",
                name = m["name"] as? String ?: "",
                price = (m["price"] as? Number)?.toDouble() ?: 0.0,
                quantity = (m["quantity"] as? Number)?.toInt() ?: 0,
                lineTotal = (m["lineTotal"] as? Number)?.toDouble() ?: 0.0,
            )
        }
        return Order(
            id = d.id,
            customerId = d.getString("customerId"),
            total = d.getDouble("total") ?: 0.0,
            itemCount = d.getLong("itemCount")?.toInt() ?: 0,
            note = d.getString("note"),
            createdAt = d.getLong("createdAt") ?: 0,
            items = items,
        )
    }

    companion object {
        @Volatile
        private var INSTANCE: AppRepository? = null

        /** Kept taking a Context for call-site compatibility; Firebase uses the default app. */
        fun get(context: Any? = null): AppRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppRepository().also { INSTANCE = it }
            }
    }
}
