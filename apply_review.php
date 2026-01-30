<?php
// apply_review.php 开头
$base64Image = $_POST['image_data'] ?? '';
if (empty($base64Image)) {
    // 如果这里触发了，说明 Android 发过来的数据被服务器防火墙或 PHP 配置拦截了
    echo json_encode(["success" => false, "message" => "服务器未接收到图片数据"]);
    exit;
}
// 屏蔽所有可能的错误干扰，确保只输出 JSON
error_reporting(0); 
header('Content-Type: application/json; charset=utf-8');

// 捕获严重错误并转为 JSON 格式
register_shutdown_function(function() {
    $error = error_get_last();
    if ($error && ($error['type'] === E_ERROR || $error['type'] === E_PARSE)) {
        echo json_encode(["success" => false, "message" => "PHP严重错误: " . $error['message']]);
    }
});

require_once '../db_config.php'; 

$response = ["success" => false, "message" => "未知错误"];

try {
    $id             = isset($_POST['id']) ? (int)$_POST['id'] : 0;
    $owner_email    = $_POST['owner_email'] ?? '';
    $item_name      = $_POST['item_name'] ?? '';
    $description    = $_POST['description'] ?? '';
    $contact_code   = $_POST['contact_code'] ?? '';
    $exchange_wish  = $_POST['exchange_wish'] ?? '';
    $base64Image    = $_POST['image_data'] ?? '';

    if (empty($owner_email) || empty($item_name)) {
        throw new Exception("必填项缺失");
    }

    $final_image_url = ""; 
    if (!empty($base64Image)) {
        // 1. 清理 Base64 头部
        if (strpos($base64Image, ',') !== false) {
            $base64Image = explode(',', $base64Image)[1];
        }
        
        $decodedData = base64_decode($base64Image);
        
        if ($decodedData) {
        $fileName = 'img_' . time() . '.jpg';
        
        // 方案 A：使用服务器绝对路径（最稳妥）
        // 请根据你服务器的实际全路径修改，例如：
        $uploadPath = '/home/username/public_html/takechinahome/uploads/' . $fileName;

        // 方案 B：如果 takechinahome 和 api 目录在同级，可以使用以下逻辑：
        // 假设脚本在 /api/v1/apply_review.php
        // dirname(__DIR__, 2) 会回退两级到根目录，然后进入 takechinahome
        $uploadDirectory = dirname(__DIR__, 2) . '/../takechinahome/uploads/';
        $uploadPath = $uploadDirectory . $fileName;
        
        // 确保目录存在
        if (!is_dir($uploadDirectory)) {
            mkdir($uploadDirectory, 0777, true);
        }

        if (file_put_contents($uploadPath, $decodedData)) {
            // 返回正确的公网 URL
            $final_image_url = "https://www.ichessgeek.com/takechinahome/uploads/" . $fileName;
        } else {
                // 如果写入失败，通过 message 返回给 Android 观察
                throw new Exception("文件写入磁盘失败，路径: " . $uploadPath);
            }
        } else {
            throw new Exception("Base64 解码失败");
        }
    }

    // 使用 INSERT ... ON DUPLICATE KEY UPDATE 以获得更好的兼容性
    $sql = "INSERT INTO swap_items (id, owner_email, item_name, description, contact_code, exchange_wish, image_url, status) 
            VALUES (?, ?, ?, ?, ?, ?, ?, 1) 
            ON DUPLICATE KEY UPDATE 
            item_name=VALUES(item_name), 
            description=VALUES(description), 
            contact_code=VALUES(contact_code), 
            exchange_wish=VALUES(exchange_wish),
            image_url=IF(VALUES(image_url)='', image_url, VALUES(image_url)),
            status=1";
    
    $stmt = $conn->prepare($sql);
    if (!$stmt) {
        throw new Exception("SQL预处理失败: " . $conn->error);
    }

    // 这里必须确保有 7 个变量对应 7 个问号
    // id(i), owner_email(s), item_name(s), description(s), contact_code(s), exchange_wish(s), image_url(s)
    $stmt->bind_param("issssss", $id, $owner_email, $item_name, $description, $contact_code, $exchange_wish, $final_image_url);

    if ($stmt->execute()) {
        $response = ["success" => true, "message" => "同步成功", "id" => $id > 0 ? $id : $conn->insert_id];
    } else {
        throw new Exception("SQL执行失败: " . $stmt->error);
    }

} catch (Exception $e) {
    $response = ["success" => false, "message" => $e->getMessage()];
}

echo json_encode([
    "success" => true, 
    "message" => "同步成功", 
    "image_saved" => ($final_image_url != ""), // 看看这个是不是 false
    "url" => $final_image_url
]);
$conn->close();
?>