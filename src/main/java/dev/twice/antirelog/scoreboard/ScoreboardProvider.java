package dev.twice.antirelog.scoreboard;

import org.bukkit.entity.Player;
import java.util.List;
import java.util.Set;

public interface ScoreboardProvider {
    void setScoreboard(Player player, String title, List<String> lines);
    void resetScoreboard(Player player);
}