package ru.leymooo.antirelog.util;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.accessors.Accessors;
import com.comphenix.protocol.reflect.accessors.MethodAccessor;
import com.comphenix.protocol.utility.MinecraftReflection;
import lombok.experimental.UtilityClass;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import ru.leymooo.antirelog.config.Settings;
import ru.leymooo.antirelog.manager.CooldownManager;
import ru.leymooo.antirelog.manager.CooldownManager.CooldownType;
import ru.leymooo.antirelog.manager.PvPManager;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@UtilityClass
public class ProtocolLibUtils {

    private static final int TICK_MS = 50;
    private static final long THRESHOLD_MS = 100L;

    private static boolean hasProtocolLib;
    private static Class<?> itemClass;
    private static MethodAccessor getItemMethod;
    private static MethodAccessor getMaterialMethod;

    static {
        initProtocolLib();
    }

    private static void initProtocolLib() {
        hasProtocolLib = Bukkit.getPluginManager().isPluginEnabled("ProtocolLib") && VersionUtils.isVersion(9);

        if (hasProtocolLib) {
            try {
                boolean is117OrAbove = VersionUtils.isVersion(17);
                String itemClassName = is117OrAbove ? "world.item.Item" : "Item";

                itemClass = MinecraftReflection.getMinecraftClass(itemClassName);

                getItemMethod = Accessors.getMethodAccessor(
                        MinecraftReflection.getCraftBukkitClass("util.CraftMagicNumbers"),
                        "getItem",
                        Material.class
                );

                getMaterialMethod = Accessors.getMethodAccessor(
                        MinecraftReflection.getCraftBukkitClass("util.CraftMagicNumbers"),
                        "getMaterial",
                        itemClass
                );
            } catch (Exception e) {
                Bukkit.getLogger().severe("[AntiRelog] Ошибка инициализации ProtocolLib: " + e.getMessage());
                hasProtocolLib = false;
            }
        }
    }

    public boolean isAvailable() {
        return hasProtocolLib && getItemMethod != null && getMaterialMethod != null;
    }

    public PacketContainer createCooldownPacket(Material material, int ticks) {
        if (!isAvailable()) {
            throw new IllegalStateException("ProtocolLib не доступен");
        }

        PacketContainer packetContainer = new PacketContainer(PacketType.Play.Server.SET_COOLDOWN);
        packetContainer.getModifier().writeDefaults();
        packetContainer.getModifier().withType(itemClass).write(0, getItemMethod.invoke(null, material));
        packetContainer.getIntegers().write(0, ticks);

        return packetContainer;
    }

    public void sendPacket(PacketContainer packetContainer, Player player) {
        if (!isAvailable() || player == null || !player.isOnline()) {
            return;
        }

        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packetContainer);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[AntiRelog] Ошибка отправки пакета: " + e.getMessage());
        }
    }

    public void createListener(CooldownManager cooldownManager, PvPManager pvpManager, Plugin plugin) {
        if (!isAvailable()) {
            Bukkit.getLogger().warning("[AntiRelog] Невозможно создать слушатель пакетов: ProtocolLib не доступен");
            return;
        }

        final Settings settings = cooldownManager.getSettings();
        final Set<CooldownType> monitoredTypes = EnumSet.of(CooldownType.CHORUS, CooldownType.ENDER_PEARL);

        ProtocolLibrary.getProtocolManager().addPacketListener(
                new PacketAdapter(plugin, ListenerPriority.LOWEST, PacketType.Play.Server.SET_COOLDOWN) {
                    @Override
                    public void onPacketSending(PacketEvent event) {
                        if (event.isCancelled() || !isAvailable()) {
                            return;
                        }

                        try {
                            Material material = (Material) getMaterialMethod.invoke(
                                    null, event.getPacket().getModifier().withType(itemClass).read(0)
                            );

                            int durationTicks = event.getPacket().getIntegers().read(0);
                            long durationMs = durationTicks * TICK_MS;

                            for (CooldownType cooldownType : monitoredTypes) {
                                if (material == cooldownType.getMaterial()) {
                                    long configCooldownMs = TimeUnit.SECONDS.toMillis(cooldownType.getCooldown(settings));

                                    if (cooldownManager.hasCooldown(event.getPlayer(), cooldownType, configCooldownMs)) {
                                        long remainingMs = cooldownManager.getRemaining(
                                                event.getPlayer(), cooldownType, configCooldownMs
                                        );

                                        if (Math.abs(remainingMs - durationMs) > THRESHOLD_MS) {
                                            boolean shouldModify = !pvpManager.isPvPModeEnabled()
                                                    || pvpManager.isInPvP(event.getPlayer());

                                            if (shouldModify) {
                                                if (durationTicks == 0) {
                                                    event.setCancelled(true);
                                                    return;
                                                }

                                                int newDurationTicks = (int) Math.ceil(remainingMs / (float) TICK_MS);
                                                event.getPacket().getIntegers().write(0, newDurationTicks);
                                            }
                                        }
                                        break;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Bukkit.getLogger().warning("[AntiRelog] Ошибка обработки пакета кулдауна: " + e.getMessage());
                        }
                    }
                }
        );
    }
}