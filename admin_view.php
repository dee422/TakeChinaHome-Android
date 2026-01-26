<?php
// 1. 访问控制：必须通过 https://www.ichessgeek.com/api/v1/admin_view.php?pw=123456 访问
if ($_GET['pw'] !== '123456') { 
    die("未授权访问");
}

// --- 数据库配置 (根据截图 image_f27e79.png 核对) ---
$db_name = 'ichessge_tch_app';  // 数据库名正确
$db_user = 'ichessge_dee';      // 用户名已修正为截图中的 ichessge_dee
$db_pass = '7uiMKrhz-N4nCV5';   // 确保这是你在 cPanel 为 dee 用户设置的密码

try {
    $pdo = new PDO("mysql:host=localhost;dbname=$db_name;charset=utf8mb4", $db_user, $db_pass);
    $pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION); // 建议开启错误提示
    $stmt = $pdo->query("SELECT email, nickname, invite_code, from_invite_code, referral_count, created_at FROM users ORDER BY created_at DESC");
    $users = $stmt->fetchAll(PDO::FETCH_ASSOC);
} catch (PDOException $e) {
    die("连接失败: " . $e->getMessage());
}
?>