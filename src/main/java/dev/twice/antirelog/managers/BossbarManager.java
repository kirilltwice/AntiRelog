package dev.twice.antirelog.managers;

import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import dev.twice.antirelog.config.Settings;
import dev.twice.antirelog.util.Utils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.WeakHashMap;

import static lombok.AccessLevel.PRIVATE;

@RequiredArgsConstructor
@FieldDefaults(level = PRIVATE, makeFinal = true)
public final class BossbarManager {

    Map<Integer, BossBar> bossBars = new ConcurrentHashMap<>();
    Map<Player, BossBar> activePlayerBars = new WeakHashMap<>();
    Settings settings;

    public void createBossBars() {
        clearBossbars();

        if (settings.getPvpTime() <= 0) return;

        String titleTemplate = settings.getMessages().getInPvpBossbar();
        if (titleTemplate == null || titleTemplate.isEmpty()) return;

        double progressIncrement = 1.0 / settings.getPvpTime();

        for (int timeRemaining = 1; timeRemaining <= settings.getPvpTime(); timeRemaining++) {
            double currentProgress = (double) timeRemaining * progressIncrement;
            String formattedTitle = Utils.color(Utils.replaceTime(titleTemplate, timeRemaining));

            BossBar bossBar = Bukkit.createBossBar(formattedTitle, BarColor.RED, BarStyle.SOLID);
            bossBar.setProgress(Math.clamp(currentProgress, 0.0, 1.0));

            bossBars.put(timeRemaining, bossBar);
        }
    }

    public void setBossBar(Player player, int timeRemaining) {
        if (player == null || !player.isOnline()) return;

        BossBar oldBar = activePlayerBars.remove(player);
        if (oldBar != null) oldBar.removePlayer(player);

        if (timeRemaining <= 0) return;

        BossBar newBar = bossBars.get(timeRemaining);
        if (newBar != null) {
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

    public boolean hasActiveBossbar(Player player) {
        return activePlayerBars.containsKey(player);
    }

    public void updateBossbar(Player player, int newTimeRemaining) {
        if (hasActiveBossbar(player)) {
            setBossBar(player, newTimeRemaining);
        }
    }
}