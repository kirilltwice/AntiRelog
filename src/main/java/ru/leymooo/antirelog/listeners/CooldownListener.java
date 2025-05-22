package ru.leymooo.antirelog.listeners;

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
import ru.leymooo.antirelog.config.Settings;
import ru.leymooo.antirelog.event.PvpStartedEvent;
import ru.leymooo.antirelog.event.PvpStoppedEvent;
import ru.leymooo.antirelog.manager.CooldownManager;
import ru.leymooo.antirelog.manager.CooldownManager.CooldownType;
import ru.leymooo.antirelog.manager.PvPManager;
import ru.leymooo.antirelog.util.Utils;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@RequiredArgsConstructor
public class CooldownListener implements Listener {

    private final CooldownManager cooldownManager;
    private final PvPManager pvpManager;
    private final Settings settings;

    private static final Map<Material, CooldownType> MATERIAL_TO_COOLDOWN_MAP = new EnumMap<>(Material.class);
    private static final long MILLIS_PER_SECOND = 1000L;

    static {
        MATERIAL_TO_COOLDOWN_MAP.put(Material.GOLDEN_APPLE, CooldownType.GOLDEN_APPLE);
        MATERIAL_TO_COOLDOWN_MAP.put(Material.ENCHANTED_GOLDEN_APPLE, CooldownType.ENC_GOLDEN_APPLE);
        MATERIAL_TO_COOLDOWN_MAP.put(Material.CHORUS_FRUIT, CooldownType.CHORUS);
        MATERIAL_TO_COOLDOWN_MAP.put(Material.ENDER_PEARL, CooldownType.ENDER_PEARL);
        MATERIAL_TO_COOLDOWN_MAP.put(Material.FIREWORK_ROCKET, CooldownType.FIREWORK);
        MATERIAL_TO_COOLDOWN_MAP.put(Material.TOTEM_OF_UNDYING, CooldownType.TOTEM);
    }

    public CooldownListener(Plugin plugin, CooldownManager cooldownManager, PvPManager pvpManager, Settings settings) {
        this.cooldownManager = cooldownManager;
        this.pvpManager = pvpManager;
        this.settings = settings;
        registerEntityResurrectEvent(plugin);
    }

