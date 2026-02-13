package cn.ymjacky;

import cn.ymjacky.command.*;
import cn.ymjacky.manager.*;
import cn.ymjacky.listener.PlayerConnectionListener;
import cn.ymjacky.listener.PlayerDeathListener;
import cn.ymjacky.listener.PlayerJoinQuitMessageListener;
import cn.ymjacky.listener.PlayerKeyboardMenuListener;
import cn.ymjacky.queue.QueueManager;
import cn.ymjacky.task.StillnessCheckTask;
import cn.ymjacky.utils.ChatSessionBlockerUtil;
import cn.ymjacky.utils.HitokotoServiceUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class SPToolsPlugin extends JavaPlugin {

    private static SPToolsPlugin instance;
    private ConfigurationManager configManager;
    private QueueManager queueManager;

    private Economy insuranceEconomy;
    private InsuranceManager insuranceManager;
    private BackupManager backupManager;
    private EconomyManager insuranceEconomyManager;
    private StillnessManager stillnessManager;
    private boolean pluginEnabled;

    @Override
    public void onEnable() {
        instance = this;
        getLogger().info("=====================================");
        getLogger().info("Starting SPTools");
        getLogger().info(STR."Version: \{getPluginMeta().getVersion()}");
        getLogger().info(STR."Authors: \{getPluginMeta().getAuthors()}");
        getLogger().info("=====================================");
        saveDefaultConfig();                     // 保存 SPTools/config.yml
        configManager = new ConfigurationManager(this);
        queueManager = new QueueManager(this);
        HitokotoServiceUtil.init(() -> Bukkit.getOnlinePlayers().size());
        HitokotoServiceUtil.startUpdateTask(this);
        ChatSessionBlockerUtil.enable(this);
        saveResource("insurance.yml", false);

        // 设置保险专用 Vault 经济
        if (!setupInsuranceEconomy()) {
            getLogger().warning("Vault not found! Insurance economy features will be disabled.");
        }
        insuranceEconomyManager = new EconomyManager(this, insuranceEconomy);
        insuranceManager = new InsuranceManager(this);
        backupManager = new BackupManager(this);
        boolean keepInventory = Boolean.TRUE.equals(Bukkit.getWorlds().getFirst().getGameRuleValue(GameRules.KEEP_INVENTORY));
        pluginEnabled = !keepInventory;
        getLogger().info(STR."Insurance module is \{pluginEnabled ? "enabled" : "disabled"} (keepInventory=\{keepInventory})");
        stillnessManager = new StillnessManager(this);
        new StillnessCheckTask(stillnessManager).runTaskTimerAsynchronously(this, 20L, 20L);
        registerCommands();
        registerListeners();

        getLogger().info("SPTools successfully enabled (Insurance module merged)");
    }

    @Override
    public void onDisable() {
        queueManager.shutdown();
        // 保险模块无特殊关闭逻辑，保留扩展点
        getLogger().info("SPTools successfully disabled");
    }

    /**
     * 注册所有命令（原 SPTools + 保险）
     */
    private void registerCommands() {
        Objects.requireNonNull(getCommand("queue")).setExecutor(new QueueCommand(queueManager));
        Objects.requireNonNull(getCommand("confirm")).setExecutor(new ConfirmCommand(queueManager));
        Objects.requireNonNull(getCommand("leavequeue")).setExecutor(new LeaveQueueCommand(queueManager));
        InsuranceCommand insuranceCommand = new InsuranceCommand(this);
        Objects.requireNonNull(getCommand("insurance")).setExecutor(insuranceCommand);
        Objects.requireNonNull(getCommand("insurance")).setTabCompleter(insuranceCommand);
        Objects.requireNonNull(getCommand("ins")).setExecutor(insuranceCommand);
        Objects.requireNonNull(getCommand("ins")).setTabCompleter(insuranceCommand);
        Objects.requireNonNull(getCommand("waitstill")).setExecutor(new StillnessCommand(stillnessManager));
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(queueManager), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinQuitMessageListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerKeyboardMenuListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(this), this);
    }

    private boolean setupInsuranceEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        insuranceEconomy = rsp.getProvider();
        return true;
    }

    public static SPToolsPlugin getInstance() {
        return instance;
    }

    public ConfigurationManager getConfigManager() {
        return configManager;
    }

    public InsuranceManager getInsuranceManager() {
        return insuranceManager;
    }

    public BackupManager getBackupManager() {
        return backupManager;
    }

    public EconomyManager getEconomyManager() {
        return insuranceEconomyManager;
    }

    public boolean isPluginEnabled() {
        return !pluginEnabled;
    }

    public void setPluginEnabled(boolean enabled) {
        this.pluginEnabled = enabled;
    }
}