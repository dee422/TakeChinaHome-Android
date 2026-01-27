<?php
// 数据库配置信息
$servername = "localhost"; // 通常是 localhost
$username = "ichessge_dee"; 
$password = "7uiMKrhz-N4nCV5";
$dbname = "ichessge_tch_app";

// 创建连接
$conn = new mysqli($servername, $username, $password, $dbname);

// 检查连接是否成功
if ($conn->connect_error) {
    die(json_encode(["success" => false, "message" => "数据库连接失败: " . $conn->connect_error]));
}

// 设置字符集，防止中文乱码
$conn->set_charset("utf8mb4");
?>