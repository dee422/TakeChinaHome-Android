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

    // ✨ 新增：Tab 状态控制
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("意向洽谈", "正式卷宗")

    // 弹窗控制
    var selectedOrder by remember { mutableStateOf<Order?>(null) }
    var showSheet by remember { mutableStateOf(false) }

    // 同步刷新逻辑：对接 PHP 新增的 type 参数
    val refreshOrders: () -> Unit = {
        scope.launch {
            isLoading = true
            try {
                // 根据 Tab 传递过滤类型：intent 或 formal
                val type = if (selectedTabIndex == 0) "intent" else "formal"
                val res = RetrofitClient.instance.getRealtimeOrders(userEmail, type)
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

    // 当 Tab 切换时自动刷新
    LaunchedEffect(selectedTabIndex) { refreshOrders() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("卷宗中心", fontWeight = FontWeight.Bold) },
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
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            // ✨ 新增：选项卡切换栏
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title, fontSize = 14.sp, fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (orders.isEmpty()) {
                    Text(
                        text = if (selectedTabIndex == 0) "暂无意向卷宗" else "暂无正式卷宗",
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.Gray
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
                    ) {
                        items(orders) { order ->
                            CustomerOrderCard(order, isFormalTab = selectedTabIndex == 1) {
                                selectedOrder = order
                                showSheet = true
                            }
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
fun CustomerOrderCard(order: Order, isFormalTab: Boolean, onClick: () -> Unit) {
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

                // ✨ 逻辑增强：如果已转正，显示经理进度；如果未转正，显示 AI 提醒
                if (isFormalTab) {
                    Text(
                        "✅ 经理「${order.managerName ?: "雅鉴经理"}」已接办",
                        color = Color(0xFF2E7D32),
                        fontSize = 12.sp
                    )
                } else if (!order.aiSuggestion.isNullOrBlank() && !isLocked) {
                    Text(
                        "💡 AI建议: ${order.aiSuggestion}",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }

            // 状态标签渲染
            val isCompleted = order.status == "Completed"
            Surface(
                color = when {
                    isCompleted -> Color(0xFFEEEEEE)
                    isFormalTab -> Color(0xFFE3F2FD) // 正式：淡蓝
                    isLocked -> Color(0xFFE8F5E9)   // 意向已锁定：淡绿
                    else -> Color(0xFFFFF3E0)        // 待完善：淡橙
                },
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = when {
                        isCompleted -> "已交付"
                        isFormalTab -> "正式"
                        isLocked -> "已锁定"
                        else -> "待完善"
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 10.sp,
                    color = when {
                        isFormalTab -> Color(0xFF1976D2)
                        isLocked -> Color(0xFF2E7D32)
                        else -> Color(0xFFE65100)
                    }
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
    // ✨ 转正判定：isIntent 为 0
    val isFormal = order.isIntent == 0

    // --- 状态管理 (保持原有变量名) ---
    var giftName by remember { mutableStateOf(order.targetGiftName ?: (order.details.firstOrNull()?.name ?: "待定")) }
    var qty by remember { mutableStateOf(if (order.targetQty == 0) (order.details.firstOrNull()?.qty?.toString() ?: "") else order.targetQty.toString()) }
    var date by remember { mutableStateOf(order.deliveryDate ?: "") }
    var contact by remember { mutableStateOf(order.contactMethod ?: "") }

    var selectedManagerName by remember { mutableStateOf(order.managerName ?: "") }
    var selectedManagerId by remember { mutableIntStateOf(0) }

    var managerList by remember { mutableStateOf<List<Manager>>(emptyList()) }
    var expanded by remember { mutableStateOf(false) }
    var showAiConfirmDialog by remember { mutableStateOf(false) }

    // ✨ 新增：用于实时显示拉取到的 AI 建议
    var currentAiSuggestion by remember { mutableStateOf(order.aiSuggestion ?: "") }

    // 原有的经理列表获取逻辑
    LaunchedEffect(Unit) {
        try {
            val res = RetrofitClient.instance.getManagers()
            if (res.success) {
                val list = res.data ?: emptyList()
                managerList = list
                if (selectedManagerName.isNotEmpty()) {
                    val match = list.find { it.nickname == selectedManagerName }
                    if (match != null) selectedManagerId = match.id
                }
            }
        } catch (e: Exception) { /* log */ }
    }

    // ✨ 新增：AI 建议自动获取逻辑
    // 如果数据库中没有建议且订单未锁定，则自动触发
    LaunchedEffect(order.id) {
        if (currentAiSuggestion.isBlank() && !isLocked && !isFormal) {
            try {
                val res = RetrofitClient.instance.getAiSuggestion(order.id)
                if (res.success && res.data != null) {
                    currentAiSuggestion = res.data
                }
            } catch (e: Exception) { /* 静默失败 */ }
        }
    }

    val performSubmit = {
        scope.launch {
            try {
                val res = RetrofitClient.instance.confirmOrderIntent(
                    orderId = order.id,
                    giftName = giftName,
                    qty = qty.toIntOrNull() ?: 0,
                    date = date,
                    contact = contact,
                    managerId = selectedManagerId,
                    managerName = selectedManagerName,
                    status = 1
                )
                if (res.success) {
                    Toast.makeText(context, "意向已锁定，等待经理转正", Toast.LENGTH_SHORT).show()
                    onRefresh()
                    onDismiss()
                } else {
                    Toast.makeText(context, "失败: ${res.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "网络错误", Toast.LENGTH_SHORT).show()
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
            text = if (isFormal) "正式卷宗详情" else "确认意向详情",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        // 正式卷宗渲染逻辑：显示成果图
        if (isFormal && !order.finalImagePath.isNullOrBlank()) {
            Text("您的卷宗已完成研制，成品图如下：", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                AsyncImage(
                    model = order.finalImagePath,
                    contentDescription = "成果图",
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                    contentScale = ContentScale.FillWidth
                )
            }
        }

        // ✨ 逻辑修正：优先展示 currentAiSuggestion
        if (currentAiSuggestion.isNotBlank() && !isLocked && !isFormal) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(vertical = 16.dp).fillMaxWidth()
            ) {
                Text(
                    text = "💡 AI 建议：$currentAiSuggestion",
                    modifier = Modifier.padding(12.dp),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // 下面进入表单区域，如果是正式订单，表单将变为只读/锁定状态
        Text("指派客户经理", style = MaterialTheme.typography.labelMedium, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { if (!isLocked && !isFormal) expanded = !expanded },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            OutlinedTextField(
                value = if (selectedManagerName.isEmpty()) "请选择经理..." else selectedManagerName,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { if(!isLocked && !isFormal) ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                enabled = !isLocked && !isFormal,
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
                            selectedManagerId = manager.id
                            expanded = false
                        }
                    )
                }
            }
        }

        IntentTextField("礼品名称", giftName, isLocked || isFormal) { giftName = it }
        IntentTextField("拟订数量", qty, isLocked || isFormal) { qty = it }
        IntentTextField("期望交期", date, isLocked || isFormal) { date = it }
        IntentTextField("联系方式", contact, isLocked || isFormal) { contact = it }

        Spacer(Modifier.height(32.dp))

        if (!isFormal) {
            Button(
                onClick = {
                    if (selectedManagerName.isEmpty()) {
                        Toast.makeText(context, "请先选择一位经理", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (isInfoIncomplete()) showAiConfirmDialog = true else performSubmit()
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
                                onRefresh(); onDismiss()
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("放弃此卷宗并销毁", color = Color.Red)
                }
            }
        } else {
            // 正式订单显示状态按钮
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
            ) {
                Text("卷宗研制中")
            }
        }
        Spacer(Modifier.height(40.dp))
    }

    if (showAiConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showAiConfirmDialog = false },
            title = { Text("岁时提醒") },
            text = { Text("尊驾信息尚未补全。若坚持锁定，后续将由经理「$selectedManagerName」为您手工补全。是否确认？") },
            confirmButton = {
                TextButton(onClick = { showAiConfirmDialog = false; performSubmit() }) { Text("确认锁定") }
            },
            dismissButton = {
                TextButton(onClick = { showAiConfirmDialog = false }) { Text("再去填填") }
            }
        )
    }
}

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