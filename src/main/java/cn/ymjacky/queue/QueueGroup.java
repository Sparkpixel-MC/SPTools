package cn.ymjacky.queue;

import cn.ymjacky.SPToolsPlugin;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import cn.ymjacky.config.ConfigurationManager;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class QueueGroup {
    private final String id;
    private final GameQueue queue;
    private final List<QueuePlayer> players;
    private final Map<UUID, Boolean> confirmations;
    private int countdownSeconds;

    public QueueGroup(GameQueue queue, List<QueuePlayer> players) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.queue = queue;
        this.players = new ArrayList<>(players);
        this.confirmations = new ConcurrentHashMap<>();
        this.countdownSeconds = queue.getConfig().getCountdownTime();
        for (QueuePlayer player : players) {
            confirmations.put(player.getPlayer().getUniqueId(), false);
        }
    }

    public void notifyReady() {

        SPToolsPlugin plugin = SPToolsPlugin.getInstance();
        ConfigurationManager configManager = plugin.getConfigManager();

        String readyMessage = configManager.getMessage("queue.group.ready");
        String confirmPrompt = configManager.getMessage("queue.group.confirm-prompt");

        for (QueuePlayer queuePlayer : players) {
            if (queuePlayer.isOnline()) {
                queuePlayer.getPlayer().sendMessage(readyMessage);
                queuePlayer.getPlayer().sendMessage(confirmPrompt);

                queuePlayer.getPlayer().playSound(
                        queuePlayer.getPlayer().getLocation(),
                        Sound.BLOCK_NOTE_BLOCK_PLING,
                        SoundCategory.PLAYERS,
                        1.0f,
                        1.0f
                );
            }
        }
    }

    public boolean confirmPlayer(QueuePlayer queuePlayer) {
        if (confirmations.containsKey(queuePlayer.getPlayer().getUniqueId())) {
            confirmations.put(queuePlayer.getPlayer().getUniqueId(), true);
            return true;
        }
        return false;
    }

    public boolean allConfirmed() {
        return !confirmations.containsValue(false);
    }

    public void updateCountdown() {
        if (countdownSeconds <= 0) {
            teleportPlayers();
            return;
        }

        SPToolsPlugin plugin = SPToolsPlugin.getInstance();
        ConfigurationManager configManager = plugin.getConfigManager();

        String countdownMessage = configManager.getMessage("queue.group.countdown", "seconds", countdownSeconds);
        broadcastMessage(countdownMessage);

        for (QueuePlayer queuePlayer : players) {
            if (queuePlayer.isOnline()) {
                queuePlayer.getPlayer().playSound(
                        queuePlayer.getPlayer().getLocation(),
                        countdownSeconds <= 3 ? Sound.BLOCK_NOTE_BLOCK_HAT : Sound.BLOCK_NOTE_BLOCK_PLING,
                        SoundCategory.PLAYERS,
                        0.5f,
                        1.0f
                );
            }
        }

        countdownSeconds--;
    }

    private void teleportPlayers() {
        SPToolsPlugin plugin = SPToolsPlugin.getInstance();
        ConfigurationManager configManager = plugin.getConfigManager();

        String teleportingMessage = configManager.getMessage("queue.group.teleporting");
        broadcastMessage(teleportingMessage);

        String gameCommand = queue.getConfig().getGameCommand();

        for (QueuePlayer queuePlayer : players) {
            if (queuePlayer.isOnline()) {
                queuePlayer.getPlayer().getScheduler().run(plugin, scheduledTask -> plugin.getServer().dispatchCommand(queuePlayer.getPlayer(), gameCommand), null);
            }
        }
    }

    public void cancel() {
        SPToolsPlugin plugin = SPToolsPlugin.getInstance();
        ConfigurationManager configManager = plugin.getConfigManager();

        String cancelledMessage = configManager.getMessage("queue.group.cancelled");
        broadcastMessage(cancelledMessage);

        for (QueuePlayer queuePlayer : players) {
            if (queuePlayer.isOnline()) {
                queuePlayer.getPlayer().playSound(
                        queuePlayer.getPlayer().getLocation(),
                        Sound.ENTITY_VILLAGER_NO,
                        SoundCategory.PLAYERS,
                        1.0f,
                        1.0f
                );
            }
        }
    }

    public void timeout() {
        SPToolsPlugin plugin = SPToolsPlugin.getInstance();
        ConfigurationManager configManager = plugin.getConfigManager();

        String timeoutMessage = configManager.getMessage("queue.group.timeout");
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
                queuePlayer.getPlayer().sendMessage(message);
            }
        }
    }

    // Getters
    public String getId() { return id; }
    public GameQueue getQueue() { return queue; }
    public int getConfirmationTime() { return queue.getConfig().getConfirmationTime(); }
}