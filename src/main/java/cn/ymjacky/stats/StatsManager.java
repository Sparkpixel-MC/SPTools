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

    public StatsManager(SPToolsPlugin plugin, MySQLManager mysqlManager) {
        this.plugin = plugin;
        this.mysqlManager = mysqlManager;
        this.autoSaveEnabled = plugin.getConfig().getBoolean("stats.auto_save.enabled", true);
        this.autoSaveInterval = plugin.getConfig().getLong("stats.auto_save.interval_seconds", 300);

        startAutoSaveTask();
    }

    public PlayerStats getPlayerStats(UUID playerUUID) {
        if (!mysqlManager.isConnected()) {
            return null;
        }

        try {
            Connection conn = mysqlManager.getConnection();
            String sql = "SELECT * FROM player_stats WHERE uuid = ?";
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerUUID.toString());
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return loadPlayerStatsFromResultSet(rs, conn);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load player stats: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    public PlayerStats getOrCreatePlayerStats(UUID playerUUID, String playerName) {
        PlayerStats stats = getPlayerStats(playerUUID);
        if (stats == null) {
            insertNewPlayer(playerUUID, playerName);
            stats = getPlayerStats(playerUUID);
        }
        return stats;
    }

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

    public void addBlocksMined(UUID playerUUID, String blockType, int amount) {
        PlayerStats stats = getOrCreatePlayerStats(playerUUID, "");
        stats.addBlocksMined(blockType, amount);
        updatePlayerStatsInDatabase(stats);
    }

    public void addBlocksPlaced(UUID playerUUID, String blockType, int amount) {
        PlayerStats stats = getOrCreatePlayerStats(playerUUID, "");
        stats.addBlocksPlaced(blockType, amount);
        updatePlayerStatsInDatabase(stats);
    }

    public void addMoneyEarned(UUID playerUUID, double amount) {
        PlayerStats stats = getOrCreatePlayerStats(playerUUID, "");
        stats.addMoneyEarned(amount);
        updatePlayerStatsInDatabase(stats);
    }

    public void addMoneySpent(UUID playerUUID, double amount) {
        PlayerStats stats = getOrCreatePlayerStats(playerUUID, "");
        stats.addMoneySpent(amount);
        updatePlayerStatsInDatabase(stats);
    }

    public void updatePlayerJoin(UUID playerUUID, String playerName) {
        PlayerStats stats = getOrCreatePlayerStats(playerUUID, playerName);
        stats.setLastJoinTime(System.currentTimeMillis());
        stats.setLastUpdateTime(System.currentTimeMillis());
        
        // 添加新的会话记录
        PlayerStats.SessionRecord record = new PlayerStats.SessionRecord(System.currentTimeMillis());
        stats.addSessionRecord(record);
        
        updatePlayerStatsInDatabase(stats);
        
        try {
            Connection conn = mysqlManager.getConnection();
            insertSessionRecord(conn, playerUUID, System.currentTimeMillis());
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to insert session record: " + e.getMessage());
        }
    }

    public void updatePlayerQuit(UUID playerUUID) {
        PlayerStats stats = getPlayerStats(playerUUID);
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
            
            updatePlayerStatsInDatabase(stats);
            updateSessionLeaveTime(playerUUID, System.currentTimeMillis());
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

    public void saveStats() {
        // MySQL模式下数据实时保存，不需要手动保存
        plugin.getLogger().info("Auto-save completed (all data is saved to MySQL immediately)");
    }

    public void loadStats() {
        // MySQL模式下数据按需加载，不需要手动加载
        plugin.getLogger().info("Stats are loaded on-demand from MySQL");
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

    public void shutdown() {
        if (mysqlManager != null) {
            mysqlManager.close();
        }
    }

    public Map<UUID, PlayerStats> getAllPlayerStats() {
        Map<UUID, PlayerStats> allStats = new HashMap<>();

        if (!mysqlManager.isConnected()) {
            return allStats;
        }

        try {
            Connection conn = mysqlManager.getConnection();
            String sql = "SELECT uuid FROM player_stats";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    PlayerStats stats = getPlayerStats(uuid);
                    if (stats != null) {
                        allStats.put(uuid, stats);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load all player stats: " + e.getMessage());
        }

        return allStats;
    }
}