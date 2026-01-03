package cn.ymjacky.command;

import cn.ymjacky.SPToolsPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import cn.ymjacky.config.ConfigurationManager;
import cn.ymjacky.queue.QueueManager;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class QueueCommand implements CommandExecutor, TabCompleter {

    private final QueueManager queueManager;
    private final ConfigurationManager configManager;

    public QueueCommand(QueueManager queueManager) {
        this.queueManager = queueManager;
        this.configManager = SPToolsPlugin.getInstance().getConfigManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以使用此命令!");
            return true;
        }

        if (args.length < 1) {
            sendUsage(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "join":
                if (args.length < 2) {
                    player.sendMessage("§c用法: /queue join <队列名>");
                    player.sendMessage("§7可用队列: " + getAvailableQueuesAsString());
                    return true;
                }
                queueManager.joinQueue(player, args[1]);
                break;

            case "leave":
                queueManager.leaveQueue(player);
                break;

            case "list":
                player.sendMessage("§6=== 可用队列 ===");
                configManager.getAllQueueConfigs().forEach((name, config) -> player.sendMessage("§e" + name + " §7- " + config.getMaxPlayers() + "人队列 (§a" +
                        config.getMinPlayers() + "§7-§c" + config.getMaxPlayers() + "§7)"));
                break;

            case "info":
                if (args.length < 2) {
                    player.sendMessage("§c用法: /queue info <队列名>");
                    player.sendMessage("§7可用队列: " + getAvailableQueuesAsString());
                    return true;
                }
                String queueName = args[1];
                var config = configManager.getQueueConfig(queueName);
                if (config != null) {
                    player.sendMessage("§6=== 队列信息: " + queueName + " ===");
                    player.sendMessage("§e最大玩家数: §7" + config.getMaxPlayers());
                    player.sendMessage("§e最小玩家数: §7" + config.getMinPlayers());
                    player.sendMessage("§e游戏命令: §7" + config.getGameCommand());
                    player.sendMessage("§e确认时间: §7" + config.getConfirmationTime() + "秒");
                    player.sendMessage("§e倒计时: §7" + config.getCountdownTime() + "秒");
                    player.sendMessage("§e缓冲时间: §7" + config.getBufferTime()/20 + "秒");
                    player.sendMessage("§e需要确认: §7" + (config.requiresConfirmation() ? "是" : "否"));
                } else {
                    player.sendMessage("§c队列 '" + queueName + "' 不存在!");
                    player.sendMessage("§7可用队列: " + getAvailableQueuesAsString());
                }
                break;

            default:
                sendUsage(player);
                break;
        }

        return true;
    }

    private String getAvailableQueuesAsString() {
        Set<String> queueNames = configManager.getAllQueueConfigs().keySet();
        return String.join("§7, §e", queueNames);
    }

    private void sendUsage(Player player) {
        player.sendMessage("§6=== SPTools 队列系统 ===");
        player.sendMessage("§e/queue join <队列名> §7- 加入队列");
        player.sendMessage("§e/queue leave §7- 离开当前队列");
        player.sendMessage("§e/queue list §7- 查看可用队列");
        player.sendMessage("§e/queue info <队列名> §7- 查看队列信息");
        player.sendMessage("§e/ready §7- 确认参与");
        player.sendMessage("§e/leavequeue §7- 离开队列 (快捷命令)");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String[] subCommands = {"join", "leave", "list", "info"};
            for (String subCmd : subCommands) {
                if (subCmd.startsWith(args[0].toLowerCase())) {
                    completions.add(subCmd);
                }
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("join") || args[0].equalsIgnoreCase("info")) {
                for (String queueName : configManager.getAllQueueConfigs().keySet()) {
                    if (queueName.toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(queueName);
                    }
                }
            }
        }

        if (completions.isEmpty()) {
            return null;
        }

        Collections.sort(completions);
        return completions;
    }
}