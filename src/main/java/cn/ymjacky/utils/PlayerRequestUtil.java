package cn.ymjacky.utils;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.UUID;

public class PlayerRequestUtil {
    private final UUID playerId;
    private final String command;
    private final long startTime;
    private Location lastLocation;
    private boolean currentlyStill = false;
    private long stillStartTime = 0;

    public PlayerRequestUtil(Player player, String command) {
        this.playerId = player.getUniqueId();
        this.command = command;
        this.startTime = System.currentTimeMillis();
        this.lastLocation = player.getLocation();
    }

    public UUID getPlayerId() { return playerId; }
    public String getCommand() { return command; }
    public long getStartTime() { return startTime; }
    public Location getLastLocation() { return lastLocation; }
    public void setLastLocation(Location location) { this.lastLocation = location; }
    public boolean isCurrentlyStill() { return currentlyStill; }
    public void setCurrentlyStill(boolean still) { this.currentlyStill = still; }
    public long getStillStartTime() { return stillStartTime; }
    public void setStillStartTime(long time) { this.stillStartTime = time; }
}