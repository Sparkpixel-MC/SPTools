package cn.ymjacky;

import org.bukkit.plugin.java.JavaPlugin;
import cn.ymjacky.queue.QueueManager;
import cn.ymjacky.config.ConfigurationManager;
import cn.ymjacky.command.*;
import cn.ymjacky.listener.PlayerConnectionListener;

import java.util.Objects;

public class SPMinigamesPlugin extends JavaPlugin {

    private static SPMinigamesPlugin instance;
    private ConfigurationManager configManager;
    private QueueManager queueManager;

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("=====================================");
        getLogger().info("SPMinigames 插件正在启动...");
        getLogger().info("版本: " + getPluginMeta().getVersion());
        getLogger().info("作者: " + getPluginMeta().getAuthors());
        getLogger().info("=====================================");

        saveDefaultConfig();
        configManager = new ConfigurationManager(this);
        queueManager = new QueueManager(this);
        registerCommands();
        registerListeners();
        getLogger().info("SPMinigames 插件已成功启用!");
    }

    @Override
    public void onDisable() {
        if (queueManager != null) {
            queueManager.shutdown();
        }

        getLogger().info("SPMinigames 插件已禁用");
    }

    private void registerCommands() {
        Objects.requireNonNull(getCommand("queue")).setExecutor(new QueueCommand(queueManager));
        Objects.requireNonNull(getCommand("confirm")).setExecutor(new ConfirmCommand(queueManager));
        Objects.requireNonNull(getCommand("leavequeue")).setExecutor(new LeaveQueueCommand(queueManager));
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(queueManager), this);
    }

    public static SPMinigamesPlugin getInstance() {
        return instance;
    }

    public ConfigurationManager getConfigManager() {
        return configManager;
    }
}