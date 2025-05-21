package ru.leymooo.antirelog.util;

import lombok.experimental.UtilityClass;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.CompletableFuture;

@UtilityClass
public class Utils {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final boolean SUPPORTS_HEX = checkHexSupport();

    public String formatTimeUnit(String ed, String a, String b, String c, int n) {
        n = Math.abs(n);

        int lastTwoDigits = n % 100;
        if (lastTwoDigits > 10 && lastTwoDigits < 21) {
            return ed + c;
        }

        int lastDigit = n % 10;
        if (lastDigit == 1) {
            return ed + a;
        } else if (lastDigit >= 2 && lastDigit <= 4) {
            return ed + b;
        } else {
            return ed + c;
        }
    }

    public String color(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        String colored = org.bukkit.ChatColor.translateAlternateColorCodes('&', message);

        return SUPPORTS_HEX ? translateHexColors(colored) : colored;
    }

    public CompletableFuture<String> colorAsync(String message) {
        return CompletableFuture.supplyAsync(() -> color(message));
    }

    public String replaceTime(String message, int time) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        return message
                .replace("%time%", Integer.toString(time))
                .replace("%formated-sec%", formatTimeUnit("секунд", "у", "ы", "", time));
    }

    private String translateHexColors(String message) {
        if (!message.contains("&#")) {
            return message;
        }

        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer(message.length() + 4 * 8);

        while (matcher.find()) {
            String hexColor = "#" + matcher.group(1);
            try {
                ChatColor color = ChatColor.of(hexColor);
                matcher.appendReplacement(buffer, color.toString());
            } catch (Exception e) {
                matcher.appendReplacement(buffer, "");
            }
        }

        return matcher.appendTail(buffer).toString();
    }

    private boolean checkHexSupport() {
        try {
            String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            int majorVersion = Integer.parseInt(version.split("_")[1]);
            return majorVersion >= 16;
        } catch (Exception e) {
            return false;
        }
    }
}