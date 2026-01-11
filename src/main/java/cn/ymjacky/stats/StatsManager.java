package cn.ymjacky.stats;

import cn.ymjacky.SPToolsPlugin;

import java.lang.reflect.Method;
import java.util.*;

public class StatsManager {

    private final SPToolsPlugin plugin;
    private final RedisManager redisManager;
    private boolean autoSaveEnabled;
    private long autoSaveInterval;

    public StatsManager(SPToolsPlugin plugin) {
        this.plugin = plugin;
        this.redisManager = new RedisManager(plugin);
        this.autoSaveEnabled = plugin.getConfig().getBoolean("stats.auto_save.enabled", true);
        this.autoSaveInterval = plugin.getConfig().getLong("stats.auto_save.interval_seconds", 300);

        startAutoSaveTask();
    }

    public PlayerStats getPlayerStats(UUID playerUUID) {
        if (!redisManager.isConnected()) {
            return null;
        }

        return redisManager.loadPlayerStats(playerUUID);
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
        if (!redisManager.isConnected()) {
            return;
        }

        PlayerStats stats = new PlayerStats(playerUUID, playerName);
        redisManager.savePlayerStats(playerUUID, stats);
        
        // 记录会话
        redisManager.addSessionRecord(playerUUID, System.currentTimeMillis());
    }

    public void addBlocksMined(UUID playerUUID, String blockType, int amount) {
        PlayerStats stats = getOrCreatePlayerStats(playerUUID, "");
        stats.addBlocksMined(blockType, amount);
        redisManager.savePlayerStats(playerUUID, stats);
    }

    public void addBlocksPlaced(UUID playerUUID, String blockType, int amount) {
        PlayerStats stats = getOrCreatePlayerStats(playerUUID, "");
        stats.addBlocksPlaced(blockType, amount);
        redisManager.savePlayerStats(playerUUID, stats);
    }

    public void addMoneyEarned(UUID playerUUID, double amount) {
        PlayerStats stats = getOrCreatePlayerStats(playerUUID, "");
        stats.addMoneyEarned(amount);
        redisManager.savePlayerStats(playerUUID, stats);
    }

    public void addMoneySpent(UUID playerUUID, double amount) {
        PlayerStats stats = getOrCreatePlayerStats(playerUUID, "");
        stats.addMoneySpent(amount);
        redisManager.savePlayerStats(playerUUID, stats);
    }

    public void updatePlayerJoin(UUID playerUUID, String playerName) {
        PlayerStats stats = getOrCreatePlayerStats(playerUUID, playerName);
        stats.setLastJoinTime(System.currentTimeMillis());
        stats.setLastUpdateTime(System.currentTimeMillis());
        
        // 添加新的会话记录
        PlayerStats.SessionRecord record = new PlayerStats.SessionRecord(System.currentTimeMillis());
        stats.addSessionRecord(record);
        
        redisManager.savePlayerStats(playerUUID, stats);
        redisManager.addSessionRecord(playerUUID, System.currentTimeMillis());
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
            
            redisManager.savePlayerStats(playerUUID, stats);
            redisManager.updateSessionLeaveTime(playerUUID, System.currentTimeMillis());
        }
    }

    public void saveStats() {
        // Redis模式下数据实时保存，不需要手动保存
        plugin.getLogger().info("Auto-save completed (all data is saved to Redis immediately)");
    }

    public void loadStats() {
        // Redis模式下数据按需加载，不需要手动加载
        plugin.getLogger().info("Stats are loaded on-demand from Redis");
    }

    private void startAutoSaveTask() {
        if (!autoSaveEnabled) {
            return;
        }

        long ticks = autoSaveInterval * 20L;

        try {
            // 尝试使用 Folia 的 GlobalRegionScheduler
            Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
            Object server = bukkitClass.getMethod("getServer").invoke(null);

            Class<?> globalRegionSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            Object globalScheduler = server.getClass().getMethod("getGlobalRegionScheduler").invoke(server);

            // Folia 的 runAtFixedRate 方法签名: runAtFixedRate(Plugin, Consumer<ScheduledTask>, long, long)
            Class<?> regionSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.RegionScheduler");
            Method runAtFixedRate = regionSchedulerClass.getMethod("runAtFixedRate", 
                org.bukkit.plugin.Plugin.class, 
                java.util.function.Consumer.class, 
                long.class, 
                long.class);

            runAtFixedRate.invoke(globalScheduler, new Object[]{
                plugin, 
                (java.util.function.Consumer<?>) t -> {
                    // Redis模式下不需要自动保存，所有数据立即写入
                    // 可以在这里执行一些维护任务，比如检查Redis连接状态
                    if (!redisManager.isConnected()) {
                        plugin.getLogger().warning("Redis连接断开，尝试重新连接...");
                    }
                }, 
                ticks, 
                ticks
            });

            plugin.getLogger().info("Using Folia GlobalRegionScheduler for stats maintenance task");
        } catch (Exception e) {
            // 回退到传统调度器
            try {
                plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::saveStats, ticks, ticks);
                plugin.getLogger().info("Using traditional async scheduler for stats maintenance task");
            } catch (UnsupportedOperationException ex) {
                plugin.getLogger().warning("Async scheduler not supported, stats maintenance disabled");
            }
        }
    }

    public void shutdown() {
        if (redisManager != null) {
            redisManager.close();
        }
    }

    public Map<UUID, PlayerStats> getAllPlayerStats() {
        Map<UUID, PlayerStats> allStats = new HashMap<>();

        if (!redisManager.isConnected()) {
            return allStats;
        }

        Set<UUID> uuids = redisManager.getAllPlayerUUIDs();
        for (UUID uuid : uuids) {
            PlayerStats stats = getPlayerStats(uuid);
            if (stats != null) {
                allStats.put(uuid, stats);
            }
        }

        return allStats;
    }
}