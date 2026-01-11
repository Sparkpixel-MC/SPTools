package cn.ymjacky.utils;

import cn.ymjacky.SPToolsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;

public class PlayerMessageUtil {

    private PlayerMessageUtil() {}

    public static boolean isFolia() {
        return true;
    }

    private enum GroupStyle {
        SVIP(
                new ColorGroup(TextColor.color(0xFFD700), TextColor.color(0xFF6B6B), TextColor.color(0xFFA500)),
                new ColorGroup(TextColor.color(0x9B30FF), TextColor.color(0xFFD700), TextColor.color(0x00CED1)),
                "每日一言：",
                new SoundEffect[]{
                        new SoundEffect(org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 0.9f),
                        new SoundEffect(org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.1f)
                },
                new ParticleConfig[]{
                        new ParticleConfig(Particle.DRAGON_BREATH, 100, 2.2, Color.PURPLE),
                        new ParticleConfig(Particle.FIREWORK, 150, 2.8, null),
                }
        ),

        VIP(
                new ColorGroup(TextColor.color(0x20B2AA), TextColor.color(0x4169E1), TextColor.color(0x9370DB)),
                new ColorGroup(TextColor.color(0x4682B4), TextColor.color(0x32CD32), TextColor.color(0x40E0D0)),
                "每日一言：",
                new SoundEffect[]{
                        new SoundEffect(org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.0f),
                        new SoundEffect(org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 1.2f)
                },
                new ParticleConfig[]{
                        new ParticleConfig(Particle.WITCH, 80, 1.8, Color.TEAL),
                        new ParticleConfig(Particle.NOTE, 40, 1.6, null),
                        new ParticleConfig(Particle.ENCHANT, 60, 2.0, Color.AQUA)
                }
        ),

        DEFAULT(
                new ColorGroup(TextColor.color(0x98FB98), TextColor.color(0x87CEEB), TextColor.color(0xDDA0DD)),
                new ColorGroup(TextColor.color(0xFFB6C1), TextColor.color(0xE0FFFF), TextColor.color(0xD8BFD8)),
                "每日一言：",
                new SoundEffect[]{
                        new SoundEffect(org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.2f)
                },
                new ParticleConfig[]{
                        new ParticleConfig(Particle.HAPPY_VILLAGER, 25, 1.4, Color.YELLOW),
                        new ParticleConfig(Particle.CLOUD, 20, 1.0, Color.WHITE)
                }
        );

        final ColorGroup messageColors;
        final ColorGroup blessingColors;
        final String blessingPrefix;
        final SoundEffect[] sounds;
        final ParticleConfig[] particles;

        GroupStyle(ColorGroup messageColors, ColorGroup blessingColors,
                   String blessingPrefix, SoundEffect[] sounds, ParticleConfig[] particles) {
            this.messageColors = messageColors;
            this.blessingColors = blessingColors;
            this.blessingPrefix = blessingPrefix;
            this.sounds = sounds;
            this.particles = particles;
        }

        static GroupStyle fromString(String group) {
            return switch (group.toLowerCase()) {
                case "svip" -> SVIP;
                case "vip" -> VIP;
                default -> DEFAULT;
            };
        }
    }

        private record ColorGroup(TextColor first, TextColor second, TextColor third) {
    }

    private record SoundEffect(Sound sound, float volume, float pitch) {
    }

