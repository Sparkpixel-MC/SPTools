package cn.ymjacky.utils;

import org.bukkit.plugin.java.JavaPlugin;

public class ChatSessionBlockerUtil {

    private ChatSessionBlockerUtil() {
    }

    public static void enable(JavaPlugin plugin) {
        try {
            // 使用反射检查 ProtocolLib 是否可用
            Class<?> protocolLibraryClass = Class.forName("com.comphenix.protocol.ProtocolLibrary");
            Object protocolManager = protocolLibraryClass.getMethod("getProtocolManager").invoke(null);

            Class<?> packetTypeClass = Class.forName("com.comphenix.protocol.PacketType");
            Object packetType = packetTypeClass.getField("Play").get(null);
            Object clientPacketType = ((Class<?>) packetType).getField("Client").get(null);
            Object chatSessionUpdate = ((Class<?>) clientPacketType).getField("CHAT_SESSION_UPDATE").get(null);

            Class<?> packetAdapterClass = Class.forName("com.comphenix.protocol.events.PacketAdapter");
            Object params = packetAdapterClass.getMethod("params").invoke(null);
            Object builder = params.getClass().getMethod("plugin", JavaPlugin.class).invoke(params, plugin);
            builder = builder.getClass().getMethod("clientSide").invoke(builder);
            builder = builder.getClass().getMethod("types", packetTypeClass).invoke(builder, chatSessionUpdate);

            Class<?> packetEventClass = Class.forName("com.comphenix.protocol.events.PacketEvent");
            Object packetAdapter = java.lang.reflect.Proxy.newProxyInstance(
                    packetAdapterClass.getClassLoader(),
                    new Class<?>[]{packetAdapterClass},
                    (proxy, method, args) -> {
                        if (method.getName().equals("onPacketReceiving") && args.length == 1 && args[0].getClass().equals(packetEventClass)) {
                            Object event = args[0];
                            event.getClass().getMethod("setReadOnly", boolean.class).invoke(event, false);
                            event.getClass().getMethod("setCancelled", boolean.class).invoke(event, true);
                        }
                        return null;
                    }
            );

            protocolManager.getClass().getMethod("addPacketListener", packetAdapterClass).invoke(protocolManager, packetAdapter);

            plugin.getLogger().info("ChatSessionBlocker enabled successfully");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("ProtocolLib not found, ChatSessionBlocker will be disabled");
            plugin.getLogger().warning("This is not a critical error, the plugin will continue to function");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to enable ChatSessionBlocker: " + e.getMessage());
            plugin.getLogger().warning("This is not a critical error, the plugin will continue to function");
        }
    }
}