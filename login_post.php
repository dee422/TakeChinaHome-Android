<?php
header('Content-Type: application/json');

// 获取 POST 参数
$user = $_POST['username'] ?? '';
$pass = $_POST['password'] ?? '';

// 纯商业逻辑：模拟准入制审核
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
    http_response_code(401);
    echo json_encode([
        "status" => "error",
        "message" => "准入审核未通过，请联系管理员"
    ]);
}
?>