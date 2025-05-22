package ru.leymooo.antirelog.manager;

import com.comphenix.protocol.events.PacketContainer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import ru.leymooo.antirelog.Antirelog;
import ru.leymooo.antirelog.config.Settings;
import ru.leymooo.antirelog.util.ProtocolLibUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Function;

@RequiredArgsConstructor
public class CooldownManager {

    private final Antirelog plugin;
    @Getter
    private final Settings settings;
    private final ScheduledExecutorService scheduledExecutorService;
    private final Map<Player, Map<CooldownType, Long>> cooldowns = new HashMap<>();
    private final Map<Player, Map<CooldownType, ScheduledFuture<?>>> futures = new HashMap<>();

    public CooldownManager(Antirelog plugin, Settings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.scheduledExecutorService = plugin.isProtocolLibEnabled()
                ? Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "AntiRelog-Cooldown");
            thread.setDaemon(true);
            return thread;
        })
                : null;
    }

    public void addCooldown(Player player, CooldownType type) {
        cooldowns.computeIfAbsent(player, k -> new HashMap<>())
                .put(type, System.currentTimeMillis());
    }

    public void addItemCooldown(Player player, CooldownType type, long duration) {
        if (!plugin.isProtocolLibEnabled() || !player.isOnline()) {
            return;
        }

        try {
            int durationInTicks = (int) Math.ceil(duration / 50.0);
            PacketContainer packet = ProtocolLibUtils.createCooldownPacket(type.getMaterial(), durationInTicks);

            if (packet != null) {
                ProtocolLibUtils.sendPacket(packet, player);

                ScheduledFuture<?> future = scheduledExecutorService.schedule(() -> {
                    if (player.isOnline()) {
                        removeItemCooldown(player, type);
                    }
                }, duration, TimeUnit.MILLISECONDS);

                futures.computeIfAbsent(player, k -> new HashMap<>())
                        .put(type, future);
            }
        } catch (Exception e) {
        }
    }

    public void removeItemCooldown(Player player, CooldownType type) {
        if (!plugin.isProtocolLibEnabled()) {
            return;
        }

        Map<CooldownType, ScheduledFuture<?>> playerFutures = futures.get(player);
        if (playerFutures != null) {
            ScheduledFuture<?> future = playerFutures.remove(type);
            if (future != null && !future.isCancelled()) {
                future.cancel(false);
            }
        }

        if (player.isOnline()) {
            try {
                PacketContainer packet = ProtocolLibUtils.createCooldownPacket(type.getMaterial(), 0);
                if (packet != null) {
                    ProtocolLibUtils.sendPacket(packet, player);
                }
            } catch (Exception e) {
            }
        }
    }

    public void enteredToPvp(Player player) {
        if (!player.isOnline()) {
            return;
        }

        for (CooldownType cooldownType : CooldownType.VALUES) {
            int cooldown = cooldownType.getCooldown(settings);
            if (cooldown == 0) {
                continue;
            }

            if (cooldown > 0 && hasCooldown(player, cooldownType, cooldown * 1000L)) {
                long remaining = getRemaining(player, cooldownType, cooldown * 1000L);
                addItemCooldown(player, cooldownType, remaining);
            } else if (cooldown < 0) {
                addItemCooldown(player, cooldownType, 300_000L);
            }
        }
    }

    public void removedFromPvp(Player player) {
        if (!player.isOnline()) {
            return;
        }

        for (CooldownType cooldownType : CooldownType.VALUES) {
            int cooldown = cooldownType.getCooldown(settings);
            if (cooldown > 0 && hasCooldown(player, cooldownType, cooldown * 1000L)) {
                removeItemCooldown(player, cooldownType);
            }
        }
    }

    public boolean hasCooldown(Player player, CooldownType type, long duration) {
        Map<CooldownType, Long> playerCooldowns = cooldowns.get(player);
        if (playerCooldowns == null) {
            return false;
        }

        Long startTime = playerCooldowns.get(type);
        return startTime != null && (System.currentTimeMillis() - startTime) < duration;
    }

    public long getRemaining(Player player, CooldownType type, long duration) {
        Map<CooldownType, Long> playerCooldowns = cooldowns.get(player);
        if (playerCooldowns == null) {
            return 0;
        }

        Long startTime = playerCooldowns.get(type);
        if (startTime == null) {
            return 0;
        }

        return Math.max(0, duration - (System.currentTimeMillis() - startTime));
    }

    public void remove(Player player) {
        cooldowns.remove(player);

        Map<CooldownType, ScheduledFuture<?>> playerFutures = futures.remove(player);
        if (playerFutures != null) {
            playerFutures.values().forEach(future -> future.cancel(false));
        }
    }

    public void clearAll() {
        futures.values().forEach(playerFutures ->
                playerFutures.forEach((type, future) -> {
                    future.cancel(true);
                    removeItemCooldown((Player) futures.entrySet().stream()
                            .filter(entry -> entry.getValue() == playerFutures)
                            .map(entry -> entry.getKey())
                            .findFirst()
                            .orElse(null), type);
                })
        );

        futures.clear();
        cooldowns.clear();
    }

    public void shutdown() {
        if (scheduledExecutorService != null && !scheduledExecutorService.isShutdown()) {
            scheduledExecutorService.shutdown();
            try {
                if (!scheduledExecutorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduledExecutorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduledExecutorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    @Getter
    @RequiredArgsConstructor
    public enum CooldownType {
        GOLDEN_APPLE(Material.GOLDEN_APPLE, Settings::getGoldenAppleCooldown),
        ENC_GOLDEN_APPLE(Material.ENCHANTED_GOLDEN_APPLE, Settings::getEnchantedGoldenAppleCooldown),
        ENDER_PEARL(Material.ENDER_PEARL, Settings::getEnderPearlCooldown),
        CHORUS(Material.CHORUS_FRUIT, Settings::getСhorusCooldown),
        TOTEM(Material.TOTEM_OF_UNDYING, Settings::getTotemCooldown),
        FIREWORK(Material.FIREWORK_ROCKET, Settings::getFireworkCooldown);

        public static final CooldownType[] VALUES = values();

        private final Material material;
        private final Function<Settings, Integer> cooldownFunction;

        public int getCooldown(Settings settings) {
            return cooldownFunction.apply(settings);
        }
    }
}