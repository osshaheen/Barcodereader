package com.example.multibarcode.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(product: Product): Long

    @Update
    suspend fun update(product: Product)

    @Delete
    suspend fun delete(product: Product)

    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): Product?

    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE")
    fun all(): Flow<List<Product>>

    @Query(
        """
        SELECT * FROM products
        WHERE name LIKE '%' || :q || '%' OR barcode LIKE '%' || :q || '%'
        ORDER BY name COLLATE NOCASE
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun page(q: String, limit: Int, offset: Int): List<Product>

    @Query(
        """
        SELECT COUNT(*) FROM products
        WHERE name LIKE '%' || :q || '%' OR barcode LIKE '%' || :q || '%'
        """
    )
    suspend fun count(q: String): Int
}

@Dao
interface CustomerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(customer: Customer): Long

    @Update
    suspend fun update(customer: Customer)

    @Delete
    suspend fun delete(customer: Customer)

    @Query("SELECT * FROM customers WHERE id = :id")
    fun observe(id: Long): Flow<Customer?>

    @Query("SELECT * FROM customers ORDER BY name COLLATE NOCASE")
    fun all(): Flow<List<Customer>>

    /**
     * A page of customers with their computed balance. [filter] is one of
     * ALL / DEBT / SETTLED — DEBT keeps balances above zero, SETTLED the rest.
     */
    @Query(
        """
        SELECT * FROM (
            SELECT c.id AS id, c.name AS name, c.phone AS phone, c.note AS note,
                   c.createdAt AS createdAt,
                   COALESCE((SELECT SUM(total) FROM orders WHERE customerId = c.id), 0)
                   - COALESCE((SELECT SUM(amount) FROM payments WHERE customerId = c.id), 0)
                   AS balance
            FROM customers c
            WHERE c.name LIKE '%' || :q || '%' OR c.phone LIKE '%' || :q || '%'
        )
        WHERE (:filter = 'ALL')
           OR (:filter = 'DEBT' AND balance > 0.0)
           OR (:filter = 'SETTLED' AND balance <= 0.0)
        ORDER BY name COLLATE NOCASE
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun page(q: String, filter: String, limit: Int, offset: Int): List<CustomerRow>

    @Query(
        """
        SELECT COUNT(*) FROM (
            SELECT c.id AS id,
                   COALESCE((SELECT SUM(total) FROM orders WHERE customerId = c.id), 0)
                   - COALESCE((SELECT SUM(amount) FROM payments WHERE customerId = c.id), 0)
                   AS balance
            FROM customers c
            WHERE c.name LIKE '%' || :q || '%' OR c.phone LIKE '%' || :q || '%'
        )
        WHERE (:filter = 'ALL')
           OR (:filter = 'DEBT' AND balance > 0.0)
           OR (:filter = 'SETTLED' AND balance <= 0.0)
        """
    )
    suspend fun count(q: String, filter: String): Int
}

@Dao
interface OrderDao {
    @Insert
    suspend fun insertOrder(order: OrderEntity): Long

    @Insert
    suspend fun insertItems(items: List<OrderItem>)

    @Delete
    suspend fun delete(order: OrderEntity)

    @Query("SELECT * FROM orders WHERE customerId = :customerId ORDER BY createdAt DESC")
    fun observeForCustomer(customerId: Long): Flow<List<OrderEntity>>

    @Query(
        """
        SELECT o.id AS id, c.name AS customerName, o.total AS total,
               o.itemCount AS itemCount, o.createdAt AS createdAt
        FROM orders o
        LEFT JOIN customers c ON c.id = o.customerId
        ORDER BY o.createdAt DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun pageRows(limit: Int, offset: Int): List<OrderRow>

    @Query("SELECT COUNT(*) FROM orders")
    suspend fun count(): Int

    @Query("SELECT * FROM order_items WHERE orderId = :orderId")
    suspend fun itemsOf(orderId: Long): List<OrderItem>

    @Query("SELECT COALESCE(SUM(total), 0) FROM orders WHERE customerId = :customerId")
    fun observeOrdersTotal(customerId: Long): Flow<Double>
}

@Dao
interface PaymentDao {
    @Insert
    suspend fun insert(payment: Payment): Long

    @Delete
    suspend fun delete(payment: Payment)

    @Query("SELECT * FROM payments WHERE customerId = :customerId ORDER BY createdAt DESC")
    fun observeForCustomer(customerId: Long): Flow<List<Payment>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE customerId = :customerId")
    fun observePaymentsTotal(customerId: Long): Flow<Double>
}
