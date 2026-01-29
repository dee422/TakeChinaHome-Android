<?php
header('Content-Type: application/json; charset=utf-8');
require_once '../db_config.php'; 

// 1. 接收基础数据
$id             = isset($_POST['id']) ? (int)$_POST['id'] : 0;
$owner_email    = $_POST['owner_email'] ?? '';
$item_name      = $_POST['item_name'] ?? '';
$description    = $_POST['description'] ?? '';
$contact_code   = $_POST['contact_code'] ?? ''; // 新增：联系暗号
$exchange_wish  = $_POST['exchange_wish'] ?? ''; // 新增：置换意向
$base64Image    = $_POST['image_data'] ?? '';   // 接收 Base64 字符串

// 2. 关键信息校验
if (empty($item_name) || empty($owner_email)) {
    echo json_encode(["success" => false, "message" => "关键信息缺失：品名或账号为空"]);
    exit;
}

// 3. 处理图片物理上传
$final_image_url = ""; 
if (!empty($base64Image) && strlen($base64Image) > 100) {
    try {
        // 去掉 Base64 头部（如果有的话，例如 data:image/jpeg;base64,）
        if (preg_match('/^(data:\s*image\/(\w+);base64,)/', $base64Image, $res)) {
            $base64Image = substr($base64Image, strpos($base64Image, ',') + 1);
        }
        
        $decodedData = base64_decode($base64Image);
        if ($decodedData !== false) {
            $fileName = uniqid('img_', true) . '.jpg';
            $uploadPath = '../uploads/' . $fileName; // 物理路径
            
            if (file_put_contents($uploadPath, $decodedData)) {
                // 生成公网访问 URL
                $final_image_url = "https://www.ichessgeek.com/takechinahome/uploads/" . $fileName;
            }
        }
    } catch (Exception $e) {
        // 图片处理失败暂不中断流程，仅记录
    }
}

// 4. 执行数据库操作 (使用参数化查询防止注入)
if ($id > 0) {
    // 【更新逻辑】
    // 使用 ON DUPLICATE KEY UPDATE 处理同步覆盖
    $sql = "INSERT INTO swap_items (id, owner_email, item_name, description, contact_code, exchange_wish, image_url, status) 
            VALUES (?, ?, ?, ?, ?, ?, ?, 1) 
            ON DUPLICATE KEY UPDATE 
            item_name=VALUES(item_name), 
            description=VALUES(description), 
            contact_code=VALUES(contact_code), 
            exchange_wish=VALUES(exchange_wish),
            status=1";
    
    // 如果没有传新图片，保留原图地址（这里可以根据业务调整）
    if (empty($final_image_url)) {
        $sql = str_replace("image_url=VALUES(image_url),", "", $sql); 
    } else {
        $sql = str_replace("status=1", "image_url=VALUES(image_url), status=1", $sql);
    }

    $stmt = $conn->prepare($sql);
    $stmt->bind_param("issssss", $id, $owner_email, $item_name, $description, $contact_code, $exchange_wish, $final_image_url);
} else {
    // 【纯插入逻辑】
    $sql = "INSERT INTO swap_items (owner_email, item_name, description, contact_code, exchange_wish, image_url, status) VALUES (?, ?, ?, ?, ?, ?, 1)";
    $stmt = $conn->prepare($sql);
    $stmt->bind_param("sssssss", $owner_email, $item_name, $description, $contact_code, $exchange_wish, $final_image_url);
}

// 5. 结果返回
if ($stmt && $stmt->execute()) {
    echo json_encode([
        "success" => true, 
        "message" => "同步成功", 
        "new_id" => ($id > 0 ? $id : $conn->insert_id),
        "debug_url" => $final_image_url // 返回给 Android 确认
    ]);
} else {
    $err = $stmt ? $stmt->error : $conn->error;
    echo json_encode(["success" => false, "message" => "SQL错误：" . $err]);
}

$stmt->close();
$conn->close();
?>