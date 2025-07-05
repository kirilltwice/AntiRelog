package dev.twice.antirelog.scoreboard;

import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.scoreboard.Scoreboard;
import me.neznamy.tab.api.scoreboard.ScoreboardManager;
import org.bukkit.entity.Player;
import java.util.List;

public class TABProvider implements ScoreboardProvider {

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

        TabAPI tabAPI = TabAPI.getInstance();
        ScoreboardManager scoreboardManager = tabAPI.getScoreboardManager();

        if (scoreboardManager == null) {
            return;
        }

        Scoreboard sb = scoreboardManager.createScoreboard("antirelog", title, lines);
        scoreboardManager.showScoreboard(tabAPI.getPlayer(player.getUniqueId()), sb);
    }

    @Override
    public void resetScoreboard(Player player) {
        if (!isTabAvailable()) {
            return;
        }

        TabAPI tabAPI = TabAPI.getInstance();
        ScoreboardManager scoreboardManager = tabAPI.getScoreboardManager();

        if (scoreboardManager == null) {
            return;
        }

        scoreboardManager.resetScoreboard(tabAPI.getPlayer(player.getUniqueId()));
    }
}