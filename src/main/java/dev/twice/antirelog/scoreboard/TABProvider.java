package dev.twice.antirelog.scoreboard;

import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.api.scoreboard.Scoreboard;
import me.neznamy.tab.api.scoreboard.ScoreboardManager;
import org.bukkit.entity.Player;
import java.util.List;

public class TABProvider implements ScoreboardProvider {

    private static final String SCOREBOARD_NAME = "antirelog";

    private boolean isTabAvailable() {
        try {
            TabAPI tabAPI = TabAPI.getInstance();
            return tabAPI != null && tabAPI.getScoreboardManager() != null;
        } catch (Exception | NoClassDefFoundError e) {
            return false;
        }
    }

    @Override
    public void setScoreboard(Player player, String title, List<String> lines) {
        if (!isTabAvailable()) {
            return;
        }

        try {
            TabAPI tabAPI = TabAPI.getInstance();
            ScoreboardManager scoreboardManager = tabAPI.getScoreboardManager();
            TabPlayer tabPlayer = tabAPI.getPlayer(player.getUniqueId());

            if (scoreboardManager == null || tabPlayer == null) {
                return;
            }

            Scoreboard scoreboard = scoreboardManager.createScoreboard(SCOREBOARD_NAME, title, lines);
            scoreboardManager.showScoreboard(tabPlayer, scoreboard);

        } catch (Exception e) {
            System.out.println("[AntiRelog] Ошибка установки скорборда: " + e.getMessage());
        }
    }

    @Override
    public void resetScoreboard(Player player) {
        if (!isTabAvailable()) {
            return;
        }

        try {
            TabAPI tabAPI = TabAPI.getInstance();
            ScoreboardManager scoreboardManager = tabAPI.getScoreboardManager();
            TabPlayer tabPlayer = tabAPI.getPlayer(player.getUniqueId());

            if (scoreboardManager == null || tabPlayer == null) {
                return;
            }

            scoreboardManager.resetScoreboard(tabPlayer);

        } catch (Exception e) {
            System.out.println("[AntiRelog] Ошибка сброса скорборда: " + e.getMessage());
        }
    }
}