<?php
header('Content-Type: application/json; charset=utf-8');
require_once dirname(__DIR__) . '/db_config.php';

$email = $_POST['user_email'] ?? '';
$contact = $_POST['contact_name'] ?? '';
$details = $_POST['order_details_json'] ?? '';

if (empty($email) || empty($details)) {
    echo json_encode(["success" => false, "message" => "订单数据残缺"]);
    exit;
}

// 建议在数据库新建一张 orders 表
// 字段：id (int ai), user_email (varchar), contact_name (varchar), details (text), created_at (timestamp)
$sql = "INSERT INTO orders (user_email, contact_name, details) VALUES (?, ?, ?)";
$stmt = $conn->prepare($sql);
$stmt->bind_param("sss", $email, $contact, $details);

if ($stmt->execute()) {
    echo json_encode(["success" => true, "message" => "订单录入成功"]);
} else {
    echo json_encode(["success" => false, "message" => "数据库录入失败"]);
}
?>