<?php
// 开启错误报告，如果崩溃会输出原因
ini_set('display_errors', 1);
error_reporting(E_ALL);

header('Content-Type: application/json; charset=utf-8');

$servername = "localhost";
$db_user = "ichessge_dee";
$db_pass = "7uiMKrhz-N4nCV5"; 
$db_name = "ichessge_tch_app";

// 获取参数
$email = $_POST['email'] ?? '';
$password = $_POST['password'] ?? '';

if (empty($email) || empty($password)) {
    echo json_encode(["status" => "error", "message" => "鸿雁传书不可为空"]);
    exit;
}

// 建立连接
$conn = new mysqli($servername, $db_user, $db_pass, $db_name);
if ($conn->connect_error) {
    echo json_encode(["status" => "error", "message" => "云端连接失败"]);
    exit;
}

// 使用 bind_result 这种最基础的写法，完全避开 mysqlnd 驱动冲突
$stmt = $conn->prepare("SELECT nickname, password, invite_code FROM users WHERE email = ?");
$stmt->bind_param("s", $email);
$stmt->execute();
$stmt->store_result();

if ($stmt->num_rows > 0) {
    // 对应你数据库里的字段顺序：nickname, password, invite_code
    $stmt->bind_result($db_nickname, $db_password, $db_invite_code);
    $stmt->fetch();

    if (password_verify($password, $db_password)) {
        echo json_encode([
            "status" => "success",
            "account" => $db_nickname, // 注意：返回给 Android 的字段名需与 ApiResponse 对应
            "invite_code" => $db_invite_code,
            "message" => "归雁入林，欢迎回来"
        ]);
    } else {
        echo json_encode(["status" => "error", "message" => "密押（密码）校验不符"]);
    }
} else {
    echo json_encode(["status" => "error", "message" => "未能在画卷中找到此名帖"]);
}

$stmt->close();
$conn->close();
?>