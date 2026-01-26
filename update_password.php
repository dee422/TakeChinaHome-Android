<?php
header('Content-Type: application/json; charset=utf-8');

// 1. 数据库配置（记得修改成你刚改过的新密码！）
$servername = "localhost";
$db_user = "ichessge_dee";
$db_pass = "7uiMKrhz-N4nCV5"; 
$db_name = "ichessge_tch_app";

// 2. 获取参数 (对应 Android ApiService 中的 @Field 名)
$email = $_POST['email'] ?? '';
$oldPass = $_POST['old_password'] ?? ''; // 旧密码
$newPass = $_POST['new_password'] ?? ''; // 新密码

if (empty($email) || empty($oldPass) || empty($newPass)) {
    die(json_encode(["success" => false, "message" => "密信资料不全"]));
}

$conn = new mysqli($servername, $db_user, $db_pass, $db_name);
if ($conn->connect_error) {
    die(json_encode(["success" => false, "message" => "云端连接失败"]));
}

// 3. 第一步：校验旧密码是否正确
$stmt = $conn->prepare("SELECT password FROM users WHERE email = ?");
$stmt->bind_param("s", $email);
$stmt->execute();
$result = $stmt->get_result();

if ($user = $result->fetch_assoc()) {
    // 校验哈希
    if (password_verify($oldPass, $user['password'])) {
        
        // 4. 第二步：旧密码正确，加密新密码并更新
        $newHashedPass = password_hash($newPass, PASSWORD_DEFAULT);
        $updateStmt = $conn->prepare("UPDATE users SET password = ? WHERE email = ?");
        $updateStmt->bind_param("ss", $newHashedPass, $email);
        
        if ($updateStmt->execute()) {
            echo json_encode(["success" => true, "message" => "密押（密码）修订成功"]);
        } else {
            echo json_encode(["success" => false, "message" => "修订失败，请稍后再试"]);
        }
        $updateStmt->close();
        
    } else {
        echo json_encode(["success" => false, "message" => "旧密押校验错误"]);
    }
} else {
    echo json_encode(["success" => false, "message" => "查无此名帖"]);
}

$stmt->close();
$conn->close();
?>