<?php
// 允许跨域（重要：防止 App 访问被拦截）
header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");

// --- 数据库配置信息 ---
$host = 'localhost';
$db   = 'ichessge_tch_app'; 
$user = 'ichessge_dee'; // 替换为你创建的数据库用户名
$pass = '7uiMKrhz-N4nCV5';       // 替换为你设置的数据库密码
$charset = 'utf8mb4';

$dsn = "mysql:host=$host;dbname=$db;charset=$charset";
$options = [
    PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
    PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
    PDO::ATTR_EMULATE_PREPARES   => false,
];

try {
     $pdo = new PDO($dsn, $user, $pass, $options);
} catch (\PDOException $e) {
     die(json_encode(['status' => 'error', 'message' => '数据库连接失败']));
}

// 获取 POST 参数
$email = $_POST['email'] ?? '';
$password = $_POST['password'] ?? ''; 
$nickname = $_POST['nickname'] ?? '';
$from_code = $_POST['from_invite_code'] ?? '';

if (!$email || !$password) {
    die(json_encode(['status' => 'error', 'message' => '邮箱或密码不能为空']));
}

// 1. 检查邮箱是否已存在
$checkStmt = $pdo->prepare("SELECT id FROM users WHERE email = ?");
$checkStmt->execute([$email]);
if ($checkStmt->fetch()) {
    die(json_encode(['status' => 'error', 'message' => '该邮箱已被注册']));
}

// 2. 生成新用户自己的随机邀请码 (8位大写字母+数字)
$my_invite_code = strtoupper(substr(md5($email . time()), 0, 8));

// 3. 存入新用户
$sql = "INSERT INTO users (email, password, nickname, invite_code, from_invite_code) VALUES (?, ?, ?, ?, ?)";
$stmt = $pdo->prepare($sql);

try {
    // 密码加密存储，更安全
    $hashedPassword = password_hash($password, PASSWORD_DEFAULT);
    $stmt->execute([$email, $hashedPassword, $nickname, $my_invite_code, $from_code]);
    
    // 4. 如果有引荐码，给引荐人的计数 +1
    if (!empty($from_code)) {
        $updateSql = "UPDATE users SET referral_count = referral_count + 1 WHERE invite_code = ?";
        $upStmt = $pdo->prepare($updateSql);
        $upStmt->execute([$from_code]);
    }

    echo json_encode([
        'status' => 'success', 
        'invite_code' => $my_invite_code,
        'message' => '登记成功，欢迎加入岁时礼序'
    ]);
} catch (Exception $e) {
    echo json_encode(['status' => 'error', 'message' => '系统登记失败: ' . $e->getMessage()]);
}