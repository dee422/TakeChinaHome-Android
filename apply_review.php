<?php
header('Content-Type: application/json; charset=utf-8');
require_once 'db_config.php'; // 确保你的数据库配置正确

// 接收来自 Retrofit 的 @Field 参数
$id        = $_POST['id'] ?? '';
$ownerName = $_POST['owner'] ?? ''; // 注意这里对应你的 Kotlin 字段名
$title     = $_POST['title'] ?? '';
$story     = $_POST['story'] ?? '';
$want      = $_POST['want'] ?? '';
$contact   = $_POST['contact'] ?? '';
$imageUrl  = $_POST['image_data'] ?? ''; // 如果传的是 URL

if (empty($id) || empty($title)) {
    echo json_encode(["success" => false, "message" => "关键信息缺失"]);
    exit;
}

// 插入或更新，状态强制设为 1 (审核中)
$sql = "INSERT INTO exchange_items (id, ownerName, title, story, want, contact, imageUrl, status) 
        VALUES (?, ?, ?, ?, ?, ?, ?, 1) 
        ON DUPLICATE KEY UPDATE status=1, title=?, story=?, want=?, contact=?";

$stmt = $conn->prepare($sql);
// 绑定参数：8个插入参数 + 4个更新参数
$stmt->bind_param("ssssssssssss", 
    $id, $ownerName, $title, $story, $want, $contact, $imageUrl,
    $title, $story, $want, $contact
);

if ($stmt->execute()) {
    echo json_encode(["success" => true, "message" => "同步成功，进入审核环节"]);
} else {
    echo json_encode(["success" => false, "message" => "同步失败：" . $conn->error]);
}
?>