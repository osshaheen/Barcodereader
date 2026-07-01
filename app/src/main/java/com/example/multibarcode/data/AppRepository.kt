package com.example.multibarcode.data

import android.content.Context
import com.example.multibarcode.util.Connectivity
import com.example.multibarcode.util.XlsxReader
import com.example.multibarcode.util.XlsxWriter
import com.example.multibarcode.util.Format
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

/** A single product line to persist when saving an order. */
data class NewOrderItem(
    val productId: String?,
    val barcode: String,
    val name: String,
    val price: Double,
    val quantity: Int,
)

/** Outcome of a backup/reset operation, with a user-facing Arabic message. */
data class BackupResult(val ok: Boolean, val message: String)

/**
 * Firestore-backed data access, scoped to the signed-in user:
 * `users/{uid}/{products|customers|orders|payments}`.
 *
 * Firestore's on-device cache is enabled by default, so reads/writes keep working offline
 * and sync automatically when the connection returns. Filtering, pagination and balance
 * computation are done in memory by the ViewModels over these snapshot flows.
 */
class AppRepository(private val appContext: Context) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val outbox = OutboxDatabase.get(appContext).pendingOpDao()
    private val prefs = appContext.getSharedPreferences("sync", Context.MODE_PRIVATE)

    // All employees share ONE shop database. Data is pulled into these caches only when the user
    // presses "refresh" (manual pull), so nothing appears automatically — matching the manual
    // upload flow. A tiny meta doc (shop/main.lastChange) tells other devices when to offer a pull.
    private fun sharedRoot(): DocumentReference = db.collection("shop").document("main")

    private fun col(name: String): CollectionReference? =
        if (auth.currentUser == null) null else sharedRoot().collection(name)

    private fun online() = Connectivity.isOnline(appContext)

    // In-memory caches of the shared data (last successful pull). The UI reads from these.
    private val productsCache = MutableStateFlow<List<Product>>(emptyList())
    private val customersCache = MutableStateFlow<List<Customer>>(emptyList())
    private val ordersCache = MutableStateFlow<List<Order>>(emptyList())
    private val paymentsCache = MutableStateFlow<List<Payment>>(emptyList())
    private val backupsCache = MutableStateFlow<List<BackupRecord>>(emptyList())

    /** Server-clock millis of the last change this device has pulled. */
    private val lastPulledFlow = MutableStateFlow(prefs.getLong("lastPulled", 0L))
    @Volatile private var initialSyncDone = false

    /** Always queue new records locally; they upload only when the user presses "upload". */
    private suspend fun queueWrite(collection: String, label: String, data: Map<String, Any?>, now: Long) {
        outbox.insert(PendingOp(type = collection, label = label, payload = mapToJson(data), createdAt = now))
    }

    /** A typed view of pending (not-yet-uploaded) records of one collection. */
    private fun <T> pendingTyped(type: String, map: (PendingOp) -> T?): Flow<List<T>> =
        outbox.observeAll().map { ops -> ops.filter { it.type == type }.mapNotNull(map) }

    // ---- Manual pull of shared data --------------------------------------

    /** Pull one collection's docs from the shared DB into memory. */
    private suspend fun <T> pull(name: String, map: (DocumentSnapshot) -> T?): List<T>? {
        val c = col(name) ?: return null
        return c.get().await().documents.mapNotNull(map)
    }

    /**
     * Pull the whole shared shop database into the in-memory caches and record the point in time
     * we synced to. Returns true on success. Called manually (the "refresh" button) and once
     * silently after sign-in so screens aren't empty.
     */
    suspend fun refreshFromRemote(): Boolean {
        if (col("products") == null || !online()) return false
        return try {
            pull("products", ::toProduct)?.let { productsCache.value = it }
            pull("customers", ::toCustomer)?.let { customersCache.value = it }
            pull("orders", ::toOrder)?.let { ordersCache.value = it }
            pull("payments", ::toPayment)?.let { paymentsCache.value = it }
            pull("backups", ::toBackup)?.let { backupsCache.value = it }
            setLastPulled(changeMillis(sharedRoot().get().await()))
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Silent one-time pull after sign-in (retries next time if this attempt fails, e.g. offline). */
    suspend fun ensureInitialSync() {
        if (initialSyncDone) return
        if (refreshFromRemote()) initialSyncDone = true
    }

    private fun changeMillis(snap: DocumentSnapshot?): Long =
        snap?.getTimestamp("lastChange")?.toDate()?.time ?: 0L

    private fun setLastPulled(v: Long) {
        prefs.edit().putLong("lastPulled", v).apply()
        lastPulledFlow.value = v
    }

    /** Stamp the shared DB as changed, so other devices are offered a pull. */
    private suspend fun bumpRemoteChange() {
        runCatching {
            sharedRoot().set(mapOf("lastChange" to FieldValue.serverTimestamp()), SetOptions.merge()).await()
        }
    }

    /**
     * After a direct online change (edit/delete/upload/backup): mark the shared DB changed and
     * re-pull so THIS device reflects it immediately and its "last pulled" advances (so it does
     * not show itself the "new updates" banner for its own change).
     */
    private suspend fun afterDirectChange() {
        bumpRemoteChange()
        refreshFromRemote()
    }

    /** Listens to the tiny meta doc and emits true when the shared DB is newer than our last pull. */
    fun hasRemoteUpdatesFlow(): Flow<Boolean> =
        combine(remoteChangeFlow(), lastPulledFlow) { remote, pulled -> remote > pulled + 1000 }

    private fun remoteChangeFlow(): Flow<Long> = callbackFlow {
        if (auth.currentUser == null) {
            trySend(0L); awaitClose { }; return@callbackFlow
        }
        val reg = sharedRoot().addSnapshotListener { snap, err ->
            trySend(if (err != null) 0L else changeMillis(snap))
        }
        awaitClose { reg.remove() }
    }

    // ---- Products ---------------------------------------------------------
    fun productsFlow(): Flow<List<Product>> =
        combine(productsCache, pendingTyped("products", ::opToProduct)) { a, b -> a + b }

    suspend fun findProductByBarcode(barcode: String): Product? {
        val c = col("products") ?: return null
        return c.whereEqualTo("barcode", barcode).limit(1).get().await()
            .documents.firstOrNull()?.let(::toProduct)
    }

    suspend fun upsertProduct(product: Product): String {
        val data = mapOf(
            "barcode" to product.barcode,
            "name" to product.name,
            "price" to product.price,
            "createdAt" to product.createdAt,
            "imageFileId" to product.imageFileId,
        )
        return if (product.id.isBlank()) {
            queueWrite("products", "منتج — ${product.name}", data, product.createdAt)
            "local-${product.createdAt}"
        } else {
            col("products")?.document(product.id)?.set(data)?.await()
            afterDirectChange(); product.id
        }
    }

    suspend fun deleteProduct(product: Product) {
        if (deleteLocalIfPending(product.id)) return
        col("products")?.document(product.id)?.delete()?.await()
        afterDirectChange()
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
        afterDirectChange()
    }

    /** Login state for [email]: whether it's allowlisted and whether it's a super admin. */
    suspend fun getAllowState(email: String): Pair<Boolean, Boolean> {
        val doc = db.collection("allowlist").document(email.trim().lowercase()).get().await()
        return doc.exists() to (doc.getBoolean("admin") == true)
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

    fun allowlistFlow(): Flow<List<AllowedUser>> =
        topFlow(db.collection("allowlist")) {
            AllowedUser(email = it.id, admin = it.getBoolean("admin") == true)
        }

    fun accessRequestsFlow(): Flow<List<AccessRequest>> =
        topFlow(db.collection("accessRequests")) {
            AccessRequest(email = it.id, requestedAt = it.getLong("requestedAt") ?: 0)
        }

    suspend fun addAllowed(email: String) {
        // Merge so re-adding an existing user never clears their admin flag.
        db.collection("allowlist").document(email.trim().lowercase())
            .set(mapOf("addedAt" to System.currentTimeMillis()), SetOptions.merge()).await()
    }

    /** Promote/demote an allowlisted user to super admin. */
    suspend fun setAdmin(email: String, admin: Boolean) {
        db.collection("allowlist").document(email.trim().lowercase())
            .set(mapOf("admin" to admin), SetOptions.merge()).await()
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

    // ---- Single storage Drive account (for product images) ----------------
    private fun storageDoc() = db.collection("settings").document("storage")

    fun storageDriveEmailFlow(): Flow<String?> = callbackFlow {
        val reg = storageDoc().addSnapshotListener { snap, err ->
            trySend(if (err != null || snap == null) null else snap.getString("driveEmail"))
        }
        awaitClose { reg.remove() }
    }

    suspend fun getStorageDriveEmail(): String? =
        storageDoc().get().await().getString("driveEmail")

    suspend fun setStorageDriveEmail(email: String) {
        storageDoc().set(mapOf("driveEmail" to email.trim()), SetOptions.merge()).await()
    }

    // ---- Customers --------------------------------------------------------
    fun customersFlow(): Flow<List<Customer>> =
        combine(customersCache, pendingTyped("customers", ::opToCustomer)) { a, b -> a + b }

    suspend fun upsertCustomer(customer: Customer): String {
        val data = mapOf(
            "name" to customer.name,
            "phone" to customer.phone,
            "note" to customer.note,
            "createdAt" to customer.createdAt,
        )
        return if (customer.id.isBlank()) {
            queueWrite("customers", "زبون — ${customer.name}", data, customer.createdAt)
            "saved"
        } else {
            // Edits target an existing shared doc (kept online).
            col("customers")?.document(customer.id)?.set(data)?.await()
            afterDirectChange(); customer.id
        }
    }

    suspend fun deleteCustomer(customer: Customer) {
        if (deleteLocalIfPending(customer.id)) return
        col("customers")?.document(customer.id)?.delete()?.await()
        afterDirectChange()
    }

    // ---- Orders -----------------------------------------------------------
    fun ordersFlow(): Flow<List<Order>> =
        combine(ordersCache, pendingTyped("orders", ::opToOrder)) { a, b -> a + b }

    suspend fun saveOrder(
        customerId: String?,
        note: String?,
        items: List<NewOrderItem>,
        now: Long,
    ): String {
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
        queueWrite("orders", "طلبية — ${items.sumOf { it.quantity }} صنف", data, now)
        return "saved"
    }

    // ---- Payments ---------------------------------------------------------
    fun paymentsFlow(): Flow<List<Payment>> =
        combine(paymentsCache, pendingTyped("payments", ::opToPayment)) { a, b -> a + b }

    suspend fun addPayment(payment: Payment): String {
        val data = mapOf(
            "customerId" to payment.customerId,
            "amount" to payment.amount,
            "note" to payment.note,
            "createdAt" to payment.createdAt,
        )
        queueWrite("payments", "دفعة — ${payment.amount}", data, payment.createdAt)
        return "saved"
    }

    suspend fun deletePayment(payment: Payment) {
        if (deleteLocalIfPending(payment.id)) return
        col("payments")?.document(payment.id)?.delete()?.await()
        afterDirectChange()
    }

    /** If [id] is a not-yet-uploaded local record ("local-<opId>"), drop it from the outbox. */
    private suspend fun deleteLocalIfPending(id: String): Boolean {
        if (!id.startsWith("local-")) return false
        val opId = id.removePrefix("local-").toLongOrNull() ?: return true
        outbox.getAll().firstOrNull { it.id == opId }?.let { outbox.delete(it) }
        return true
    }

    // ---- Pending-op -> model mapping (for showing local data before upload) ----
    private fun opToProduct(op: PendingOp): Product? = runCatching {
        val m = jsonToMap(op.payload)
        Product(
            id = "local-${op.id}",
            barcode = m["barcode"] as? String ?: "",
            name = m["name"] as? String ?: "",
            price = (m["price"] as? Number)?.toDouble() ?: 0.0,
            createdAt = (m["createdAt"] as? Number)?.toLong() ?: op.createdAt,
            imageFileId = m["imageFileId"] as? String,
        )
    }.getOrNull()

    private fun opToCustomer(op: PendingOp): Customer? = runCatching {
        val m = jsonToMap(op.payload)
        Customer(
            id = "local-${op.id}",
            name = m["name"] as? String ?: "",
            phone = m["phone"] as? String,
            note = m["note"] as? String,
            createdAt = (m["createdAt"] as? Number)?.toLong() ?: op.createdAt,
        )
    }.getOrNull()

    private fun opToPayment(op: PendingOp): Payment? = runCatching {
        val m = jsonToMap(op.payload)
        Payment(
            id = "local-${op.id}",
            customerId = m["customerId"] as? String ?: "",
            amount = (m["amount"] as? Number)?.toDouble() ?: 0.0,
            note = m["note"] as? String,
            createdAt = (m["createdAt"] as? Number)?.toLong() ?: op.createdAt,
        )
    }.getOrNull()

    @Suppress("UNCHECKED_CAST")
    private fun opToOrder(op: PendingOp): Order? = runCatching {
        val m = jsonToMap(op.payload)
        val rawItems = m["items"] as? List<Map<String, Any?>> ?: emptyList()
        val items = rawItems.map { im ->
            OrderItem(
                productId = im["productId"] as? String,
                barcode = im["barcode"] as? String ?: "",
                name = im["name"] as? String ?: "",
                price = (im["price"] as? Number)?.toDouble() ?: 0.0,
                quantity = (im["quantity"] as? Number)?.toInt() ?: 0,
                lineTotal = (im["lineTotal"] as? Number)?.toDouble() ?: 0.0,
            )
        }
        Order(
            id = "local-${op.id}",
            customerId = m["customerId"] as? String,
            total = (m["total"] as? Number)?.toDouble() ?: 0.0,
            itemCount = (m["itemCount"] as? Number)?.toInt() ?: 0,
            note = m["note"] as? String,
            createdAt = (m["createdAt"] as? Number)?.toLong() ?: op.createdAt,
            items = items,
        )
    }.getOrNull()

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

    // ---- Local outbox (offline queue) + manual upload ---------------------
    fun pendingOpsFlow(): Flow<List<PendingOp>> = outbox.observeAll()
    fun pendingCountFlow(): Flow<Int> = outbox.count()

    /**
     * Push every queued op to Firestore, deleting each on success. Customers upload first so that
     * orders/payments referencing a locally-created customer ("local-…") get remapped to the real
     * Firestore id. Returns uploaded count.
     */
    suspend fun uploadPending(): Int {
        if (!online()) return 0
        var uploaded = 0
        val idMap = HashMap<String, String>() // "local-<opId>" -> real Firestore id
        val ordered = outbox.getAll().sortedBy { typeRank(it.type) }
        for (op in ordered) {
            try {
                val data = jsonToMap(op.payload).toMutableMap()
                (data["customerId"] as? String)?.let { cid ->
                    idMap[cid]?.let { data["customerId"] = it }
                }
                val ref = col(op.type)?.add(data)?.await()
                if (op.type == "customers" && ref != null) {
                    idMap["local-${op.id}"] = ref.id
                }
                outbox.delete(op)
                uploaded++
            } catch (_: Exception) {
                // keep the op for a later retry
            }
        }
        if (uploaded > 0) afterDirectChange()
        return uploaded
    }

    private fun typeRank(type: String): Int = when (type) {
        "customers" -> 0
        "products" -> 1
        "orders" -> 2
        "payments" -> 3
        else -> 4
    }

    private fun mapToJson(data: Map<String, Any?>): String = JSONObject(data).toString()

    @Suppress("UNCHECKED_CAST")
    private fun jsonToMap(json: String): Map<String, Any?> = jsonObjectToMap(JSONObject(json))

    private fun jsonObjectToMap(o: JSONObject): Map<String, Any?> {
        val map = HashMap<String, Any?>()
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            map[k] = unwrap(o.get(k))
        }
        return map
    }

    private fun unwrap(value: Any?): Any? = when (value) {
        is JSONObject -> jsonObjectToMap(value)
        is JSONArray -> (0 until value.length()).map { unwrap(value.get(it)) }
        JSONObject.NULL -> null
        else -> value
    }

    // ---- Backups (Excel files on the storage Drive) -----------------------

    /** Records of every saved backup file, newest first. */
    fun backupsFlow(): Flow<List<BackupRecord>> =
        backupsCache.map { list -> list.sortedByDescending { it.createdAt } }

    private fun toBackup(d: DocumentSnapshot) = BackupRecord(
        id = d.id,
        fileId = d.getString("fileId") ?: "",
        fileName = d.getString("fileName") ?: "",
        customerId = d.getString("customerId"),
        customerName = d.getString("customerName"),
        kind = d.getString("kind") ?: "customer",
        fromDate = d.getLong("fromDate") ?: 0,
        toDate = d.getLong("toDate") ?: 0,
        createdAt = d.getLong("createdAt") ?: 0,
    )

    private suspend fun fetchCustomerOrders(customerId: String): List<Order> =
        col("orders")?.whereEqualTo("customerId", customerId)?.get()?.await()
            ?.documents?.map(::toOrder)?.sortedBy { it.createdAt } ?: emptyList()

    private suspend fun fetchCustomerPayments(customerId: String): List<Payment> =
        col("payments")?.whereEqualTo("customerId", customerId)?.get()?.await()
            ?.documents?.map(::toPayment)?.sortedBy { it.createdAt } ?: emptyList()

    private fun sanitizeFileName(s: String): String =
        s.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "غير_مسمى" }

    /** Build the per-customer workbook (summary + orders + payments sheets). */
    private fun buildCustomerWorkbook(customer: Customer, orders: List<Order>, payments: List<Payment>): ByteArray {
        val ordersTotal = orders.sumOf { it.total }
        val paymentsTotal = payments.sumOf { it.amount }
        val summary = XlsxWriter.Sheet(
            "ملخص",
            listOf(
                listOf("الزبون", customer.name),
                listOf("الهاتف", customer.phone ?: ""),
                listOf("إجمالي الطلبيات", Format.money(ordersTotal)),
                listOf("إجمالي المدفوعات", Format.money(paymentsTotal)),
                listOf("الرصيد المتبقي", Format.money(ordersTotal - paymentsTotal)),
                listOf("عدد الطلبيات", orders.size.toString()),
                listOf("عدد المدفوعات", payments.size.toString()),
            ),
        )
        val orderRows = ArrayList<List<String>>()
        orderRows.add(listOf("رقم الطلبية", "التاريخ", "الصنف", "الباركود", "السعر", "الكمية", "إجمالي السطر"))
        for (o in orders) {
            if (o.items.isEmpty()) {
                orderRows.add(listOf(o.id, Format.dateTime(o.createdAt), "", "", "", "", Format.money(o.total)))
            } else {
                o.items.forEach { it2 ->
                    orderRows.add(
                        listOf(
                            o.id, Format.dateTime(o.createdAt), it2.name, it2.barcode,
                            Format.money(it2.price), it2.quantity.toString(), Format.money(it2.lineTotal),
                        )
                    )
                }
            }
        }
        val paymentRows = ArrayList<List<String>>()
        paymentRows.add(listOf("التاريخ", "المبلغ", "ملاحظة"))
        payments.forEach { p -> paymentRows.add(listOf(Format.dateTime(p.createdAt), Format.money(p.amount), p.note ?: "")) }
        return XlsxWriter.build(listOf(summary, XlsxWriter.Sheet("الطلبيات", orderRows), XlsxWriter.Sheet("المدفوعات", paymentRows)))
    }

    private fun dayStamp(ms: Long): String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(ms))

    /**
     * Build an Excel backup of one customer's uploaded orders/payments, upload it to the storage
     * Drive, and record it in the `backups` collection. Does NOT delete anything.
     */
    suspend fun backupCustomer(customerId: String, kind: String = "customer"): BackupResult {
        if (!online()) return BackupResult(false, "لا يوجد اتصال بالإنترنت — النسخ الاحتياطي يتطلب اتصالاً.")
        val storageEmail = getStorageDriveEmail()
            ?: return BackupResult(false, "لم يتم تعيين حساب Google Drive للتخزين من شاشة الإدارة.")
        val customer = col("customers")?.document(customerId)?.get()?.await()?.let(::toCustomer)
            ?: return BackupResult(false, "الزبون غير موجود على الخادم (قد يكون غير مرفوع بعد).")
        val orders = fetchCustomerOrders(customerId)
        val payments = fetchCustomerPayments(customerId)
        if (orders.isEmpty() && payments.isEmpty()) {
            return BackupResult(false, "لا توجد بيانات مرفوعة لهذا الزبون لأخذ نسخة منها.")
        }
        val dates = (orders.map { it.createdAt } + payments.map { it.createdAt }).filter { it > 0 }
        val from = dates.minOrNull() ?: System.currentTimeMillis()
        val to = dates.maxOrNull() ?: System.currentTimeMillis()
        val bytes = buildCustomerWorkbook(customer, orders, payments)
        val fileName = "${sanitizeFileName(customer.name)}_${dayStamp(from)}_${dayStamp(to)}.xlsx"
        val fileId = DriveService.uploadBytes(
            appContext, storageEmail, fileName,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes,
        ) ?: return BackupResult(false, "فشل رفع الملف إلى Drive. تأكد من صلاحية حساب التخزين من شاشة الإدارة.")
        col("backups")?.add(
            mapOf(
                "fileId" to fileId, "fileName" to fileName,
                "customerId" to customerId, "customerName" to customer.name,
                "kind" to kind, "fromDate" to from, "toDate" to to,
                "createdAt" to System.currentTimeMillis(),
            )
        )?.await()
        afterDirectChange()
        return BackupResult(true, "تم حفظ النسخة الاحتياطية: $fileName")
    }

    /** Take a backup, then delete every uploaded order/payment for this customer ("تصفير الحساب"). */
    suspend fun resetCustomer(customerId: String): BackupResult {
        val backup = backupCustomer(customerId, kind = "reset")
        // Only wipe when we actually secured a backup (or there was genuinely nothing to back up).
        val nothingToBackup = !backup.ok && backup.message.contains("لا توجد بيانات")
        if (!backup.ok && !nothingToBackup) return backup
        val orders = col("orders")?.whereEqualTo("customerId", customerId)?.get()?.await()?.documents ?: emptyList()
        val payments = col("payments")?.whereEqualTo("customerId", customerId)?.get()?.await()?.documents ?: emptyList()
        orders.forEach { it.reference.delete().await() }
        payments.forEach { it.reference.delete().await() }
        afterDirectChange()
        return BackupResult(true, "تم تصفير حساب الزبون بعد حفظ نسخة احتياطية.")
    }

    /** Full backup: one Excel file per customer that has data. Used for the daily/manual full backup. */
    suspend fun backupAll(): BackupResult {
        if (!online()) return BackupResult(false, "لا يوجد اتصال بالإنترنت.")
        val customers = col("customers")?.get()?.await()?.documents?.map(::toCustomer) ?: emptyList()
        if (customers.isEmpty()) return BackupResult(false, "لا يوجد زبائن لأخذ نسخة منهم.")
        var saved = 0
        for (c in customers) {
            val r = backupCustomer(c.id, kind = "daily")
            if (r.ok) saved++
        }
        return if (saved > 0) BackupResult(true, "تم حفظ نسخة احتياطية لعدد $saved زبون.")
        else BackupResult(false, "لا توجد بيانات مرفوعة لأخذ نسخة منها.")
    }

    /** Download and parse a backup's Excel file into sheets for the archive viewer. */
    suspend fun readBackup(record: BackupRecord): List<XlsxReader.Sheet>? {
        val bytes = DriveService.downloadPublic(record.fileId) ?: return null
        return runCatching { XlsxReader.read(bytes) }.getOrNull()
    }

    companion object {
        @Volatile
        private var INSTANCE: AppRepository? = null

        fun get(context: Context): AppRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}
