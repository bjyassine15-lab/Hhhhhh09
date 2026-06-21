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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
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

    val isDark = MaterialTheme.colorScheme.background == Color(0xFF000000)
    val rootBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF000000),
                Color(0xFF070C10),
                Color(0xFF020406)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFF4F6F9),
                Color(0xFFECEFF1),
                Color(0xFFF4F6F9)
            )
        )
    }

    val containerColor = if (isDark) Color(0xFF0C0C0C) else Color(0xFFFFFFFF)
    val contentColor = if (isDark) Color.White else Color(0xFF1E293B)
    val dividerColor = if (isDark) Color(0xFF161616) else Color(0xFFE2E8F0)
    val unselectedTabColor = if (isDark) Color(0xFF666666) else Color(0xFF64748B)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(brush = rootBrush)
    ) {
        // Premium Custom Animated TabRow
        TabRow(
            selectedTabIndex = tabIndex,
            modifier = Modifier.fillMaxWidth(),
            containerColor = containerColor,
            contentColor = contentColor,
            indicator = { tabPositions ->
                if (tabIndex < tabPositions.size) {
                    val activeColor = when (tabIndex) {
                        0 -> Color(0xFF4CAF50) // المرابيح = Green
                        1 -> Color(0xFFE040FB) // سجل الفواتير = Fuchsia/Purple
                        2 -> Color(0xFFF44336) // الديون (الكريدي) = Red
                        3 -> Color(0xFFFF9800) // تنبيهات المخزن = Amber
                        else -> Color(0xFFE040FB)
                    }
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[tabIndex]),
                        color = activeColor,
                        height = 3.dp
                    )
                }
            },
            divider = {
                HorizontalDivider(color = dividerColor)
            }
        ) {
            tabTitles.forEachIndexed { index, title ->
                val isSelected = tabIndex == index
                val activeTabColor = when (index) {
                    0 -> Color(0xFF4CAF50)
                    1 -> Color(0xFFE040FB)
                    2 -> Color(0xFFF44336)
                    3 -> Color(0xFFFF9800)
                    else -> Color(0xFFE040FB)
                }
                Tab(
                    selected = isSelected,
                    onClick = { tabIndex = index },
                    selectedContentColor = activeTabColor,
                    unselectedContentColor = unselectedTabColor,
                    text = {
                        Text(
                            text = title,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
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

// Reusable Professional Material 3 Empty State Composable
@Composable
fun EmptyStateRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    accentColor: Color
) {
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF000000)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(accentColor.copy(alpha = 0.05f), CircleShape)
                .border(1.dp, accentColor.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = if (isDark) Color.White else Color(0xFF1E293B),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            fontSize = 12.sp,
            color = if (isDark) Color(0xFF8A8A8A) else Color(0xFF64748B),
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
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
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF000000)

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
                color = if (isDark) Color.White else Color(0xFF1E293B),
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // Card 1: Total Sales Revenue
        item {
            StatCard(
                title = "إجمالي المداخيل (المبيعات)",
                value = String.format("%.2f د.ت", totalIncome),
                icon = Icons.Default.TrendingUp,
                color = Color(0xFF121212),
                onColor = Color.White
            )
        }

        // Card 2: Net profit
        item {
            StatCard(
                title = "صافي المرابيح (الأرباح الصافية)",
                value = String.format("%.2f د.ت", totalProfit),
                icon = Icons.Default.Paid,
                color = Color(0xFF121212),
                onColor = Color(0xFF4CAF50)
            )
        }

        // Card 3: Market Credit list
        item {
            StatCard(
                title = "إجمالي ديون السوق (الكريدي الخارجي)",
                value = String.format("%.2f د.ت", totalMarketDebts),
                icon = Icons.Default.MoneyOff,
                color = Color(0xFF121212),
                onColor = Color(0xFFF44336)
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
    // Dynamically apply beautiful cyber POS system design
    val isProfit = title.contains("مرابيح") || title.contains("الأرباح")
    val isDebt = title.contains("ديون") || title.contains("الكريدي")
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF000000)

    val strokeColor = when {
        isProfit -> Color(0xFF4CAF50).copy(alpha = 0.3f)
        isDebt -> Color(0xFFF44336).copy(alpha = 0.3f)
        else -> if (isDark) Color(0xFFE040FB).copy(alpha = 0.3f) else Color(0xFFE2E8F0)
    }

    val defaultValColor = if (isDark) Color.White else Color(0xFF12151C)
    val valueTextColor = when {
        isProfit -> Color(0xFF4CAF50)
        isDebt -> Color(0xFFF44336)
        else -> defaultValColor
    }

    val iconTint = when {
        isProfit -> Color(0xFF4CAF50)
        isDebt -> Color(0xFFF44336)
        else -> Color(0xFFE040FB)
    }

    val cardBg = if (isDark) Color(0xFF121212) else Color(0xFFFFFFFF)
    val titleColor = if (isDark) Color(0xFFC7C7C7) else Color(0xFF475569)
    val circleBg = if (isDark) Color(0xFF171717) else Color(0xFFF1F5F9)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, strokeColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = titleColor
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = value,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = valueTextColor,
                    style = androidx.compose.ui.text.TextStyle(
                        shadow = if (!isProfit && !isDebt) Shadow(
                            color = Color(0xFFE040FB).copy(alpha = 0.2f),
                            offset = Offset(0f, 0f),
                            blurRadius = 8f
                        ) else null
                    )
                )
            }
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(circleBg, CircleShape)
                    .border(1.dp, strokeColor.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(26.dp)
                )
            }
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
    var invoiceToDelete by remember { mutableStateOf<Invoice?>(null) }
    val context = LocalContext.current

    val isDark = MaterialTheme.colorScheme.background == Color(0xFF000000)
    val tabRowContainerColor = if (isDark) Color(0xFF0C0C0C) else Color(0xFFF1F5F9)
    val tabRowContentColor = if (isDark) Color.White else Color(0xFF1E293B)
    val dividerColor = if (isDark) Color(0xFF161616) else Color(0xFFE2E8F0)
    
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
            containerColor = tabRowContainerColor,
            contentColor = tabRowContentColor,
            indicator = { tabPositions ->
                if (filterIndex < tabPositions.size) {
                    val activeColor = when (filterIndex) {
                        0 -> Color(0xFFE040FB) // الكل = Fuchsia
                        1 -> Color(0xFF4CAF50) // كاش = Green
                        2 -> Color(0xFFF44336) // كريدي = Red
                        else -> Color(0xFFE040FB)
                    }
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[filterIndex]),
                        color = activeColor,
                        height = 3.dp
                    )
                }
            },
            divider = {
                HorizontalDivider(color = dividerColor)
            }
        ) {
            filterTitles.forEachIndexed { index, title ->
                val isSelected = filterIndex == index
                val activeTabColor = when (index) {
                    0 -> Color(0xFFE040FB)
                    1 -> Color(0xFF4CAF50)
                    2 -> Color(0xFFF44336)
                    else -> Color(0xFFE040FB)
                }
                Tab(
                    selected = isSelected,
                    onClick = { filterIndex = index },
                    selectedContentColor = activeTabColor,
                    unselectedContentColor = Color(0xFF8A8A8A),
                    text = {
                        Text(
                            text = title,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        if (filteredInvoices.isEmpty()) {
            EmptyStateRow(
                icon = Icons.Default.Receipt,
                title = "سجل الفواتير فارغ",
                description = "لا توجد أي فواتير مسجلة مطابقة لهذا الفلتر بعد في سجل مبيعات متجرك.",
                accentColor = Color(0xFFE040FB)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                items(filteredInvoices, key = { it.invoice.id }) { item ->
                    InvoiceRow(
                        invoiceWithItems = item,
                        onClick = { selectedInvoiceForDetail = item },
                        onDeleteClick = { invoiceToDelete = it }
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

    if (invoiceToDelete != null) {
        AlertDialog(
            onDismissRequest = { invoiceToDelete = null },
            title = {
                Text(
                    "حذف الفاتورة",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
            },
            text = {
                Text(
                    "هل أنت متأكد من حذف هذه الفاتورة؟ سيتم تحديث الحسابات بناءً على ذلك.",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                    onClick = {
                        invoiceToDelete?.let { invoice ->
                            viewModel.deleteInvoice(invoice) {
                                invoiceToDelete = null
                                Toast.makeText(context, "تم حذف الفاتورة وتحديث الحسابات بنجاح.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("تأكيد", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { invoiceToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
fun InvoiceRow(
    invoiceWithItems: InvoiceWithItems,
    onClick: () -> Unit,
    onDeleteClick: (Invoice) -> Unit
) {
    val dateText = remember(invoiceWithItems.invoice.timestamp) {
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        sdf.format(Date(invoiceWithItems.invoice.timestamp))
    }

    val isDark = MaterialTheme.colorScheme.background == Color(0xFF000000)
    val cardBg = if (isDark) Color(0xFF121212) else Color(0xFFFFFFFF)
    val cardBorder = if (isDark) Color(0xFF1C1C1C) else Color(0xFFE2E8F0)
    val textPrimary = if (isDark) Color.White else Color(0xFF12151C)
    val textSecondary = if (isDark) Color(0xFF8A8A8A) else Color(0xFF64748B)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "فاتورة: ${invoiceWithItems.invoice.invoiceNumber}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = dateText,
                    fontSize = 11.sp,
                    color = textSecondary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${invoiceWithItems.items.size} من المواد المباعة",
                    fontSize = 12.sp,
                    color = Color(0xFFE040FB)
                )
            }

            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(
                    text = String.format("%.2f د.ت", invoiceWithItems.invoice.totalAmount),
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = Color(0xFFE040FB)
                )
                
                Spacer(modifier = Modifier.height(6.dp))

                val isDebtStatus = invoiceWithItems.invoice.isDebt
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isDebtStatus) Color(0xFFF44336).copy(alpha = 0.15f) else Color(0xFF4CAF50).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .border(
                            1.dp,
                            if (isDebtStatus) Color(0xFFF44336).copy(alpha = 0.3f) else Color(0xFF4CAF50).copy(alpha = 0.3f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isDebtStatus) "كريدي (دين)" else "نقداً",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDebtStatus) Color(0xFFF44336) else Color(0xFF4CAF50)
                    )
                }
            }

            IconButton(
                onClick = { onDeleteClick(invoiceWithItems.invoice) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "حذف الفاتورة",
                    tint = Color(0xFFF44336).copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
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
            EmptyStateRow(
                icon = Icons.Default.Groups,
                title = "سجل الديون فارغ تماماً",
                description = "دفتر الكريدي نظيف ولا توجد مبالغ مستحقة أو ديون معلقة على أي زبون حالياً.",
                accentColor = Color(0xFFF44336)
            )
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

        // Extended Floating Action Button to add new debts manually (cyber red glowing styled as requested)
        ExtendedFloatingActionButton(
            onClick = { showAddManualDebtGeneralDialog = true },
            icon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp)) },
            text = { Text("إضافة دين جديد", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
            containerColor = Color(0xFFF44336),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(16.dp),
                    clip = false,
                    ambientColor = Color(0xFFF44336),
                    spotColor = Color(0xFFF44336)
                )
                .border(
                    BorderStroke(1.5.dp, Color(0xFFFF8A80).copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp)
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

    val isDark = MaterialTheme.colorScheme.background == Color(0xFF000000)
    val cardBg = if (isDark) Color(0xFF171717) else Color(0xFFFFFFFF)
    val cardBorder = if (isDark) Color(0xFF262626) else Color(0xFFE2E8F0)
    val labelColor = if (isDark) Color(0xFF8A8A8A) else Color(0xFF64748B)
    val fieldBg = if (isDark) Color(0xFF121212) else Color(0xFFF8FAFC)
    val textCol = if (isDark) Color.White else Color(0xFF12151C)

    val inputColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = cardBorder,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = labelColor,
        focusedTextColor = textCol,
        unfocusedTextColor = textCol,
        cursorColor = MaterialTheme.colorScheme.primary,
        focusedContainerColor = fieldBg,
        unfocusedContainerColor = fieldBg
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .shadow(16.dp, RoundedCornerShape(28.dp))
                .border(BorderStroke(1.dp, cardBorder), RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Circular icon header badge
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(56.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = "تسجيل دين جديد يدوياً",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textCol,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                // TAB SELECTOR (Custom Modern Pill Switcher)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isDark) Color(0xFF1E1E1E) else Color(0xFFF1F5F9))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val activeBg = MaterialTheme.colorScheme.primary
                    val activeText = if (isDark) Color.Black else Color.White
                    val inactiveText = if (isDark) Color(0xFF8A8A8A) else Color(0xFF64748B)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (tabIndex == 0) activeBg else Color.Transparent)
                            .clickable { tabIndex = 0 }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "عميل مسجل",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (tabIndex == 0) activeText else inactiveText
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (tabIndex == 1) activeBg else Color.Transparent)
                            .clickable { tabIndex = 1 }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "عميل جديد",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (tabIndex == 1) activeText else inactiveText
                        )
                    }
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
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                    )
                                },
                                trailingIcon = {
                                    IconButton(onClick = { expandedDropdown = !expandedDropdown }) {
                                        Icon(
                                            Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = inputColors,
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
                                        text = { Text(customer.name, fontWeight = FontWeight.Medium) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Person,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
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
                        leadingIcon = {
                            Icon(
                                Icons.Default.PersonAdd,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = inputColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // AMOUNT
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("مبلغ الدين د.ت *") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.AttachMoney,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                    },
                    trailingIcon = {
                        Text(
                            text = "د.ت",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = inputColors,
                    modifier = Modifier.fillMaxWidth()
                )

                // NOTE/DESCRIPTION
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("توصيف وسبب الدين *") },
                    placeholder = { Text("مثال: علف، حليب، زيت...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Notes,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = inputColors,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(25.dp),
                        border = BorderStroke(1.dp, if (isDark) Color(0xFF333333) else Color(0xFFCBD5E1)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isDark) Color(0xFFC7C7C7) else Color(0xFF475569)
                        )
                    ) {
                        Text(
                            text = "إلغاء",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

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
                                (if (tabIndex == 0) selectedCustomer != null else newCustomerName.trim().isNotEmpty()),
                        modifier = Modifier
                            .weight(1.5f)
                            .height(50.dp),
                        shape = RoundedCornerShape(25.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            contentColor = if (isDark) Color.Black else Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 4.dp,
                            pressedElevation = 2.dp,
                            disabledElevation = 0.dp
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "تسجيل الدين",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
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
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF000000)
    val cardBg = if (isDark) Color(0xFF121212) else Color(0xFFFFFFFF)
    val cardBorder = if (isDark) Color(0xFF1C1C1C) else Color(0xFFE2E8F0)
    val textPrimary = if (isDark) Color.White else Color(0xFF12151C)
    val textSecondary = if (isDark) Color(0xFF8A8A8A) else Color(0xFF64748B)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorder)
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
                    fontSize = 15.sp,
                    color = textPrimary
                )
                if (!customer.phone.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "الهاتف: ${customer.phone}",
                        fontSize = 12.sp,
                        color = textSecondary
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "صافي الدين المستحق:",
                    fontSize = 10.sp,
                    color = textSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = String.format("%.2f د.ت", customer.totalDebt),
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = if (customer.totalDebt > 0) Color(0xFFF44336) else Color(0xFF4CAF50)
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
    var invoiceToDelete by remember { mutableStateOf<Invoice?>(null) }
    var paymentToDelete by remember { mutableStateOf<DebtPayment?>(null) }

    // Forms
    var amountText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }

    val isDark = MaterialTheme.colorScheme.background == Color(0xFF000000)
    val cardBg = if (isDark) Color(0xFF1E1E1E) else Color(0xFFFFFFFF)
    val txtPrimary = if (isDark) Color.White else Color(0xFF12151C)
    val txtSecondary = if (isDark) Color(0xFF8A8A8A) else Color(0xFF64748B)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 12.dp)
                .heightIn(max = 520.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg)
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
                        Text("صافي الديون المستحقة الكلية", fontSize = 11.sp, color = txtPrimary)
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
                                        color = if (isDark) MaterialTheme.colorScheme.onErrorContainer else Color(0xFF991B1B)
                                    )
                                    Text(
                                        text = "بتاريخ: $invDate", 
                                        fontSize = 10.sp, 
                                        color = txtSecondary
                                    )
                                    if (item.items.isNotEmpty()) {
                                        val itemsSummary = item.items.joinToString(", ") { "${it.productName} (x${it.quantity})" }
                                        Text(
                                            text = "البضائع: $itemsSummary",
                                            fontSize = 9.sp,
                                            color = txtSecondary.copy(alpha = 0.8f),
                                            maxLines = 1
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                    IconButton(
                                        onClick = { invoiceToDelete = item.invoice },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "حذف الفاتورة",
                                            tint = Color(0xFFF44336).copy(alpha = 0.8f),
                                            modifier = Modifier.size(16.dp)
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
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("تسديد نقدي: ${payment.note ?: "بدون ملاحظة"}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = txtPrimary)
                                    Text("بتاريخ: $payDate", fontSize = 9.sp, color = txtSecondary)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("- " + String.format("%.2f د.ت", payment.amountPaid), color = Color(0xFF4CAF50), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    IconButton(
                                        onClick = { paymentToDelete = payment },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "حذف الدفعة",
                                            tint = Color(0xFFF44336).copy(alpha = 0.8f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
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

    // C. Sub-dialogs: Confirm invoice deletion
    if (invoiceToDelete != null) {
        AlertDialog(
            onDismissRequest = { invoiceToDelete = null },
            title = { Text("تأكيد حذف الفاتورة", fontWeight = FontWeight.Bold) },
            text = { Text("هل أنت متأكد من حذف هذه الفاتورة؟ سيتم تحديث الحسابات والديون بناءً على ذلك.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                    onClick = {
                        invoiceToDelete?.let { inv ->
                            viewModel.deleteInvoice(inv) {
                                invoiceToDelete = null
                                Toast.makeText(context, "تم حذف الفاتورة وتحديث الحسابات بنجاح.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("تأكيد", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { invoiceToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // D. Sub-dialogs: Confirm payment deletion
    if (paymentToDelete != null) {
        AlertDialog(
            onDismissRequest = { paymentToDelete = null },
            title = { Text("تأكيد حذف الدفعة", fontWeight = FontWeight.Bold) },
            text = { Text("هل أنت متأكد من حذف دفعة التسديد هذه كلياً؟ سيتم رفع الدين الأصلي للعميل بناءً على ذلك.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                    onClick = {
                        paymentToDelete?.let { pay ->
                            viewModel.deleteDebtPayment(pay) {
                                paymentToDelete = null
                                Toast.makeText(context, "تم حذف دفعة التسديد بنجاح وتحديث صافي الديون.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("تأكيد", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { paymentToDelete = null }) {
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
        EmptyStateRow(
            icon = Icons.Default.CheckCircle,
            title = "مستوى المخازن سليم بالكامل",
            description = "كل شيء على ما يرام! لا توجد منتجات منخفضة المخزون حالياً والمستودع مؤمن بالكامل بسلام.",
            accentColor = Color(0xFF4CAF50)
        )
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
