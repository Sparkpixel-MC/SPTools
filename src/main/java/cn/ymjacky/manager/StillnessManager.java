package cn.ymjacky.manager;

import cn.ymjacky.utils.PlayerRequestUtil;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StillnessManager {
    private final JavaPlugin plugin;
    private final Map<UUID, PlayerRequestUtil> playerRequests = new HashMap<>();

    public StillnessManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 注册新请求，自动取消旧请求
     */
    public void registerRequest(Player player, String command) {
        cancelPreviousRequest(player);

        PlayerRequestUtil request = new PlayerRequestUtil(player, command);
        playerRequests.put(player.getUniqueId(), request);

        // 发光效果30秒（600 ticks）
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 600, 0));

        player.sendMessage(ChatColor.GREEN + "请求已登记！请在30秒内保持静止5秒以执行: " + command);
        player.sendMessage(ChatColor.YELLOW + "你获得了30秒的发光效果。");
    }

    /**
     * 取消玩家之前的请求
     */
    public void cancelPreviousRequest(Player player) {
        PlayerRequestUtil request = playerRequests.remove(player.getUniqueId());
        if (request != null) {
            player.sendMessage(ChatColor.YELLOW + "上一个请求已被取消。");
        }
    }

    /**
     * 直接移除指定玩家的请求（不发送消息）
     */
    public void cancelRequest(UUID playerId) {
        playerRequests.remove(playerId);
    }

    /**
     * 获取指定玩家的请求（可能为null）
     */
    public PlayerRequestUtil getRequest(UUID playerId) {
        return playerRequests.get(playerId);
    }

    /**
     * 返回整个请求Map（仅供定时任务只读遍历）
     */
    public Map<UUID, PlayerRequestUtil> getRequestsMap() {
        return playerRequests;
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }
}
