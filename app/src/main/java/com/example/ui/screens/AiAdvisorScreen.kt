package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.ChatMessage
import com.example.ui.viewmodel.PosViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAdvisorScreen(
    viewModel: PosViewModel,
    paddingValues: PaddingValues,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    val chatMessages by viewModel.aiChatMessages.collectAsState()
    val isLoading by viewModel.isAiLoading.collectAsState()

    var inputPromptText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    var showContextPreviewDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Scroll to bottom when a new message arrives
    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // AI ADVISOR SUB-HEADER
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "المستشار المالي الذكي (Gemini)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "مستشارك المحاسبي لتحليل الأرباح والديون فورياً",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Preview shared store stats
                    IconButton(
                        onClick = { showContextPreviewDialog = true },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.QueryStats,
                            contentDescription = "ملخص البيانات",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Reset / Broom chat history
                    IconButton(
                        onClick = {
                            viewModel.clearAiChat()
                            Toast.makeText(context, "تم مسح المحادثة وتصفير القناة", Toast.LENGTH_SHORT).show()
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "مسح المحادثة",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Key Config / Settings
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "إعدادات المفتاح",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // CHAT MESSAGE STREAM (LazyColumn)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            if (chatMessages.isEmpty()) {
                // Empty Welcome Screen with starter prompts
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "مرحباً بك في مستشارك المالي الذكي!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "يمكنك سؤالي فورياً عن مبيعات متجرك، الديون العالقة، توقع الأرباح الكامنة أو طلب نصائح لترشيد وتدبير الكريدي والديون.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "أسئلة مقترحة سريعة:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val suggestions = listOf(
                        "كيف ترى الوضع المالي الحالي لمتجري بناءً على أرقامي؟",
                        "ما هي المنتجات منخفضة المخزون حالياً ومقترحاتك لشرائها؟",
                        "أعطني استراتيجية ذكية لتقليص ديون (كريدي) الزبائن."
                    )

                    suggestions.forEach { prompt ->
                        OutlinedButton(
                            onClick = {
                                inputPromptText = prompt
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = prompt,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(chatMessages, key = { it.id }) { message ->
                        ChatBubbleItem(message)
                    }

                    if (isLoading) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "جاري تحليل الحسابات وتركيب الرد المالي الخاص بك...",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // BOTTOM TYPING BAR
        Surface(
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputPromptText,
                    onValueChange = { inputPromptText = it },
                    placeholder = { Text("أدخل سؤالك المالي هنا...", fontSize = 13.sp) },
                    singleLine = true,
                    maxLines = 1,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputPromptText.isNotBlank() && !isLoading) {
                                val text = inputPromptText
                                inputPromptText = ""
                                focusManager.clearFocus()
                                viewModel.sendPromptToAi(text, context)
                            }
                        }
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                IconButton(
                    onClick = {
                        if (inputPromptText.isNotBlank() && !isLoading) {
                            val text = inputPromptText
                            inputPromptText = ""
                            focusManager.clearFocus()
                            viewModel.sendPromptToAi(text, context)
                        }
                    },
                    enabled = inputPromptText.isNotBlank() && !isLoading,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.outlineVariant,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.size(46.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "إرسال",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    // --- DIALOG: DATABASE SUMMARY PREVIEW ---
    if (showContextPreviewDialog) {
        AlertDialog(
            onDismissRequest = { showContextPreviewDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Visibility,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("التقرير الحسابي المشترك", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "هذه هي الأرقام والبيانات الإجمالية والمالية لمتجرك الفعلي التي يفرزها التطبيق تلقائياً مع كل سؤال لتمكين خبير الذكاء الاصطناعي من الإجابة الدقيقة:",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item {
                                Text(
                                    text = viewModel.buildStoreDataSummary(),
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showContextPreviewDialog = false }) {
                    Text("حسناً، فهمت")
                }
            }
        )
    }

    // --- DIALOG: CUSTOM API KEY SETTINGS ---
    if (showSettingsDialog) {
        var apiKeyInput by remember { mutableStateOf(com.example.data.util.GeminiService.getSavedApiKey(context)) }
        var keyVisibility by remember { mutableStateOf(false) }
        var isVerifying by remember { mutableStateOf(false) }
        var verifyResultText by remember { mutableStateOf<String?>(null) }
        var verifyResultColor by remember { mutableStateOf(Color.Gray) }
        var selectedModel by remember { mutableStateOf(com.example.data.util.GeminiService.getSelectedModel(context)) }

        val primaryColor = MaterialTheme.colorScheme.primary
        val errorColor = MaterialTheme.colorScheme.error

        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "ضبط إعدادات ومستشار الذكاء الاصطناعي",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "لحفظ مفتاح خاص بك لخدمة Gemini، يرجى تزويد المفتاح أدناه. يتم حفظ هذا المفتاح محلياً وبشكل آمن على جهازك فقط.",
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = {
                            apiKeyInput = it
                            verifyResultText = null
                        },
                        label = { Text("مفتاح Gemini API Key", fontSize = 12.sp) },
                        singleLine = true,
                        visualTransformation = if (keyVisibility) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { keyVisibility = !keyVisibility }) {
                                Icon(
                                    imageVector = if (keyVisibility) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "إصدار نموذج الذكاء الاصطناعي (Model Version):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    var modelExpanded by remember { mutableStateOf(false) }
                    val modelsList = listOf(
                        "gemini-3.5-flash" to "3.5 Flash",
                        "gemini-3.1-flash-lite-preview" to "3.1 Flash-Lite"
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { modelExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = modelsList.firstOrNull { it.first == selectedModel }?.second ?: selectedModel,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                    imageVector = if (modelExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = modelExpanded,
                            onDismissRequest = { modelExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            modelsList.forEach { (modelId, modelLabel) ->
                                DropdownMenuItem(
                                    text = { Text(modelLabel, fontSize = 12.sp) },
                                    onClick = {
                                        selectedModel = modelId
                                        com.example.data.util.GeminiService.saveSelectedModel(context, modelId)
                                        modelExpanded = false
                                        Toast.makeText(context, "تم تحديد نموذج: $modelLabel", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }

                    if (verifyResultText != null) {
                        Text(
                            text = verifyResultText!!,
                            fontSize = 11.sp,
                            color = verifyResultColor,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = !isVerifying,
                            onClick = {
                                if (apiKeyInput.isBlank()) {
                                    verifyResultText = "⚠️ يرجى إدخال مفتاح الـ API أولاً"
                                    verifyResultColor = errorColor
                                    return@Button
                                }
                                isVerifying = true
                                verifyResultText = "جاري الاتصال والتحقق من المفتاح ومسار الخدمة..."
                                verifyResultColor = primaryColor
                                coroutineScope.launch {
                                    val err = com.example.data.util.GeminiService.verifyApiKeyDetailed(apiKeyInput, selectedModel)
                                    isVerifying = false
                                    if (err == null) {
                                        com.example.data.util.GeminiService.saveApiKey(context, apiKeyInput)
                                        verifyResultText = "🎉 تم التحقق بنجاح وتنشيط الخدمة!"
                                        verifyResultColor = Color(0xFF2E7D32)
                                        Toast.makeText(context, "تم حفظ وتفعيل مفتاح Gemini بنجاح!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        verifyResultText = "❌ فشل الاتصال: $err"
                                        verifyResultColor = errorColor
                                    }
                                }
                            }
                        ) {
                            if (isVerifying) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("تكامل وحفظ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Bypass Button: Skip & Save directly
                        Button(
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            onClick = {
                                val finalKey = if (apiKeyInput.isBlank()) "BYPASS" else apiKeyInput
                                com.example.data.util.GeminiService.saveApiKey(context, finalKey)
                                Toast.makeText(context, "تم تخطي التحقق وتضمين الحفظ الآمن والميزات بنجاح!", Toast.LENGTH_LONG).show()
                                showSettingsDialog = false
                            }
                        ) {
                            Text("تخطي وحفظ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                com.example.data.util.GeminiService.saveApiKey(context, "")
                                apiKeyInput = ""
                                verifyResultText = "🗑️ تم حذف المفتاح بنجاح."
                                verifyResultColor = errorColor
                                Toast.makeText(context, "تم مسح مفتاحك بنجاح من الذاكرة", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("مسح المفتاح", fontSize = 11.sp, color = errorColor)
                        }

                        TextButton(
                            modifier = Modifier.weight(1f),
                            onClick = { showSettingsDialog = false }
                        ) {
                            Text("إغلاق", fontSize = 11.sp)
                        }
                    }
                }
            },
            dismissButton = null
        )
    }
}

@Composable
fun ChatBubbleItem(message: ChatMessage) {
    val isUser = message.sender == "user"
    val bubbleColor = if (isUser) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        )
                    )
                    .background(bubbleColor)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message.text,
                    color = textColor,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (isUser) "أنت" else "المستشار الذكي",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}
