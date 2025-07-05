package dev.twice.antirelog.managers;

import dev.twice.antirelog.config.Settings;
import dev.twice.antirelog.util.Utils;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public final class BossbarManager {
    private final Map<Integer, BossBar> bossBars = new ConcurrentHashMap<>();
    private final Map<Player, BossBar> activePlayerBars = new WeakHashMap<>();
    private final Settings settings;

    private static final LegacyComponentSerializer LEGACY_SERIALIZER =
            LegacyComponentSerializer.legacySection();

    public void createBossBars() {
        clearBossbars();
        if (!settings.getBossbar().isEnabled() || settings.getCombatTime() <= 0) return;

        String titleTemplate = settings.getBossbar().getTitle();
        if (titleTemplate == null || titleTemplate.isEmpty()) return;

        BarColor color = parseColor(settings.getBossbar().getColor());
        BarStyle style = parseStyle(settings.getBossbar().getStyle());

        for (int timeRemaining = 1; timeRemaining <= settings.getCombatTime(); timeRemaining++) {
            double currentProgress = (double) timeRemaining / (double) settings.getCombatTime();

            String processedTitle = Utils.replaceTime(titleTemplate, timeRemaining);
            Component coloredComponent = Utils.colorize(processedTitle);
            String legacyTitle = LEGACY_SERIALIZER.serialize(coloredComponent);

            BossBar bossBar = Bukkit.createBossBar(legacyTitle, color, style);
            bossBar.setProgress(Math.max(0.0, Math.min(1.0, currentProgress)));
            bossBars.put(timeRemaining, bossBar);
        }
    }

    public void setBossBar(Player player, int timeRemaining) {
        if (!settings.getBossbar().isEnabled() || player == null || !player.isOnline()) return;

        BossBar oldBar = activePlayerBars.remove(player);
        if (oldBar != null) oldBar.removePlayer(player);

        if (timeRemaining <= 0) return;

        BossBar bossBar = bossBars.get(timeRemaining);
        if (bossBar == null) {
            String titleTemplate = settings.getBossbar().getTitle();
            if (titleTemplate != null && !titleTemplate.isEmpty()) {
                String processedTitle = Utils.replaceTime(titleTemplate, timeRemaining);
                Component coloredComponent = Utils.colorize(processedTitle);
                String legacyTitle = LEGACY_SERIALIZER.serialize(coloredComponent);

                BarColor color = parseColor(settings.getBossbar().getColor());
                BarStyle style = parseStyle(settings.getBossbar().getStyle());
                double currentProgress = (double) timeRemaining / (double) settings.getCombatTime();

                bossBar = Bukkit.createBossBar(legacyTitle, color, style);
                bossBar.setProgress(Math.max(0.0, Math.min(1.0, currentProgress)));
                bossBars.put(timeRemaining, bossBar);
            }
        } else {
            String titleTemplate = settings.getBossbar().getTitle();
            if (titleTemplate != null && !titleTemplate.isEmpty()) {
                String processedTitle = Utils.replaceTime(titleTemplate, timeRemaining);
                Component coloredComponent = Utils.colorize(processedTitle);
                String legacyTitle = LEGACY_SERIALIZER.serialize(coloredComponent);
                bossBar.setTitle(legacyTitle);

                double currentProgress = (double) timeRemaining / (double) settings.getCombatTime();
                bossBar.setProgress(Math.max(0.0, Math.min(1.0, currentProgress)));
            }
        }

        if (bossBar != null) {
            bossBar.addPlayer(player);
            activePlayerBars.put(player, bossBar);
        }
    }

    public void clearBossbar(Player player) {
        if (player == null) return;
        BossBar currentBar = activePlayerBars.remove(player);
        if (currentBar != null) currentBar.removePlayer(player);
    }

    public void clearBossbars() {
        activePlayerBars.values().forEach(bar -> {
            if (bar != null) bar.removeAll();
        });
        activePlayerBars.clear();
        bossBars.values().forEach(BossBar::removeAll);
        bossBars.clear();
    }

    private BarColor parseColor(String colorName) {
        if (colorName == null) return BarColor.RED;
        try {
            return BarColor.valueOf(colorName.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return BarColor.RED;
        }
    }

    private BarStyle parseStyle(String styleName) {
        if (styleName == null) return BarStyle.SOLID;
        try {
            return BarStyle.valueOf(styleName.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return BarStyle.SOLID;
        }
    }
}