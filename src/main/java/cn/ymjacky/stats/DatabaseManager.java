package cn.ymjacky.stats;

import cn.ymjacky.SPToolsPlugin;

import java.io.File;
import java.sql.*;

public class DatabaseManager {

    private final SPToolsPlugin plugin;
    private Connection connection;

    public DatabaseManager(SPToolsPlugin plugin) {
        this.plugin = plugin;
        this.connection = null;
        initialize();
    }

    private void initialize() {
        try {
            // 创建数据库文件
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            File dbFile = new File(dataFolder, "stats.db");
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();

            // 加载 SQLite JDBC 驱动
            Class.forName("org.sqlite.JDBC");

            // 创建连接
            connection = DriverManager.getConnection(url);
            plugin.getLogger().info("SQLite database connected successfully!");

            // 创建表
            createTables();
        } catch (ClassNotFoundException | SQLException e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createTables() throws SQLException {
        // 创建玩家统计表
        String createStatsTable = """
            CREATE TABLE IF NOT EXISTS player_stats (
                uuid TEXT PRIMARY KEY,
                player_name TEXT NOT NULL,
                blocks_mined INTEGER DEFAULT 0,
                blocks_placed INTEGER DEFAULT 0,
                online_time_seconds INTEGER DEFAULT 0,
                total_money_earned INTEGER DEFAULT 0,
                total_money_spent INTEGER DEFAULT 0,
                last_join_time INTEGER,
                last_update_time INTEGER
            )
        """;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createStatsTable);
        }

        // 创建方块挖掘统计表
        String createBlocksMinedTable = """
            CREATE TABLE IF NOT EXISTS blocks_mined (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                player_uuid TEXT NOT NULL,
                block_type TEXT NOT NULL,
                count INTEGER NOT NULL,
                FOREIGN KEY (player_uuid) REFERENCES player_stats(uuid) ON DELETE CASCADE
            )
        """;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createBlocksMinedTable);
        }

        // 创建方块放置统计表
        String createBlocksPlacedTable = """
            CREATE TABLE IF NOT EXISTS blocks_placed (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                player_uuid TEXT NOT NULL,
                block_type TEXT NOT NULL,
                count INTEGER NOT NULL,
                FOREIGN KEY (player_uuid) REFERENCES player_stats(uuid) ON DELETE CASCADE
            )
        """;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createBlocksPlacedTable);
        }

        // 创建会话记录表
        String createSessionsTable = """
            CREATE TABLE IF NOT EXISTS player_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                player_uuid TEXT NOT NULL,
                join_time INTEGER NOT NULL,
                leave_time INTEGER,
                duration_seconds INTEGER DEFAULT 0,
                FOREIGN KEY (player_uuid) REFERENCES player_stats(uuid) ON DELETE CASCADE
            )
        """;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createSessionsTable);
        }

        plugin.getLogger().info("Database tables created successfully!");
    }

    public Connection getConnection() {
        return connection;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Database connection closed!");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to close database connection: " + e.getMessage());
        }
    }

    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }
}