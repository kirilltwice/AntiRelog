package dev.twice.antirelog.managers;

import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import dev.twice.antirelog.config.Settings;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.concurrent.TimeUnit;

public class CooldownManager {

    @Getter
    private final Settings settings;
    private final Map<Player, Map<CooldownType, Long>> cooldownEndTimes = new ConcurrentHashMap<>();

    public CooldownManager(Settings settings) {
        this.settings = settings;
    }

    public void startLogicalCooldown(Player player, CooldownType type, int durationSeconds) {
        if (durationSeconds <= 0 || player == null || !player.isOnline()) {
            return;
        }
        long durationMillis = durationSeconds * 1000L;
        long endTime = System.currentTimeMillis() + durationMillis;
        cooldownEndTimes.computeIfAbsent(player, k -> new ConcurrentHashMap<>()).put(type, endTime);
    }

    public void startLogicalCooldownMillis(Player player, CooldownType type, long durationMillis) {
        if (durationMillis <= 0 || player == null || !player.isOnline()) {
            return;
        }
        long endTime = System.currentTimeMillis() + durationMillis;
        cooldownEndTimes.computeIfAbsent(player, k -> new ConcurrentHashMap<>()).put(type, endTime);
    }

    public void removePlayerCooldown(Player player, CooldownType type) {
        if (player == null) {
            return;
        }
        Map<CooldownType, Long> playerCooldowns = cooldownEndTimes.get(player);
        if (playerCooldowns != null) {
            playerCooldowns.remove(type);
            if (playerCooldowns.isEmpty()) {
                cooldownEndTimes.remove(player);
            }
        }
        if (player.isOnline() && type.getMaterial() != null) {
            player.setCooldown(type.getMaterial(), 0);
        }
    }

    public void enteredToCombat(Player player) {
        if (!player.isOnline()) {
            return;
        }
        for (CooldownType cooldownType : CooldownType.VALUES) {
            int configCooldownSeconds = cooldownType.getCooldown(settings);

            if (configCooldownSeconds > 0) {
                long remainingMillis = getRemainingMillis(player, cooldownType);
                if (remainingMillis > 0 && cooldownType.getMaterial() != null) {
                    player.setCooldown(cooldownType.getMaterial(), (int) Math.ceil(remainingMillis / 50.0));
                }
            }
        }
    }

    public void removedFromCombat(Player player) {
        if (!player.isOnline()) {
            return;
        }
        for (CooldownType cooldownType : CooldownType.VALUES) {
            int configCooldownSeconds = cooldownType.getCooldown(settings);
            if (configCooldownSeconds < 0) {
                if (cooldownEndTimes.getOrDefault(player, Map.of()).containsKey(cooldownType) && cooldownType.getMaterial() != null) {
                    player.setCooldown(cooldownType.getMaterial(), 0);
                }
                Map<CooldownType, Long> playerMap = cooldownEndTimes.get(player);
                if (playerMap != null) {
                    playerMap.remove(cooldownType);
                    if (playerMap.isEmpty()) {
                        cooldownEndTimes.remove(player);
                    }
                }
            }
        }
    }

    public boolean hasLogicalCooldown(Player player, CooldownType type) {
        Map<CooldownType, Long> playerCooldowns = cooldownEndTimes.get(player);
        if (playerCooldowns == null) {
            return false;
        }
        Long endTime = playerCooldowns.get(type);
        return endTime != null && System.currentTimeMillis() < endTime;
    }

    public long getRemainingMillis(Player player, CooldownType type) {
        Map<CooldownType, Long> playerCooldowns = cooldownEndTimes.get(player);
        if (playerCooldowns == null) {
            return 0L;
        }
        Long endTime = playerCooldowns.get(type);
        if (endTime == null) {
            return 0L;
        }
        return Math.max(0L, endTime - System.currentTimeMillis());
    }

    public int getRemainingSeconds(Player player, CooldownType type) {
        return (int) TimeUnit.MILLISECONDS.toSeconds(getRemainingMillis(player, type));
    }

    public void removeAllCooldownsForPlayer(Player player) {
        Map<CooldownType, Long> playerMap = cooldownEndTimes.remove(player);
        if (playerMap != null && player.isOnline()) {
            for (CooldownType type : playerMap.keySet()) {
                if (type.getMaterial() != null) {
                    player.setCooldown(type.getMaterial(), 0);
                }
            }
        }
    }

    public void clearAllPlayerData() {
        for (Player player : cooldownEndTimes.keySet()) {
            if (player.isOnline()) {
                Map<CooldownType, Long> playerSpecificCooldowns = cooldownEndTimes.get(player);
                if (playerSpecificCooldowns != null) {
                    for (CooldownType type : playerSpecificCooldowns.keySet()) {
                        if (type.getMaterial() != null) {
                            player.setCooldown(type.getMaterial(), 0);
                        }
                    }
                }
            }
        }
        cooldownEndTimes.clear();
    }

    public void shutdown() {
        clearAllPlayerData();
    }

    @Getter
    public enum CooldownType {
        GOLDEN_APPLE(Material.GOLDEN_APPLE, Settings::getGoldenAppleCooldown),
        ENC_GOLDEN_APPLE(Material.ENCHANTED_GOLDEN_APPLE, Settings::getEnchantedGoldenAppleCooldown),
        ENDER_PEARL(Material.ENDER_PEARL, Settings::getEnderPearlCooldown),
        CHORUS(Material.CHORUS_FRUIT, Settings::getChorusCooldown),
        TOTEM(Material.TOTEM_OF_UNDYING, Settings::getTotemCooldown),
        FIREWORK(Material.FIREWORK_ROCKET, Settings::getFireworkCooldown);

        public static final CooldownType[] VALUES = values();
        private final Material material;
        private final Function<Settings, Integer> cooldownFunction;

        CooldownType(Material material, Function<Settings, Integer> cooldownFunction) {
            this.material = material;
            this.cooldownFunction = cooldownFunction;
        }

        public int getCooldown(Settings settings) {
            return cooldownFunction.apply(settings);
        }
    }
}