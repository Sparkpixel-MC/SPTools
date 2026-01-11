package cn.ymjacky;

import cn.ymjacky.command.ConfirmCommand;
import cn.ymjacky.command.LeaveQueueCommand;
import cn.ymjacky.command.QueueCommand;
import cn.ymjacky.config.ConfigurationManager;
import cn.ymjacky.insurance.commands.InsuranceCommand;
import cn.ymjacky.insurance.commands.InsuranceTabCompleter;
import cn.ymjacky.insurance.config.ConfigManager;
import cn.ymjacky.insurance.listeners.DeathListener;
import cn.ymjacky.insurance.manager.BackupManager;
import cn.ymjacky.insurance.manager.EconomyManager;
import cn.ymjacky.insurance.manager.InsuranceManager;
import cn.ymjacky.listener.PlayerConnectionListener;
import cn.ymjacky.listener.PlayerJoinQuitMessageListener;
import cn.ymjacky.queue.QueueManager;
import cn.ymjacky.stats.StatsManager;
import cn.ymjacky.stats.api.StatsAPI;
import cn.ymjacky.stats.command.StatsCommand;
import cn.ymjacky.stats.listener.EconomyStatsListener;
import cn.ymjacky.stats.listener.StatsListener;
import cn.ymjacky.transaction.TransactionMonitor;
import cn.ymjacky.transaction.TransactionUploadManager;
import cn.ymjacky.transaction.listener.TransactionListener;
import cn.ymjacky.utils.ChatSessionBlockerUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class SPToolsPlugin extends JavaPlugin {

    private static SPToolsPlugin instance;
    private ConfigurationManager configManager;
    private QueueManager queueManager;

    private Economy economy;
    private ConfigManager insuranceConfigManager;
    private InsuranceManager insuranceManager;
    private BackupManager backupManager;
    private EconomyManager economyManager;
    private boolean insuranceEnabled;

    private StatsManager statsManager;
    private StatsAPI statsAPI;

    private TransactionUploadManager transactionUploadManager;
    private TransactionListener transactionListener;
    private TransactionMonitor transactionMonitor;

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("=====================================");
        getLogger().info("Starting SPTools");
        getLogger().info("Version: " + getPluginMeta().getVersion());
        getLogger().info("Authors: " + getPluginMeta().getAuthors());
        getLogger().info("=====================================");

        try {
            saveDefaultConfig();
            configManager = new ConfigurationManager(this);
            queueManager = new QueueManager(this);

            initializeInsurance();
            initializeStats();
            initializeTransactionSystem();
            registerCommands();
            registerListeners();
            ChatSessionBlockerUtil.enable(this);
            getLogger().info("SPTools successfully enabled");
        } catch (Exception e) {
            getLogger().severe("Failed to enable SPTools!");
            getLogger().severe("Error: " + e.getMessage());
            e.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (queueManager != null) {
            queueManager.shutdown();
        }
        if (statsManager != null) {
            statsManager.shutdown();
        }
        if (transactionUploadManager != null) {
            transactionUploadManager.shutdown();
        }
        if (transactionMonitor != null) {
            transactionMonitor.shutdown();
        }
        getLogger().info("SPTools 插件已禁用");
    }

    private void initializeInsurance() {
        if (!setupEconomy()) {
            getLogger().warning("Vault not found! Economy features will be disabled.");
        }

        insuranceConfigManager = new ConfigManager(this);
        economyManager = new EconomyManager(this, economy);
        insuranceManager = new InsuranceManager(this);
        backupManager = new BackupManager(this);

        boolean keepInventory = false;
        if (!Bukkit.getWorlds().isEmpty()) {
            keepInventory = Bukkit.getWorlds().get(0).getGameRuleValue(org.bukkit.GameRule.KEEP_INVENTORY);
        }
        insuranceEnabled = !keepInventory;

        getLogger().info("Insurance feature is " + (insuranceEnabled ? "enabled" : "disabled"));
    }

    private void initializeStats() {
        statsManager = new StatsManager(this);
        statsAPI = new StatsAPI(statsManager);

        getServer().getPluginManager().registerEvents(new StatsListener(this, statsManager), this);

        if (economy != null) {
            getServer().getPluginManager().registerEvents(new EconomyStatsListener(this, statsManager, economy), this);
        }

        getLogger().info("Stats system initialized!");
    }

    private void initializeTransactionSystem() {
        if (!getConfig().getBoolean("transaction_upload_enabled", false)) {
            getLogger().info("交易记录上传功能未启用");
            return;
        }

        if (economy == null) {
            getLogger().warning("Vault未找到，无法启用交易记录监控功能");
            return;
        }

        transactionUploadManager = new TransactionUploadManager(this);
        transactionListener = new TransactionListener(transactionUploadManager, economy);
        transactionMonitor = new TransactionMonitor(this, transactionUploadManager, transactionListener, economy);

        getServer().getPluginManager().registerEvents(transactionListener, this);

        getLogger().info("交易记录系统已初始化");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    private void registerCommands() {
        Objects.requireNonNull(getCommand("queue")).setExecutor(new QueueCommand(queueManager));
        Objects.requireNonNull(getCommand("confirm")).setExecutor(new ConfirmCommand(queueManager));
        Objects.requireNonNull(getCommand("leavequeue")).setExecutor(new LeaveQueueCommand(queueManager));

        InsuranceCommand insuranceCommand = new InsuranceCommand(this);
        Objects.requireNonNull(getCommand("insurance")).setExecutor(insuranceCommand);
        Objects.requireNonNull(getCommand("ins")).setExecutor(insuranceCommand);
        Objects.requireNonNull(getCommand("insurance")).setTabCompleter(new InsuranceTabCompleter());
        Objects.requireNonNull(getCommand("ins")).setTabCompleter(new InsuranceTabCompleter());

        Objects.requireNonNull(getCommand("stats")).setExecutor(new StatsCommand(this, statsManager, economy));
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(queueManager), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinQuitMessageListener(this), this);
        getServer().getPluginManager().registerEvents(new DeathListener(this), this);
    }

    public static SPToolsPlugin getInstance() {
        return instance;
    }

    public ConfigurationManager getConfigManager() {
        return configManager;
    }

    public Economy getEconomy() {
        return economy;
    }

    public ConfigManager getInsuranceConfigManager() {
        return insuranceConfigManager;
    }

    public InsuranceManager getInsuranceManager() {
        return insuranceManager;
    }

    public BackupManager getBackupManager() {
        return backupManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public boolean isInsuranceEnabled() {
        return insuranceEnabled;
    }

    public void setInsuranceEnabled(boolean enabled) {
        this.insuranceEnabled = enabled;
    }

    public StatsAPI getStatsAPI() {
        return statsAPI;
    }

    public StatsManager getStatsManager() {
        return statsManager;
    }
}