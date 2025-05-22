package ru.leymooo.antirelog.manager;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import ru.leymooo.antirelog.config.Settings;
import ru.leymooo.antirelog.util.Utils;

import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
public class BossbarManager {

    private final Map<Integer, BossBar> bossBars = new HashMap<>();
    private final Settings settings;

    public void createBossBars() {
        clearBossbars();

        if (settings.getPvpTime() <= 0) {
            return;
        }

        String titleTemplate = settings.getMessages().getInPvpBossbar();
        if (titleTemplate.isEmpty()) {
            return;
        }

        double progressIncrement = 1.0 / settings.getPvpTime();
        double currentProgress = progressIncrement;

        for (int timeRemaining = 1; timeRemaining <= settings.getPvpTime(); timeRemaining++) {
            String formattedTitle = Utils.color(Utils.replaceTime(titleTemplate, timeRemaining));

            BossBar bossBar = Bukkit.createBossBar(formattedTitle, BarColor.RED, BarStyle.SOLID);
            bossBar.setProgress(Math.min(currentProgress, 1.0));

            bossBars.put(timeRemaining, bossBar);
            currentProgress += progressIncrement;
        }
    }

    public void setBossBar(Player player, int timeRemaining) {
        if (player == null || !player.isOnline() || bossBars.isEmpty()) {
            return;
        }

        bossBars.values().forEach(bar -> bar.removePlayer(player));

        BossBar targetBar = bossBars.get(timeRemaining);
        if (targetBar != null) {
            targetBar.addPlayer(player);
        }
    }

    public void clearBossbar(Player player) {
        if (player == null) {
            return;
        }
        bossBars.values().forEach(bar -> bar.removePlayer(player));
    }

    public void clearBossbars() {
        bossBars.values().forEach(BossBar::removeAll);
        bossBars.clear();
    }
}