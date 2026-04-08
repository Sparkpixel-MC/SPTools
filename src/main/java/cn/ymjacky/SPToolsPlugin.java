package cn.ymjacky;

import cn.ymjacky.command.ConfirmCommand;
import cn.ymjacky.command.LeaveQueueCommand;
import cn.ymjacky.command.QueueCommand;
import cn.ymjacky.listener.PlayerConnectionListener;
import cn.ymjacky.listener.PlayerDeathListener;
import cn.ymjacky.listener.PlayerJoinQuitMessageListener;
import cn.ymjacky.listener.PlayerKeyboardMenuListener;
import cn.ymjacky.manager.ConfigurationManager;
import cn.ymjacky.queue.QueueManager;
import cn.ymjacky.utils.ChatSessionBlockerUtil;
import cn.ymjacky.utils.HitokotoServiceUtil;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class SPToolsPlugin extends JavaPlugin {

    private QueueManager queueManager;
    private boolean pluginEnabled;
    private static SPToolsPlugin instance;
    private ConfigurationManager configManager;

    @Override
    public void onEnable() {
        getLogger().info("=====================================");
        getLogger().info("Starting SPTools");
        getLogger().info(STR."Version: \{getPluginMeta().getVersion()}");
        getLogger().info(STR."Authors: \{getPluginMeta().getAuthors()}");
        getLogger().info("=====================================");
        saveDefaultConfig();
        queueManager = new QueueManager(this);
        HitokotoServiceUtil.init(() -> Bukkit.getOnlinePlayers().size());
        HitokotoServiceUtil.startUpdateTask(this);
        ChatSessionBlockerUtil.enable(this);
        registerCommands();
        registerListeners();
        getLogger().info("SPTools successfully enabled");
    }

    @Override
    public void onDisable() {
        queueManager.shutdown();
        getLogger().info("SPTools successfully disabled");
    }

    private void registerCommands() {
        Objects.requireNonNull(getCommand("queue")).setExecutor(new QueueCommand(queueManager));
        Objects.requireNonNull(getCommand("confirm")).setExecutor(new ConfirmCommand(queueManager));
        Objects.requireNonNull(getCommand("leavequeue")).setExecutor(new LeaveQueueCommand(queueManager));
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(queueManager), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinQuitMessageListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerKeyboardMenuListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(this), this);
    }

    public boolean isPluginEnabled() {
        return !pluginEnabled;
    }

    public static SPToolsPlugin getInstance() {
        return instance;
    }
    public ConfigurationManager getConfigManager() {
        return configManager;
    }
}