    private record ParticleConfig(Particle particle, int count, double radius, Color color) {
    }
    private static CompletableFuture<Component> createJoinMessageWithBlessing(Player player, GroupStyle group) {
        String baseMessage = player.getName() + " 加入了服务器";
        return HitokotoServiceUtil.getHitokotoAsync().thenApply(hitokoto -> createTwoPartGradientMessage(baseMessage, group.messageColors,
                group.blessingPrefix + hitokoto, group.blessingColors)).exceptionally(e -> {
            String fallback = "愿此刻成为美好记忆的开端。";
            return createTwoPartGradientMessage(baseMessage, group.messageColors,
                    group.blessingPrefix + fallback, group.blessingColors);
        });
    }
    private static Component createQuitMessageWithBlessing(Player player, GroupStyle group) {
        String baseMessage = player.getName() + " 离开了服务器";
        String hitokoto = HitokotoServiceUtil.getHitokotoWithTimeout();

        return createTwoPartGradientMessage(baseMessage, group.messageColors,
                group.blessingPrefix + hitokoto, group.blessingColors);
    }
    private static Component createTwoPartGradientMessage(String firstPart, ColorGroup firstColors,
                                                          String secondPart, ColorGroup secondColors) {
        Component firstComponent = createGradientMessage(firstPart, firstColors);
        Component secondComponent = createGradientMessage(secondPart, secondColors);

        return Component.empty()
                .append(firstComponent)
                .append(Component.newline())
                .append(secondComponent);
    }

    private static Component createGradientMessage(String message, ColorGroup colors) {
        if (message == null || message.isEmpty()) return Component.empty();

        Component gradientComponent = Component.empty();
        int length = message.length();

        for (int i = 0; i < length; i++) {
            char c = message.charAt(i);
            TextColor charColor = calculateColor(colors, i, length);

            gradientComponent = gradientComponent.append(
                    Component.text(c)
                            .color(charColor)
                            .decoration(TextDecoration.ITALIC, false)
            );
        }
        return gradientComponent;
    }

    private static TextColor calculateColor(ColorGroup colors, int index, int total) {
        if (total <= 1) return colors.first;

        float progress = (float) index / (total - 1);
        int red, green, blue;

        if (progress < 0.5f) {
            float ratio = progress * 2;
            red = (int) (colors.first.red() * (1 - ratio) + colors.second.red() * ratio);
            green = (int) (colors.first.green() * (1 - ratio) + colors.second.green() * ratio);
            blue = (int) (colors.first.blue() * (1 - ratio) + colors.second.blue() * ratio);
        } else {
            float ratio = (progress - 0.5f) * 2;
            red = (int) (colors.second.red() * (1 - ratio) + colors.third.red() * ratio);
            green = (int) (colors.second.green() * (1 - ratio) + colors.third.green() * ratio);
            blue = (int) (colors.second.blue() * (1 - ratio) + colors.third.blue() * ratio);
        }
        return TextColor.color(red, green, blue);
    }

    private static void playJoinSound(Player player, GroupStyle group) {
        Location loc = player.getLocation();
        for (SoundEffect soundEffect : group.sounds) {
            player.getWorld().playSound(loc, soundEffect.sound, soundEffect.volume, soundEffect.pitch);
        }
    }
    public static void broadcastMessage(Component message) {
        if (message == null) return;
        if (isFolia()) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.getScheduler().run(JavaPlugin.getPlugin(SPToolsPlugin.class), scheduledTask -> p.sendMessage(message), null);
            }
        }
    }

    public static void handlePlayerJoin(JavaPlugin plugin, Player player) {
        String groupStr = GroupValueManagerUtil.getPlayerGroup(player);
        GroupStyle group = GroupStyle.fromString(groupStr);
        playJoinSound(player, group);
        createJoinMessageWithBlessing(player, group).thenAccept(fullMessage -> {
            if (isFolia()) {
                player.getScheduler().run(plugin, scheduledTask -> {
                    broadcastMessage(fullMessage);
                    if (group == GroupStyle.SVIP) {
                        Component personal = Component.text("✦ 愿此处的时光为您珍藏 ✦")
                                .color(TextColor.color(0xFFD700))
                                .decorate(TextDecoration.BOLD);
                        player.sendMessage(personal);
                    }
                }, null);
            }
        });
    }

    public static void handlePlayerQuit(Player player) {
        String groupStr = GroupValueManagerUtil.getPlayerGroup(player);
        GroupStyle group = GroupStyle.fromString(groupStr);
        Component quitMessage = createQuitMessageWithBlessing(player, group);
        broadcastMessage(quitMessage);
    }
}