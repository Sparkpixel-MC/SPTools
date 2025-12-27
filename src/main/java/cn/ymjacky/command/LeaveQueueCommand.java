package cn.ymjacky.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import cn.ymjacky.queue.QueueManager;
import org.jetbrains.annotations.NotNull;

public class LeaveQueueCommand implements CommandExecutor {

    private final QueueManager queueManager;

    public LeaveQueueCommand(QueueManager queueManager) {
        this.queueManager = queueManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以使用此命令!");
            return true;
        }
        queueManager.leaveQueue(player);
        return true;
    }
}