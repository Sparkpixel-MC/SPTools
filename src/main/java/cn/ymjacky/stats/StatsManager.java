package cn.ymjacky.stats;

import cn.ymjacky.SPToolsPlugin;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StatsManager {

    private final SPToolsPlugin plugin;
    private final Map<UUID, PlayerStats> playerStats;
    private final File dataFile;
    private final Gson gson;
    private boolean autoSaveEnabled;
    private long autoSaveInterval;

    public StatsManager(SPToolsPlugin plugin) {
        this.plugin = plugin;
        this.playerStats = new ConcurrentHashMap<>();
        this.dataFile = new File(plugin.getDataFolder(), "player_stats.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.autoSaveEnabled = plugin.getConfig().getBoolean("stats.auto_save.enabled", true);
        this.autoSaveInterval = plugin.getConfig().getLong("stats.auto_save.interval_seconds", 300);

        loadStats();
        startAutoSaveTask();
    }

    public PlayerStats getPlayerStats(UUID playerUUID) {
        return playerStats.get(playerUUID);
    }

    public PlayerStats getOrCreatePlayerStats(UUID playerUUID, String playerName) {
        return playerStats.computeIfAbsent(playerUUID, uuid -> new PlayerStats(uuid, playerName));
    }

    public void addBlocksMined(UUID playerUUID, String blockType, int amount) {
        PlayerStats stats = getOrCreatePlayerStats(playerUUID, "");
        stats.addBlocksMined(blockType, amount);
    }

    public void addBlocksPlaced(UUID playerUUID, String blockType, int amount) {
        PlayerStats stats = getOrCreatePlayerStats(playerUUID, "");
        stats.addBlocksPlaced(blockType, amount);
    }

    public void addMoneyEarned(UUID playerUUID, double amount) {
        PlayerStats stats = getOrCreatePlayerStats(playerUUID, "");
        stats.addMoneyEarned(amount);
    }

    public void addMoneySpent(UUID playerUUID, double amount) {
        PlayerStats stats = getOrCreatePlayerStats(playerUUID, "");
        stats.addMoneySpent(amount);
    }

    public void updatePlayerJoin(UUID playerUUID, String playerName) {
        PlayerStats stats = getOrCreatePlayerStats(playerUUID, playerName);
        stats.setLastJoinTime(System.currentTimeMillis());
        stats.setLastUpdateTime(System.currentTimeMillis());
    }

    public void updatePlayerQuit(UUID playerUUID) {
        PlayerStats stats = getPlayerStats(playerUUID);
        if (stats != null) {
            stats.updateOnlineTime();
        }
    }

    public void saveStats() {
        try {
            Map<String, Map<String, Object>> serializedStats = new HashMap<>();
            for (PlayerStats stats : playerStats.values()) {
                Map<String, Object> data = new HashMap<>();
                data.put("playerName", stats.getPlayerName());
                data.put("blocksMined", stats.getBlocksMined());
                data.put("blocksPlaced", stats.getBlocksPlaced());
                data.put("onlineTimeSeconds", stats.getOnlineTimeSeconds());
                data.put("totalMoneyEarned", stats.getTotalMoneyEarned());
                data.put("totalMoneySpent", stats.getTotalMoneySpent());
                data.put("blocksMinedByType", stats.getBlocksMinedByType());
                data.put("blocksPlacedByType", stats.getBlocksPlacedByType());
                data.put("lastJoinTime", stats.getLastJoinTime());
                data.put("lastUpdateTime", System.currentTimeMillis());
                serializedStats.put(stats.getPlayerUUID().toString(), data);
            }

            if (!dataFile.exists()) {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            }

            try (FileWriter writer = new FileWriter(dataFile)) {
                gson.toJson(serializedStats, writer);
            }

            plugin.getLogger().info("Player stats saved successfully!");
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save player stats: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public void loadStats() {
        if (!dataFile.exists()) {
            plugin.getLogger().info("No existing stats file found, starting fresh.");
            return;
        }

        try (FileReader reader = new FileReader(dataFile)) {
            Type type = new TypeToken<Map<String, Map<String, Object>>>() {}.getType();
            Map<String, Map<String, Object>> serializedStats = gson.fromJson(reader, type);

            if (serializedStats != null) {
                for (Map.Entry<String, Map<String, Object>> entry : serializedStats.entrySet()) {
                    UUID uuid = UUID.fromString(entry.getKey());
                    Map<String, Object> data = entry.getValue();

                    PlayerStats stats = new PlayerStats(
                        uuid,
                        (String) data.get("playerName")
                    );

                    stats.setBlocksMined(((Number) data.getOrDefault("blocksMined", 0)).longValue());
                    stats.setBlocksPlaced(((Number) data.getOrDefault("blocksPlaced", 0)).longValue());
                    stats.setOnlineTimeSeconds(((Number) data.getOrDefault("onlineTimeSeconds", 0)).longValue());
                    stats.setTotalMoneyEarned(((Number) data.getOrDefault("totalMoneyEarned", 0)).longValue());
                    stats.setTotalMoneySpent(((Number) data.getOrDefault("totalMoneySpent", 0)).longValue());
                    stats.setLastJoinTime(((Number) data.getOrDefault("lastJoinTime", System.currentTimeMillis())).longValue());
                    stats.setLastUpdateTime(((Number) data.getOrDefault("lastUpdateTime", System.currentTimeMillis())).longValue());

                    Map<String, Long> minedByType = (Map<String, Long>) data.getOrDefault("blocksMinedByType", new HashMap<>());
                    Map<String, Long> placedByType = (Map<String, Long>) data.getOrDefault("blocksPlacedByType", new HashMap<>());

                    for (Map.Entry<String, Long> minedEntry : minedByType.entrySet()) {
                        stats.addBlocksMined(minedEntry.getKey(), minedEntry.getValue());
                    }
                    for (Map.Entry<String, Long> placedEntry : placedByType.entrySet()) {
                        stats.addBlocksPlaced(placedEntry.getKey(), placedEntry.getValue());
                    }

                    playerStats.put(uuid, stats);
                }
            }

            plugin.getLogger().info("Player stats loaded successfully!");
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to load player stats: " + e.getMessage());
        }
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
                (java.util.function.Consumer<?>) t -> saveStats(), 
                ticks, 
                ticks
            });

            plugin.getLogger().info("Using Folia GlobalRegionScheduler for auto-save task");
        } catch (Exception e) {
            // 回退到传统调度器
            try {
                plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::saveStats, ticks, ticks);
                plugin.getLogger().info("Using traditional async scheduler for auto-save task");
            } catch (UnsupportedOperationException ex) {
                plugin.getLogger().warning("Async scheduler not supported, disabling auto-save task");
                plugin.getLogger().warning("Stats will only be saved on plugin shutdown");
            }
        }
    }

    public void shutdown() {
        saveStats();
        playerStats.clear();
    }

    public Map<UUID, PlayerStats> getAllPlayerStats() {
        return new HashMap<>(playerStats);
    }
}