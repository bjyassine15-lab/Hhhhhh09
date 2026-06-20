package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import com.example.data.entity.Product
import com.example.ui.components.CameraScannerView
import com.example.ui.viewmodel.PosViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    viewModel: PosViewModel,
    paddingValues: PaddingValues
) {
    val context = LocalContext.current
    val products by viewModel.allProducts.collectAsState()

    var showAddEditDialog by remember { mutableStateOf(false) }
    var selectedProductForEdit by remember { mutableStateOf<Product?>(null) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    selectedProductForEdit = null
                    showAddEditDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color(0xFF121212),
                modifier = Modifier.shadow(
                    elevation = 10.dp,
                    shape = FloatingActionButtonDefaults.shape,
                    clip = false,
                    ambientColor = MaterialTheme.colorScheme.primary,
                    spotColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة منتج", modifier = Modifier.size(24.dp))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .shadow(
                        elevation = 4.dp,
                        shape = RoundedCornerShape(12.dp),
                        clip = false
                    )
                    .background(
                        color = Color(0xFF1E1E1E),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "المنتجات والمخازن",
                        color = Color(0xFFE0E0E0),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .size(28.dp)
                            .background(
                                color = Color(0xFF000000),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = products.size.toString(),
                            color = Color(0xFF00E5FF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            if (products.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Inventory,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "لا يوجد أي منتج مسجل في النظام حالياً. إضغط على الزر العائم (+) في الأسفل لإضافة أول منتج.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 80.dp)
                ) {
                    items(products, key = { it.id }) { product ->
                        ProductItemRow(
                            product = product,
                            onEdit = {
                                selectedProductForEdit = product
                                showAddEditDialog = true
                            },
                            onDelete = {
                                viewModel.deleteProduct(product) {
                                    Toast.makeText(context, "تم حذف المنتج بنجاح", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // --- Add/Edit Dialog ---
    if (showAddEditDialog) {
        AddEditProductDialog(
            product = selectedProductForEdit,
            viewModel = viewModel,
            onDismiss = { showAddEditDialog = false },
            onSave = {
                showAddEditDialog = false
                Toast.makeText(context, "تم حفظ بيانات المنتج بنجاح", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun ProductItemRow(
    product: Product,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Local Image preview
            val painter = rememberAsyncImagePainter(
                model = if (!product.imagePath.isNullOrEmpty()) File(product.imagePath) else null
            )

            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!product.imagePath.isNullOrEmpty()) {
                    Image(
                        painter = painter,
                        contentDescription = "صورة المنتج",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "باركود: ${product.barcode}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = String.format("شراء: %.2f د.ت", product.purchasePrice),
                        fontSize = 12.sp,
                        color = Color.Red.copy(alpha = 0.8f)
                    )
                    Text(
                        text = String.format("بيع: %.2f د.ت", product.salePrice),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                // Optional stock indicator
                if (product.stockQuantity != null) {
                    val lowStock = product.stockQuantity <= 5
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .background(
                                color = if (lowStock) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "المخزن: ${product.stockQuantity} قطع",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (lowStock) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Edit & Delete icons
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "تعديل المنتج", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "حذف المنتج", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("حذف المنتج", fontWeight = FontWeight.Bold) },
            text = { Text("هل أنت متأكد من حذف المنتج (${product.name})؟ سيتم حذفه محلياً وتفادي عرضه في البيوعات.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }
                ) {
                    Text("نعم، احذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditProductDialog(
    product: Product?,
    viewModel: PosViewModel,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current

    // Forms fields
    var name by remember { mutableStateOf(product?.name ?: "") }
    var barcode by remember { mutableStateOf(product?.barcode ?: "") }
    var purchasePriceStr by remember { mutableStateOf(product?.purchasePrice?.toString() ?: "") }
    var salePriceStr by remember { mutableStateOf(product?.salePrice?.toString() ?: "") }
    var stockQuantityStr by remember { mutableStateOf(product?.stockQuantity?.toString() ?: "") }
    val capturedImagePath = product?.imagePath

    var showBarcodeScannerDialog by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = if (product == null) "إضافة منتج جديد للمخزن" else "تعديل بيانات المنتج",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }

                // Mandatory fields
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("أدخل اسم المنتج *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = barcode,
                        onValueChange = { barcode = it },
                        label = { Text("رقم الباركود التعريفى *") },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = {
                                showBarcodeScannerDialog = true
                            }) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = "مسح باركود", tint = Color(0xFF00E5FF))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = purchasePriceStr,
                            onValueChange = { purchasePriceStr = it },
                            label = { Text("سعر الشراء *") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = salePriceStr,
                            onValueChange = { salePriceStr = it },
                            label = { Text("سعر البيع *") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Optional Field: Stock Quantity
                item {
                    OutlinedTextField(
                        value = stockQuantityStr,
                        onValueChange = { stockQuantityStr = it },
                        label = { Text("الكمية المتوفرة بالمخزن (اختياري)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Action controls at bottom
                item {
                    Spacer(modifier = Modifier.height(10.dp))
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
                                val purchaseVal = purchasePriceStr.toDoubleOrNull()
                                val saleVal = salePriceStr.toDoubleOrNull()
                                val stockVal = stockQuantityStr.toIntOrNull()

                                if (name.trim().isEmpty()) {
                                    Toast.makeText(context, "يرجى تعبئة اسم المنتج", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (barcode.trim().isEmpty()) {
                                    Toast.makeText(context, "يرجى مسح أو إدخال باركود", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (purchaseVal == null || purchaseVal < 0) {
                                    Toast.makeText(context, "يرجى إدخال سعر شراء صحيح", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (saleVal == null || saleVal < 0) {
                                    Toast.makeText(context, "يرجى إدخال سعر بيع صحيح", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                viewModel.saveProduct(
                                    id = product?.id ?: 0,
                                    name = name.trim(),
                                    barcode = barcode.trim(),
                                    purchasePrice = purchaseVal,
                                    salePrice = saleVal,
                                    stockQuantity = stockVal,
                                    imagePath = capturedImagePath,
                                    onSuccess = onSave
                                )
                            },
                            enabled = name.isNotEmpty() && barcode.isNotEmpty()
                        ) {
                            Text("حفظ")
                        }
                    }
                }
            }
        }
    }

    if (showBarcodeScannerDialog) {
        Dialog(onDismissRequest = { showBarcodeScannerDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "مسح باركود المنتج",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.5.dp, Color(0xFF00E5FF), RoundedCornerShape(12.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        CameraScannerView(
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize(),
                            onBarcodeDetected = { code, onComplete ->
                                barcode = code
                                viewModel.playBeep()
                                onComplete(true)
                                showBarcodeScannerDialog = false
                            }
                        )
                    }
                    
                    Button(
                        onClick = { showBarcodeScannerDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("إلغاء", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}
