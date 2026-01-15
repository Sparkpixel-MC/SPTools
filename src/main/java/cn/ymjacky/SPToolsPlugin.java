package cn.ymjacky;

import cn.ymjacky.command.ConfirmCommand;
import cn.ymjacky.command.LeaveQueueCommand;
import cn.ymjacky.command.QueueCommand;
import cn.ymjacky.config.ConfigurationManager;
import cn.ymjacky.database.MySQLManager;
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
import cn.ymjacky.transaction.TransactionMonitor;
import cn.ymjacky.transaction.TransactionUploadManager;
import cn.ymjacky.transaction.listener.TransactionListener;
import cn.ymjacky.utils.ChatSessionBlockerUtil;
import net.milkbowl.vault.economy.Economy;

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
    private MySQLManager mysqlManager;
    private TransactionUploadManager transactionUploadManager;
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
        initializeInsurance();
        initializeTransactionSystem();
        setupEconomy();
        ChatSessionBlockerUtil.enable(this);
        getLogger().info("SPTools successfully enabled");
    }

    @Override
    public void onDisable() {
        queueManager.shutdown();
        mysqlManager.close();
        transactionUploadManager.shutdown();
        transactionMonitor.shutdown();
        getLogger().info("SPTools successfully disabled");
    }

    private void initializeInsurance() {
        getLogger().info("Initializing Insurance system...");
        insuranceConfigManager = new ConfigManager(this);
        economyManager = new EconomyManager(this, economy);
        insuranceManager = new InsuranceManager(this);
        backupManager = new BackupManager(this);
        insuranceEnabled = true;

    }

    private void initializeTransactionSystem() {
        if (!getConfig().getBoolean("transaction_upload_enabled", false)) {
            getLogger().info("Transaction upload disabled in config");
            return;
        }
        if (economy == null) {
            getLogger().warning("Vault not found, transaction upload disabled");
            return;
        }
        if (mysqlManager == null) {
            mysqlManager = new MySQLManager(this);
        }
        transactionUploadManager = new TransactionUploadManager(this, mysqlManager);
        TransactionListener transactionListener = new TransactionListener(transactionUploadManager, economy);
        transactionMonitor = new TransactionMonitor(this, transactionListener, economy);
        getServer().getPluginManager().registerEvents(transactionListener, this);
        if (mysqlManager != null && mysqlManager.isConnected()) {
            getLogger().info("Payment system initialized");
        } else {
            getLogger().warning("Payment system initialized but MySQL connection failed - payment will not be saved");
        }
    }

    private void setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return;
        }
        economy = rsp.getProvider();
    }

    private void registerCommands() {
        Objects.requireNonNull(getCommand("queue")).setExecutor(new QueueCommand(queueManager));
        Objects.requireNonNull(getCommand("confirm")).setExecutor(new ConfirmCommand(queueManager));
        Objects.requireNonNull(getCommand("leavequeue")).setExecutor(new LeaveQueueCommand(queueManager));
        InsuranceCommand insuranceCommand = new InsuranceCommand(this);
        Objects.requireNonNull(getCommand("insurance")).setExecutor(insuranceCommand);
        Objects.requireNonNull(getCommand("insurance")).setTabCompleter(new InsuranceTabCompleter());

    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(queueManager), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinQuitMessageListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(queueManager), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinQuitMessageListener(this), this);
        getServer().getPluginManager().registerEvents(new DeathListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerKeyboardMenuListener(this), this);
    }

    public static SPToolsPlugin getInstance() {
        return instance;
    }

    public ConfigurationManager getConfigManager() {
        return configManager;
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
        return !insuranceEnabled;
    }

    public void setInsuranceEnabled(boolean enabled) {
        this.insuranceEnabled = enabled;
    }
}