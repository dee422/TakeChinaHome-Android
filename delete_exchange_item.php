<?php
header('Content-Type: application/json; charset=utf-8');
require_once 'db_config.php';

$id = isset($_POST['id']) ? (int)$_POST['id'] : 0;
$email = isset($_POST['owner_email']) ? trim($_POST['owner_email']) : '';

if ($id <= 0 || empty($email)) {
    echo json_encode(["success" => false, "message" => "删除失败：凭证不全"]);
    exit;
}

// 彻底删除逻辑
$stmt = $conn->prepare("DELETE FROM swap_items WHERE id = ? AND owner_email = ?");
$stmt->bind_param("is", $id, $email);
$stmt->execute();

if ($stmt->affected_rows > 0) {
    echo json_encode(["success" => true, "message" => "物什已彻底从云端抹除"]);
} else {
    echo json_encode(["success" => false, "message" => "删除失败：记录不存在"]);
}
$stmt->close();
$conn->close();
?>