<?php
header('Content-Type: application/json; charset=utf-8');
// 引用你 takechinahome 根目录下的数据库配置（路径根据实际层级调整）
require_once '../../../db_config.php'; 

// 只查询 status = 2 (管理员已审核通过) 的藏品
$sql = "SELECT * FROM exchange_items WHERE status = 2 ORDER BY created_at DESC";
$result = $conn->query($sql);

$items = [];
if ($result->num_rows > 0) {
    while($row = $result->fetch_assoc()) {
        // 关键点：拼接完整图片 URL。假设你的域名是 yourdomain.com
        // 这样 App 拿到的 imageUrl 直接就是能显示的链接
        $row['imageUrl'] = "https://yourdomain.com/takechinahome/uploads/" . $row['imageUrl'];
        $items[] = $row;
    }
}

// 返回 JSON 数组，App 的 getMarketGifts() 会自动解析
echo json_encode($items);
?>