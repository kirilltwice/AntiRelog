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
import ru.leymooo.antirelog.manager.CooldownManager;
import ru.leymooo.antirelog.manager.CooldownManager.CooldownType;
import ru.leymooo.antirelog.manager.PvPManager;
import ru.leymooo.antirelog.config.Settings;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

@UtilityClass
public class ProtocolLibUtils {

    private static final int TICK_MS = 50;
    private static final long THRESHOLD_MS = 100L;

    private static boolean hasProtocolLib;
    private static boolean protocolLibWorking = true;
    private static Class<?> itemClass;
    private static MethodAccessor getItemMethod;
    private static MethodAccessor getMaterialMethod;

    static {
        initProtocolLib();
    }

    private static void initProtocolLib() {
        hasProtocolLib = Bukkit.getPluginManager().isPluginEnabled("ProtocolLib");

        if (hasProtocolLib) {
            try {
                itemClass = MinecraftReflection.getItemClass();

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
                Bukkit.getLogger().log(Level.SEVERE, "[AntiRelog] Ошибка инициализации ProtocolLib", e);
                hasProtocolLib = false;
            }
        }
    }

    public boolean isAvailable() {
        return hasProtocolLib && protocolLibWorking && getItemMethod != null && getMaterialMethod != null;
    }

    public void disableProtocolLib() {
        protocolLibWorking = false;
        Bukkit.getLogger().info("[AntiRelog] ProtocolLib функциональность отключена из-за несовместимости");
    }

    public PacketContainer createCooldownPacket(Material material, int ticks) {
        if (!isAvailable()) {
            return null;
        }

        try {
            PacketContainer packetContainer = new PacketContainer(PacketType.Play.Server.SET_COOLDOWN);

            Object item = getItemMethod.invoke(null, material);
            if (item == null) {
                return null;
            }

            try {
                packetContainer.getModifier().withType(itemClass).writeDefaults();
                packetContainer.getModifier().withType(itemClass).write(0, item);
                packetContainer.getIntegers().write(0, ticks);
                return packetContainer;
            } catch (Exception e1) {
                try {
                    packetContainer.getModifier().writeDefaults();
                    if (packetContainer.getModifier().withType(itemClass).size() > 0) {
                        packetContainer.getModifier().withType(itemClass).write(0, item);
                    }
                    if (packetContainer.getIntegers().size() > 0) {
                        packetContainer.getIntegers().write(0, ticks);
                    }
                    return packetContainer;
                } catch (Exception e2) {
                    try {
                        Object handle = packetContainer.getHandle();

                        java.lang.reflect.Field[] fields = handle.getClass().getDeclaredFields();
                        for (java.lang.reflect.Field field : fields) {
                            field.setAccessible(true);
                            if (field.getType().equals(itemClass)) {
                                field.set(handle, item);
                                break;
                            }
                        }

                        for (java.lang.reflect.Field field : fields) {
                            field.setAccessible(true);
                            if (field.getType().equals(int.class) || field.getType().equals(Integer.class)) {
                                field.set(handle, ticks);
                                break;
                            }
                        }

                        return packetContainer;
                    } catch (Exception e3) {
                        protocolLibWorking = false;
                        Bukkit.getLogger().warning("[AntiRelog] ProtocolLib пакеты кулдаунов не поддерживаются в данной версии. Функция отключена.");
                        throw e3;
                    }
                }
            }
        } catch (Exception e) {
            protocolLibWorking = false;
            Bukkit.getLogger().log(Level.WARNING, "[AntiRelog] ProtocolLib пакеты кулдаунов не поддерживаются. Функция отключена: " + e.getMessage());
            return null;
        }
    }

    public void sendPacket(PacketContainer packetContainer, Player player) {
        if (!isAvailable() || packetContainer == null || player == null || !player.isOnline()) {
            return;
        }

        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packetContainer);
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.WARNING, "[AntiRelog] Ошибка отправки пакета игроку " + player.getName(), e);
        }
    }

    public void createListener(CooldownManager cooldownManager, PvPManager pvpManager, Plugin plugin) {
        if (!isAvailable()) {
            Bukkit.getLogger().warning("[AntiRelog] Невозможно создать слушатель пакетов: ProtocolLib не доступен");
            return;
        }

        try {
            PacketContainer testPacket = new PacketContainer(PacketType.Play.Server.SET_COOLDOWN);
            testPacket.getModifier().withType(itemClass).size();
            testPacket.getIntegers().size();
        } catch (Exception e) {
            protocolLibWorking = false;
            Bukkit.getLogger().warning("[AntiRelog] ProtocolLib несовместим с данной версией. Слушатель пакетов отключен.");
            return;
        }

        final Settings settings = cooldownManager.getSettings();
        final Set<CooldownType> monitoredTypes = EnumSet.of(CooldownType.CHORUS, CooldownType.ENDER_PEARL);

        try {
            ProtocolLibrary.getProtocolManager().addPacketListener(
                    new PacketAdapter(plugin, ListenerPriority.LOWEST, PacketType.Play.Server.SET_COOLDOWN) {
                        @Override
                        public void onPacketSending(PacketEvent event) {
                            if (event.isCancelled() || !isAvailable()) {
                                return;
                            }

                            try {
                                if (event.getPacket().getModifier().withType(itemClass).size() == 0) {
                                    return;
                                }

                                Object itemObject = event.getPacket().getModifier().withType(itemClass).read(0);
                                if (itemObject == null) {
                                    return;
                                }

                                Material material = (Material) getMaterialMethod.invoke(null, itemObject);
                                if (material == null) {
                                    return;
                                }

                                if (event.getPacket().getIntegers().size() == 0) {
                                    return;
                                }

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
                                protocolLibWorking = false;
                                Bukkit.getLogger().log(Level.WARNING, "[AntiRelog] Слушатель пакетов отключен из-за ошибки", e);
                            }
                        }
                    }
            );
        } catch (Exception e) {
            protocolLibWorking = false;
            Bukkit.getLogger().log(Level.SEVERE, "[AntiRelog] Не удалось создать слушатель пакетов", e);
        }
    }
}