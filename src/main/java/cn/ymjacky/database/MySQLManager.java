package cn.ymjacky.database;

import cn.ymjacky.SPToolsPlugin;

import java.sql.*;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

public class MySQLManager {

    private final SPToolsPlugin plugin;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;

    private Connection connection;

    private final AtomicBoolean isRetrying = new AtomicBoolean(false);
    private static final long RETRY_INTERVAL_MS = 5000;

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
            Class.forName("com.mysql.cj.jdbc.Driver");

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
            props.setProperty("connectTimeout", "30000");
            props.setProperty("socketTimeout", "30000");

            String url = String.format("jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true", host, port, database);
            plugin.getLogger().info("Attempting to connect to MySQL database: " + host + ":" + port + "/" + database);
            connection = DriverManager.getConnection(url, props);

            plugin.getLogger().info("Successfully connected to MySQL database: " + host + ":" + port + "/" + database);

            if (connection.isValid(5)) {
                plugin.getLogger().info("Database connection validation successful");
            } else {
                plugin.getLogger().warning("Database connection validation failed");
            }

            initializeTables();

        } catch (ClassNotFoundException e) {
            plugin.getLogger().severe("MySQL driver not found, please ensure MySQL Connector/J dependency is added: " + e.getMessage());
            connection = null;
            startRetryTask();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to connect to MySQL database: " + e.getMessage());
            plugin.getLogger().severe("Error code: " + e.getErrorCode());
            plugin.getLogger().severe("SQL state: " + e.getSQLState());
            plugin.getLogger().severe("Please check MySQL configuration: host=" + host + ", port=" + port + ", database=" + database + ", username=" + username);
            connection = null;
            startRetryTask();
        } catch (Exception e) {
            plugin.getLogger().severe("Unknown error occurred while initializing MySQL connection: " + e.getMessage());
            connection = null;
            startRetryTask();
        }
    }

    private void startRetryTask() {
        if (isRetrying.compareAndSet(false, true)) {
            plugin.getLogger().info("Starting database connection retry task, will retry every 5 seconds...");

            plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, (task) -> {
                if (isConnected()) {
                    task.cancel();
                    isRetrying.set(false);
                    plugin.getLogger().info("Database connection reestablished, retry task cancelled");
                    return;
                }

                plugin.getLogger().info("Attempting to reconnect to MySQL database...");
                try {
                    Class.forName("com.mysql.cj.jdbc.Driver");

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
                    props.setProperty("connectTimeout", "10000");
                    props.setProperty("socketTimeout", "10000");

                    String url = String.format("jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true", host, port, database);
                    Connection newConnection = DriverManager.getConnection(url, props);

                    if (connection != null && !connection.isClosed()) {
                        try {
                            connection.close();
                        } catch (SQLException e) {
                            plugin.getLogger().warning("Failed to close old database connection: " + e.getMessage());
                        }
                    }

                    connection = newConnection;

                    if (connection.isValid(5)) {
                        plugin.getLogger().info("Database reconnection successful: " + host + ":" + port + "/" + database);

                        initializeTables();

                        task.cancel();
                        isRetrying.set(false);
                    } else {
                        plugin.getLogger().warning("Database connection validation failed, will retry in 5 seconds...");
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Database reconnection failed: " + e.getMessage() + ", will retry in 5 seconds...");
                }
            }, RETRY_INTERVAL_MS / 50, RETRY_INTERVAL_MS / 50);
        }
    }

    private void initializeTables() {
        if (connection == null) {
            plugin.getLogger().warning("Database connection is null, cannot initialize tables");
            return;
        }

        try {
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

            plugin.getLogger().info("MySQL database tables initialized");
        } catch (Exception ignored) {
        }
    }

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                initialize();
            }
            return connection;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to get database connection: " + e.getMessage());
            startRetryTask();
            return null;
        }
    }

    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed() && connection.isValid(5);
        } catch (SQLException e) {
            if (!isRetrying.get()) {
                startRetryTask();
            }
            return false;
        }
    }

    public void close() {
        if (isRetrying.compareAndSet(true, false)) {
            plugin.getLogger().info("Database connection retry task cancelled");
        }

        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                }
                plugin.getLogger().info("MySQL database connection closed");
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to close database connection: " + e.getMessage());
            }
        }
    }
}