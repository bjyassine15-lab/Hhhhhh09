package com.example.data.dao

import androidx.room.*
import com.example.data.entity.*
import com.example.data.relation.InvoiceWithItems
import com.example.data.relation.CustomerWithDebt
import kotlinx.coroutines.flow.Flow

@Dao
interface PosDao {

    // --- PRODUCTS ---
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProducts(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    suspend fun getProductByBarcode(barcode: String): Product?

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: Long): Product?

    @Query("SELECT * FROM products WHERE stockQuantity IS NOT NULL AND stockQuantity <= 5 ORDER BY stockQuantity ASC")
    fun getLowStockProducts(): Flow<List<Product>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: Product): Long

    @Update
    suspend fun updateProduct(product: Product)

    @Delete
    suspend fun deleteProduct(product: Product)


    // --- CUSTOMERS ---
    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun getAllCustomers(): Flow<List<Customer>>

    // Calculated net debt query per customer
    @Query("""
        SELECT id, name, phone, 
        (
            SELECT IFNULL(SUM(totalAmount - paidAmount), 0.0) 
            FROM invoices 
            WHERE customerId = customers.id AND isDebt = 1
        ) - (
            SELECT IFNULL(SUM(amountPaid), 0.0) 
            FROM debt_payments 
            WHERE customerId = customers.id
        ) as totalDebt 
        FROM customers 
        ORDER BY name ASC
    """)
    fun getAllCustomersWithDebt(): Flow<List<CustomerWithDebt>>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getCustomerById(id: Long): Customer?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: Customer): Long

    @Delete
    suspend fun deleteCustomer(customer: Customer)


    // --- INVOICES & ITEMS ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: Invoice): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoiceItems(items: List<InvoiceItem>)

    @Delete
    suspend fun deleteInvoice(invoice: Invoice)

    @Transaction
    @Query("SELECT * FROM invoices ORDER BY timestamp DESC")
    fun getAllInvoicesWithItems(): Flow<List<InvoiceWithItems>>

    @Query("SELECT * FROM invoices ORDER BY timestamp DESC")
    fun getAllInvoices(): Flow<List<Invoice>>


    // --- DEBT PAYMENTS ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebtPayment(payment: DebtPayment): Long

    @Delete
    suspend fun deleteDebtPayment(payment: DebtPayment)

    @Query("SELECT * FROM debt_payments WHERE customerId = :customerId ORDER BY timestamp DESC")
    fun getDebtPaymentsForCustomer(customerId: Long): Flow<List<DebtPayment>>


    // --- SYNCHRONOUS LISTS FOR BACKUP ---
    @Query("SELECT * FROM products")
    suspend fun getProductsList(): List<Product>

    @Query("SELECT * FROM customers")
    suspend fun getCustomersList(): List<Customer>

    @Query("SELECT * FROM invoices")
    suspend fun getInvoicesList(): List<Invoice>

    @Query("SELECT * FROM invoice_items")
    suspend fun getInvoiceItemsList(): List<InvoiceItem>

    @Query("SELECT * FROM debt_payments")
    suspend fun getDebtPaymentsList(): List<DebtPayment>


    // --- BULK RESTORE (CLEAR & RE-INSERT TRANSACTIONS) ---
    @Query("DELETE FROM products")
    suspend fun clearProducts()

    @Query("DELETE FROM customers")
    suspend fun clearCustomers()

    @Query("DELETE FROM invoices")
    suspend fun clearInvoices()

    @Query("DELETE FROM invoice_items")
    suspend fun clearInvoiceItems()

    @Query("DELETE FROM debt_payments")
    suspend fun clearDebtPayments()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProductsBulk(products: List<Product>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomersBulk(customers: List<Customer>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoicesBulk(invoices: List<Invoice>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoiceItemsBulk(items: List<InvoiceItem>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebtPaymentsBulk(payments: List<DebtPayment>)

    @Transaction
    suspend fun restoreDatabase(
        products: List<Product>,
        customers: List<Customer>,
        invoices: List<Invoice>,
        items: List<InvoiceItem>,
        payments: List<DebtPayment>
    ) {
        clearInvoiceItems()
        clearDebtPayments()
        clearInvoices()
        clearCustomers()
        clearProducts()

        insertProductsBulk(products)
        insertCustomersBulk(customers)
        insertInvoicesBulk(invoices)
        insertInvoiceItemsBulk(items)
        insertDebtPaymentsBulk(payments)
    }
}
