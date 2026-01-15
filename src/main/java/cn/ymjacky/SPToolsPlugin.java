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
import cn.ymjacky.listener.PlayerKeyboardMenuListener;
import cn.ymjacky.queue.QueueManager;
import cn.ymjacky.utils.ChatSessionBlockerUtil;
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

        saveDefaultConfig();
        configManager = new ConfigurationManager(this);
        queueManager = new QueueManager(this);
        registerCommands();
        registerListeners();
        ChatSessionBlockerUtil.enable(this);
        getLogger().info("SPTools successfully enabled");
        try {
            try {
                saveDefaultConfig();
                getLogger().info("Configuration loaded successfully");
            } catch (Exception e) {
                getLogger().severe("Failed to load configuration: " + e.getMessage());
                e.printStackTrace();
                throw e;
            }
            try {
                configManager = new ConfigurationManager(this);
                getLogger().info("ConfigurationManager initialized");
            } catch (Exception e) {
                getLogger().severe("Failed to initialize ConfigurationManager: " + e.getMessage());
                e.printStackTrace();
                throw e;
            }
            try {
                queueManager = new QueueManager(this);
                getLogger().info("QueueManager initialized");
            } catch (Exception e) {
                getLogger().severe("Failed to initialize QueueManager: " + e.getMessage());
                e.printStackTrace();
                throw e;
            }
            try {
                initializeInsurance();
            } catch (Exception e) {
                getLogger().severe("Failed to initialize Insurance system: " + e.getMessage());
                e.printStackTrace();
                throw e;
            }
            try {
                initializeStats();
            } catch (Exception e) {
                getLogger().severe("Failed to initialize Stats system: " + e.getMessage());
                e.printStackTrace();
                throw e;
            }
            try {
                initializeTransactionSystem();
            } catch (Exception e) {
                getLogger().severe("Failed to initialize Transaction system: " + e.getMessage());
                e.printStackTrace();
                throw e;
            }
            try {
                registerCommands();
                getLogger().info("Commands registered successfully");
            } catch (Exception e) {
                getLogger().severe("Failed to register commands: " + e.getMessage());
                e.printStackTrace();
                throw e;
            }
            try {
                registerListeners();
                getLogger().info("Listeners registered successfully");
            } catch (Exception e) {
                getLogger().severe("Failed to register listeners: " + e.getMessage());
                e.printStackTrace();
                throw e;
            }
            try {
                ChatSessionBlockerUtil.enable(this);
                getLogger().info("ChatSessionBlockerUtil enabled");
            } catch (Exception e) {
                getLogger().warning("Failed to enable ChatSessionBlockerUtil: " + e.getMessage());
                e.printStackTrace();
            }

            getLogger().info("=====================================");
            getLogger().info("SPTools successfully enabled!");
            getLogger().info("=====================================");
        } catch (Exception e) {
            getLogger().severe("=====================================");
            getLogger().severe("CRITICAL: Failed to enable SPTools!");
            getLogger().severe("Error: " + e.getMessage());
            getLogger().severe("Error Type: " + e.getClass().getName());
            getLogger().severe("=====================================");
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
        getLogger().info("SPTools successfully disabled");
    }

    private void initializeInsurance() {
        getLogger().info("Initializing Insurance system...");
        if (!setupEconomy()) {
            getLogger().warning("Vault not found! Economy features will be disabled.");
        } else {
            getLogger().info("Economy system loaded successfully");
        }
        try {
            insuranceConfigManager = new ConfigManager(this);
            getLogger().info("Insurance ConfigManager initialized");
        } catch (Exception e) {
            getLogger().severe("Failed to initialize Insurance ConfigManager: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to initialize Insurance ConfigManager", e);
        }
        try {
            economyManager = new EconomyManager(this, economy);
            getLogger().info("EconomyManager initialized");
        } catch (Exception e) {
            getLogger().severe("Failed to initialize EconomyManager: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to initialize EconomyManager", e);
        }
        try {
            insuranceManager = new InsuranceManager(this);
            getLogger().info("InsuranceManager initialized");
        } catch (Exception e) {
            getLogger().severe("Failed to initialize InsuranceManager: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to initialize InsuranceManager", e);
        }
        try {
            backupManager = new BackupManager(this);
            getLogger().info("BackupManager initialized");
        } catch (Exception e) {
            getLogger().severe("Failed to initialize BackupManager: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to initialize BackupManager", e);
        }
        boolean keepInventory = false;
        try {
            if (!Bukkit.getWorlds().isEmpty()) {
                keepInventory = Bukkit.getWorlds().get(0).getGameRuleValue(org.bukkit.GameRule.KEEP_INVENTORY);
            }
        } catch (Exception e) {
            getLogger().warning("Failed to check KEEP_INVENTORY gamerule: " + e.getMessage());
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
        getLogger().info("Registering commands...");

        try {
            Objects.requireNonNull(getCommand("queue")).setExecutor(new QueueCommand(queueManager));
            getLogger().info("Queue command registered");
        } catch (Exception e) {
            getLogger().severe("Failed to register queue command: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to register queue command", e);
        }

        try {
            Objects.requireNonNull(getCommand("confirm")).setExecutor(new ConfirmCommand(queueManager));
            getLogger().info("Confirm command registered");
        } catch (Exception e) {
            getLogger().severe("Failed to register confirm command: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to register confirm command", e);
        }

        try {
            Objects.requireNonNull(getCommand("leavequeue")).setExecutor(new LeaveQueueCommand(queueManager));
            getLogger().info("LeaveQueue command registered");
        } catch (Exception e) {
            getLogger().severe("Failed to register leavequeue command: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to register leavequeue command", e);
        }

        try {
            InsuranceCommand insuranceCommand = new InsuranceCommand(this);
            Objects.requireNonNull(getCommand("insurance")).setExecutor(insuranceCommand);
            Objects.requireNonNull(getCommand("insurance")).setTabCompleter(new InsuranceTabCompleter());
            getLogger().info("Insurance command registered");
        } catch (Exception e) {
            getLogger().severe("Failed to register insurance command: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to register insurance command", e);
        }

        try {
            Objects.requireNonNull(getCommand("stats")).setExecutor(new StatsCommand(this, statsManager, economy));
            getLogger().info("Stats command registered");
        } catch (Exception e) {
            getLogger().severe("Failed to register stats command: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to register stats command", e);
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(queueManager), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinQuitMessageListener(this), this);
        getLogger().info("Registering listeners...");

        try {
            getServer().getPluginManager().registerEvents(new PlayerConnectionListener(queueManager), this);
            getLogger().info("PlayerConnectionListener registered");
        } catch (Exception e) {
            getLogger().severe("Failed to register PlayerConnectionListener: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to register PlayerConnectionListener", e);
        }

        try {
            getServer().getPluginManager().registerEvents(new PlayerJoinQuitMessageListener(this), this);
            getLogger().info("PlayerJoinQuitMessageListener registered");
        } catch (Exception e) {
            getLogger().severe("Failed to register PlayerJoinQuitMessageListener: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to register PlayerJoinQuitMessageListener", e);
        }

        try {
            getServer().getPluginManager().registerEvents(new DeathListener(this), this);
            getLogger().info("DeathListener registered");
        } catch (Exception e) {
            getLogger().severe("Failed to register DeathListener: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to register DeathListener", e);
        }
        getServer().getPluginManager().registerEvents(new PlayerKeyboardMenuListener(this), this);
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