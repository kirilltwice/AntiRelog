package dev.twice.antirelog.listeners;

import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import dev.twice.antirelog.config.Settings;
import dev.twice.antirelog.events.PvpStartedEvent;
import dev.twice.antirelog.events.PvpStoppedEvent;
import dev.twice.antirelog.managers.CooldownManager;
import dev.twice.antirelog.managers.CooldownManager.CooldownType;
import dev.twice.antirelog.managers.PvPManager;
import dev.twice.antirelog.util.Utils;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class CooldownListener implements Listener {

    private final Plugin plugin;
    private final CooldownManager cooldownManager;
    private final PvPManager pvpManager;
    private final Settings settings;

    private static final Map<Material, CooldownType> MATERIAL_TO_COOLDOWN_MAP = new EnumMap<>(Material.class);

    static {
        MATERIAL_TO_COOLDOWN_MAP.put(Material.GOLDEN_APPLE, CooldownType.GOLDEN_APPLE);
        MATERIAL_TO_COOLDOWN_MAP.put(Material.ENCHANTED_GOLDEN_APPLE, CooldownType.ENC_GOLDEN_APPLE);
        MATERIAL_TO_COOLDOWN_MAP.put(Material.CHORUS_FRUIT, CooldownType.CHORUS);
        MATERIAL_TO_COOLDOWN_MAP.put(Material.ENDER_PEARL, CooldownType.ENDER_PEARL);
        MATERIAL_TO_COOLDOWN_MAP.put(Material.FIREWORK_ROCKET, CooldownType.FIREWORK);
        MATERIAL_TO_COOLDOWN_MAP.put(Material.TOTEM_OF_UNDYING, CooldownType.TOTEM);
    }

    public void initializeListeners() {
        if (this.plugin == null) {
            return;
        }
        registerEntityResurrectEvent();
    }

    private void registerEntityResurrectEvent() {
        this.plugin.getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
            public void onResurrect(EntityResurrectEvent event) {
                if (event.getEntityType() != EntityType.PLAYER) {
                    return;
                }
                Player player = (Player) event.getEntity();
                int totemCooldownSeconds = settings.getTotemCooldown();
                handleGenericCooldownAction(event, player, CooldownType.TOTEM, totemCooldownSeconds);
            }
        }, this.plugin);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        CooldownType cooldownType = determineCooldownType(item);

        if (cooldownType == null) {
            return;
        }
        int cooldownTimeSeconds = getCooldownDurationSeconds(cooldownType);
        handleGenericCooldownAction(event, event.getPlayer(), cooldownType, cooldownTimeSeconds);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntityType() != EntityType.ENDER_PEARL || !(event.getEntity().getShooter() instanceof Player player)) {
            return;
        }

        int cooldownTimeSeconds = settings.getEnderPearlCooldown();
        if (cooldownTimeSeconds > 0 && !pvpManager.isBypassed(player)) {
            cooldownManager.startLogicalCooldown(player, CooldownType.ENDER_PEARL, cooldownTimeSeconds);
            if (pvpManager.isInPvP(player)) {
                player.setCooldown(Material.ENDER_PEARL, cooldownTimeSeconds * 20);
            }
        } else if (cooldownTimeSeconds <= -1) {
            cancelEventIfInPvp(event, CooldownType.ENDER_PEARL, player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.hasItem() || pvpManager.isBypassed(event.getPlayer())) {
            return;
        }

        ItemStack item = event.getItem();
        Player player = event.getPlayer();
        Material itemType = item.getType();

        if (itemType == Material.ENDER_PEARL) {
            int enderPearlCooldownSetting = settings.getEnderPearlCooldown();
            if (enderPearlCooldownSetting <= -1) {
                cancelEventIfInPvp(event, CooldownType.ENDER_PEARL, player);
            } else if (enderPearlCooldownSetting > 0) {
                if (checkCooldownAndNotify(player, CooldownType.ENDER_PEARL)) {
                    event.setCancelled(true);
                }
            }
        } else if (itemType == Material.FIREWORK_ROCKET) {
            handleGenericCooldownAction(event, player, CooldownType.FIREWORK, settings.getFireworkCooldown());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        cooldownManager.removeAllCooldownsForPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPvpStart(PvpStartedEvent event) {
        cooldownManager.enteredToPvp(event.getDefender());
        cooldownManager.enteredToPvp(event.getAttacker());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPvpStop(PvpStoppedEvent event) {
        cooldownManager.removedFromPvp(event.getPlayer());
    }

    private void handleGenericCooldownAction(Cancellable event, Player player, CooldownType type, int cooldownTimeSeconds) {
        if (cooldownTimeSeconds == 0 || pvpManager.isBypassed(player)) {
            return;
        }

        if (cooldownTimeSeconds <= -1) {
            cancelEventIfInPvp(event, type, player);
            return;
        }

        if (checkCooldownAndNotify(player, type)) {
            event.setCancelled(true);
            return;
        }

        cooldownManager.startLogicalCooldown(player, type, cooldownTimeSeconds);
        if (pvpManager.isInPvP(player) && type.getMaterial() != null) {
            player.setCooldown(type.getMaterial(), cooldownTimeSeconds * 20);
        }
    }

    private CooldownType determineCooldownType(ItemStack item) {
        Material material = item.getType();
        if (material == Material.GOLDEN_APPLE && item.hasItemMeta() && item.getItemMeta().hasEnchants()) {
            return CooldownType.ENC_GOLDEN_APPLE;
        }
        return MATERIAL_TO_COOLDOWN_MAP.get(material);
    }

    private int getCooldownDurationSeconds(CooldownType type) {
        return type.getCooldown(settings);
    }

    private void cancelEventIfInPvp(Cancellable event, CooldownType type, Player player) {
        if (!pvpManager.isInPvP(player)) {
            return;
        }
        event.setCancelled(true);

        String messageKey = (type == CooldownType.TOTEM)
                ? settings.getMessages().getTotemDisabledInPvp()
                : settings.getMessages().getItemDisabledInPvp();

        if (messageKey != null && !messageKey.isEmpty() && player.isOnline()) {
            Component message = LegacyComponentSerializer.legacySection().deserialize(Utils.color(messageKey));
            player.sendMessage(message);
        }
    }

    private boolean checkCooldownAndNotify(Player player, CooldownType cooldownType) {
        boolean isCooldownCheckRelevant = !pvpManager.isPvPModeEnabled() || pvpManager.isInPvP(player);
        if (!isCooldownCheckRelevant) {
            return false;
        }

        if (!cooldownManager.hasLogicalCooldown(player, cooldownType)) {
            return false;
        }

        long remainingMillis = cooldownManager.getRemainingMillis(player, cooldownType);
        int remainingSeconds = (int) TimeUnit.MILLISECONDS.toSeconds(remainingMillis);
        if (remainingSeconds <= 0) {
            return false;
        }

        String messageTemplate = (cooldownType == CooldownType.TOTEM)
                ? settings.getMessages().getTotemCooldown()
                : settings.getMessages().getItemCooldown();

        if (messageTemplate != null && !messageTemplate.isEmpty() && player.isOnline()) {
            double remainingSecondsDecimal = Math.max(0.0, Math.round(remainingMillis / 100.0)) / 10.0;
            String formattedMessage = messageTemplate.replace("%time%", String.valueOf(remainingSecondsDecimal));
            formattedMessage = Utils.color(Utils.replaceTime(formattedMessage, remainingSeconds));
            Component message = LegacyComponentSerializer.legacySection().deserialize(formattedMessage);
            player.sendMessage(message);
        }
        return true;
    }
}
