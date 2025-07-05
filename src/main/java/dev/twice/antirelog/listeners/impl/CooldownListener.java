package dev.twice.antirelog.listeners.impl;

import dev.twice.antirelog.Main;
import dev.twice.antirelog.api.events.CombatEndEvent;
import dev.twice.antirelog.api.events.CombatStartEvent;
import dev.twice.antirelog.config.Settings;
import dev.twice.antirelog.managers.CombatManager;
import dev.twice.antirelog.managers.CooldownManager;
import dev.twice.antirelog.managers.CooldownManager.CooldownType;
import dev.twice.antirelog.util.Utils;
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

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class CooldownListener implements Listener {
    private final Main plugin;
    private static final Map<Material, CooldownType> MATERIAL_TO_COOLDOWN_MAP = new EnumMap<>(Material.class);

    static {
        MATERIAL_TO_COOLDOWN_MAP.put(Material.GOLDEN_APPLE, CooldownType.GOLDEN_APPLE);
        MATERIAL_TO_COOLDOWN_MAP.put(Material.ENCHANTED_GOLDEN_APPLE, CooldownType.ENC_GOLDEN_APPLE);
        MATERIAL_TO_COOLDOWN_MAP.put(Material.CHORUS_FRUIT, CooldownType.CHORUS);
        MATERIAL_TO_COOLDOWN_MAP.put(Material.ENDER_PEARL, CooldownType.ENDER_PEARL);
        MATERIAL_TO_COOLDOWN_MAP.put(Material.FIREWORK_ROCKET, CooldownType.FIREWORK);
        MATERIAL_TO_COOLDOWN_MAP.put(Material.TOTEM_OF_UNDYING, CooldownType.TOTEM);
    }

    private Settings getSettings() { return plugin.getSettings(); }
    private CombatManager getCombatManager() { return plugin.getCombatManager(); }
    private CooldownManager getCooldownManager() { return getCombatManager().getCooldownManager(); }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onResurrect(EntityResurrectEvent event) {
        if (event.getEntityType() != EntityType.PLAYER) return;

        Player player = (Player) event.getEntity();
        int totemCooldownSeconds = getSettings().getTotemCooldown();
        handleGenericCooldownAction(event, player, CooldownType.TOTEM, totemCooldownSeconds);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        CooldownType cooldownType = determineCooldownType(item);
        if (cooldownType == null) return;

        int cooldownTimeSeconds = getCooldownDurationSeconds(cooldownType);
        handleGenericCooldownAction(event, event.getPlayer(), cooldownType, cooldownTimeSeconds);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntityType() != EntityType.ENDER_PEARL ||
                !(event.getEntity().getShooter() instanceof Player player)) return;

        int cooldownTimeSeconds = getSettings().getEnderPearlCooldown();
        if (cooldownTimeSeconds > 0 && !getCombatManager().isBypassed(player)) {
            getCooldownManager().startLogicalCooldown(player, CooldownType.ENDER_PEARL, cooldownTimeSeconds);
            if (getCombatManager().isInCombat(player)) {
                player.setCooldown(Material.ENDER_PEARL, cooldownTimeSeconds * 20);
            }
        } else if (cooldownTimeSeconds <= -1) {
            cancelEventIfInCombat(event, CooldownType.ENDER_PEARL, player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.hasItem() || getCombatManager().isBypassed(event.getPlayer())) return;

        ItemStack item = event.getItem();
        Player player = event.getPlayer();
        Material itemType = item.getType();

        if (itemType == Material.ENDER_PEARL) {
            int enderPearlCooldownSetting = getSettings().getEnderPearlCooldown();
            if (enderPearlCooldownSetting <= -1) {
                cancelEventIfInCombat(event, CooldownType.ENDER_PEARL, player);
            } else if (enderPearlCooldownSetting > 0 && checkCooldownAndNotify(player, CooldownType.ENDER_PEARL)) {
                event.setCancelled(true);
            }
        } else if (itemType == Material.FIREWORK_ROCKET) {
            handleGenericCooldownAction(event, player, CooldownType.FIREWORK, getSettings().getFireworkCooldown());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        getCooldownManager().removeAllCooldownsForPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCombatStart(CombatStartEvent event) {
        getCooldownManager().enteredToCombat(event.getDefender());
        getCooldownManager().enteredToCombat(event.getAttacker());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCombatEnd(CombatEndEvent event) {
        getCooldownManager().removedFromCombat(event.getPlayer());
    }

    private void handleGenericCooldownAction(Cancellable event, Player player, CooldownType type, int cooldownTimeSeconds) {
        if (cooldownTimeSeconds == 0 || getCombatManager().isBypassed(player)) return;

        if (cooldownTimeSeconds <= -1) {
            cancelEventIfInCombat(event, type, player);
            return;
        }

        if (checkCooldownAndNotify(player, type)) {
            event.setCancelled(true);
            return;
        }

        getCooldownManager().startLogicalCooldown(player, type, cooldownTimeSeconds);
        if (getCombatManager().isInCombat(player) && type.getMaterial() != null) {
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
        return type.getCooldown(getSettings());
    }

    private void cancelEventIfInCombat(Cancellable event, CooldownType type, Player player) {
        if (!getCombatManager().isInCombat(player)) return;

        event.setCancelled(true);
        String messageKey = (type == CooldownType.TOTEM) ?
                getSettings().getMessages().getTotemDisabledInCombat() :
                getSettings().getMessages().getItemDisabledInCombat();

        if (messageKey != null && !messageKey.isEmpty() && player.isOnline()) {
            Component message = LegacyComponentSerializer.legacySection().deserialize(Utils.color(messageKey));
            player.sendMessage(message);
        }
    }

    private boolean checkCooldownAndNotify(Player player, CooldownType cooldownType) {
        boolean isCooldownCheckRelevant = !getCombatManager().isCombatModeEnabled() ||
                getCombatManager().isInCombat(player);
        if (!isCooldownCheckRelevant || !getCooldownManager().hasLogicalCooldown(player, cooldownType)) {
            return false;
        }

        long remainingMillis = getCooldownManager().getRemainingMillis(player, cooldownType);
        int remainingSeconds = (int) TimeUnit.MILLISECONDS.toSeconds(remainingMillis);
        if (remainingSeconds <= 0) return false;

        String messageTemplate = (cooldownType == CooldownType.TOTEM) ?
                getSettings().getMessages().getTotemCooldown() :
                getSettings().getMessages().getItemCooldown();

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