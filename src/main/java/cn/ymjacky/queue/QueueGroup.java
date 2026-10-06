package cn.ymjacky.queue;

import cn.ymjacky.SPToolsPlugin;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import cn.ymjacky.manager.ConfigurationManager;
import cn.ymjacky.utils.SchedulerUtil;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class QueueGroup {
    private final String id;
    private final GameQueue queue;
    private final List<QueuePlayer> players;
    private final Map<UUID, Boolean> confirmations;
    private int countdownSeconds;

    public QueueGroup(GameQueue queue, List<QueuePlayer> players) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.queue = queue;
        this.players = new CopyOnWriteArrayList<>(players);
        this.confirmations = new ConcurrentHashMap<>();
        this.countdownSeconds = queue.getConfig().getCountdownTime();
        for (QueuePlayer player : players) {
            confirmations.put(player.getPlayer().getUniqueId(), false);
        }
    }

    public void notifyReady() {
        ConfigurationManager configManager = configManager();

        String readyMessage = configManager.getMessage("queue.group.ready");
        String confirmPrompt = configManager.getMessage("queue.group.confirm-prompt");

        for (QueuePlayer queuePlayer : players) {
            if (queuePlayer.isOnline()) {
                SchedulerUtil.runAtEntity(queuePlayer.getPlayer(), () -> {
                    Player p = queuePlayer.getPlayer();
                    p.sendMessage(readyMessage);
                    p.sendMessage(confirmPrompt);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.PLAYERS, 1.0f, 1.0f);
                });
            }
        }
    }

    public boolean confirmPlayer(QueuePlayer queuePlayer) {
        return confirmations.replace(queuePlayer.getPlayer().getUniqueId(), false, true);
    }

    public boolean allConfirmed() {
        return !confirmations.containsValue(false);
    }

    public void updateCountdown() {
        if (countdownSeconds <= 0) {
            teleportPlayers();
            return;
        }

        ConfigurationManager configManager = configManager();

        String countdownMessage = configManager.getMessage("queue.group.countdown", "seconds", countdownSeconds);
        broadcastMessage(countdownMessage);

        for (QueuePlayer queuePlayer : players) {
            if (queuePlayer.isOnline()) {
                SchedulerUtil.runAtEntity(queuePlayer.getPlayer(), () -> {
                    Player p = queuePlayer.getPlayer();
                    p.playSound(
                            p.getLocation(),
                            countdownSeconds <= 3 ? Sound.BLOCK_NOTE_BLOCK_HAT : Sound.BLOCK_NOTE_BLOCK_PLING,
                            SoundCategory.PLAYERS,
                            0.5f,
                            1.0f
                    );
                });
            }
        }

        countdownSeconds--;
    }

    private void teleportPlayers() {
        ConfigurationManager configManager = configManager();

        String teleportingMessage = configManager.getMessage("queue.group.teleporting");
        broadcastMessage(teleportingMessage);

        String gameCommand = queue.getConfig().getGameCommand();
        SPToolsPlugin plugin = SPToolsPlugin.getInstance();

        for (QueuePlayer queuePlayer : players) {
            if (queuePlayer.isOnline()) {
                SchedulerUtil.runAtEntity(queuePlayer.getPlayer(),
                        () -> plugin.getServer().dispatchCommand(queuePlayer.getPlayer(), gameCommand));
            }
        }

        // 所有成员的状态（queuePlayers / 任务）随分组一起清理
        SchedulerUtil.runGlobalDelayed(() -> plugin.getQueueManager().disbandGroup(id), 1L);
    }

    public void cancel() {
        ConfigurationManager configManager = configManager();

        String cancelledMessage = configManager.getMessage("queue.group.cancelled");
        broadcastMessage(cancelledMessage);

        for (QueuePlayer queuePlayer : players) {
            if (queuePlayer.isOnline()) {
                SchedulerUtil.runAtEntity(queuePlayer.getPlayer(), () -> {
                    Player p = queuePlayer.getPlayer();
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 1.0f, 1.0f);
                });
            }
        }
    }

    public void timeout() {
        String timeoutMessage = configManager().getMessage("queue.group.timeout");
        broadcastMessage(timeoutMessage);
        cancel();
    }

    public void removePlayer(QueuePlayer queuePlayer) {
        boolean removed = players.remove(queuePlayer);
        if (removed) {
            confirmations.remove(queuePlayer.getPlayer().getUniqueId());
        }
    }

    public boolean containsPlayer(UUID playerId) {
        for (QueuePlayer queuePlayer : players) {
            if (queuePlayer.getPlayer().getUniqueId().equals(playerId)) {
                return true;
            }
        }
        return false;
    }

    public boolean isEmpty() {
        return players.isEmpty();
    }

    public void broadcastMessage(String message) {
        for (QueuePlayer queuePlayer : players) {
            if (queuePlayer.isOnline()) {
                SchedulerUtil.runAtEntity(queuePlayer.getPlayer(),
                        () -> queuePlayer.getPlayer().sendMessage(message));
            }
        }
    }

    private static ConfigurationManager configManager() {
        return SPToolsPlugin.getInstance().getConfigManager();
    }

    // Getters
    public String getId() { return id; }
    public GameQueue getQueue() { return queue; }
    public List<QueuePlayer> getPlayers() { return List.copyOf(players); }
    public int getConfirmationTime() { return queue.getConfig().getConfirmationTime(); }
}
