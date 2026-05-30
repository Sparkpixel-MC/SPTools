package cn.ymjacky;

import cn.ymjacky.command.BossBarRemoveCommand;
import cn.ymjacky.command.ConfirmCommand;
import cn.ymjacky.command.LeaveQueueCommand;
import cn.ymjacky.command.QueueCommand;
import cn.ymjacky.listener.PlayerConnectionListener;
import cn.ymjacky.listener.PlayerJoinQuitMessageListener;
import cn.ymjacky.listener.PlayerKeyboardMenuListener;
import cn.ymjacky.manager.ConfigurationManager;
import cn.ymjacky.queue.QueueManager;
import cn.ymjacky.utils.ChatSessionBlockerUtil;
import cn.ymjacky.utils.HitokotoServiceUtil;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class SPToolsPlugin extends JavaPlugin {

    private QueueManager queueManager;
    private static SPToolsPlugin instance;
    private ConfigurationManager configManager;

    @Override
    public void onEnable() {
        instance = this;
        getLogger().info("=====================================");
        getLogger().info("Starting SPTools");
        getLogger().info("Version: " + getPluginMeta().getVersion());
        getLogger().info("Authors: " + getPluginMeta().getAuthors());
        getLogger().info("=====================================");

        configManager = new ConfigurationManager(this);
        saveDefaultConfig();
        getConfig().addDefault("queue-enabled", true);
        getConfig().addDefault("hitokoto-enabled", true);
        getConfig().options().copyDefaults(true);
        saveConfig();

        boolean queueEnabled = getConfig().getBoolean("queue-enabled");
        boolean hitokotoEnabled = getConfig().getBoolean("hitokoto-enabled");

        if (queueEnabled) {
            queueManager = new QueueManager(this);
            registerQueueCommands();
            registerQueueListeners();
            getLogger().info("Queue system enabled.");
        } else {
            getLogger().info("Queue system disabled by config.");
        }

        if (hitokotoEnabled) {
            HitokotoServiceUtil.startUpdateTask(this);
            getLogger().info("Hitokoto service enabled.");
        } else {
            getLogger().info("Hitokoto service disabled by config.");
        }
        ChatSessionBlockerUtil.enable(this);
        registerCommonCommands();
        registerCommonListeners();
        getLogger().info("SPTools successfully enabled");
    }

    @Override
    public void onDisable() {
        queueManager.shutdown();
        getLogger().info("SPTools successfully disabled");
    }

    private void registerQueueCommands() {
        Objects.requireNonNull(getCommand("queue")).setExecutor(new QueueCommand(queueManager));
        Objects.requireNonNull(getCommand("confirm")).setExecutor(new ConfirmCommand(queueManager));
        Objects.requireNonNull(getCommand("leavequeue")).setExecutor(new LeaveQueueCommand(queueManager));
    }

    private void registerCommonCommands() {
        Objects.requireNonNull(getCommand("rmbbars")).setExecutor(new BossBarRemoveCommand(this));
    }

    private void registerQueueListeners() {
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(queueManager), this);
        getServer().getPluginManager().registerEvents(new PlayerKeyboardMenuListener(this), this);
    }

    private void registerCommonListeners() {
        getServer().getPluginManager().registerEvents(new PlayerJoinQuitMessageListener(this), this);
    }

    public static SPToolsPlugin getInstance() {
        return instance;
    }

    public ConfigurationManager getConfigManager() {
        return configManager;
    }
}