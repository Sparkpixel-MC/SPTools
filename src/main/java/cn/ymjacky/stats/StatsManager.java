package cn.ymjacky.stats;

import cn.ymjacky.SPToolsPlugin;
import cn.ymjacky.database.MySQLManager;

import java.lang.reflect.Method;
import java.sql.*;
import java.util.*;

public class StatsManager {

    private final SPToolsPlugin plugin;
    private final MySQLManager mysqlManager;
    private boolean autoSaveEnabled;
    private long autoSaveInterval;
    
    // 内存缓存，用于快速访问玩家统计数据
    private final Map<UUID, PlayerStats> cachedStats = new HashMap<>();
    
    public StatsManager(SPToolsPlugin plugin, MySQLManager mysqlManager) {
        this.plugin = plugin;
        this.mysqlManager = mysqlManager;
        this.autoSaveEnabled = plugin.getConfig().getBoolean("stats.auto_save.enabled", true);
        this.autoSaveInterval = plugin.getConfig().getLong("stats.auto_save.interval_seconds", 300);

        startAutoSaveTask();
    }

    /**
     * 获取玩家统计数据（从缓存获取，如果没有则异步加载）
     */
    public PlayerStats getPlayerStats(UUID playerUUID) {
        // 首先从缓存中获取
        PlayerStats cached = cachedStats.get(playerUUID);
        if (cached != null) {
            return cached;
        }
        
        // 缓存中没有，异步加载并返回null（稍后会通过回调通知）
        if (mysqlManager.isConnected()) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                loadPlayerStatsFromDatabase(playerUUID);
            });
        }
        
        return null;
    }
    
    /**
     * 从数据库异步加载玩家统计数据
     */
    private void loadPlayerStatsFromDatabase(UUID playerUUID) {
        if (!mysqlManager.isConnected()) {
            return;
        }

        try {
            Connection conn = mysqlManager.getConnection();
            String sql = "SELECT * FROM player_stats WHERE uuid = ?";
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerUUID.toString());
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    PlayerStats stats = loadPlayerStatsFromResultSet(rs, conn);
                    // 更新缓存
                    cachedStats.put(playerUUID, stats);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load player stats: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 获取或创建玩家统计数据（同步方法，从缓存获取）
     */
    public PlayerStats getOrCreatePlayerStats(UUID playerUUID, String playerName) {
        // 首先从缓存中获取
        PlayerStats stats = cachedStats.get(playerUUID);
        if (stats == null) {
            // 缓存中没有，创建新的统计对象
            stats = new PlayerStats(playerUUID, playerName);
            cachedStats.put(playerUUID, stats);
            
            // 异步插入数据库
            if (mysqlManager.isConnected()) {
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    insertNewPlayer(playerUUID, playerName);
                });
            }
        }
        return stats;
    }

    /**
     * 异步插入新玩家到数据库
     */
    private void insertNewPlayer(UUID playerUUID, String playerName) {
        if (!mysqlManager.isConnected()) {
            return;
        }

        try {
            Connection conn = mysqlManager.getConnection();
            String sql = """
                INSERT INTO player_stats (uuid, player_name, blocks_mined, blocks_placed, online_time_seconds, 
                total_money_earned, total_money_spent, last_join_time, last_update_time)
                VALUES (?, ?, 0, 0, 0, 0, 0, ?, ?)
            """;
            long currentTime = System.currentTimeMillis();
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerUUID.toString());
                stmt.setString(2, playerName);
                stmt.setLong(3, currentTime);
                stmt.setLong(4, currentTime);
                stmt.executeUpdate();

                // 记录会话
                insertSessionRecord(conn, playerUUID, currentTime);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to insert new player: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void insertSessionRecord(Connection conn, UUID playerUUID, long joinTime) throws SQLException {
        String sql = "INSERT INTO player_sessions (player_uuid, join_time, leave_time, duration_seconds) VALUES (?, ?, NULL, 0)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUUID.toString());
            stmt.setLong(2, joinTime);
            stmt.executeUpdate();
        }
    }

    private void updateSessionLeaveTime(UUID playerUUID, long leaveTime) {
        if (!mysqlManager.isConnected()) {
            return;
        }

        try {
            Connection conn = mysqlManager.getConnection();
            String sql = """
                UPDATE player_sessions 
                SET leave_time = ?, duration_seconds = ? 
                WHERE player_uuid = ? AND leave_time IS NULL
                ORDER BY join_time DESC LIMIT 1
            """;
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                long joinTime = getJoinTimeOfLastSession(conn, playerUUID);
                stmt.setLong(1, leaveTime);
                stmt.setLong(2, (leaveTime - joinTime) / 1000);
                stmt.setString(3, playerUUID.toString());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to update session record: " + e.getMessage());
        }
    }

    private long getJoinTimeOfLastSession(Connection conn, UUID playerUUID) throws SQLException {
        String sql = "SELECT join_time FROM player_sessions WHERE player_uuid = ? AND leave_time IS NULL ORDER BY join_time DESC LIMIT 1";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUUID.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getLong("join_time");
            }
        }
        return 0;
    }

    private PlayerStats loadPlayerStatsFromResultSet(ResultSet rs, Connection conn) throws SQLException {
        UUID uuid = UUID.fromString(rs.getString("uuid"));
        String playerName = rs.getString("player_name");
        long blocksMined = rs.getLong("blocks_mined");
        long blocksPlaced = rs.getLong("blocks_placed");
        long onlineTimeSeconds = rs.getLong("online_time_seconds");
        long totalMoneyEarned = rs.getLong("total_money_earned");
        long totalMoneySpent = rs.getLong("total_money_spent");
        long lastJoinTime = rs.getLong("last_join_time");

        PlayerStats stats = new PlayerStats(uuid, playerName);
        stats.setBlocksMined(blocksMined);
        stats.setBlocksPlaced(blocksPlaced);
        stats.setOnlineTimeSeconds(onlineTimeSeconds);
        stats.setTotalMoneyEarned(totalMoneyEarned);
        stats.setTotalMoneySpent(totalMoneySpent);
        stats.setLastJoinTime(lastJoinTime);
        stats.setLastUpdateTime(System.currentTimeMillis());

        // 加载方块挖掘统计
        loadBlocksMined(conn, stats);

        // 加载方块放置统计
        loadBlocksPlaced(conn, stats);

        // 加载会话记录
        loadSessionRecords(conn, stats);

        return stats;
    }

    private void loadBlocksMined(Connection conn, PlayerStats stats) throws SQLException {
        String sql = "SELECT block_type, count FROM blocks_mined WHERE player_uuid = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, stats.getPlayerUUID().toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String blockType = rs.getString("block_type");
                long count = rs.getLong("count");
                stats.addBlocksMined(blockType, count);
            }
        }
    }

    private void loadBlocksPlaced(Connection conn, PlayerStats stats) throws SQLException {
        String sql = "SELECT block_type, count FROM blocks_placed WHERE player_uuid = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, stats.getPlayerUUID().toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String blockType = rs.getString("block_type");
                long count = rs.getLong("count");
                stats.addBlocksPlaced(blockType, count);
            }
        }
    }

    private void loadSessionRecords(Connection conn, PlayerStats stats) throws SQLException {
        String sql = "SELECT join_time, leave_time, duration_seconds FROM player_sessions WHERE player_uuid = ? ORDER BY join_time DESC";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, stats.getPlayerUUID().toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                long joinTime = rs.getLong("join_time");
                PlayerStats.SessionRecord record = new PlayerStats.SessionRecord(joinTime);
                
                Long leaveTime = rs.getLong("leave_time");
                if (!rs.wasNull()) {
                    record.setLeaveTime(leaveTime);
                }
                
                stats.addSessionRecord(record);
            }
        }
    }

    /**
     * 添加方块挖掘统计（同步更新缓存，异步更新数据库）
     */
    public void addBlocksMined(UUID playerUUID, String blockType, int amount) {
        PlayerStats stats = getOrCreatePlayerStats(playerUUID, "");
        if (stats == null) {
            plugin.getLogger().warning("Failed to get or create player stats for UUID: " + playerUUID);
            return;
        }
        stats.addBlocksMined(blockType, amount);
        
        // 异步更新数据库
        if (mysqlManager.isConnected()) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                updatePlayerStatsInDatabase(stats);
            });
        }
    }

    /**
     * 添加方块放置统计（同步更新缓存，异步更新数据库）
     */
    public void addBlocksPlaced(UUID playerUUID, String blockType, int amount) {
        PlayerStats stats = getOrCreatePlayerStats(playerUUID, "");
        if (stats == null) {
            plugin.getLogger().warning("Failed to get or create player stats for UUID: " + playerUUID);
            return;
        }
        stats.addBlocksPlaced(blockType, amount);
        
        // 异步更新数据库
        if (mysqlManager.isConnected()) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                updatePlayerStatsInDatabase(stats);
            });
        }
    }

    /**
     * 添加赚取金钱统计（同步更新缓存，异步更新数据库）
     */
    public void addMoneyEarned(UUID playerUUID, double amount) {
        PlayerStats stats = getOrCreatePlayerStats(playerUUID, "");
        if (stats == null) {
            plugin.getLogger().warning("Failed to get or create player stats for UUID: " + playerUUID);
            return;
        }
        stats.addMoneyEarned(amount);
        
        // 异步更新数据库
        if (mysqlManager.isConnected()) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                updatePlayerStatsInDatabase(stats);
            });
        }
    }

    /**
     * 添加花费金钱统计（同步更新缓存，异步更新数据库）
     */
    public void addMoneySpent(UUID playerUUID, double amount) {
        PlayerStats stats = getOrCreatePlayerStats(playerUUID, "");
        if (stats == null) {
            plugin.getLogger().warning("Failed to get or create player stats for UUID: " + playerUUID);
            return;
        }
        stats.addMoneySpent(amount);
        
        // 异步更新数据库
        if (mysqlManager.isConnected()) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                updatePlayerStatsInDatabase(stats);
            });
        }
    }

    /**
     * 更新玩家加入信息（同步更新缓存，异步更新数据库）
     */
    public void updatePlayerJoin(UUID playerUUID, String playerName) {
        PlayerStats stats = getOrCreatePlayerStats(playerUUID, playerName);
        if (stats == null) {
            plugin.getLogger().warning("Failed to get or create player stats for UUID: " + playerUUID);
            return;
        }
        stats.setLastJoinTime(System.currentTimeMillis());
        stats.setLastUpdateTime(System.currentTimeMillis());
        
        // 添加新的会话记录
        PlayerStats.SessionRecord record = new PlayerStats.SessionRecord(System.currentTimeMillis());
        stats.addSessionRecord(record);
        
        // 异步更新数据库
        if (mysqlManager.isConnected()) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                updatePlayerStatsInDatabase(stats);
                try {
                    Connection conn = mysqlManager.getConnection();
                    insertSessionRecord(conn, playerUUID, System.currentTimeMillis());
                } catch (SQLException e) {
                    plugin.getLogger().severe("Failed to insert session record: " + e.getMessage());
                }
            });
        }
    }

    /**
     * 更新玩家退出信息（同步更新缓存，异步更新数据库）
     */
    public void updatePlayerQuit(UUID playerUUID) {
        PlayerStats stats = cachedStats.get(playerUUID);
        if (stats != null) {
            stats.updateOnlineTime();
            
            // 更新最后一个会话的离开时间
            List<PlayerStats.SessionRecord> records = stats.getSessionRecords();
            if (!records.isEmpty()) {
                PlayerStats.SessionRecord lastRecord = records.get(records.size() - 1);
                if (lastRecord.getLeaveTime() == null) {
                    lastRecord.setLeaveTime(System.currentTimeMillis());
                }
            }
            
            // 异步更新数据库
            if (mysqlManager.isConnected()) {
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    updatePlayerStatsInDatabase(stats);
                    updateSessionLeaveTime(playerUUID, System.currentTimeMillis());
                });
            }
        }
    }

    private void updatePlayerStatsInDatabase(PlayerStats stats) {
        if (!mysqlManager.isConnected()) {
            return;
        }

        try {
            Connection conn = mysqlManager.getConnection();
            String sql = """
                UPDATE player_stats 
                SET blocks_mined = ?, blocks_placed = ?, online_time_seconds = ?,
                    total_money_earned = ?, total_money_spent = ?, last_update_time = ?
                WHERE uuid = ?
            """;
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, stats.getBlocksMined());
                stmt.setLong(2, stats.getBlocksPlaced());
                stmt.setLong(3, stats.getOnlineTimeSeconds());
                stmt.setLong(4, stats.getTotalMoneyEarned());
                stmt.setLong(5, stats.getTotalMoneySpent());
                stmt.setLong(6, System.currentTimeMillis());
                stmt.setString(7, stats.getPlayerUUID().toString());
                stmt.executeUpdate();
            }

            // 更新方块挖掘统计
            updateBlocksMined(conn, stats);

            // 更新方块放置统计
            updateBlocksPlaced(conn, stats);
            
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to update player stats: " + e.getMessage());
        }
    }

    private void updateBlocksMined(Connection conn, PlayerStats stats) throws SQLException {
        // 先删除旧记录
        String deleteSql = "DELETE FROM blocks_mined WHERE player_uuid = ?";
        try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
            stmt.setString(1, stats.getPlayerUUID().toString());
            stmt.executeUpdate();
        }

        // 插入新记录
        String insertSql = "INSERT INTO blocks_mined (player_uuid, block_type, count) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            for (Map.Entry<String, Long> entry : stats.getBlocksMinedByType().entrySet()) {
                stmt.setString(1, stats.getPlayerUUID().toString());
                stmt.setString(2, entry.getKey());
                stmt.setLong(3, entry.getValue());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    private void updateBlocksPlaced(Connection conn, PlayerStats stats) throws SQLException {
        // 先删除旧记录
        String deleteSql = "DELETE FROM blocks_placed WHERE player_uuid = ?";
        try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
            stmt.setString(1, stats.getPlayerUUID().toString());
            stmt.executeUpdate();
        }

        // 插入新记录
        String insertSql = "INSERT INTO blocks_placed (player_uuid, block_type, count) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            for (Map.Entry<String, Long> entry : stats.getBlocksPlacedByType().entrySet()) {
                stmt.setString(1, stats.getPlayerUUID().toString());
                stmt.setString(2, entry.getKey());
                stmt.setLong(3, entry.getValue());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    /**
     * 保存所有统计数据到数据库（异步）
     */
    public void saveStats() {
        if (mysqlManager.isConnected()) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                int savedCount = 0;
                for (PlayerStats stats : cachedStats.values()) {
                    updatePlayerStatsInDatabase(stats);
                    savedCount++;
                }
                plugin.getLogger().info("Auto-save completed: " + savedCount + " players' stats saved to MySQL");
            });
        } else {
            plugin.getLogger().warning("MySQL连接断开，无法保存统计数据");
        }
    }

    /**
     * 从数据库加载所有统计数据（异步）
     */
    public void loadStats() {
        if (mysqlManager.isConnected()) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                loadAllPlayerStatsFromDatabase();
            });
        } else {
            plugin.getLogger().warning("MySQL连接断开，无法加载统计数据");
        }
    }
    
    /**
     * 从数据库加载所有玩家统计数据
     */
    private void loadAllPlayerStatsFromDatabase() {
        if (!mysqlManager.isConnected()) {
            return;
        }

        try {
            Connection conn = mysqlManager.getConnection();
            String sql = "SELECT uuid FROM player_stats";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    loadPlayerStatsFromDatabase(uuid);
                }
            }
            plugin.getLogger().info("Loaded all player stats from MySQL");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load all player stats: " + e.getMessage());
        }
    }

    private void startAutoSaveTask() {
        if (!autoSaveEnabled) {
            return;
        }

        long ticks = autoSaveInterval * 20L;
        boolean isFolia = checkFolia();

        if (isFolia) {
            // 使用 Folia 的 GlobalRegionScheduler
            try {
                Class<?> globalRegionSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
                Object globalScheduler = plugin.getServer().getClass().getMethod("getGlobalRegionScheduler").invoke(plugin.getServer());
                java.lang.reflect.Method runAtFixedRate = globalRegionSchedulerClass.getMethod("runAtFixedRate",
                    org.bukkit.plugin.Plugin.class,
                    java.util.function.Consumer.class,
                    long.class,
                    long.class);

                runAtFixedRate.invoke(globalScheduler, new Object[]{
                    plugin,
                    (java.util.function.Consumer<Object>) t -> {
                        // MySQL模式下不需要自动保存，所有数据立即写入
                        // 可以在这里执行一些维护任务，比如检查数据库连接状态
                        if (!mysqlManager.isConnected()) {
                            plugin.getLogger().warning("MySQL连接断开，尝试重新连接...");
                        }
                    },
                    ticks,
                    ticks
                });

                plugin.getLogger().info("Using Folia GlobalRegionScheduler for stats maintenance task");
            } catch (Exception ex) {
                plugin.getLogger().warning("Async scheduler not supported, stats maintenance disabled");
            }
        } else {
            // 使用传统调度器
            plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::saveStats, ticks, ticks);
            plugin.getLogger().info("Using traditional async scheduler for stats maintenance task");
        }
    }

    private boolean checkFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * 关闭统计管理器，保存所有数据
     */
    public void shutdown() {
        // 保存所有缓存数据到数据库
        if (mysqlManager != null && mysqlManager.isConnected() && !cachedStats.isEmpty()) {
            plugin.getLogger().info("Saving all cached stats before shutdown...");
            for (PlayerStats stats : cachedStats.values()) {
                updatePlayerStatsInDatabase(stats);
            }
            plugin.getLogger().info("Saved " + cachedStats.size() + " players' stats to database");
        }
        
        if (mysqlManager != null) {
            mysqlManager.close();
        }
    }

    /**
     * 获取所有玩家统计数据（从缓存获取）
     */
    public Map<UUID, PlayerStats> getAllPlayerStats() {
        return new HashMap<>(cachedStats);
    }
}