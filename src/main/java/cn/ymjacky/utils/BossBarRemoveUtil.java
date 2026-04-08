package cn.ymjacky.utils;

import org.bukkit.Bukkit;
import org.bukkit.boss.KeyedBossBar;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class BossBarRemoveUtil {
    public static int removeAllBossBars() {
        Iterator<KeyedBossBar> iterator = Bukkit.getBossBars();
        List<KeyedBossBar> bars = new ArrayList<>();
        iterator.forEachRemaining(bars::add);

        int count = 0;
        for (KeyedBossBar bar : bars) {
            bar.removeAll();
            count++;
        }
        return count;
    }
}