<?php
header('Content-Type: application/json');

// 修改这里：使用 $_REQUEST 既能收 POST 也能收 GET
$user = $_REQUEST['username'] ?? '';
$pass = $_REQUEST['password'] ?? '';

if ($user === 'admin' && $pass === 'china123') {
    echo json_encode([
        "status" => "success",
        "data" => [
            "token" => "tk_" . bin2hex(random_bytes(16)),
            "role" => "owner",
            "welcome_msg" => "欢迎回来，Take china home 的掌门人"
        ]
    ]);
} else {
    // 如果没有参数，就返回这个提示
    echo json_encode([
        "status" => "error",
        "message" => "准入审核未通过，请输入正确的凭证"
    ]);
}
?>