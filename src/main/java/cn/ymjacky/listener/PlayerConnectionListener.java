package cn.ymjacky.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import cn.ymjacky.queue.QueueManager;

public class PlayerConnectionListener implements Listener {

    private final QueueManager queueManager;

    public PlayerConnectionListener(QueueManager queueManager) {
        this.queueManager = queueManager;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        queueManager.leaveQueue(event.getPlayer());
    }
}