    private void registerEntityResurrectEvent(Plugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
            public void onResurrect(EntityResurrectEvent event) {
                if (event.getEntityType() != EntityType.PLAYER) {
                    return;
                }

                Player player = (Player) event.getEntity();
                handleCooldown(event, player, CooldownType.TOTEM, settings.getTotemCooldown());
            }
        }, plugin);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        CooldownType cooldownType = determineCooldownType(item);

        if (cooldownType == null) {
            return;
        }

        long cooldownTime = getCooldownTime(cooldownType);
        handleCooldown(event, event.getPlayer(), cooldownType, cooldownTime);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntityType() != EntityType.ENDER_PEARL ||
                !(event.getEntity().getShooter() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity().getShooter();
        long cooldownTime = settings.getEnderPearlCooldown();

        if (cooldownTime > 0 && !pvpManager.isBypassed(player)) {
            cooldownManager.addCooldown(player, CooldownType.ENDER_PEARL);
            addItemCooldownIfNeeded(player, CooldownType.ENDER_PEARL);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.hasItem() || pvpManager.isBypassed(event.getPlayer())) {
            return;
        }

        ItemStack item = event.getItem();
        Player player = event.getPlayer();

        if (item.getType() == Material.ENDER_PEARL) {
            handleInteraction(event, player, CooldownType.ENDER_PEARL, settings.getEnderPearlCooldown(), false);
        } else if (item.getType() == Material.FIREWORK_ROCKET) {
            handleInteraction(event, player, CooldownType.FIREWORK, settings.getFireworkCooldown(), true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        cooldownManager.remove(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPvpStart(PvpStartedEvent event) {
        switch (event.getPvpStatus()) {
            case ALL_NOT_IN_PVP -> {
                cooldownManager.enteredToPvp(event.getDefender());
                cooldownManager.enteredToPvp(event.getAttacker());
            }
            case ATTACKER_IN_PVP -> cooldownManager.enteredToPvp(event.getDefender());
            case DEFENDER_IN_PVP -> cooldownManager.enteredToPvp(event.getAttacker());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPvpStop(PvpStoppedEvent event) {
        cooldownManager.removedFromPvp(event.getPlayer());
    }

    private void handleCooldown(Cancellable event, Player player, CooldownType type, long cooldownTime) {
        if (cooldownTime == 0 || pvpManager.isBypassed(player)) {
            return;
        }

        if (cooldownTime <= -1) {
            cancelEventIfInPvp(event, type, player);
            return;
        }

        long cooldownMs = cooldownTime * MILLIS_PER_SECOND;
        if (checkCooldown(player, type, cooldownMs)) {
            event.setCancelled(true);
            return;
        }

        cooldownManager.addCooldown(player, type);
        addItemCooldownIfNeeded(player, type);
    }

    private void handleInteraction(PlayerInteractEvent event, Player player, CooldownType type, long cooldownTime, boolean addCooldown) {
        if (cooldownTime == 0) {
            return;
        }

        if (cooldownTime <= -1) {
            cancelEventIfInPvp(event, type, player);
            return;
        }

        long cooldownMs = cooldownTime * MILLIS_PER_SECOND;
        if (checkCooldown(player, type, cooldownMs)) {
            event.setCancelled(true);
            return;
        }

        if (addCooldown) {
            cooldownManager.addCooldown(player, type);
            addItemCooldownIfNeeded(player, type);
        }
    }

    private CooldownType determineCooldownType(ItemStack item) {
        Material material = item.getType();

        if (material == Material.GOLDEN_APPLE && item.hasItemMeta() &&
                item.getItemMeta().hasEnchants()) {
            return CooldownType.ENC_GOLDEN_APPLE;
        }

        return MATERIAL_TO_COOLDOWN_MAP.get(material);
    }

    private long getCooldownTime(CooldownType type) {
        return switch (type) {
            case GOLDEN_APPLE -> settings.getGoldenAppleCooldown();
            case ENC_GOLDEN_APPLE -> settings.getEnchantedGoldenAppleCooldown();
            case CHORUS -> settings.getСhorusCooldown();
            case ENDER_PEARL -> settings.getEnderPearlCooldown();
            case FIREWORK -> settings.getFireworkCooldown();
            case TOTEM -> settings.getTotemCooldown();
        };
    }

    private void cancelEventIfInPvp(Cancellable event, CooldownType type, Player player) {
        if (!pvpManager.isInPvP(player)) {
            return;
        }

        event.setCancelled(true);

        String messageKey = (type == CooldownType.TOTEM)
                ? settings.getMessages().getTotemDisabledInPvp()
                : settings.getMessages().getItemDisabledInPvp();

        if (!messageKey.isEmpty() && player.isOnline()) {
            String coloredMessage = Utils.color(messageKey);
            Component message = LegacyComponentSerializer.legacySection().deserialize(coloredMessage);
            player.sendMessage(message);
        }
    }

    private boolean checkCooldown(Player player, CooldownType cooldownType, long cooldownTime) {
        boolean cooldownActive = !pvpManager.isPvPModeEnabled() || pvpManager.isInPvP(player);

        if (!cooldownActive || !cooldownManager.hasCooldown(player, cooldownType, cooldownTime)) {
            return false;
        }

        long remaining = cooldownManager.getRemaining(player, cooldownType, cooldownTime);
        int remainingSeconds = (int) TimeUnit.MILLISECONDS.toSeconds(remaining);

        String messageTemplate = (cooldownType == CooldownType.TOTEM)
                ? settings.getMessages().getTotemCooldown()
                : settings.getMessages().getItemCooldown();

        if (!messageTemplate.isEmpty() && player.isOnline()) {
            String formattedMessage = Utils.color(Utils.replaceTime(
                    messageTemplate.replace("%time%", String.valueOf(Math.round(remaining / 1000.0))),
                    remainingSeconds
            ));
            Component message = LegacyComponentSerializer.legacySection().deserialize(formattedMessage);
            player.sendMessage(message);
        }

        return true;
    }

    private void addItemCooldownIfNeeded(Player player, CooldownType cooldownType) {
        boolean shouldAddCooldown = !pvpManager.isPvPModeEnabled() || pvpManager.isInPvP(player);

        if (shouldAddCooldown) {
            long cooldownMs = cooldownType.getCooldown(settings) * MILLIS_PER_SECOND;
            cooldownManager.addItemCooldown(player, cooldownType, cooldownMs);
        }
    }
}