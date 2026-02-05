package com.dee.android.pbl.takechinahome

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerIntentListScreen(userEmail: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var orders by remember { mutableStateOf<List<Order>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // 弹窗控制
    var selectedOrder by remember { mutableStateOf<Order?>(null) }
    var showSheet by remember { mutableStateOf(false) }

    // 同步刷新逻辑
    val refreshOrders: () -> Unit = {
        scope.launch {
            isLoading = true
            try {
                val res = RetrofitClient.instance.getRealtimeOrders(userEmail)
                if (res.success) {
                    orders = res.data ?: emptyList()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "同步失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { refreshOrders() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的意向卷宗", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { refreshOrders() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (orders.isEmpty()) {
                Text("暂无意向卷宗", modifier = Modifier.align(Alignment.Center), color = Color.Gray)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
                ) {
                    items(orders) { order ->
                        CustomerOrderCard(order) {
                            selectedOrder = order
                            showSheet = true
                        }
                    }
                }
            }
        }

        if (showSheet && selectedOrder != null) {
            ModalBottomSheet(
                onDismissRequest = { showSheet = false }
            ) {
                CustomerIntentConfirmContent(
                    order = selectedOrder!!,
                    onDismiss = { showSheet = false },
                    onRefresh = { refreshOrders() }
                )
            }
        }
    }
}

@Composable
fun CustomerOrderCard(order: Order, onClick: () -> Unit) {
    val isLocked = order.intentConfirmStatus == 1

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("卷宗号: #${order.id}", fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(4.dp))
                val title = if (!order.targetGiftName.isNullOrBlank() && order.targetGiftName != "待定")
                    order.targetGiftName!!
                else order.details.firstOrNull()?.name ?: "未知礼品"
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)

                if (!order.aiSuggestion.isNullOrBlank() && !isLocked) {
                    Text(
                        "💡 AI提醒: ${order.aiSuggestion}",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }

            Surface(
                color = if (isLocked) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = if (isLocked) "已锁定" else "待完善",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 10.sp,
                    color = if (isLocked) Color(0xFF2E7D32) else Color(0xFFE65100)
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.LightGray)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerIntentConfirmContent(
    order: Order,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isLocked = order.intentConfirmStatus == 1

    // 修正：确保这里的 isIntent 和 finalImagePath 与你的 Order 数据类定义一致
    val isFormal = order.isIntent == 0 && !order.finalImagePath.isNullOrBlank()

    // --- 状态管理 ---
    var giftName by remember { mutableStateOf(order.targetGiftName ?: (order.details.firstOrNull()?.name ?: "待定")) }
    var qty by remember { mutableStateOf(if (order.targetQty == 0) (order.details.firstOrNull()?.qty?.toString() ?: "") else order.targetQty.toString()) }
    var date by remember { mutableStateOf(order.deliveryDate ?: "") }
    var contact by remember { mutableStateOf(order.contactMethod ?: "") }
    var selectedManagerName by remember { mutableStateOf(order.managerName ?: "") }
    var managerList by remember { mutableStateOf<List<Manager>>(emptyList()) }
    var expanded by remember { mutableStateOf(false) }
    var showAiConfirmDialog by remember { mutableStateOf(false) }

    // 获取经理列表
    LaunchedEffect(Unit) {
        try {
            val res = RetrofitClient.instance.getManagers()
            if (res.success) managerList = res.data ?: emptyList()
        } catch (e: Exception) {
            android.util.Log.e("API", "获取经理失败", e)
        }
    }

    // 提交逻辑封装
    val performSubmit = {
        scope.launch {
            val res = RetrofitClient.instance.confirmOrderIntent(
                orderId = order.id,
                giftName = giftName,
                qty = qty.toIntOrNull() ?: 0,
                date = date,
                contact = contact,
                managerName = selectedManagerName,
                status = 1
            )
            if (res.success) {
                Toast.makeText(context, "意向已锁定，等待经理转正", Toast.LENGTH_SHORT).show()
                onRefresh()
                onDismiss()
            }
        }
    }

    val isInfoIncomplete = {
        giftName.isBlank() || giftName == "待定" ||
                qty.isBlank() || qty == "0" ||
                date.isBlank() || contact.isBlank()
    }

    Column(modifier = Modifier
        .padding(horizontal = 24.dp)
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (isFormal) "正式卷宗凭证" else "确认意向详情",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        if (isFormal) {
            Text("您的意向已由经理转为正式卷宗，详情如下：", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                AsyncImage(
                    model = order.finalImagePath,
                    contentDescription = "正式卷宗图片",
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                    contentScale = ContentScale.FillWidth
                )
            }
        }

        if (!order.aiSuggestion.isNullOrBlank() && !isLocked) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(vertical = 16.dp).fillMaxWidth()
            ) {
                Text(
                    text = "💡 AI 建议：${order.aiSuggestion}",
                    modifier = Modifier.padding(12.dp),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (!isFormal) {
            Text("指派客户经理", style = MaterialTheme.typography.labelMedium, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { if (!isLocked) expanded = !expanded },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = if (selectedManagerName.isEmpty()) "请选择经理..." else selectedManagerName,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    enabled = !isLocked,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    managerList.forEach { manager ->
                        DropdownMenuItem(
                            text = { Text(manager.nickname) },
                            onClick = {
                                selectedManagerName = manager.nickname
                                expanded = false
                            }
                        )
                    }
                }
            }

            IntentTextField("礼品名称", giftName, isLocked) { giftName = it }
            IntentTextField("拟订数量", qty, isLocked) { qty = it }
            IntentTextField("期望交期", date, isLocked) { date = it }
            IntentTextField("联系方式", contact, isLocked) { contact = it }

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = {
                    if (selectedManagerName.isEmpty()) {
                        Toast.makeText(context, "请先选择一位客户经理", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (isInfoIncomplete()) {
                        showAiConfirmDialog = true
                    } else {
                        performSubmit()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLocked,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
            ) {
                Text(if (isLocked) "意向已同步经理" else "确认无误并锁定意向")
            }

            if (!isLocked) {
                TextButton(
                    onClick = {
                        scope.launch {
                            val res = RetrofitClient.instance.deleteOrder(order.id)
                            if (res.success) {
                                Toast.makeText(context, "订单已销毁", Toast.LENGTH_SHORT).show()
                                onRefresh()
                                onDismiss()
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
                ) {
                    Text("放弃此卷宗并销毁", color = Color.Red)
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }

    // --- AI 智能确认对话框：修正了 image_c174c9.jpg 中的语法错误 ---
    if (showAiConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showAiConfirmDialog = false },
            title = { Text("岁时提醒") },
            text = { Text("尊驾信息尚未补全。若坚持锁定，后续将由经理「$selectedManagerName」为您手工补全并联系。是否确认？") },
            confirmButton = {
                TextButton(onClick = {
                    showAiConfirmDialog = false
                    performSubmit()
                }) { Text("确认锁定") }
            },
            dismissButton = {
                TextButton(onClick = { showAiConfirmDialog = false }) { Text("再去填填") }
            }
        ) // 确保这里闭合，不要有重复的 title 或 text 块
    }
}

// ✨ 确保你的文件末尾有这个组件定义，否则会报 Unresolved reference
@Composable
fun IntentTextField(label: String, value: String, isLocked: Boolean, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        enabled = !isLocked,
        singleLine = true
    )
}