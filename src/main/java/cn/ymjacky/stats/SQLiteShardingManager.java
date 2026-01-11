package cn.ymjacky.stats;

import cn.ymjacky.SPToolsPlugin;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SQLiteShardingManager {

    private final SPToolsPlugin plugin;
    private final File databaseDir;
    private final String databasePrefix;
    private final int maxPlayersPerDb;
    
    // 数据库连接缓存
    private final Map<Integer, Connection> connectionCache;
    // 数据库索引映射（UUID -> 数据库索引）
    private final Map<UUID, Integer> uuidToDbIndex;
    // 数据库玩家数量缓存
    private final Map<Integer, Integer> dbPlayerCount;
    
    // 当前最大数据库索引
    private int maxDbIndex = 0;

    public SQLiteShardingManager(SPToolsPlugin plugin) {
        this.plugin = plugin;
        this.databaseDir = new File(plugin.getDataFolder(), plugin.getConfig().getString("sqlite.database_dir", "stats"));
        this.databasePrefix = plugin.getConfig().getString("sqlite.database_prefix", "stats_");
        this.maxPlayersPerDb = plugin.getConfig().getInt("sqlite.max_players_per_db", 10000);
        
        this.connectionCache = new ConcurrentHashMap<>();
        this.uuidToDbIndex = new ConcurrentHashMap<>();
        this.dbPlayerCount = new ConcurrentHashMap<>();
        
        initialize();
    }

    private void initialize() {
        try {
            // 创建数据库目录
            if (!databaseDir.exists()) {
                databaseDir.mkdirs();
            }
            
            // 扫描现有数据库文件
            scanExistingDatabases();
            
            plugin.getLogger().info("SQLite分库管理器初始化完成，共发现 " + (maxDbIndex + 1) + " 个数据库文件");
            
        } catch (Exception e) {
            plugin.getLogger().severe("SQLite分库管理器初始化失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 扫描现有数据库文件
     */
    private void scanExistingDatabases() {
        File[] dbFiles = databaseDir.listFiles((dir, name) -> name.startsWith(databasePrefix) && name.endsWith(".db"));
        
        if (dbFiles != null) {
            for (File dbFile : dbFiles) {
                try {
                    // 从文件名提取索引
                    String fileName = dbFile.getName();
                    String indexStr = fileName.substring(databasePrefix.length(), fileName.length() - 3);
                    int index = Integer.parseInt(indexStr);
                    
                    if (index > maxDbIndex) {
                        maxDbIndex = index;
                    }
                    
                    // 统计该数据库中的玩家数量
                    int playerCount = countPlayersInDatabase(dbFile);
                    dbPlayerCount.put(index, playerCount);
                    
                    plugin.getLogger().info("发现数据库文件: " + dbFile.getName() + " (玩家数: " + playerCount + ")");
                    
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("无法解析数据库文件索引: " + dbFile.getName());
                }
            }
        }
    }

    /**
     * 统计数据库中的玩家数量
     */
    private int countPlayersInDatabase(File dbFile) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM player_stats")) {
            
            if (rs.next()) {
                return rs.getInt(1);
            }
            
        } catch (SQLException e) {
            // 表可能不存在，返回0
            return 0;
        }
        
        return 0;
    }

    /**
     * 根据UUID获取数据库索引
     */
    private int getDatabaseIndex(UUID playerUUID) {
        // 首先检查缓存
        Integer cachedIndex = uuidToDbIndex.get(playerUUID);
        if (cachedIndex != null) {
            return cachedIndex;
        }
        
        // 使用UUID哈希值分配数据库索引
        int hashCode = playerUUID.hashCode();
        int dbIndex = Math.abs(hashCode) % (maxDbIndex + 1);
        
        // 检查该数据库是否已满
        if (dbPlayerCount.getOrDefault(dbIndex, 0) >= maxPlayersPerDb) {
            // 查找一个未满的数据库
            for (int i = 0; i <= maxDbIndex; i++) {
                if (dbPlayerCount.getOrDefault(i, 0) < maxPlayersPerDb) {
                    dbIndex = i;
                    break;
                }
            }
            
            // 如果所有数据库都满了，创建新的数据库
            if (dbPlayerCount.getOrDefault(dbIndex, 0) >= maxPlayersPerDb) {
                maxDbIndex++;
                dbIndex = maxDbIndex;
                plugin.getLogger().info("创建新的数据库文件: " + databasePrefix + dbIndex + ".db");
            }
        }
        
        // 缓存映射关系
        uuidToDbIndex.put(playerUUID, dbIndex);
        
        return dbIndex;
    }

    /**
     * 获取数据库连接
     */
    public Connection getConnection(UUID playerUUID) {
        int dbIndex = getDatabaseIndex(playerUUID);
        
        // 检查连接缓存
        Connection cachedConn = connectionCache.get(dbIndex);
        if (cachedConn != null) {
            try {
                if (!cachedConn.isClosed()) {
                    return cachedConn;
                }
            } catch (SQLException e) {
                connectionCache.remove(dbIndex);
            }
        }
        
        // 创建新连接
        File dbFile = new File(databaseDir, databasePrefix + dbIndex + ".db");
        try {
            Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            connectionCache.put(dbIndex, conn);
            
            // 初始化数据库表结构
            initializeDatabaseTables(conn);
            
            return conn;
            
        } catch (SQLException e) {
            plugin.getLogger().severe("创建数据库连接失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 初始化数据库表结构
     */
    private void initializeDatabaseTables(Connection conn) throws SQLException {
        // 创建玩家统计表
        String createPlayerStatsTable = """
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
        
        // 创建方块挖掘表
        String createBlocksMinedTable = """
            CREATE TABLE IF NOT EXISTS blocks_mined (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                player_uuid TEXT NOT NULL,
                block_type TEXT NOT NULL,
                count INTEGER NOT NULL,
                FOREIGN KEY (player_uuid) REFERENCES player_stats(uuid) ON DELETE CASCADE
            )
        """;
        
        // 创建方块放置表
        String createBlocksPlacedTable = """
            CREATE TABLE IF NOT EXISTS blocks_placed (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                player_uuid TEXT NOT NULL,
                block_type TEXT NOT NULL,
                count INTEGER NOT NULL,
                FOREIGN KEY (player_uuid) REFERENCES player_stats(uuid) ON DELETE CASCADE
            )
        """;
        
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
        
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createPlayerStatsTable);
            stmt.execute(createBlocksMinedTable);
            stmt.execute(createBlocksPlacedTable);
            stmt.execute(createSessionsTable);
        }
    }

    /**
     * 增加数据库玩家计数
     */
    public void incrementPlayerCount(UUID playerUUID) {
        int dbIndex = getDatabaseIndex(playerUUID);
        dbPlayerCount.put(dbIndex, dbPlayerCount.getOrDefault(dbIndex, 0) + 1);
    }

    /**
     * 检查连接状态
     */
    public boolean isConnected(UUID playerUUID) {
        try {
            Connection conn = getConnection(playerUUID);
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * 获取所有数据库中的所有玩家UUID
     */
    public Set<UUID> getAllPlayerUUIDs() {
        Set<UUID> uuids = new HashSet<>();
        
        for (int i = 0; i <= maxDbIndex; i++) {
            File dbFile = new File(databaseDir, databasePrefix + i + ".db");
            if (!dbFile.exists()) {
                continue;
            }
            
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT uuid FROM player_stats")) {
                
                while (rs.next()) {
                    String uuidStr = rs.getString("uuid");
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        uuids.add(uuid);
                        // 缓存映射关系
                        uuidToDbIndex.put(uuid, i);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("无效的UUID: " + uuidStr);
                    }
                }
                
            } catch (SQLException e) {
                plugin.getLogger().severe("读取数据库失败: " + dbFile.getName() + " - " + e.getMessage());
            }
        }
        
        return uuids;
    }

    /**
     * 关闭所有数据库连接
     */
    public void close() {
        for (Map.Entry<Integer, Connection> entry : connectionCache.entrySet()) {
            try {
                if (entry.getValue() != null && !entry.getValue().isClosed()) {
                    entry.getValue().close();
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("关闭数据库连接失败: " + e.getMessage());
            }
        }
        
        connectionCache.clear();
        uuidToDbIndex.clear();
        dbPlayerCount.clear();
        
        plugin.getLogger().info("SQLite分库管理器已关闭所有连接");
    }
}