package cn.ymjacky.database;

import cn.ymjacky.SPToolsPlugin;

import java.sql.*;
import java.util.Properties;

public class MySQLManager {

    private final SPToolsPlugin plugin;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    
    private Connection connection;

    public MySQLManager(SPToolsPlugin plugin) {
        this.plugin = plugin;
        this.host = plugin.getConfig().getString("mysql.host", "localhost");
        this.port = plugin.getConfig().getInt("mysql.port", 3306);
        this.database = plugin.getConfig().getString("mysql.database", "data-server");
        this.username = plugin.getConfig().getString("mysql.username", "data-server");
        this.password = plugin.getConfig().getString("mysql.password", "");
        
        initialize();
    }

    private void initialize() {
        try {
            // 加载MySQL驱动
            Class.forName("com.mysql.cj.jdbc.Driver");
            
            // 配置连接属性
            Properties props = new Properties();
            props.setProperty("user", username);
            props.setProperty("password", password);
            props.setProperty("useSSL", "false");
            props.setProperty("allowPublicKeyRetrieval", "true");
            props.setProperty("serverTimezone", "Asia/Shanghai");
            props.setProperty("characterEncoding", "utf8");
            props.setProperty("autoReconnect", "true");
            props.setProperty("failOverReadOnly", "false");
            props.setProperty("maxReconnects", "10");
            props.setProperty("connectTimeout", "10000"); // 10秒连接超时
            
            // 建立连接
            String url = String.format("jdbc:mysql://%s:%d/%s", host, port, database);
            connection = DriverManager.getConnection(url, props);
            
            plugin.getLogger().info("成功连接到MySQL数据库: " + host + ":" + port + "/" + database);
            
            // 初始化数据库表
            initializeTables();
            
        } catch (ClassNotFoundException e) {
            plugin.getLogger().severe("MySQL驱动未找到: " + e.getMessage());
            e.printStackTrace();
        } catch (SQLException e) {
            plugin.getLogger().severe("连接MySQL数据库失败: " + e.getMessage());
            plugin.getLogger().severe("请检查MySQL配置: host=" + host + ", port=" + port + ", database=" + database);
            e.printStackTrace();
            connection = null; // 确保连接为null
        }
    }

    private void initializeTables() {
        try {
            // 创建玩家统计表
            String createPlayerStatsTable = """
                CREATE TABLE IF NOT EXISTS player_stats (
                    uuid VARCHAR(36) PRIMARY KEY,
                    player_name VARCHAR(100) NOT NULL,
                    blocks_mined BIGINT DEFAULT 0,
                    blocks_placed BIGINT DEFAULT 0,
                    online_time_seconds BIGINT DEFAULT 0,
                    total_money_earned BIGINT DEFAULT 0,
                    total_money_spent BIGINT DEFAULT 0,
                    last_join_time BIGINT,
                    last_update_time BIGINT,
                    INDEX idx_player_name (player_name),
                    INDEX idx_last_join_time (last_join_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;
            
            // 创建方块挖掘表
            String createBlocksMinedTable = """
                CREATE TABLE IF NOT EXISTS blocks_mined (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    player_uuid VARCHAR(36) NOT NULL,
                    block_type VARCHAR(100) NOT NULL,
                    count BIGINT NOT NULL,
                    INDEX idx_player_uuid (player_uuid),
                    INDEX idx_block_type (block_type),
                    FOREIGN KEY (player_uuid) REFERENCES player_stats(uuid) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;
            
            // 创建方块放置表
            String createBlocksPlacedTable = """
                CREATE TABLE IF NOT EXISTS blocks_placed (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    player_uuid VARCHAR(36) NOT NULL,
                    block_type VARCHAR(100) NOT NULL,
                    count BIGINT NOT NULL,
                    INDEX idx_player_uuid (player_uuid),
                    INDEX idx_block_type (block_type),
                    FOREIGN KEY (player_uuid) REFERENCES player_stats(uuid) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;
            
            // 创建会话记录表
            String createSessionsTable = """
                CREATE TABLE IF NOT EXISTS player_sessions (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    player_uuid VARCHAR(36) NOT NULL,
                    join_time BIGINT NOT NULL,
                    leave_time BIGINT,
                    duration_seconds BIGINT DEFAULT 0,
                    INDEX idx_player_uuid (player_uuid),
                    INDEX idx_join_time (join_time),
                    FOREIGN KEY (player_uuid) REFERENCES player_stats(uuid) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;
            
            // 创建交易记录表
            String createTransactionsTable = """
                CREATE TABLE IF NOT EXISTS transactions (
                    transaction_id VARCHAR(36) PRIMARY KEY,
                    player_uuid VARCHAR(36) NOT NULL,
                    player_name VARCHAR(100) NOT NULL,
                    type VARCHAR(50) NOT NULL,
                    amount DOUBLE NOT NULL,
                    balance_before DOUBLE NOT NULL,
                    balance_after DOUBLE NOT NULL,
                    description TEXT,
                    timestamp DATETIME NOT NULL,
                    sent TINYINT DEFAULT 0,
                    INDEX idx_player_uuid (player_uuid),
                    INDEX idx_type (type),
                    INDEX idx_timestamp (timestamp),
                    INDEX idx_sent (sent)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;
            
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(createPlayerStatsTable);
                stmt.execute(createBlocksMinedTable);
                stmt.execute(createBlocksPlacedTable);
                stmt.execute(createSessionsTable);
                stmt.execute(createTransactionsTable);
            }
            
            plugin.getLogger().info("MySQL数据库表初始化完成");
            
        } catch (SQLException e) {
            plugin.getLogger().severe("初始化数据库表失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                // 重新连接
                initialize();
            }
            return connection;
        } catch (SQLException e) {
            plugin.getLogger().severe("获取数据库连接失败: " + e.getMessage());
            return null;
        }
    }

    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed() && connection.isValid(5);
        } catch (SQLException e) {
            return false;
        }
    }

    public void close() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                }
                plugin.getLogger().info("MySQL数据库连接已关闭");
            } catch (SQLException e) {
                plugin.getLogger().severe("关闭数据库连接失败: " + e.getMessage());
            }
        }
    }
}