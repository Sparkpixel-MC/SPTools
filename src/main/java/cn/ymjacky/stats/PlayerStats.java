package cn.ymjacky.stats;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerStats {

    private final UUID playerUUID;
    private final String playerName;
    private long blocksMined;
    private long blocksPlaced;
    private long onlineTimeSeconds;
    private long totalMoneyEarned;
    private long totalMoneySpent;
    private final Map<String, Long> blocksMinedByType;
    private final Map<String, Long> blocksPlacedByType;
    private long lastJoinTime;
    private long lastUpdateTime;

    public PlayerStats(UUID playerUUID, String playerName) {
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.blocksMined = 0;
        this.blocksPlaced = 0;
        this.onlineTimeSeconds = 0;
        this.totalMoneyEarned = 0;
        this.totalMoneySpent = 0;
        this.blocksMinedByType = new HashMap<>();
        this.blocksPlacedByType = new HashMap<>();
        this.lastJoinTime = System.currentTimeMillis();
        this.lastUpdateTime = System.currentTimeMillis();
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public String getPlayerName() {
        return playerName;
    }

    public long getBlocksMined() {
        return blocksMined;
    }

    public void addBlocksMined(String blockType, long amount) {
        this.blocksMined += amount;
        this.blocksMinedByType.merge(blockType, amount, Long::sum);
    }

    public long getBlocksPlaced() {
        return blocksPlaced;
    }

    public void addBlocksPlaced(String blockType, long amount) {
        this.blocksPlaced += amount;
        this.blocksPlacedByType.merge(blockType, amount, Long::sum);
    }

    public long getOnlineTimeSeconds() {
        updateOnlineTime();
        return onlineTimeSeconds;
    }

    public void updateOnlineTime() {
        long currentTime = System.currentTimeMillis();
        long sessionTime = (currentTime - lastUpdateTime) / 1000;
        this.onlineTimeSeconds += sessionTime;
        this.lastUpdateTime = currentTime;
    }

    public long getTotalMoneyEarned() {
        return totalMoneyEarned;
    }

    public void addMoneyEarned(double amount) {
        this.totalMoneyEarned += (long) amount;
    }

    public long getTotalMoneySpent() {
        return totalMoneySpent;
    }

    public void addMoneySpent(double amount) {
        this.totalMoneySpent += (long) amount;
    }

    public Map<String, Long> getBlocksMinedByType() {
        return new HashMap<>(blocksMinedByType);
    }

    public Map<String, Long> getBlocksPlacedByType() {
        return new HashMap<>(blocksPlacedByType);
    }

    public long getLastJoinTime() {
        return lastJoinTime;
    }

    public void setLastJoinTime(long lastJoinTime) {
        this.lastJoinTime = lastJoinTime;
    }

    public void setLastUpdateTime(long lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    public void setBlocksMined(long blocksMined) {
        this.blocksMined = blocksMined;
    }

    public void setBlocksPlaced(long blocksPlaced) {
        this.blocksPlaced = blocksPlaced;
    }

    public void setOnlineTimeSeconds(long onlineTimeSeconds) {
        this.onlineTimeSeconds = onlineTimeSeconds;
    }

    public void setTotalMoneyEarned(long totalMoneyEarned) {
        this.totalMoneyEarned = totalMoneyEarned;
    }

    public void setTotalMoneySpent(long totalMoneySpent) {
        this.totalMoneySpent = totalMoneySpent;
    }

    public String getOnlineTimeFormatted() {
        updateOnlineTime();
        long totalSeconds = onlineTimeSeconds;
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (days > 0) {
            return String.format("%d天 %d小时 %d分钟 %d秒", days, hours, minutes, seconds);
        } else if (hours > 0) {
            return String.format("%d小时 %d分钟 %d秒", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%d分钟 %d秒", minutes, seconds);
        } else {
            return String.format("%d秒", seconds);
        }
    }
}