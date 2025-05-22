package ru.leymooo.antirelog.util;

import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;

@UtilityClass
public class Utils {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER =
            LegacyComponentSerializer.legacySection();

    public String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public Component colorize(String message) {
        return LEGACY_SERIALIZER.deserialize(color(message));
    }

    public String formatTimeUnit(String base, String singular, String few, String many, int number) {
        int absNumber = Math.abs(number);
        int lastTwoDigits = absNumber % 100;
        int lastDigit = absNumber % 10;

        if (lastTwoDigits >= 11 && lastTwoDigits <= 14) {
            return base + many;
        }

        return base + switch (lastDigit) {
            case 1 -> singular;
            case 2, 3, 4 -> few;
            default -> many;
        };
    }

    public String replaceTime(String message, int timeInSeconds) {
        String timeString = String.valueOf(timeInSeconds);
        String formattedSeconds = formatTimeUnit("секунд", "у", "ы", "", timeInSeconds);

        return message.replace("%time%", timeString)
                .replace("%formated-sec%", formattedSeconds);
    }

    public Component replaceTimeComponent(String message, int timeInSeconds) {
        String replacedMessage = replaceTime(message, timeInSeconds);
        return colorize(replacedMessage);
    }

    public String formatDuration(int seconds) {
        if (seconds < 60) {
            return seconds + formatTimeUnit(" секунд", "у", "ы", "", seconds);
        }

        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;

        StringBuilder result = new StringBuilder();
        result.append(minutes)
                .append(formatTimeUnit(" минут", "у", "ы", "", minutes));

        if (remainingSeconds > 0) {
            result.append(" ")
                    .append(remainingSeconds)
                    .append(formatTimeUnit(" секунд", "у", "ы", "", remainingSeconds));
        }

        return result.toString();
    }

    public Component formatDurationComponent(int seconds) {
        return colorize(formatDuration(seconds));
    }
}