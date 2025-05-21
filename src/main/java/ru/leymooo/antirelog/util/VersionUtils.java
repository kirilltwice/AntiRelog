package ru.leymooo.antirelog.util;

import lombok.Getter;
import lombok.experimental.UtilityClass;
import org.bukkit.Bukkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@UtilityClass
public class VersionUtils {

    private static final String DEFAULT_VERSION = "1.21.4";
    private static final Pattern VERSION_PATTERN = Pattern.compile("\\(MC: (\\d)\\.(\\d+)\\.?(\\d+?)?\\)");

    @Getter
    private static int majorVersion;

    @Getter
    private static int minorVersion = 0;

    private static boolean minorVersionResolved = false;
    private static Logger logger;

    static {
        try {
            logger = LoggerFactory.getLogger(VersionUtils.class);
        } catch (Throwable t) {
            System.err.println("Не удалось инициализировать SLF4J логгер для VersionUtils.");
            t.printStackTrace();
        }
        detectServerVersion();
    }

    public boolean isVersion(int major) {
        return majorVersion >= major;
    }

    public boolean isVersion(int major, int minor) {
        if (majorVersion > major) {
            return true;
        }
        return majorVersion == major && (!minorVersionResolved || minorVersion >= minor);
    }

    private void detectServerVersion() {
        if (logger == null) {
            System.err.println("Логгер не был инициализирован в VersionUtils. Попытка определить версию...");
        }

        try {

            if (tryParseVersionFromBukkitVersion()) {
                logDetectedVersion();
                return;
            }

            if (tryParseVersionFromBukkitVersionString()) {
                logDetectedVersion();
                return;
            }

            if (tryParseVersionFromPackageName()) {
                logDetectedVersion();
                return;
            }

            if (logger != null) {
                logger.warn("Не удалось определить версию всеми способами. Устанавливаем {}", DEFAULT_VERSION);
            } else {
                System.out.println("Не удалось определить версию всеми способами. Устанавливаем " + DEFAULT_VERSION);
            }
            String[] defaultVersionParts = DEFAULT_VERSION.split("\\.");
            majorVersion = Integer.parseInt(defaultVersionParts[1]);
            minorVersion = Integer.parseInt(defaultVersionParts[2]);
            minorVersionResolved = true;
            logDetectedVersion();

        } catch (Exception e) {
            if (logger != null) {
                logger.error("Непредвиденная ошибка при определении версии", e);
            } else {
                System.err.println("Непредвиденная ошибка при определении версии:");
                e.printStackTrace();
            }
            majorVersion = 21;
            minorVersion = 4;
            minorVersionResolved = true;
            logDetectedVersion();
        }
    }

    private boolean tryParseVersionFromBukkitVersion() {
        try {
            Matcher matcher = VERSION_PATTERN.matcher(Bukkit.getVersion());
            if (matcher.find()) {
                majorVersion = Integer.parseInt(matcher.group(2));
                if (matcher.groupCount() >= 3 && matcher.group(3) != null) {
                    minorVersion = Integer.parseInt(matcher.group(3));
                    minorVersionResolved = true;
                }
                return true;
            }
        } catch (Exception e) {
            if (logger != null) {
                logger.warn("Не удалось определить версию MC из Bukkit.getVersion()", e);
            } else {
                System.out.println("Не удалось определить версию MC из Bukkit.getVersion()");
            }
        }
        return false;
    }

    private boolean tryParseVersionFromBukkitVersionString() {
        try {
            String[] split = Bukkit.getBukkitVersion().split("-")[0].split("\\.");
            majorVersion = Integer.parseInt(split[1]);
            if (split.length >= 3) {
                minorVersion = Integer.parseInt(split[2]);
                minorVersionResolved = true;
            }
            return true;
        } catch (Exception e) {
            if (logger != null) {
                logger.warn("Не удалось определить версию MC из Bukkit.getBukkitVersion()", e);
            } else {
                System.out.println("Не удалось определить версию MC из Bukkit.getBukkitVersion()");
            }
        }
        return false;
    }

    private boolean tryParseVersionFromPackageName() {
        try {
            String[] split = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3].split("_");
            majorVersion = Integer.parseInt(split[1]);
            if (split.length >= 3) {
                minorVersion = Integer.parseInt(split[2]);
                minorVersionResolved = true;
            }
            return true;
        } catch (Exception e) {
            if (logger != null) {
                logger.warn("Не удалось определить версию MC из имени пакета", e);
            } else {
                System.out.println("Не удалось определить версию MC из имени пакета");
            }
        }
        return false;
    }

    private void logDetectedVersion() {
        if (logger != null) {
            String minorVersionStr = minorVersionResolved ? String.valueOf(minorVersion) : (minorVersion == 0 ? "x" : String.valueOf(minorVersion));
            logger.info("Обнаружена версия: 1.{}.{}", majorVersion, minorVersionStr);
        } else {
            String minorVersionStr = minorVersionResolved ? String.valueOf(minorVersion) : (minorVersion == 0 ? "x" : String.valueOf(minorVersion));
            System.out.println("Обнаружена версия: 1." + majorVersion + "." + minorVersionStr);
        }
    }

    public boolean isPaper21() {
        return isVersion(21, 4);
    }
}