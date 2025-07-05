package dev.twice.antirelog.util;

import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@UtilityClass
public class Utils {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER =
            LegacyComponentSerializer.legacyAmpersand();

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public String color(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        message = translateHexCodes(message);
        return message.replace('&', '§');
    }

    public Component colorize(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }

        message = translateHexCodes(message);
        return LEGACY_SERIALIZER.deserialize(message);
    }

    private String translateHexCodes(String message) {
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuilder buffer = new StringBuilder(message.length() + 32);

        while (matcher.find()) {
            String hexColor = matcher.group(1);
            String replacement = "<#" + hexColor + ">";
            matcher.appendReplacement(buffer, replacement);
        }

        matcher.appendTail(buffer);
        return buffer.toString();
    }

    public String stripColor(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        message = message.replaceAll("&[0-9a-fk-or]", "");
        message = message.replaceAll("§[0-9a-fk-or]", "");
        message = message.replaceAll("&#[0-9A-Fa-f]{6}", "");
        message = message.replaceAll("<#[0-9A-Fa-f]{6}>", "");

        return message;
    }

    public String getPlainText(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        Component component = colorize(message);
        return LegacyComponentSerializer.legacySection().serialize(component);
    }

    public boolean hasColors(String message) {
        if (message == null || message.isEmpty()) {
            return false;
        }

        return message.contains("&") || message.contains("§");
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

    public String applyGradient(String text, String startColor, String endColor) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        if (text.length() <= 1) {
            return startColor + text;
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            String color = (i % 2 == 0) ? startColor : endColor;
            result.append(color).append(text.charAt(i));
        }

        return result.toString();
    }
}