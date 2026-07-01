package com.example.multibarcode.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

/** A single product line to persist when saving an order. */
data class NewOrderItem(
    val productId: Long?,
    val barcode: String,
    val name: String,
    val price: Double,
    val quantity: Int,
)

/**
 * Thin façade over the Room DAOs. Centralises the few multi-step operations
 * (most importantly saving an order with its items in one transaction).
 */
class AppRepository(private val db: AppDatabase) {

    private val products = db.productDao()
    private val customers = db.customerDao()
    private val orders = db.orderDao()
    private val payments = db.paymentDao()

    // ---- Products ---------------------------------------------------------
    fun productsFlow(): Flow<List<Product>> = products.all()
    suspend fun productPage(q: String, limit: Int, offset: Int) = products.page(q, limit, offset)
    suspend fun productCount(q: String) = products.count(q)
    suspend fun findProductByBarcode(barcode: String) = products.findByBarcode(barcode)
    suspend fun upsertProduct(product: Product): Long = products.upsert(product)
    suspend fun deleteProduct(product: Product) = products.delete(product)

    // ---- Customers --------------------------------------------------------
    fun customersFlow(): Flow<List<Customer>> = customers.all()
    fun observeCustomer(id: Long): Flow<Customer?> = customers.observe(id)
    fun customerPageFlow(q: String, filter: String, limit: Int, offset: Int) =
        customers.pageFlow(q, filter, limit, offset)
    fun customerCountFlow(q: String, filter: String) = customers.countFlow(q, filter)
    suspend fun upsertCustomer(customer: Customer): Long = customers.upsert(customer)
    suspend fun updateCustomer(customer: Customer) = customers.update(customer)
    suspend fun deleteCustomer(customer: Customer) = customers.delete(customer)

    // ---- Orders -----------------------------------------------------------
    fun ordersForCustomer(customerId: Long): Flow<List<OrderEntity>> =
        orders.observeForCustomer(customerId)
    fun ordersTotal(customerId: Long): Flow<Double> = orders.observeOrdersTotal(customerId)
    suspend fun orderPageRows(limit: Int, offset: Int) = orders.pageRows(limit, offset)
    suspend fun orderCount() = orders.count()
    suspend fun orderItems(orderId: Long) = orders.itemsOf(orderId)

    /** Persist an order and all its lines in a single transaction. Returns the new order id. */
    suspend fun saveOrder(
        customerId: Long?,
        note: String?,
        items: List<NewOrderItem>,
        now: Long,
    ): Long = db.withTransaction {
        val total = items.sumOf { it.price * it.quantity }
        val orderId = orders.insertOrder(
            OrderEntity(
                customerId = customerId,
                total = total,
                itemCount = items.sumOf { it.quantity },
                note = note,
                createdAt = now,
            )
        )
        orders.insertItems(
            items.map {
                OrderItem(
                    orderId = orderId,
                    productId = it.productId,
                    barcode = it.barcode,
                    name = it.name,
                    price = it.price,
                    quantity = it.quantity,
                    lineTotal = it.price * it.quantity,
                )
            }
        )
        orderId
    }

    // ---- Payments ---------------------------------------------------------
    fun paymentsForCustomer(customerId: Long): Flow<List<Payment>> =
        payments.observeForCustomer(customerId)
    fun paymentsTotal(customerId: Long): Flow<Double> = payments.observePaymentsTotal(customerId)
    suspend fun addPayment(payment: Payment): Long = payments.insert(payment)
    suspend fun deletePayment(payment: Payment) = payments.delete(payment)

    companion object {
        @Volatile
        private var INSTANCE: AppRepository? = null

        fun get(context: Context): AppRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppRepository(AppDatabase.get(context)).also { INSTANCE = it }
            }
    }
}
