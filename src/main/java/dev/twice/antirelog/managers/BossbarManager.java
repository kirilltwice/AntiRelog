package dev.twice.antirelog.managers;

import dev.twice.antirelog.config.Settings;
import dev.twice.antirelog.util.Utils;
import lombok.RequiredArgsConstructor;
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

    public void createBossBars() {
        clearBossbars();
        if (!settings.getBossbar().isEnabled() || settings.getCombatTime() <= 0) return;

        String titleTemplate = settings.getBossbar().getTitle();
        if (titleTemplate == null || titleTemplate.isEmpty()) return;

        BarColor color = parseColor(settings.getBossbar().getColor());
        BarStyle style = parseStyle(settings.getBossbar().getStyle());
        double progressIncrement = 1.0 / settings.getCombatTime();

        for (int timeRemaining = 1; timeRemaining <= settings.getCombatTime(); timeRemaining++) {
            double currentProgress = (double) timeRemaining * progressIncrement;

            String processedTitle = Utils.replaceTime(titleTemplate, timeRemaining);
            String coloredTitle = Utils.color(processedTitle);

            BossBar bossBar = Bukkit.createBossBar(coloredTitle, color, style);
            bossBar.setProgress(Math.clamp(currentProgress, 0.0, 1.0));
            bossBars.put(timeRemaining, bossBar);
        }
    }

    public void setBossBar(Player player, int timeRemaining) {
        if (!settings.getBossbar().isEnabled() || player == null || !player.isOnline()) return;

        BossBar oldBar = activePlayerBars.remove(player);
        if (oldBar != null) oldBar.removePlayer(player);

        if (timeRemaining <= 0) return;

        BossBar newBar = bossBars.get(timeRemaining);
        if (newBar != null) {
            String titleTemplate = settings.getBossbar().getTitle();
            if (titleTemplate != null && !titleTemplate.isEmpty()) {
                String processedTitle = Utils.replaceTime(titleTemplate, timeRemaining);
                String coloredTitle = Utils.color(processedTitle);
                newBar.setTitle(coloredTitle);
            }

            newBar.addPlayer(player);
            activePlayerBars.put(player, newBar);
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
        try {
            return BarColor.valueOf(colorName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return BarColor.RED;
        }
    }

    private BarStyle parseStyle(String styleName) {
        try {
            return BarStyle.valueOf(styleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return BarStyle.SOLID;
        }
    }
}