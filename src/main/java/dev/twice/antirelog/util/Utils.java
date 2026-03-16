package dev.twice.antirelog.util;

import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@UtilityClass
public class Utils {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER =
            LegacyComponentSerializer.legacySection();
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public String translateColors(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        message = translateHexCodes(message);
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public Component translateColorsToComponent(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }

        return LEGACY_SERIALIZER.deserialize(translateColors(message));
    }

    private String translateHexCodes(String message) {
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuilder buffer = new StringBuilder(message.length() + 4 * 8);

        while (matcher.find()) {
            String group = matcher.group(1);
            matcher.appendReplacement(buffer, "§x§" + group.charAt(0) + "§" + group.charAt(1) +
                    "§" + group.charAt(2) + "§" + group.charAt(3) + "§" + group.charAt(4) + "§" + group.charAt(5));
        }

        matcher.appendTail(buffer);
        return buffer.toString();
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

    public String replaceTimePlaceholders(String message, int timeInSeconds) {
        String timeString = String.valueOf(timeInSeconds);
        String formattedSeconds = formatTimeUnit("секунд", "у", "ы", "", timeInSeconds);

        return message.replace("%time%", timeString)
                .replace("%formated-sec%", formattedSeconds);
    }

}
