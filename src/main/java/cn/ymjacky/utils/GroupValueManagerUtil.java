package cn.ymjacky.utils;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import org.bukkit.entity.Player;

public class GroupValueManagerUtil {

    public static String getPlayerGroup(Player player) {
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user == null) {
                return "default";
            }
            if (user.getNodes().stream()
                    .filter(NodeType.INHERITANCE::matches)
                    .map(NodeType.INHERITANCE::cast)
                    .anyMatch(node -> node.getGroupName().equalsIgnoreCase("svip"))) {
                return "svip";
            }
            if (user.getNodes().stream()
                    .filter(NodeType.INHERITANCE::matches)
                    .map(NodeType.INHERITANCE::cast)
                    .anyMatch(node -> node.getGroupName().equalsIgnoreCase("vip"))) {
                return "vip";
            }
            if (user.getNodes().stream()
                    .filter(NodeType.INHERITANCE::matches)
                    .map(NodeType.INHERITANCE::cast)
                    .anyMatch(node -> node.getGroupName().equalsIgnoreCase("default"))) {
                return "default";
            }
            return "default";
        } catch (Exception e) {
            return "default";
        }
    }
}