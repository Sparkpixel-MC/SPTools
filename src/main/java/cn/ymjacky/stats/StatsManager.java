package cn.ymjacky.stats;

import cn.ymjacky.SPToolsPlugin;
import cn.ymjacky.database.MySQLManager;

import java.lang.reflect.Method;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class StatsManager {

    private final SPToolsPlugin plugin;
    private final MySQLManager mysqlManager;
    private boolean autoSaveEnabled;
    private long autoSaveInterval;
    
    // 内存缓存，用于快速访问玩家统计数据（使用ConcurrentHashMap保证线程安全）
    private final Map<UUID, PlayerStats> cachedStats = new ConcurrentHashMap<>();
    
    // 标记是否有未保存的数据
    private volatile boolean hasUnsavedChanges = false;
    
    public StatsManager(SPToolsPlugin plugin, MySQLManager mysqlManager) {
        this.plugin = plugin;
        this.mysqlManager = mysqlManager;
        this.autoSaveEnabled = plugin.getConfig().getBoolean("stats.auto_save.enabled", true);
        // 默认2分钟自动保存一次
        this.autoSaveInterval = plugin.getConfig().getLong("stats.auto_save.interval_seconds", 120);

        // 启动时加载所有玩家数据到缓存（延迟执行以避免在插件启用时阻塞）
        scheduleLoadStats();
        
        startAutoSaveTask();
    }

    /**
     * 获取玩家统计数据（从缓存获取，如果没有则异步加载）
     */
    public PlayerStats getPlayerStats(UUID playerUUID) {
        // 首先从缓存中获取
        return cachedStats.get(playerUUID);
    }
    
    /**
     * 从数据库异步加载玩家统计数据
     */
    private void loadPlayerStatsFromDatabase(UUID playerUUID) {
        if (!mysqlManager.isConnected()) {
            return;
        }

        Connection conn = null;
        try {
            conn = mysqlManager.getConnection();
            if (conn == null || conn.isClosed()) {
                return;
            }
            
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
        } finally {
            // 关闭连接
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    plugin.getLogger().warning("Failed to close database connection: " + e.getMessage());
                }
            }
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
            
            // 标记有未保存的更改
            hasUnsavedChanges = true;
        }
        return stats;
    }

    /**
     * 异步插入新玩家到数据库
     */
    private void insertNewPlayer(UUID playerUUID, String playerName) {
        if (!mysqlManager.isConnected()) {
            plugin.getLogger().warning("MySQL连接断开，无法插入新玩家数据");
            return;
        }

        Connection conn = null;
        try {
            conn = mysqlManager.getConnection();
            if (conn == null || conn.isClosed()) {
                plugin.getLogger().warning("无法获取数据库连接");
                return;
            }
            
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
        } finally {
            // 关闭连接
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    plugin.getLogger().warning("Failed to close database connection: " + e.getMessage());
                }
            }
        }
    }

    /**
     * 插入会话记录（每次操作后关闭连接）
     */
    private void insertSessionRecord(Connection conn, UUID playerUUID, long joinTime) throws SQLException {
        String sql = "INSERT INTO player_sessions (player_uuid, join_time, leave_time, duration_seconds) VALUES (?, ?, NULL, 0)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUUID.toString());
            stmt.setLong(2, joinTime);
            stmt.executeUpdate();
        }
    }

    /**
     * 更新会话离开时间（每次操作后关闭连接）
     */
    private void updateSessionLeaveTime(UUID playerUUID, long leaveTime) {
        if (!mysqlManager.isConnected()) {
            plugin.getLogger().warning("MySQL连接断开，无法更新会话记录");
            return;
        }

        Connection conn = null;
        try {
            conn = mysqlManager.getConnection();
            if (conn == null || conn.isClosed()) {
                plugin.getLogger().warning("无法获取数据库连接");
                return;
            }
            
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
        } finally {
            // 关闭连接
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    plugin.getLogger().warning("Failed to close database connection: " + e.getMessage());
                }
            }
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
     * 添加方块挖掘统计（同步更新缓存）
     */
    public void addBlocksMined(UUID playerUUID, String blockType, int amount) {
        PlayerStats stats = getOrCreatePlayerStats(playerUUID, "");
        if (stats == null) {
            plugin.getLogger().warning("Failed to get or create player stats for UUID: " + playerUUID);
            return;
        }
        stats.addBlocksMined(blockType, amount);
        hasUnsavedChanges = true;
    }

    /**
     * 添加方块放置统计（同步更新缓存）
     */
    public void addBlocksPlaced(UUID playerUUID, String blockType, int amount) {
        PlayerStats stats = getOrCreatePlayerStats(playerUUID, "");
        if (stats == null) {
            plugin.getLogger().warning("Failed to get or create player stats for UUID: " + playerUUID);
            return;
        }
        stats.addBlocksPlaced(blockType, amount);
        hasUnsavedChanges = true;
    }

    /**
     * 添加赚取金钱统计（同步更新缓存）
     */
    public void addMoneyEarned(UUID playerUUID, double amount) {
        PlayerStats stats = getOrCreatePlayerStats(playerUUID, "");
        if (stats == null) {
            plugin.getLogger().warning("Failed to get or create player stats for UUID: " + playerUUID);
            return;
        }
        stats.addMoneyEarned(amount);
        hasUnsavedChanges = true;
    }

    /**
     * 添加花费金钱统计（同步更新缓存）
     */
    public void addMoneySpent(UUID playerUUID, double amount) {
        PlayerStats stats = getOrCreatePlayerStats(playerUUID, "");
        if (stats == null) {
            plugin.getLogger().warning("Failed to get or create player stats for UUID: " + playerUUID);
            return;
        }
        stats.addMoneySpent(amount);
        hasUnsavedChanges = true;
    }

    /**
     * 更新玩家加入信息（同步更新缓存）
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
        
        hasUnsavedChanges = true;
    }

    /**
     * 更新玩家退出信息（同步更新缓存）
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
            
            hasUnsavedChanges = true;
        }
    }

    /**
     * 更新玩家统计数据到数据库（每次操作后关闭连接）
     */
    private void updatePlayerStatsInDatabase(PlayerStats stats) {
        if (!mysqlManager.isConnected()) {
            plugin.getLogger().warning("MySQL连接断开，无法保存玩家数据");
            return;
        }

        Connection conn = null;
        try {
            conn = mysqlManager.getConnection();
            if (conn == null || conn.isClosed()) {
                plugin.getLogger().warning("无法获取数据库连接");
                return;
            }
            
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
        } finally {
            // 关闭连接
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    plugin.getLogger().warning("Failed to close database connection: " + e.getMessage());
                }
            }
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
     * 保存所有统计数据到数据库（异步，每次操作后关闭连接）
     */
    public void saveStats() {
        if (!mysqlManager.isConnected()) {
            plugin.getLogger().warning("MySQL连接断开，尝试重新连接...");
            // 尝试获取新连接，这会触发MySQLManager的重试机制
            if (mysqlManager.getConnection() == null) {
                plugin.getLogger().severe("MySQL重连失败，无法保存统计数据");
                return;
            }
            plugin.getLogger().info("MySQL重连成功");
        }
        
        if (!hasUnsavedChanges) {
            plugin.getLogger().info("没有未保存的更改，跳过保存");
            return;
        }

        boolean isFolia = checkFolia();
        
        if (isFolia) {
            // Folia环境：使用GlobalRegionScheduler
            try {
                Class<?> globalRegionSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
                Object globalScheduler = plugin.getServer().getClass().getMethod("getGlobalRegionScheduler").invoke(plugin.getServer());
                java.lang.reflect.Method run = globalRegionSchedulerClass.getMethod("run",
                    org.bukkit.plugin.Plugin.class,
                    java.util.function.Consumer.class);

                run.invoke(globalScheduler, new Object[]{
                    plugin,
                    (java.util.function.Consumer<Object>) t -> saveAllStatsToDatabase()
                });
            } catch (Exception ex) {
                plugin.getLogger().warning("无法使用Folia调度器保存数据: " + ex.getMessage());
            }
        } else {
            // 传统环境：使用异步调度器
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::saveAllStatsToDatabase);
        }
    }
    
    /**
     * 实际执行保存操作
     */
    private void saveAllStatsToDatabase() {
        int savedCount = 0;
        int failedCount = 0;
        
        for (PlayerStats stats : cachedStats.values()) {
            try {
                updatePlayerStatsInDatabase(stats);
                savedCount++;
            } catch (Exception e) {
                plugin.getLogger().severe("保存玩家数据失败: " + stats.getPlayerUUID() + " - " + e.getMessage());
                failedCount++;
            }
        }
        
        hasUnsavedChanges = false;
        plugin.getLogger().info("自动保存完成: 成功保存 " + savedCount + " 个玩家数据，失败 " + failedCount + " 个");
    }

    /**
     * 从数据库加载所有统计数据（异步，每次操作后关闭连接）
     */
    public void loadStats() {
        if (!mysqlManager.isConnected()) {
            plugin.getLogger().warning("MySQL连接断开，无法加载统计数据");
            return;
        }
        
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            loadAllPlayerStatsFromDatabase();
        });
    }
    
    /**
     * 调度加载统计数据
     */
    private void scheduleLoadStats() {
        boolean isFolia = checkFolia();
        
        if (isFolia) {
            // Folia环境：使用GlobalRegionScheduler
            try {
                Class<?> globalRegionSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
                Object globalScheduler = plugin.getServer().getClass().getMethod("getGlobalRegionScheduler").invoke(plugin.getServer());
                java.lang.reflect.Method runLater = globalRegionSchedulerClass.getMethod("runLater",
                    org.bukkit.plugin.Plugin.class,
                    java.util.function.Consumer.class,
                    long.class);

                runLater.invoke(globalScheduler, new Object[]{
                    plugin,
                    (java.util.function.Consumer<Object>) t -> loadAllPlayerStatsFromDatabase(),
                    1L // 延迟1tick执行
                });
            } catch (Exception ex) {
                plugin.getLogger().warning("无法使用Folia调度器加载数据: " + ex.getMessage());
            }
        } else {
            // 传统环境：使用异步调度器
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::loadAllPlayerStatsFromDatabase);
        }
    }
    
    /**
     * 从数据库加载所有玩家统计数据（每次操作后关闭连接）
     */
    private void loadAllPlayerStatsFromDatabase() {
        if (!mysqlManager.isConnected()) {
            plugin.getLogger().warning("MySQL连接断开，无法加载玩家数据");
            return;
        }

        Connection conn = null;
        try {
            conn = mysqlManager.getConnection();
            if (conn == null || conn.isClosed()) {
                plugin.getLogger().warning("无法获取数据库连接");
                return;
            }
            
            String sql = "SELECT uuid FROM player_stats";
            int loadedCount = 0;
            
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    loadPlayerStatsFromDatabase(uuid);
                    loadedCount++;
                }
            }
            
            plugin.getLogger().info("从MySQL加载了 " + loadedCount + " 个玩家统计数据");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load all player stats: " + e.getMessage());
        } finally {
            // 关闭连接
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    plugin.getLogger().warning("Failed to close database connection: " + e.getMessage());
                }
            }
        }
    }

    /**
     * 启动自动保存任务（每2分钟执行一次）
     */
    private void startAutoSaveTask() {
        if (!autoSaveEnabled) {
            plugin.getLogger().info("自动保存功能未启用");
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
                        // 执行自动保存
                        saveStats();
                    },
                    ticks,
                    ticks
                });

                plugin.getLogger().info("使用Folia GlobalRegionScheduler，每 " + autoSaveInterval + " 秒自动保存一次统计数据");
            } catch (Exception ex) {
                plugin.getLogger().warning("无法使用Folia异步调度器，自动保存功能已禁用");
                ex.printStackTrace();
            }
        } else {
            // 使用传统调度器
            plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::saveStats, ticks, ticks);
            plugin.getLogger().info("使用传统异步调度器，每 " + autoSaveInterval + " 秒自动保存一次统计数据");
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
        plugin.getLogger().info("========================================");
        plugin.getLogger().info("开始关闭统计管理器并上报数据...");
        plugin.getLogger().info("========================================");
        
        // 保存所有缓存数据到数据库
        if (!cachedStats.isEmpty()) {
            plugin.getLogger().info("待上报玩家数据: " + cachedStats.size() + " 个");
            
            if (mysqlManager != null && mysqlManager.isConnected()) {
                int savedCount = 0;
                int failedCount = 0;
                long startTime = System.currentTimeMillis();
                
                for (PlayerStats stats : cachedStats.values()) {
                    try {
                        updatePlayerStatsInDatabase(stats);
                        savedCount++;
                        
                        // 每100个玩家输出一次进度
                        if (savedCount % 100 == 0) {
                            plugin.getLogger().info("上报进度: " + savedCount + "/" + cachedStats.size());
                        }
                    } catch (Exception e) {
                        plugin.getLogger().severe("上报玩家数据失败: " + stats.getPlayerUUID() + " - " + e.getMessage());
                        failedCount++;
                    }
                }
                
                long duration = System.currentTimeMillis() - startTime;
                plugin.getLogger().info("数据上报完成:");
                plugin.getLogger().info("  - 成功: " + savedCount + " 个");
                plugin.getLogger().info("  - 失败: " + failedCount + " 个");
                plugin.getLogger().info("  - 耗时: " + duration + " 毫秒");
                
                if (failedCount > 0) {
                    plugin.getLogger().warning("警告: 有 " + failedCount + " 个玩家数据上报失败，请检查数据库连接");
                }
            } else {
                plugin.getLogger().warning("MySQL连接断开，无法上报数据到数据库");
                plugin.getLogger().warning("建议: 检查MySQL服务器状态和网络连接");
            }
        } else {
            plugin.getLogger().info("没有需要上报的数据");
        }
        
        // 关闭MySQL连接
        if (mysqlManager != null) {
            mysqlManager.close();
        }
        
        // 清空缓存
        cachedStats.clear();
        hasUnsavedChanges = false;
        
        plugin.getLogger().info("========================================");
        plugin.getLogger().info("统计管理器已关闭");
        plugin.getLogger().info("========================================");
    }

    /**
     * 获取所有玩家统计数据（从缓存获取）
     */
    public Map<UUID, PlayerStats> getAllPlayerStats() {
        return new ConcurrentHashMap<>(cachedStats);
    }
}