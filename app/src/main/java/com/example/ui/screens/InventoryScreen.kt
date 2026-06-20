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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
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
        containerColor = Color.Transparent, // Transparent because we use custom gradient on Column
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    selectedProductForEdit = null
                    showAddEditDialog = true
                },
                containerColor = Color(0xFFFF9800),
                contentColor = Color.Black,
                shape = CircleShape,
                modifier = Modifier
                    .size(60.dp)
                    .shadow(
                        elevation = 16.dp,
                        shape = CircleShape,
                        clip = false,
                        spotColor = Color(0xFFFF9800),
                        ambientColor = Color(0xFFFF9800)
                    )
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة منتج", modifier = Modifier.size(28.dp))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF000000),
                            Color(0xFF070C10),
                            Color(0xFF020406)
                        )
                    )
                )
        ) {
            // REDESIGNED HEADER: Products and Stores card with integrated badge
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0xFF1E1E1E))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inventory,
                            contentDescription = null,
                            tint = Color(0xFFFF9800),
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "المنتجات والمخازن",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Modern Badge with custom glowing border
                    Box(
                        modifier = Modifier
                            .background(
                                color = Color(0xFFFF9800).copy(alpha = 0.08f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(1.dp, Color(0xFFFF9800).copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${products.size} منتج",
                            color = Color(0xFFFF9800),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
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
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .background(Color(0xFFFF9800).copy(alpha = 0.05f), CircleShape)
                                .border(1.dp, Color(0xFFFF9800).copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Inventory,
                                contentDescription = null,
                                tint = Color(0xFFFF9800),
                                modifier = Modifier.size(42.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "لا توجد منتجات بالمخزن",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "لا يوجد أي منتج مسجل في النظام حالياً. إضغط على الزر العائم (+) في الأسفل لإضافة أول منتج.",
                            color = Color(0xFF8A8A8A),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp)
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
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF1C1C1C))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Local Image preview
            val painter = rememberAsyncImagePainter(
                model = if (!product.imagePath.isNullOrEmpty()) File(product.imagePath) else null
            )

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF171717))
                    .border(1.dp, Color(0xFF1C1C1C), RoundedCornerShape(12.dp)),
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
                        tint = Color(0xFFCCFFFF).copy(alpha = 0.2f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "باركود: ${product.barcode}",
                    fontSize = 11.sp,
                    color = Color(0xFF8A8A8A)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format("شراء: %.2f د.ت", product.purchasePrice),
                        fontSize = 12.sp,
                        color = Color(0xFFF44336).copy(alpha = 0.8f)
                    )
                    Text(
                        text = String.format("بيع: %.2f د.ت", product.salePrice),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF9800)
                    )
                }
                
                // Optional stock indicator
                if (product.stockQuantity != null) {
                    val lowStock = product.stockQuantity <= 5
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .background(
                                color = if (lowStock) Color(0xFFF44336).copy(alpha = 0.15f) else Color(0xFF4CAF50).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (lowStock) Color(0xFFF44336).copy(alpha = 0.3f) else Color(0xFF4CAF50).copy(alpha = 0.3f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "المخزن: ${product.stockQuantity} قطع",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (lowStock) Color(0xFFF44336) else Color(0xFF4CAF50)
                        )
                    }
                }
            }

            // Edit & Delete icons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF171717), RoundedCornerShape(10.dp))
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "تعديل المنتج",
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF171717), RoundedCornerShape(10.dp))
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "حذف المنتج",
                        tint = Color(0xFFF44336).copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
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
                .padding(vertical = 12.dp)
                .border(BorderStroke(1.dp, Color(0xFF262626)), RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF171717))
        ) {
            // Correctly declare styling inside Composable card content scope
            val inputShape = RoundedCornerShape(20.dp)
            val inputColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFFF9800),
                unfocusedBorderColor = Color(0xFF262626),
                focusedLabelColor = Color(0xFFFF9800),
                unfocusedLabelColor = Color(0xFF8A8A8A),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color(0xFFFF9800),
                focusedContainerColor = Color(0xFF121212),
                unfocusedContainerColor = Color(0xFF121212)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = if (product == null) "إضافة منتج جديد" else "تعديل منتج",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }

                // Mandatory fields
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("اسم المنتج *", fontSize = 13.sp) },
                        singleLine = true,
                        shape = inputShape,
                        colors = inputColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = barcode,
                        onValueChange = { barcode = it },
                        label = { Text("رمز الباركود *", fontSize = 13.sp) },
                        singleLine = true,
                        shape = inputShape,
                        colors = inputColors,
                        trailingIcon = {
                            IconButton(
                                onClick = { showBarcodeScannerDialog = true },
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .size(36.dp)
                                    .background(Color(0xFF171717), CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.QrCodeScanner,
                                    contentDescription = "مسح باركود",
                                    tint = Color(0xFFFF9800),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = purchasePriceStr,
                            onValueChange = { purchasePriceStr = it },
                            label = { Text("سعر الشراء *", fontSize = 11.sp) },
                            singleLine = true,
                            shape = inputShape,
                            colors = inputColors,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = salePriceStr,
                            onValueChange = { salePriceStr = it },
                            label = { Text("سعر البيع *", fontSize = 11.sp) },
                            singleLine = true,
                            shape = inputShape,
                            colors = inputColors,
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
                        label = { Text("الكمية بالمخزن (اختياري)", fontSize = 13.sp) },
                        singleLine = true,
                        shape = inputShape,
                        colors = inputColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Action controls at bottom
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "إلغاء",
                                color = Color(0xFFC7C7C7),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        
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
                            enabled = name.isNotEmpty() && barcode.isNotEmpty(),
                            modifier = Modifier
                                .weight(1.5f)
                                .height(46.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF9800),
                                contentColor = Color.Black,
                                disabledContainerColor = Color(0xFFFF9800).copy(alpha = 0.3f),
                                disabledContentColor = Color.Black.copy(alpha = 0.5f)
                            )
                        ) {
                            Text(
                                "حفظ البيانات",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
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
                            .border(1.5.dp, Color(0xFFFF9800), RoundedCornerShape(12.dp))
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
