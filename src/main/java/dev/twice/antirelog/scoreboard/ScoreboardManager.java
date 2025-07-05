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
                        System.out.println("[AntiRelog] TAB плагин не найден, скорборды отключены");
                        yield null;
                    }
                }
                default -> null;
            };
        } catch (Exception e) {
            System.out.println("[AntiRelog] Ошибка при инициализации провайдера скорборда: " + e.getMessage());
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
        List<String> lines = getLines(player, time, opponents);
        provider.setScoreboard(player, title, lines);
    }

    public void resetScoreboard(Player player) {
        if (provider == null) {
            return;
        }

        provider.resetScoreboard(player);
    }

    private List<String> getLines(Player player, String time, Set<Player> opponents) {
        List<String> result = new ArrayList<>();

        if (config == null || config.getLines() == null) {
            return result;
        }

        for (String line : config.getLines()) {
            if (line.contains("{opponents}")) {
                result.addAll(getOpponents(opponents));
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

        if (opponents == null || opponents.isEmpty()) {
            String emptyMessage = config.getEmpty();
            if (emptyMessage != null) {
                result.add(Utils.color(emptyMessage));
            }
        } else {
            String opponentTemplate = config.getOpponent();
            if (opponentTemplate != null) {
                opponents.forEach(opponent -> {
                    String opponentLine = opponentTemplate
                            .replace("{player}", opponent.getName())
                            .replace("{health}", String.format("%.1f", opponent.getHealth()))
                            .replace("{ping}", String.valueOf(opponent.getPing()));
                    result.add(Utils.color(opponentLine));
                });
            }
        }

        return result;
    }
}