package cn.ymjacky.stats.api;

import cn.ymjacky.stats.PlayerStats;
import cn.ymjacky.stats.StatsManager;

import java.util.Map;
import java.util.UUID;

/**
 * StatsAPI - 提供统计数据的API接口
 * 其他插件可以通过此接口获取玩家的统计数据
 */
public class StatsAPI {

    private final StatsManager statsManager;

    public StatsAPI(StatsManager statsManager) {
        this.statsManager = statsManager;
    }

    /**
     * 获取指定玩家的统计数据
     * @param playerUUID 玩家UUID
     * @return 玩家统计数据，如果不存在则返回null
     */
    public PlayerStats getPlayerStats(UUID playerUUID) {
        return statsManager.getPlayerStats(playerUUID);
    }

    /**
     * 获取指定玩家挖掘的方块总数
     * @param playerUUID 玩家UUID
     * @return 挖掘的方块总数
     */
    public long getBlocksMined(UUID playerUUID) {
        PlayerStats stats = statsManager.getPlayerStats(playerUUID);
        return stats != null ? stats.getBlocksMined() : 0;
    }

    /**
     * 获取指定玩家放置的方块总数
     * @param playerUUID 玩家UUID
     * @return 放置的方块总数
     */
    public long getBlocksPlaced(UUID playerUUID) {
        PlayerStats stats = statsManager.getPlayerStats(playerUUID);
        return stats != null ? stats.getBlocksPlaced() : 0;
    }

    /**
     * 获取指定玩家的在线时长（秒）
     * @param playerUUID 玩家UUID
     * @return 在线时长（秒）
     */
    public long getOnlineTimeSeconds(UUID playerUUID) {
        PlayerStats stats = statsManager.getPlayerStats(playerUUID);
        return stats != null ? stats.getOnlineTimeSeconds() : 0;
    }

    /**
     * 获取指定玩家的累计收入
     * @param playerUUID 玩家UUID
     * @return 累计收入
     */
    public long getTotalMoneyEarned(UUID playerUUID) {
        PlayerStats stats = statsManager.getPlayerStats(playerUUID);
        return stats != null ? stats.getTotalMoneyEarned() : 0;
    }

    /**
     * 获取指定玩家的累计支出
     * @param playerUUID 玩家UUID
     * @return 累计支出
     */
    public long getTotalMoneySpent(UUID playerUUID) {
        PlayerStats stats = statsManager.getPlayerStats(playerUUID);
        return stats != null ? stats.getTotalMoneySpent() : 0;
    }

    /**
     * 获取指定玩家按类型分类的挖掘统计数据
     * @param playerUUID 玩家UUID
     * @return 方块类型到挖掘数量的映射
     */
    public Map<String, Long> getBlocksMinedByType(UUID playerUUID) {
        PlayerStats stats = statsManager.getPlayerStats(playerUUID);
        return stats != null ? stats.getBlocksMinedByType() : Map.of();
    }

    /**
     * 获取指定玩家按类型分类的放置统计数据
     * @param playerUUID 玩家UUID
     * @return 方块类型到放置数量的映射
     */
    public Map<String, Long> getBlocksPlacedByType(UUID playerUUID) {
        PlayerStats stats = statsManager.getPlayerStats(playerUUID);
        return stats != null ? stats.getBlocksPlacedByType() : Map.of();
    }

    /**
     * 获取所有玩家的统计数据
     * @return 所有玩家统计数据的映射
     */
    public Map<UUID, PlayerStats> getAllPlayerStats() {
        return statsManager.getAllPlayerStats();
    }

    /**
     * 手动添加挖掘统计数据
     * @param playerUUID 玩家UUID
     * @param blockType 方块类型
     * @param amount 数量
     */
    public void addBlocksMined(UUID playerUUID, String blockType, int amount) {
        statsManager.addBlocksMined(playerUUID, blockType, amount);
    }

    /**
     * 手动添加放置统计数据
     * @param playerUUID 玩家UUID
     * @param blockType 方块类型
     * @param amount 数量
     */
    public void addBlocksPlaced(UUID playerUUID, String blockType, int amount) {
        statsManager.addBlocksPlaced(playerUUID, blockType, amount);
    }

    /**
     * 手动添加收入统计数据
     * @param playerUUID 玩家UUID
     * @param amount 金额
     */
    public void addMoneyEarned(UUID playerUUID, double amount) {
        statsManager.addMoneyEarned(playerUUID, amount);
    }

    /**
     * 手动添加支出统计数据
     * @param playerUUID 玩家UUID
     * @param amount 金额
     */
    public void addMoneySpent(UUID playerUUID, double amount) {
        statsManager.addMoneySpent(playerUUID, amount);
    }

    /**
     * 保存统计数据到文件
     */
    public void saveStats() {
        statsManager.saveStats();
    }

    /**
     * 从文件加载统计数据
     */
    public void loadStats() {
        statsManager.loadStats();
    }
}