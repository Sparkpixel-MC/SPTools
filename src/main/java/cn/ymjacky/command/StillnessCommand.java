package cn.ymjacky.command;

import cn.ymjacky.manager.StillnessManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class StillnessCommand implements CommandExecutor {
    private final StillnessManager manager;

    public StillnessCommand(StillnessManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("只有玩家可以使用此命令！");
            return true;
        }

        Player player = (Player) sender;

        if (cmd.getName().equalsIgnoreCase("waitstill")) {
            if (args.length == 0) {
                player.sendMessage(ChatColor.RED + "用法: /waitstill <要执行的命令>");
                return true;
            }

            // 合并所有参数为一个完整命令
            String fullCommand = String.join(" ", args);
            manager.registerRequest(player, fullCommand);
            return true;
        }
        return false;
    }
}