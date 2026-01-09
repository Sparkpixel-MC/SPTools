package cn.ymjacky.utils;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class ChatSessionBlockerUtil {

    private ChatSessionBlockerUtil() {
    }
    public static void enable(JavaPlugin plugin) {
        ProtocolLibrary.getProtocolManager().addPacketListener(
                new PacketAdapter(PacketAdapter.params()
                        .plugin(plugin)
                        .clientSide()
                        .types(PacketType.Play.Client.CHAT_SESSION_UPDATE)) {
                    @Override
                    public void onPacketReceiving(PacketEvent event) {
                        event.setReadOnly(false);
                        event.setCancelled(true);
                    }
                }
        );
    }
}