<?php
header('Content-Type: application/json; charset=utf-8');

// 1. 数据库配置
$host = "localhost";
$username = "ichessge_dee";
$password = "7uiMKrhz-N4nCV5";
$dbname = "ichessge_tch_app";

// 2. 建立连接
$conn = new mysqli($host, $username, $password, $dbname);

// 检查连接
if ($conn->connect_error) {
    echo json_encode(["success" => false, "message" => "数据库连接失败"]);
    exit;
}

// 3. 获取 POST 参数并强制转为整数 (Int)
// 注意：$_POST['id'] 的引号必须加上
$giftId = isset($_POST['id']) ? intval($_POST['id']) : 0;

if ($giftId <= 0) {
    echo json_encode(["success" => false, "message" => "无效的礼品ID"]);
    exit;
}

// 4. 执行删除
// 由于是 Int 类型，SQL 语句中 $giftId 不需要单引号
$sql = "DELETE FROM gifts WHERE id = $giftId";

if ($conn->query($sql) === TRUE) {
    // 检查是否有行被删除
    if ($conn->affected_rows > 0) {
        echo json_encode(["success" => true, "message" => "画轴项目已成功裁撤"]);
    } else {
        echo json_encode(["success" => false, "message" => "未找到该礼品，或已被删除"]);
    }
} else {
    echo json_encode(["success" => false, "message" => "执行错误: " . $conn->error]);
}

$conn->close();
?>