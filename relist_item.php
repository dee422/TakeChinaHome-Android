<?php
header('Content-Type: application/json; charset=utf-8');
require_once 'db_config.php';

$id = isset($_POST['id']) ? (int)$_POST['id'] : 0;
$email = isset($_POST['owner_email']) ? trim($_POST['owner_email']) : '';

if ($id <= 0 || empty($email)) {
    echo json_encode(["success" => false, "message" => "操作失败：凭证不全"]);
    exit;
}

// 执行重新上架：将状态由 3 改为 2
// 增加 status = 3 的条件是为了确保只有下架的物品可以重新上架
$stmt = $conn->prepare("UPDATE swap_items SET status = 2 WHERE id = ? AND owner_email = ? AND status = 3");
$stmt->bind_param("is", $id, $email);

if ($stmt->execute() && $stmt->affected_rows > 0) {
    echo json_encode(["success" => true, "message" => "物什已重新上架"]);
} else {
    echo json_encode(["success" => false, "message" => "上架失败：记录不存在或状态异常"]);
}

$stmt->close();
$conn->close();
?>