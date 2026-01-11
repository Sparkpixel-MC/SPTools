package cn.ymjacky.utils;

import org.bukkit.plugin.java.JavaPlugin;

public class ChatSessionBlockerUtil {

    private static boolean enabled = false;

    private ChatSessionBlockerUtil() {
    }

    public static void enable(JavaPlugin plugin) {
        // 检查 ProtocolLib 是否可用
        if (plugin.getServer().getPluginManager().getPlugin("ProtocolLib") == null) {
            plugin.getLogger().warning("ProtocolLib not found, ChatSessionBlocker will be disabled");
            plugin.getLogger().warning("This is not a critical error, the plugin will continue to function");
            return;
        }

        try {
            // 使用反射来避免编译时依赖
            Class<?> protocolLibraryClass = Class.forName("com.comphenix.protocol.ProtocolLibrary");
            Object protocolManager = protocolLibraryClass.getMethod("getProtocolManager").invoke(null);

            // 获取 PacketType.Play.Client.CHAT_SESSION_UPDATE
            Class<?> packetTypeClass = Class.forName("com.comphenix.protocol.PacketType");
            Object play = packetTypeClass.getField("Play").get(null);
            Object client = ((Class<?>) play).getField("Client").get(null);
            Object chatSessionUpdate = ((Class<?>) client).getField("CHAT_SESSION_UPDATE").get(null);

            // 创建 PacketAdapter
            Class<?> packetAdapterClass = Class.forName("com.comphenix.protocol.events.PacketAdapter");
            Class<?> packetEventClass = Class.forName("com.comphenix.protocol.events.PacketEvent");

            // 创建适配器实例
            Object adapter = java.lang.reflect.Proxy.newProxyInstance(
                    packetAdapterClass.getClassLoader(),
                    new Class<?>[]{packetAdapterClass},
                    (proxy, method, args) -> {
                        if (method.getName().equals("onPacketReceiving") && args.length == 1) {
                            Object event = args[0];
                            event.getClass().getMethod("setReadOnly", boolean.class).invoke(event, false);
                            event.getClass().getMethod("setCancelled", boolean.class).invoke(event, true);
                        }
                        return null;
                    }
            );

            // 注册监听器
            protocolManager.getClass().getMethod("addPacketListener", packetAdapterClass)
                    .invoke(protocolManager, adapter);

            enabled = true;
            plugin.getLogger().info("ChatSessionBlocker enabled successfully");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to enable ChatSessionBlocker: " + e.getMessage());
            plugin.getLogger().warning("This is not a critical error, the plugin will continue to function");
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }
}