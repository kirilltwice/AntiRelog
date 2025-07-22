package dev.twice.antirelog.scoreboard;

import dev.twice.antirelog.config.Settings;
import dev.twice.antirelog.util.Utils;
import lombok.experimental.UtilityClass;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@UtilityClass
public class ScoreboardManager {
    private ScoreboardProvider provider;
    private Settings.ScoreboardConfig config;

    public void setProvider(String providerName) {
        if (providerName == null) {
            provider = null;
            return;
        }

        try {
            provider = switch (providerName.toLowerCase()) {
                case "tab" -> {
                    try {
                        Class.forName("me.neznamy.tab.api.TabAPI");
                        yield new TABProvider();
                    } catch (ClassNotFoundException | NoClassDefFoundError e) {
                        yield null;
                    }
                }
                default -> null;
            };
        } catch (Exception e) {
            provider = null;
        }
    }

    public void setConfig(Settings.ScoreboardConfig config) {
        ScoreboardManager.config = config;
    }

    public void setScoreboard(Player player, String time, Set<Player> opponents) {
        if (provider == null || config == null || !config.isEnabled()) {
            return;
        }

        String title = Utils.color(config.getTitle());
        List<String> lines = getLines(time, opponents);
        provider.setScoreboard(player, title, lines);
    }

    public void resetScoreboard(Player player) {
        if (provider == null) {
            return;
        }
        provider.resetScoreboard(player);
    }

    private List<String> getLines(String time, Set<Player> opponents) {
        List<String> result = new ArrayList<>();

        if (config == null || config.getLines() == null) {
            return result;
        }

        for (String line : config.getLines()) {
            if (line.contains("{opponents}")) {
                List<String> opponentLines = getOpponents(opponents);
                if (opponentLines.isEmpty()) {
                    String emptyMessage = config.getEmpty();
                    if (emptyMessage != null && !emptyMessage.trim().isEmpty()) {
                        result.add(Utils.color(emptyMessage));
                    }
                } else {
                    result.addAll(opponentLines);
                }
            } else {
                String processedLine = line.replace("{time}", time);
                result.add(Utils.color(processedLine));
            }
        }

        return result;
    }

    private List<String> getOpponents(Set<Player> opponents) {
        List<String> result = new ArrayList<>();

        if (config == null) {
            return result;
        }

        if (opponents != null && !opponents.isEmpty()) {
            String opponentTemplate = config.getOpponent();
            if (opponentTemplate != null) {
                for (Player opponent : opponents) {
                    if (opponent != null && opponent.isOnline()) {
                        String opponentLine = opponentTemplate
                                .replace("{player}", opponent.getName())
                                .replace("{health}", String.format("%.1f", opponent.getHealth()))
                                .replace("{ping}", String.valueOf(opponent.getPing()));
                        result.add(Utils.color(opponentLine));
                    }
                }
            }
        }

        return result;
    }
}