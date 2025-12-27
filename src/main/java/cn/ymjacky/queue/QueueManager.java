package cn.ymjacky.queue;

import cn.ymjacky.SPMinigamesPlugin;
import cn.ymjacky.config.ConfigurationManager;
import cn.ymjacky.config.QueueConfig;
import cn.ymjacky.task.QueueScheduler;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class QueueManager {

    private final SPMinigamesPlugin plugin;
    private final ConfigurationManager configManager;
    private final Map<String, GameQueue> activeQueues;
    private final Map<UUID, QueuePlayer> queuePlayers;
    private final Map<String, QueueGroup> activeGroups;
    private final QueueScheduler scheduler;

    public QueueManager(SPMinigamesPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.activeQueues = new ConcurrentHashMap<>();
        this.queuePlayers = new ConcurrentHashMap<>();
        this.activeGroups = new ConcurrentHashMap<>();
        this.scheduler = new QueueScheduler(plugin, this);
        initializeQueues();
    }

    private void initializeQueues() {
        for (QueueConfig config : configManager.getAllQueueConfigs().values()) {
            activeQueues.put(config.getName().toLowerCase(), new GameQueue(config));
        }
        plugin.getLogger().info("已初始化 " + activeQueues.size() + " 个队列");
    }

    public void joinQueue(Player player, String queueName) {
        if (queuePlayers.containsKey(player.getUniqueId())) {
            String message = configManager.getMessage("queue.join.already-in");
            player.sendMessage(message);
            return;
        }

        GameQueue queue = activeQueues.get(queueName.toLowerCase());
        if (queue == null) {
            String message = configManager.getMessage("queue.join.not-found", "queue", queueName);
            player.sendMessage(message);
            return;
        }

        if (queue.isFull()) {
            String message = configManager.getMessage("queue.join.full");
            player.sendMessage(message);
            return;
        }
        QueuePlayer queuePlayer = new QueuePlayer(player);
        boolean success = queue.addPlayer(queuePlayer);
        if (success) {
            queuePlayers.put(player.getUniqueId(), queuePlayer);
            String message = configManager.getMessage("queue.join.success",
                    "queue", queue.getName(),
                    "current", queue.getPlayerCount(),
                    "max", queue.getMaxPlayers());
            player.sendMessage(message);
            if (queue.isFull()) {
                processFullQueue(queue);
            }

        }

    }

    public void leaveQueue(Player player) {
        QueuePlayer queuePlayer = queuePlayers.remove(player.getUniqueId());
        if (queuePlayer == null) {
            String message = configManager.getMessage("queue.leave.not-in");
            player.sendMessage(message);
            return;
        }
        queuePlayer.getQueue().removePlayer(queuePlayer);
        QueueGroup group = findPlayerGroup(player);
        if (group != null) {
            group.removePlayer(queuePlayer);
            if (group.isEmpty()) {
                activeGroups.remove(group.getId());
                scheduler.cancelGroupTasks(group.getId());
            }
        }

        String message = configManager.getMessage("queue.leave.success");
        player.sendMessage(message);
    }

    public void confirmParticipation(Player player) {
        QueueGroup group = findPlayerGroup(player);
        if (group == null) {
            player.sendMessage("§c您没有待确认的队列");
            return;
        }

        QueuePlayer queuePlayer = queuePlayers.get(player.getUniqueId());
        if (queuePlayer == null) {
            player.sendMessage("§c您不在任何队列中");
            return;
        }

        boolean confirmed = group.confirmPlayer(queuePlayer);
        if (confirmed) {
            String message = configManager.getMessage("queue.group.confirm-success");
            player.sendMessage(message);
            if (group.allConfirmed()) {
                String allConfirmedMsg = configManager.getMessage("queue.group.confirm-all");
                group.broadcastMessage(allConfirmedMsg);
                scheduler.startCountdown(group);
            }
        }

    }

    private void processFullQueue(GameQueue queue) {
        List<QueuePlayer> players = queue.getPlayers();
        QueueGroup group = new QueueGroup(queue, players);
        queue.clear();
        for (QueuePlayer player : players) {
            queuePlayers.remove(player.getPlayer().getUniqueId());
        }
        group.notifyReady();
        scheduler.scheduleGroup(group);
        activeGroups.put(group.getId(), group);
    }

    private QueueGroup findPlayerGroup(Player player) {
        for (QueueGroup group : activeGroups.values()) {
            if (group.containsPlayer(player.getUniqueId())) {
                return group;
            }
        }
        return null;
    }

    public void shutdown() {
        scheduler.shutdown();
        for (QueuePlayer queuePlayer : queuePlayers.values()) {
            if (queuePlayer.isOnline()) {
                queuePlayer.getPlayer().sendMessage("§cSPMinigames 插件正在关闭，您的队列已取消");
            }
        }

        activeQueues.clear();
        queuePlayers.clear();
        activeGroups.clear();
    }

    public void removeGroup(String groupId) {
        activeGroups.remove(groupId);
    }

    public QueueGroup getGroup(String groupId) {
        return activeGroups.get(groupId);
    }
}
