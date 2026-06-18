package com.example.ui.viewmodel

import android.app.Application
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.dao.PosDao
import com.example.data.database.AppDatabase
import com.example.data.entity.*
import com.example.data.model.CartItem
import com.example.data.relation.CustomerWithDebt
import com.example.data.relation.InvoiceWithItems
import com.example.data.util.BackupRestoreUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class PosViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val posDao = db.posDao()

    // --- STATE MANAGEMENT ---
    // Products Flow
    val allProducts: StateFlow<List<Product>> = posDao.getAllProducts().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Low stock flow
    val lowStockProducts: StateFlow<List<Product>> = posDao.getLowStockProducts().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Customers Flow
    val allCustomers: StateFlow<List<Customer>> = posDao.getAllCustomers().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Customers with Debt Flow
    val customersWithDebt: StateFlow<List<CustomerWithDebt>> = posDao.getAllCustomersWithDebt().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Invoices Flow
    val allInvoices: StateFlow<List<InvoiceWithItems>> = posDao.getAllInvoicesWithItems().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // --- CURRENT CART ENGINE ---
    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

    // Total price state deriving from _cartItems
    val cartTotal: StateFlow<Double> = _cartItems.combine(MutableStateFlow(0.0)) { items, _ ->
        items.sumOf { it.totalAmount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Scan memory to avoid loop scanning same product
    private var lastScannedBarcode: String? = null
    private var lastScannedTime: Long = 0

    // Sound Tone
    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        } catch (e: Exception) {
            Log.e("PosViewModel", "Failed to init ToneGenerator", e)
        }
    }

    /**
     * Beep sound for barcode scans
     */
    fun playBeep() {
        try {
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            }
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
        } catch (e: Exception) {
            Log.e("PosViewModel", "Beep play failed", e)
        }
    }

    /**
     * Handle scan barcode event on POS screen
     */
    fun scanProductBarcode(barcode: String, onMatched: (Product) -> Unit = {}, onNotFound: (String) -> Unit = {}) {
        val now = System.currentTimeMillis()
        // If it's the same barcode within 3 seconds, ignore it to prevent duplicating scans
        if (barcode == lastScannedBarcode && (now - lastScannedTime) < 3000) {
            return
        }

        lastScannedBarcode = barcode
        lastScannedTime = now

        viewModelScope.launch {
            val product = withContext(Dispatchers.IO) {
                posDao.getProductByBarcode(barcode)
            }
            if (product != null) {
                // Play feedback sound
                playBeep()
                
                // Add to cart state
                addToCart(product)
                
                onMatched(product)
            } else {
                onNotFound(barcode)
            }
        }
    }

    fun forceResetScanMemory() {
        lastScannedBarcode = null
        lastScannedTime = 0
    }

    // --- CART ACTIONS ---
    fun addToCart(product: Product) {
        val currentList = _cartItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.product.id == product.id }
        if (index != -1) {
            currentList[index] = currentList[index].copy(quantity = currentList[index].quantity + 1)
        } else {
            currentList.add(CartItem(product = product, quantity = 1))
        }
        _cartItems.value = currentList
    }

    fun incrementCartItem(product: Product) {
        addToCart(product)
    }

    fun decrementCartItem(product: Product) {
        val currentList = _cartItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.product.id == product.id }
        if (index != -1) {
            val item = currentList[index]
            if (item.quantity > 1) {
                currentList[index] = item.copy(quantity = item.quantity - 1)
            } else {
                currentList.removeAt(index)
            }
            _cartItems.value = currentList
        }
    }

    fun removeFromCart(product: Product) {
        val currentList = _cartItems.value.toMutableList()
        currentList.removeAll { it.product.id == product.id }
        _cartItems.value = currentList
    }

    fun clearCart() {
        _cartItems.value = emptyList()
        forceResetScanMemory()
    }

    // --- SALES SETTLEMENT ---
    suspend fun completeCashSale(): Boolean {
        if (_cartItems.value.isEmpty()) return false
        val total = cartTotal.value

        return withContext(Dispatchers.IO) {
            try {
                val invoiceNumber = "INV-" + System.currentTimeMillis().toString().takeLast(6)
                val invoice = Invoice(
                    invoiceNumber = invoiceNumber,
                    totalAmount = total,
                    isDebt = false,
                    customerId = null,
                    paidAmount = total
                )
                val invoiceId = posDao.insertInvoice(invoice)

                val items = _cartItems.value.map { cart ->
                    InvoiceItem(
                        invoiceId = invoiceId,
                        productId = cart.product.id,
                        productName = cart.product.name,
                        productBarcode = cart.product.barcode,
                        quantity = cart.quantity,
                        salePrice = cart.product.salePrice,
                        purchasePrice = cart.product.purchasePrice
                    )
                }
                posDao.insertInvoiceItems(items)

                // Update stock inventory
                _cartItems.value.forEach { cart ->
                    if (cart.product.stockQuantity != null) {
                        val remaining = maxOf(0, cart.product.stockQuantity - cart.quantity)
                        posDao.updateProduct(cart.product.copy(stockQuantity = remaining))
                    }
                }

                _cartItems.value = emptyList()
                forceResetScanMemory()
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    suspend fun completeCreditSale(customerId: Long): Boolean {
        if (_cartItems.value.isEmpty()) return false
        val total = cartTotal.value

        return withContext(Dispatchers.IO) {
            try {
                val invoiceNumber = "INV-CR-" + System.currentTimeMillis().toString().takeLast(6)
                val invoice = Invoice(
                    invoiceNumber = invoiceNumber,
                    totalAmount = total,
                    isDebt = true,
                    customerId = customerId,
                    paidAmount = 0.0
                )
                val invoiceId = posDao.insertInvoice(invoice)

                val items = _cartItems.value.map { cart ->
                    InvoiceItem(
                        invoiceId = invoiceId,
                        productId = cart.product.id,
                        productName = cart.product.name,
                        productBarcode = cart.product.barcode,
                        quantity = cart.quantity,
                        salePrice = cart.product.salePrice,
                        purchasePrice = cart.product.purchasePrice
                    )
                }
                posDao.insertInvoiceItems(items)

                // Update stock inventory
                _cartItems.value.forEach { cart ->
                    if (cart.product.stockQuantity != null) {
                        val remaining = maxOf(0, cart.product.stockQuantity - cart.quantity)
                        posDao.updateProduct(cart.product.copy(stockQuantity = remaining))
                    }
                }

                _cartItems.value = emptyList()
                forceResetScanMemory()
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    // --- PRODUCTS MANAGEMENT ---
    fun saveProduct(
        id: Long = 0,
        name: String,
        barcode: String,
        purchasePrice: Double,
        salePrice: Double,
        stockQuantity: Int?,
        imagePath: String?,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val product = Product(
                id = if (id == 0L) 0 else id,
                name = name,
                barcode = barcode,
                purchasePrice = purchasePrice,
                salePrice = salePrice,
                stockQuantity = stockQuantity,
                imagePath = imagePath
            )
            posDao.insertProduct(product)
            withContext(Dispatchers.Main) {
                onSuccess()
            }
        }
    }

    fun deleteProduct(product: Product, onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            posDao.deleteProduct(product)
            withContext(Dispatchers.Main) {
                onSuccess()
            }
        }
    }


    // --- CUSTOMER & DEBTS MANAGEMENT ---
    fun addCustomer(name: String, phone: String?, onSuccess: (Customer) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val customer = Customer(name = name, phone = phone)
            val id = posDao.insertCustomer(customer)
            val created = customer.copy(id = id)
            withContext(Dispatchers.Main) {
                onSuccess(created)
            }
        }
    }

    fun addManualDebt(customerId: Long, amount: Double, note: String, onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val invoiceNumber = "INV-MAN-" + System.currentTimeMillis().toString().takeLast(6)
                val invoice = Invoice(
                    invoiceNumber = invoiceNumber,
                    totalAmount = amount,
                    isDebt = true,
                    customerId = customerId,
                    paidAmount = 0.0
                )
                val invoiceId = posDao.insertInvoice(invoice)

                val item = InvoiceItem(
                    invoiceId = invoiceId,
                    productId = null,
                    productName = "دين يدوي: $note",
                    productBarcode = "",
                    quantity = 1,
                    salePrice = amount,
                    purchasePrice = 0.0
                )
                posDao.insertInvoiceItems(listOf(item))
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun recordDebtPayment(customerId: Long, amountPaid: Double, note: String?, onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val payment = DebtPayment(
                customerId = customerId,
                amountPaid = amountPaid,
                note = note
            )
            posDao.insertDebtPayment(payment)
            withContext(Dispatchers.Main) {
                onSuccess()
            }
        }
    }

    fun getDebtPaymentsForCustomer(customerId: Long) = posDao.getDebtPaymentsForCustomer(customerId)


    // --- DATA RECOVERY (BACKUP & RESTORE) ---
    suspend fun exportEncryptedBackup(): String {
        return withContext(Dispatchers.IO) {
            BackupRestoreUtil.generateBackup(posDao)
        }
    }

    suspend fun importEncryptedBackup(encryptedBase64: String): Boolean {
        return withContext(Dispatchers.IO) {
            BackupRestoreUtil.restoreBackup(posDao, encryptedBase64)
        }
    }

    // --- PHOTO SAVE CONVENIENCE ---
    fun getProductImageDirectory(): File {
        val context = getApplication<Application>()
        val dir = File(context.filesDir, "product_photos")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    override fun onCleared() {
        super.onCleared()
        try {
            toneGenerator?.release()
        } catch (e: Exception) {
            // Safe silence
        }
    }
}

class PosViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PosViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PosViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
