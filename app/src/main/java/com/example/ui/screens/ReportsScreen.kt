package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.entity.Customer
import com.example.data.entity.DebtPayment
import com.example.data.entity.Invoice
import com.example.data.entity.InvoiceItem
import com.example.data.relation.CustomerWithDebt
import com.example.data.relation.InvoiceWithItems
import com.example.ui.viewmodel.PosViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReportsScreen(
    viewModel: PosViewModel,
    paddingValues: PaddingValues
) {
    var tabIndex by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("المرابيح", "سجل الفواتير", "الديون (الكريدي)", "تنبيهات المخزن")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Tab Layout
        TabRow(
            selectedTabIndex = tabIndex,
            modifier = Modifier.fillMaxWidth()
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = tabIndex == index,
                    onClick = { tabIndex = index },
                    text = {
                        Text(
                            text = title,
                            fontSize = 12.sp,
                            fontWeight = if (tabIndex == index) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                )
            }
        }

        when (tabIndex) {
            0 -> ProfitsTab(viewModel)
            1 -> InvoicesHistoryTab(viewModel)
            2 -> DebtsTab(viewModel)
            3 -> StockAlertsTab(viewModel)
        }
    }
}

// ==========================================
// 1. PROFITS SUBSECTION
// ==========================================
@Composable
fun ProfitsTab(viewModel: PosViewModel) {
    val invoicesWithItems by viewModel.allInvoices.collectAsState()
    val customersWithDebt by viewModel.customersWithDebt.collectAsState()

    // Calculations
    val totalIncome = invoicesWithItems.sumOf { it.invoice.totalAmount }
    
    // Profit = sales price - purchase price for each barcode item sold
    val totalProfit = invoicesWithItems.sumOf { invoiceWithItems ->
        invoiceWithItems.items.sumOf { item ->
            (item.salePrice - item.purchasePrice) * item.quantity
        }
    }

    val totalMarketDebts = customersWithDebt.sumOf { it.totalDebt }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "التقرير المالي العام للمبيعات",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Card 1: Total Sales Revenue
        item {
            StatCard(
                title = "إجمالي المداخيل (المبيعات)",
                value = String.format("%.2f د.ت", totalIncome),
                icon = Icons.Default.TrendingUp,
                color = MaterialTheme.colorScheme.primaryContainer,
                onColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        // Card 2: Net profit
        item {
            StatCard(
                title = "صافي المرابيح (الأرباح الصافية)",
                value = String.format("%.2f د.ت", totalProfit),
                icon = Icons.Default.Paid,
                color = Color(0xFFE8F5E9), // Light Green
                onColor = Color(0xFF1B5E20)
            )
        }

        // Card 3: Market Credit list
        item {
            StatCard(
                title = "إجمالي ديون السوق (الكريدي الخارجي)",
                value = String.format("%.2f د.ت", totalMarketDebts),
                icon = Icons.Default.MoneyOff,
                color = MaterialTheme.colorScheme.errorContainer,
                onColor = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = onColor.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = value,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = onColor
                )
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = onColor.copy(alpha = 0.6f),
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

// ==========================================
// 2. INVOICES HISTORY SUBSECTION
// ==========================================
@Composable
fun InvoicesHistoryTab(viewModel: PosViewModel) {
    val invoicesWithItems by viewModel.allInvoices.collectAsState()
    var selectedInvoiceForDetail by remember { mutableStateOf<InvoiceWithItems?>(null) }
    
    // Filter status index: 0 = الكل, 1 = نقداً (كاش), 2 = كريدي (ديون)
    var filterIndex by remember { mutableIntStateOf(0) }
    val filterTitles = listOf("الكل", "نقداً (كاش)", "كريدي (ديون)")

    val filteredInvoices = remember(invoicesWithItems, filterIndex) {
        when (filterIndex) {
            1 -> invoicesWithItems.filter { !it.invoice.isDebt }
            2 -> invoicesWithItems.filter { it.invoice.isDebt }
            else -> invoicesWithItems
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = filterIndex,
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ) {
            filterTitles.forEachIndexed { index, title ->
                Tab(
                    selected = filterIndex == index,
                    onClick = { filterIndex = index },
                    text = {
                        Text(
                            text = title,
                            fontSize = 12.sp,
                            fontWeight = if (filterIndex == index) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        if (filteredInvoices.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "لا توجد فواتير مطابقة لهذا الفلتر بعد.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                items(filteredInvoices, key = { it.invoice.id }) { item ->
                    InvoiceRow(
                        invoiceWithItems = item,
                        onClick = { selectedInvoiceForDetail = item }
                    )
                }
            }
        }
    }

    if (selectedInvoiceForDetail != null) {
        InvoiceDetailDialog(
            invoiceWithItems = selectedInvoiceForDetail!!,
            onDismiss = { selectedInvoiceForDetail = null }
        )
    }
}

@Composable
fun InvoiceRow(
    invoiceWithItems: InvoiceWithItems,
    onClick: () -> Unit
) {
    val dateText = remember(invoiceWithItems.invoice.timestamp) {
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        sdf.format(Date(invoiceWithItems.invoice.timestamp))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "فاتورة: ${invoiceWithItems.invoice.invoiceNumber}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = dateText,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${invoiceWithItems.items.size} من المواد المباعة",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = String.format("%.2f د.ت", invoiceWithItems.invoice.totalAmount),
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .background(
                            color = if (invoiceWithItems.invoice.isDebt) MaterialTheme.colorScheme.errorContainer else Color(0xFFE8F5E9),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (invoiceWithItems.invoice.isDebt) "كريدي (دين)" else "نقداً",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (invoiceWithItems.invoice.isDebt) MaterialTheme.colorScheme.onErrorContainer else Color(0xFF1B5E20)
                    )
                }
            }
        }
    }
}

@Composable
fun InvoiceDetailDialog(
    invoiceWithItems: InvoiceWithItems,
    onDismiss: () -> Unit
) {
    val dateText = remember(invoiceWithItems.invoice.timestamp) {
        val sdf = SimpleDateFormat("yyyy/MM/dd - HH:mm:ss", Locale.getDefault())
        sdf.format(Date(invoiceWithItems.invoice.timestamp))
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                val dialogContext = LocalContext.current
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            com.example.ui.util.InvoiceShareHelper.shareInvoiceAsPdf(dialogContext, invoiceWithItems)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "مشاركة الفاتورة كـ PDF",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = "تفاصيل الفاتورة",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.size(48.dp))
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "رقم الفاتورة: ${invoiceWithItems.invoice.invoiceNumber}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(text = "تاريخ البيع: $dateText", fontSize = 12.sp)
                    Text(
                        text = "طريقة الدفع: " + if (invoiceWithItems.invoice.isDebt) "كريدي (ديون)" else "نقدا (كاش)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "المواد المشتراة:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(invoiceWithItems.items) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(item.productName, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Text("باركود: ${item.productBarcode}", fontSize = 10.sp, color = Color.Gray)
                            }
                            Text(
                                "(${item.quantity} قطة) * ${item.salePrice} د.ت",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("الإجمالي الكلي للبيع:", fontWeight = FontWeight.Bold)
                    Text(
                        text = String.format("%.2f د.ت", invoiceWithItems.invoice.totalAmount),
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 18.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("حسناً")
                }
            }
        }
    }
}


// ==========================================
// 3. CREDIT / DEBTS SUBSECTION
// ==========================================
@Composable
fun DebtsTab(viewModel: PosViewModel) {
    val context = LocalContext.current
    val customersWithDebt by viewModel.customersWithDebt.collectAsState()
    var selectedCustomerForTransactions by remember { mutableStateOf<CustomerWithDebt?>(null) }
    var showAddManualDebtGeneralDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (customersWithDebt.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "لا يوجد أي عميل مسجل ديون (كريدي) في النظام.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                items(customersWithDebt, key = { it.id }) { customer ->
                    CustomerDebtRow(
                        customer = customer,
                        onClick = { selectedCustomerForTransactions = customer }
                    )
                }
            }
        }

        // Extended Floating Action Button to add new debts manually (cyber cyan glowing styled)
        ExtendedFloatingActionButton(
            onClick = { showAddManualDebtGeneralDialog = true },
            icon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp)) },
            text = { Text("إضافة دين جديد", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color(0xFF121212),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .shadow(
                    elevation = 10.dp,
                    shape = RoundedCornerShape(16.dp),
                    clip = false,
                    ambientColor = MaterialTheme.colorScheme.primary,
                    spotColor = MaterialTheme.colorScheme.primary
                )
        )
    }

    if (selectedCustomerForTransactions != null) {
        CustomerTransactionsDialog(
            customer = selectedCustomerForTransactions!!,
            viewModel = viewModel,
            onDismiss = { selectedCustomerForTransactions = null }
        )
    }

    if (showAddManualDebtGeneralDialog) {
        val allCustomersState by viewModel.allCustomers.collectAsState()

        AddManualDebtGeneralDialog(
            customers = allCustomersState,
            onDismiss = { showAddManualDebtGeneralDialog = false },
            onConfirmNewCustomer = { name, amount, note ->
                viewModel.addCustomer(name, phone = null) { customer ->
                    viewModel.addManualDebt(
                        customerId = customer.id,
                        amount = amount,
                        note = note
                    ) {
                        showAddManualDebtGeneralDialog = false
                        Toast.makeText(context, "تم تسجيل الدين للعميل الجديد بنجاح", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onConfirmExistingCustomer = { customerId, amount, note ->
                viewModel.addManualDebt(
                    customerId = customerId,
                    amount = amount,
                    note = note
                ) {
                    showAddManualDebtGeneralDialog = false
                    Toast.makeText(context, "تم تسجيل الدين للعميل بنجاح", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddManualDebtGeneralDialog(
    customers: List<Customer>,
    onDismiss: () -> Unit,
    onConfirmNewCustomer: (String, Double, String) -> Unit,
    onConfirmExistingCustomer: (Long, Double, String) -> Unit
) {
    var tabIndex by remember { mutableIntStateOf(0) } // 0: Existing, 1: New
    var selectedCustomer by remember { mutableStateOf<Customer?>(null) }
    var expandedDropdown by remember { mutableStateOf(false) }

    var newCustomerName by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "تسجيل دين جديد يدوياً",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                // TAB SELECTOR
                TabRow(
                    selectedTabIndex = tabIndex,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Tab(
                        selected = tabIndex == 0,
                        onClick = { tabIndex = 0 },
                        text = { Text("عميل مسجل", fontSize = 13.sp) }
                    )
                    Tab(
                        selected = tabIndex == 1,
                        onClick = { tabIndex = 1 },
                        text = { Text("عميل جديد", fontSize = 13.sp) }
                    )
                }

                // CUSTOMER SELECTION
                if (tabIndex == 0) {
                    if (customers.isEmpty()) {
                        Text(
                            text = "لا يوجد عملاء مسجلين حالياً. يرجى إنشاء عميل جديد.",
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = selectedCustomer?.name ?: "إختر العميل...",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("اختر عميل") },
                                trailingIcon = {
                                    IconButton(onClick = { expandedDropdown = !expandedDropdown }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedDropdown = !expandedDropdown }
                            )

                            DropdownMenu(
                                expanded = expandedDropdown,
                                onDismissRequest = { expandedDropdown = false },
                                modifier = Modifier.fillMaxWidth(0.9f)
                            ) {
                                customers.forEach { customer ->
                                    DropdownMenuItem(
                                        text = { Text(customer.name) },
                                        onClick = {
                                            selectedCustomer = customer
                                            expandedDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = newCustomerName,
                        onValueChange = { newCustomerName = it },
                        label = { Text("اسم العميل الجديد *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // AMOUNT
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("مبلغ الدين د.ت *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // NOTE/DESCRIPTION
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("توصيف وسبب الدين *") },
                    placeholder = { Text("مثال: علف، حليب، زيت...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("إلغاء")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val amt = amountText.toDoubleOrNull()
                            if (amt == null || amt <= 0) return@Button
                            if (noteText.trim().isEmpty()) return@Button
                            if (tabIndex == 0) {
                                selectedCustomer?.let {
                                    onConfirmExistingCustomer(it.id, amt, noteText.trim())
                                }
                            } else {
                                if (newCustomerName.trim().isNotEmpty()) {
                                    onConfirmNewCustomer(newCustomerName.trim(), amt, noteText.trim())
                                }
                            }
                        },
                        enabled = (amountText.toDoubleOrNull() != null && amountText.toDouble() > 0 && noteText.trim().isNotEmpty()) &&
                                (if (tabIndex == 0) selectedCustomer != null else newCustomerName.trim().isNotEmpty())
                    ) {
                        Text("تسجيل الدين يدوياً")
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerDebtRow(
    customer: CustomerWithDebt,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = customer.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                if (!customer.phone.isNullOrEmpty()) {
                    Text(
                        text = "الهاتف: ${customer.phone}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "صافي الدين المستحق:",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = String.format("%.2f د.ت", customer.totalDebt),
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = if (customer.totalDebt > 0) MaterialTheme.colorScheme.error else Color(0xFF1B5E20)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerTransactionsDialog(
    customer: CustomerWithDebt,
    viewModel: PosViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Payments lists
    val paymentsFlow = remember(customer.id) { viewModel.getDebtPaymentsForCustomer(customer.id) }
    val payments by paymentsFlow.collectAsState(initial = emptyList())

    // Direct fetch of customer's unpaid invoices
    val allInvoices by viewModel.allInvoices.collectAsState()
    val unpaidInvoices = remember(allInvoices, customer.id) {
        allInvoices.filter { it.invoice.isDebt && it.invoice.customerId == customer.id }
    }

    // Actions state
    var showAddPaymentDialog by remember { mutableStateOf(false) }
    var showAddManualDebtDialog by remember { mutableStateOf(false) }

    // Forms
    var amountText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 12.dp)
                .heightIn(max = 520.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    text = "تفاصيل حساب: ${customer.name}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // High contrast debt tracker
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("صافي الديون المستحقة الكلية", fontSize = 11.sp)
                        Text(
                            text = String.format("%.2f د.ت", customer.totalDebt),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action buttons side-by-side
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Record payment
                    Button(
                        onClick = { showAddPaymentDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.PriceCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تسديد جزء", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Add Debt manual
                    Button(
                        onClick = { showAddManualDebtDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("دين جديد يدوي", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 1. Unpaid credit invoices causing this debt
                Text(
                    text = "الفواتير غير المدفوعة (الكريدي المفرّق):",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error
                )

                Spacer(modifier = Modifier.height(6.dp))

                if (unpaidInvoices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("لا توجد فواتير معلقة حالياً.", fontSize = 11.sp, color = Color.Gray)
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        unpaidInvoices.forEach { item ->
                            val invSdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
                            val invDate = invSdf.format(Date(item.invoice.timestamp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "رقم الفاتورة: ${item.invoice.invoiceNumber}", 
                                        fontSize = 12.sp, 
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = "بتاريخ: $invDate", 
                                        fontSize = 10.sp, 
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (item.items.isNotEmpty()) {
                                        val itemsSummary = item.items.joinToString(", ") { "${it.productName} (x${it.quantity})" }
                                        Text(
                                            text = "البضائع: $itemsSummary",
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                            maxLines = 1
                                        )
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = String.format("%.2f د.ت", item.invoice.totalAmount),
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (item.invoice.paidAmount > 0) {
                                        Text(
                                            text = "مدفوع جزئي: " + String.format("%.2f", item.invoice.paidAmount),
                                            color = Color(0xFF1B5E20),
                                            fontSize = 8.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Settlement payments list
                Text(
                    text = "سجل التنزيلات والتسديدات السابقة:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFF1B5E20)
                )

                Spacer(modifier = Modifier.height(6.dp))

                if (payments.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("لا يوجد دفعات مسددة بعد.", fontSize = 11.sp, color = Color.Gray)
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        payments.forEach { payment ->
                            val pSdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
                            val payDate = pSdf.format(Date(payment.timestamp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("تسديد نقدي: ${payment.note ?: "بدون ملاحظة"}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    Text("بتاريخ: $payDate", fontSize = 9.sp, color = Color.Gray)
                                }
                                Text("- " + String.format("%.2f د.ت", payment.amountPaid), color = Color(0xFF1B5E20), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("إغلاق وإخفاء التقرير")
                }
            }
        }
    }

    // A. Sub-Dialog: Record Partial Payment
    if (showAddPaymentDialog) {
        AlertDialog(
            onDismissRequest = { showAddPaymentDialog = false },
            title = { Text("تسجيل دفعة جزئية (تسديد)", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("أدخل تفاصيل ومقدار المبلغ المسلم من العميل:")
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("المبلغ المدفوع د.ت *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text("ملاحظة التسديد (اختياري)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = amountText.toDoubleOrNull()
                        if (amt == null || amt <= 0) {
                            Toast.makeText(context, "الرجاء إدخال قيمة صحيحة", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        viewModel.recordDebtPayment(
                            customerId = customer.id,
                            amountPaid = amt,
                            note = noteText.trim().ifEmpty { null }
                        ) {
                            amountText = ""
                            noteText = ""
                            showAddPaymentDialog = false
                            Toast.makeText(context, "تم تسجيل الدفعة وتخفيض الدين بنجاح", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("تسجيل التسديد")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPaymentDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // B. Sub-Dialog: Record new manual debt
    if (showAddManualDebtDialog) {
        AlertDialog(
            onDismissRequest = { showAddManualDebtDialog = false },
            title = { Text("إضافة دين جديد يدوياً", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("أدخل تفاصيل ومقدار الدين المضاف للعميل:")
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("مقدار الدين د.ت *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text("توصيف الدين (مثال: علف، حليب...) *") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = amountText.toDoubleOrNull()
                        if (amt == null || amt <= 0) {
                            Toast.makeText(context, "الرجاء إدخال دين صحيح", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (noteText.trim().isEmpty()) {
                            Toast.makeText(context, "الرجاء إدخال توصيف وتفصيل للدين لتوثيقه", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        viewModel.addManualDebt(
                            customerId = customer.id,
                            amount = amt,
                            note = noteText.trim()
                        ) {
                            amountText = ""
                            noteText = ""
                            showAddManualDebtDialog = false
                            Toast.makeText(context, "تم تسجيل الدين يدوياً بنجاح", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("إضافة دين")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddManualDebtDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}


// ==========================================
// 4. STOCK ALERTS SUBSECTION
// ==========================================
@Composable
fun StockAlertsTab(viewModel: PosViewModel) {
    val alerts by viewModel.lowStockProducts.collectAsState()

    if (alerts.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF1B5E20),
                    modifier = Modifier.size(54.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "كل شىء على ما يرام! لا يوجد أي منتج قارب على النفاد والمخزن ممتلئ بسلام.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            // Notice Banner
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "المنتجات التالية أوشكت كميتها على النفاد (الكمية المتوفرة تساوي أو تقل عن 5 قطع). يرجى التزويد الفوري لها لحفظ سلاسة المستودع.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp)
            ) {
                items(alerts, key = { it.id }) { product ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = product.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "الباركود: ${product.barcode}",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "المتبقي: ${product.stockQuantity ?: 0} قطع",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
