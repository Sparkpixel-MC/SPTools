package cn.ymjacky.task;

import cn.ymjacky.manager.StillnessManager;
import cn.ymjacky.utils.PlayerRequestUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.Map;
import java.util.UUID;

public class StillnessCheckTask extends BukkitRunnable {
    private final StillnessManager manager;

    public StillnessCheckTask(StillnessManager manager) {
        this.manager = manager;
    }

    @Override
    public void run() {
        long currentTime = System.currentTimeMillis();
        Map<UUID, PlayerRequestUtil> requests = manager.getRequestsMap();

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            PlayerRequestUtil request = requests.get(playerId);
            if (request == null) continue;

            // 超时检查（30秒）
            if (currentTime - request.getStartTime() > 30000) {
                player.sendMessage(ChatColor.RED + "请求超时！未执行命令: " + request.getCommand());
                requests.remove(playerId);
                continue;
            }

            Location currentLoc = player.getLocation();
            double distance = currentLoc.distance(request.getLastLocation());

            if (distance < 0.1) { // 几乎没移动
                if (!request.isCurrentlyStill()) {
                    request.setStillStartTime(currentTime);
                    request.setCurrentlyStill(true);
                }

                // 静止达到5秒
                if (currentTime - request.getStillStartTime() >= 5000) {
                    String command = request.getCommand();
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                    player.sendMessage(ChatColor.GREEN + "成功静止5秒！执行命令: " + command);
                    requests.remove(playerId);
                }
            } else { // 移动了
                request.setCurrentlyStill(false);
                request.setLastLocation(currentLoc);
            }
        }
    }
}