package cn.ymjacky.queue;

import org.bukkit.entity.Player;

public class QueuePlayer {
    private final Player player;
    private GameQueue queue;

    public QueuePlayer(Player player) {
        this.player = player;
        this.queue = null;
    }

    public boolean isOnline() {
        return player != null && player.isOnline();
    }
    public Player getPlayer() { return player; }
    public GameQueue getQueue() { return queue; }
    public void setQueue(GameQueue queue) { this.queue = queue; }
}