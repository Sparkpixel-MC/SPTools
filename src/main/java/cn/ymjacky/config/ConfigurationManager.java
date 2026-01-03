package cn.ymjacky.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import cn.ymjacky.SPToolsPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ConfigurationManager {

    private final SPToolsPlugin plugin;
    private final Map<String, QueueConfig> queueConfigs;
    private final Map<String, String> messages;

    public ConfigurationManager(SPToolsPlugin plugin) {
        this.plugin = plugin;
        this.queueConfigs = new HashMap<>();
        this.messages = new HashMap<>();

        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        loadQueueConfigs();
        loadMessages();
    }

    private void loadQueueConfigs() {
        queueConfigs.clear();
        FileConfiguration config = plugin.getConfig();

        ConfigurationSection queuesSection = config.getConfigurationSection("queues");
        if (queuesSection == null) {
            plugin.getLogger().warning("未找到队列配置，将使用默认配置");
            createDefaultConfig();
            config = plugin.getConfig();
            queuesSection = config.getConfigurationSection("queues");
        }

        if (queuesSection != null) {
            for (String queueName : queuesSection.getKeys(false)) {
                String path = "queues." + queueName;

                QueueConfig queueConfig = new QueueConfig(
                        queueName,
                        config.getInt(path + ".max-players", 12),
                        config.getInt(path + ".min-players", 2),
                        config.getString(path + ".game-command", "bw join " + queueName),
                        config.getInt(path + ".confirmation-time", 30),
                        config.getInt(path + ".countdown-time", 10),
                        config.getInt(path + ".buffer-time", 20),
                        config.getBoolean(path + ".require-confirmation", true)
                );

                queueConfigs.put(queueName.toLowerCase(), queueConfig);
                plugin.getLogger().info("已加载队列配置: " + queueName + " (最大玩家: " + queueConfig.getMaxPlayers() + ")");
            }
        }
    }

    private void loadMessages() {
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection messagesSection = config.getConfigurationSection("messages");

        if (messagesSection != null) {
            for (String key : messagesSection.getKeys(true)) {
                messages.put(key, messagesSection.getString(key));
            }
        }
    }

    private void createDefaultConfig() {
        FileConfiguration config = plugin.getConfig();
        Map<String, Object> defaultQueue = new HashMap<>();
        defaultQueue.put("max-players", 12);
        defaultQueue.put("min-players", 2);
        defaultQueue.put("game-command", "bw join 111");
        defaultQueue.put("confirmation-time", 30);
        defaultQueue.put("countdown-time", 10);
        defaultQueue.put("buffer-time", 20);
        defaultQueue.put("require-confirmation", true);
        defaultQueue.put("whitelist-modes", Arrays.asList("survival", "creative"));
        defaultQueue.put("blacklist-modes", List.of("spectator"));

        config.set("queues.111", defaultQueue);
        final var defaultMessages = getStringStringMap();

        for (Map.Entry<String, String> entry : defaultMessages.entrySet()) {
            config.set("messages." + entry.getKey(), entry.getValue());
        }

        plugin.saveConfig();
    }

    private static @NotNull Map<String, String> getStringStringMap() {
        Map<String, String> defaultMessages = new HashMap<>();
        defaultMessages.put("queue.join.success", "&a您已加入 &e{queue}&a 队列! 当前排队: &6{current}&a/&6{max}");
        defaultMessages.put("queue.join.already-in", "&c您已经在队列中!");
        defaultMessages.put("queue.join.full", "&c队列已满!");
        defaultMessages.put("queue.join.not-found", "&c队列 &e{queue}&c 不存在!");
        defaultMessages.put("queue.leave.success", "&a您已离开队列");
        defaultMessages.put("queue.leave.not-in", "&c您不在任何队列中");
        defaultMessages.put("queue.group.ready", "&6&l[SPTools] &e您的小游戏队列已准备就绪!");
        defaultMessages.put("queue.group.confirm-prompt", "&e请使用 &a/ready &e确认参与 (&730秒后自动取消&e)");
        defaultMessages.put("queue.group.confirm-success", "&a您已确认参与!");
        defaultMessages.put("queue.group.confirm-all", "&a所有玩家已确认! 开始倒计时...");
        defaultMessages.put("queue.group.countdown", "&6传送倒计时: &c{seconds} &6秒");
        defaultMessages.put("queue.group.cancelled", "&c队列已取消");
        defaultMessages.put("queue.group.timeout", "&c确认超时，队列已取消");
        defaultMessages.put("queue.group.teleporting", "&a正在传送至游戏...");
        return defaultMessages;
    }

    public QueueConfig getQueueConfig(String queueName) {
        return queueConfigs.get(queueName.toLowerCase());
    }

    public Map<String, QueueConfig> getAllQueueConfigs() {
        return new HashMap<>(queueConfigs);
    }

    public String getMessage(String key, Object... replacements) {
        String message = messages.getOrDefault(key, "&c消息未配置: " + key);

        if (replacements.length % 2 != 0) {
            return message;
        }

        for (int i = 0; i < replacements.length; i += 2) {
            String placeholder = "{" + replacements[i] + "}";
            String replacement = String.valueOf(replacements[i + 1]);
            message = message.replace(placeholder, replacement);
        }

        return message.replace('&', '§');
    }
}