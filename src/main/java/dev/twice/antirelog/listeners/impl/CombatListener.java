package dev.twice.antirelog.listeners.impl;

import dev.twice.antirelog.Main;
import dev.twice.antirelog.api.events.PlayerKickInCombatEvent;
import dev.twice.antirelog.api.events.PlayerLeaveInCombatEvent;
import dev.twice.antirelog.config.Settings;
import dev.twice.antirelog.managers.CombatManager;
import dev.twice.antirelog.util.Utils;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@RequiredArgsConstructor
public class CombatListener implements Listener {
    private static final String FIREWORK_META_KEY = "antirelog_firework_shooter";

    private final Main plugin;
    private final Map<Player, AtomicInteger> allowedTeleports = new ConcurrentHashMap<>();

    private Settings getSettings() { return plugin.getSettings(); }
    private CombatManager getCombatManager() { return plugin.getCombatManager(); }

    @EventHandler
    public void onEnable(org.bukkit.event.server.PluginEnableEvent event) {
        if (event.getPlugin() == plugin) {
            startTeleportCleanupTask();
        }
    }

    private void startTeleportCleanupTask() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            allowedTeleports.forEach((player, counter) -> counter.incrementAndGet());
            allowedTeleports.entrySet().removeIf(entry ->
                    entry.getValue().get() >= getSettings().getTeleportGracePeriodTicks());
        }, 1L, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) return;

        Player damager = getDamagerFromEntity(event.getDamager());
        if (damager != null && !damager.equals(target)) {
            getCombatManager().playerDamagedByPlayer(damager, target);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (getSettings().isCancelInteractWithEntities() &&
                getCombatManager().isCombatModeEnabled() &&
                getCombatManager().isInCombat(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityCombustByEntity(EntityCombustByEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) return;

        Player damager = getDamagerFromEntity(event.getCombuster());
        if (damager != null && !damager.equals(target)) {
            getCombatManager().playerDamagedByPlayer(damager, target);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (event.getProjectile() instanceof Firework && event.getEntity() instanceof Player shooter) {
            event.getProjectile().setMetadata(FIREWORK_META_KEY,
                    new FixedMetadataValue(plugin, shooter.getUniqueId()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        if (!(event.getPotion().getShooter() instanceof Player shooter)) return;

        boolean hasHarmfulEffect = event.getPotion().getEffects().stream()
                .anyMatch(effect -> effect.getType() == PotionEffectType.HARM);

        if (hasHarmfulEffect) {
            event.getAffectedEntities().stream()
                    .filter(entity -> entity instanceof Player && !entity.equals(shooter))
                    .map(entity -> (Player) entity)
                    .forEach(target -> getCombatManager().playerDamagedByPlayer(shooter, target));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (!getSettings().isDisableTeleportsInCombat() || !getCombatManager().isInCombat(player)) return;

        if (allowedTeleports.containsKey(player)) return;

        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT ||
                cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            allowedTeleports.put(player, new AtomicInteger(0));
            return;
        }

        if (event.getFrom().getWorld() != event.getTo().getWorld() ||
                event.getFrom().distanceSquared(event.getTo()) > Math.pow(getSettings().getMaxTeleportDistance(), 2)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!getSettings().isDisableCommandsInCombat() || !getCombatManager().isInCombat(player)) return;

        String command = extractCommand(event.getMessage());
        if (!getCombatManager().isCommandWhiteListed(command)) {
            event.setCancelled(true);
            sendCommandDisabledMessage(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerKick(PlayerKickEvent event) {
        Player player = event.getPlayer();
        if (!getCombatManager().isInCombat(player) && !getCombatManager().isInSilentCombat(player)) return;

        Bukkit.getPluginManager().callEvent(new PlayerKickInCombatEvent(player,
                LegacyComponentSerializer.legacySection().serialize(event.reason())));

        getCombatManager().stopCombatSilent(player);
        if (shouldKillOnKick(event)) {
            handleKickedInCombat(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        allowedTeleports.remove(player);

        if (getSettings().isHideLeaveMessage()) {
            event.quitMessage(null);
        }

        if (getCombatManager().isInCombat(player)) {
            Bukkit.getPluginManager().callEvent(new PlayerLeaveInCombatEvent(player));
            handlePlayerLeave(player);
        } else if (getCombatManager().isInSilentCombat(player)) {
            getCombatManager().stopCombatSilent(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        if (getSettings().isHideDeathMessage()) {
            event.deathMessage(null);
        }

        if (getCombatManager().isInSilentCombat(player) || getCombatManager().isInCombat(player)) {
            getCombatManager().stopCombatSilent(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (getSettings().isHideJoinMessage()) {
            event.joinMessage(null);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerInteractEnderChest(PlayerInteractEvent event) {
        if (!getSettings().isDisableEnderchestInCombat() ||
                !getCombatManager().isInCombat(event.getPlayer()) ||
                event.getAction() != Action.RIGHT_CLICK_BLOCK ||
                event.getClickedBlock() == null ||
                event.getClickedBlock().getType() != Material.ENDER_CHEST) return;

        event.setCancelled(true);
        sendDisabledMessage(event.getPlayer(), getSettings().getMessages().getItemDisabledInCombat());
    }

    private String extractCommand(String rawMessage) {
        if (rawMessage == null || rawMessage.isEmpty()) return "";

        String[] parts = rawMessage.split(" ", 2);
        String commandPart = parts[0];
        return commandPart.startsWith("/") ?
                commandPart.substring(1).toLowerCase(Locale.ROOT) :
                commandPart.toLowerCase(Locale.ROOT);
    }

    private void sendCommandDisabledMessage(Player player) {
        String messageText = getSettings().getMessages().getCommandsDisabled();
        if (messageText != null && !messageText.isEmpty()) {
            String formattedMessage = Utils.color(Utils.replaceTime(messageText,
                    getCombatManager().getTimeRemainingInCombat(player)));
            player.sendMessage(LegacyComponentSerializer.legacySection().deserialize(formattedMessage));
        }
    }

    private void sendDisabledMessage(Player player, String message) {
        if (message != null && !message.isEmpty()) {
            player.sendMessage(LegacyComponentSerializer.legacySection().deserialize(Utils.color(message)));
        }
    }

    private boolean shouldKillOnKick(PlayerKickEvent event) {
        List<String> configuredKillReasons = getSettings().getKickMessages();
        if (configuredKillReasons.isEmpty()) return true;

        String reasonText = LegacyComponentSerializer.legacySection()
                .serialize(event.reason()).toLowerCase(Locale.ROOT);

        return configuredKillReasons.stream()
                .anyMatch(reason -> reasonText.contains(reason.toLowerCase(Locale.ROOT)));
    }

    private void handleKickedInCombat(Player player) {
        if (getSettings().isKillOnKick()) {
            player.setHealth(0.0D);
            sendPlayerLeftMessage(player, getSettings().getMessages().getCombatLeft());
        }
        if (getSettings().isRunCommandsOnKick()) {
            executeLeaveCommands(player);
        }
    }

    private void handlePlayerLeave(Player player) {
        getCombatManager().stopCombatSilent(player);
        if (getSettings().isKillOnLeave()) {
            player.setHealth(0.0D);
            sendPlayerLeftMessage(player, getSettings().getMessages().getCombatLeft());
        }
        executeLeaveCommands(player);
    }

    private void sendPlayerLeftMessage(Player leaver, String messageKey) {
        if (messageKey == null || messageKey.isEmpty()) return;

        String rawMessage = messageKey.replace("%player%", leaver.getName());
        Component messageComponent = LegacyComponentSerializer.legacySection().deserialize(Utils.color(rawMessage));
        Bukkit.getOnlinePlayers().forEach(player -> player.sendMessage(messageComponent));
    }

    private void executeLeaveCommands(Player player) {
        List<String> commandsToRun = getSettings().getCommandsOnLeave();
        if (commandsToRun == null || commandsToRun.isEmpty()) return;

        String playerName = player.getName();
        commandsToRun.forEach(command ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", playerName)));
    }

    private Player getDamagerFromEntity(Entity initialDamager) {
        Entity currentEntity = initialDamager;

        for (int i = 0; i < getSettings().getMaxDamagerSearchDepth() && currentEntity != null; i++) {
            if (currentEntity instanceof Player player) return player;

            if (currentEntity instanceof Projectile projectile) {
                ProjectileSource shooter = projectile.getShooter();
                if (shooter instanceof Player playerShooter) return playerShooter;
                if (shooter instanceof Entity entityShooter) {
                    currentEntity = entityShooter;
                    continue;
                }
                return null;
            }

            if (currentEntity instanceof TNTPrimed tnt) {
                Entity source = tnt.getSource();
                if (source != null && source != tnt) {
                    currentEntity = source;
                    continue;
                }
                return null;
            }

            if (currentEntity instanceof AreaEffectCloud cloud) {
                ProjectileSource source = cloud.getSource();
                if (source instanceof Player playerSource) return playerSource;
                if (source instanceof Entity entitySource) {
                    currentEntity = entitySource;
                    continue;
                }
                return null;
            }

            if (currentEntity instanceof Firework firework) {
                return getFireworkShooter(firework);
            }
            return null;
        }
        return null;
    }

    private Player getFireworkShooter(Firework firework) {
        for (MetadataValue meta : firework.getMetadata(FIREWORK_META_KEY)) {
            if (plugin.equals(meta.getOwningPlugin())) {
                firework.removeMetadata(FIREWORK_META_KEY, plugin);
                if (meta.value() instanceof UUID shooterUuid) {
                    return Bukkit.getPlayer(shooterUuid);
                }
                return null;
            }
        }
        return null;
    }
}
