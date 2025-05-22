package ru.leymooo.antirelog.listeners;

import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;
import ru.leymooo.antirelog.config.Messages;
import ru.leymooo.antirelog.config.Settings;
import ru.leymooo.antirelog.manager.PvPManager;
import ru.leymooo.antirelog.util.Utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@RequiredArgsConstructor
public class PvPListener implements Listener {

    private static final String FIREWORK_META_KEY = "ar-f-shooter";
    private static final double MAX_TELEPORT_DISTANCE_SQUARED = 100.0;
    private static final int TELEPORT_GRACE_PERIOD_TICKS = 5;

    private final Plugin plugin;
    private final PvPManager pvpManager;
    private final Settings settings;
    private final Messages messages;
    private final Map<Player, AtomicInteger> allowedTeleports = new HashMap<>();

    public PvPListener(Plugin plugin, PvPManager pvpManager, Settings settings) {
        this.plugin = plugin;
        this.pvpManager = pvpManager;
        this.settings = settings;
        this.messages = settings.getMessages();

        startTeleportCleanupTask();
    }

    private void startTeleportCleanupTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            allowedTeleports.values().forEach(counter -> counter.incrementAndGet());
            allowedTeleports.entrySet().removeIf(entry ->
                    entry.getValue().get() >= TELEPORT_GRACE_PERIOD_TICKS
            );
        }, 1L, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity().getType() != EntityType.PLAYER) {
            return;
        }

        Player target = (Player) event.getEntity();
        Player damager = getDamager(event.getDamager());

        if (damager != null) {
            pvpManager.playerDamagedByPlayer(damager, target);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (settings.isCancelInteractWithEntities() &&
                pvpManager.isPvPModeEnabled() &&
                pvpManager.isInPvP(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityCombustByEntity(EntityCombustByEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }

        Player damager = getDamager(event.getCombuster());
        if (damager != null) {
            pvpManager.playerDamagedByPlayer(damager, target);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (event.getProjectile() instanceof Firework &&
                event.getEntity().getType() == EntityType.PLAYER) {
            event.getProjectile().setMetadata(FIREWORK_META_KEY,
                    new FixedMetadataValue(plugin, event.getEntity().getUniqueId()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        if (!(event.getPotion().getShooter() instanceof Player shooter)) {
            return;
        }

        Set<PotionEffectType> harmfulEffects = Set.of(
                PotionEffectType.POISON,
                PotionEffectType.WITHER,
                PotionEffectType.INSTANT_DAMAGE
        );

        for (LivingEntity entity : event.getAffectedEntities()) {
            if (entity instanceof Player target && !target.equals(shooter)) {
                boolean hasHarmfulEffect = event.getPotion().getEffects().stream()
                        .anyMatch(effect -> harmfulEffects.contains(effect.getType()));

                if (hasHarmfulEffect) {
                    pvpManager.playerDamagedByPlayer(shooter, target);
                    break;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!settings.isDisableTeleportsInPvp() || !pvpManager.isInPvP(event.getPlayer())) {
            return;
        }

        Player player = event.getPlayer();
        TeleportCause cause = event.getCause();

        if (allowedTeleports.containsKey(player)) {
            return;
        }

        if (cause == TeleportCause.CHORUS_FRUIT || cause == TeleportCause.ENDER_PEARL) {
            allowedTeleports.put(player, new AtomicInteger(0));
            return;
        }

        if (isWorldChange(event) || isLongDistanceTeleport(event)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (!settings.isDisableCommandsInPvp() || !pvpManager.isInPvP(event.getPlayer())) {
            return;
        }

        String command = extractCommand(event.getMessage());
        if (pvpManager.isCommandWhiteListed(command)) {
            return;
        }

        event.setCancelled(true);
        sendCommandDisabledMessage(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerKick(PlayerKickEvent event) {
        Player player = event.getPlayer();

        if (pvpManager.isInSilentPvP(player)) {
            pvpManager.stopPvPSilent(player);
            return;
        }

        if (!pvpManager.isInPvP(player)) {
            return;
        }

        pvpManager.stopPvPSilent(player);

        if (shouldKillOnKick(event)) {
            handleKickedInPvp(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        allowedTeleports.remove(player);

        if (settings.isHideLeaveMessage()) {
            event.setQuitMessage(null);
        }

        if (pvpManager.isInPvP(player)) {
            handlePlayerLeave(player);
        } else if (pvpManager.isInSilentPvP(player)) {
            pvpManager.stopPvPSilent(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (settings.isHideDeathMessage()) {
            event.setDeathMessage(null);
        }

        Player player = event.getEntity();
        if (pvpManager.isInSilentPvP(player) || pvpManager.isInPvP(player)) {
            pvpManager.stopPvPSilent(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (settings.isHideJoinMessage()) {
            event.setJoinMessage(null);
        }
    }

    private boolean isWorldChange(PlayerTeleportEvent event) {
        return !event.getFrom().getWorld().equals(event.getTo().getWorld());
    }

    private boolean isLongDistanceTeleport(PlayerTeleportEvent event) {
        return event.getFrom().distanceSquared(event.getTo()) > MAX_TELEPORT_DISTANCE_SQUARED;
    }

    private String extractCommand(String message) {
        return message.split(" ")[0].replaceFirst("/", "");
    }

    private void sendCommandDisabledMessage(Player player) {
        String messageText = messages.getCommandsDisabled();
        if (messageText.isEmpty()) {
            return;
        }

        String formattedMessage = Utils.replaceTime(messageText, pvpManager.getTimeRemainingInPvP(player));
        Component message = LegacyComponentSerializer.legacySection().deserialize(formattedMessage);
        player.sendMessage(message);
    }

    private boolean shouldKillOnKick(PlayerKickEvent event) {
        if (settings.getKickMessages().isEmpty()) {
            return true;
        }

        Component reason = event.reason();
        if (reason == null) {
            return false;
        }

        String reasonText = LegacyComponentSerializer.legacySection()
                .serialize(reason)
                .toLowerCase();

        return settings.getKickMessages().stream()
                .anyMatch(killReason -> reasonText.contains(killReason.toLowerCase()));
    }

    private void handleKickedInPvp(Player player) {
        if (settings.isKillOnKick()) {
            player.setHealth(0.0);
            sendPlayerLeftMessage(player);
        }
        if (settings.isRunCommandsOnKick()) {
            executeLeaveCommands(player);
        }
    }

    private void handlePlayerLeave(Player player) {
        pvpManager.stopPvPSilent(player);

        if (settings.isKillOnLeave()) {
            sendPlayerLeftMessage(player);
            player.setHealth(0.0);
        }

        executeLeaveCommands(player);
    }

    private void sendPlayerLeftMessage(Player player) {
        String messageText = messages.getPvpLeaved();
        if (messageText.isEmpty()) {
            return;
        }

        String formattedMessage = Utils.color(messageText.replace("%player%", player.getName()));
        Component message = LegacyComponentSerializer.legacySection().deserialize(formattedMessage);

        Bukkit.getOnlinePlayers().forEach(onlinePlayer -> onlinePlayer.sendMessage(message));
    }

    private void executeLeaveCommands(Player player) {
        if (settings.getCommandsOnLeave().isEmpty()) {
            return;
        }

        settings.getCommandsOnLeave()
                .forEach(command -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        command.replace("%player%", player.getName())));
    }

    private Player getDamager(Entity damager) {
        return switch (damager) {
            case Player player -> player;
            case Projectile projectile when projectile.getShooter() instanceof Player ->
                    (Player) projectile.getShooter();
            case TNTPrimed tnt when tnt.getSource() != null ->
                    getDamager(tnt.getSource());
            case AreaEffectCloud cloud when cloud.getSource() instanceof Player ->
                    (Player) cloud.getSource();
            case Firework firework when firework.hasMetadata(FIREWORK_META_KEY) ->
                    getFireworkShooter(firework);
            default -> null;
        };
    }

    private Player getFireworkShooter(Firework firework) {
        MetadataValue metadata = firework.getMetadata(FIREWORK_META_KEY).stream()
                .filter(meta -> meta.getOwningPlugin().equals(plugin))
                .findFirst()
                .orElse(null);

        if (metadata != null) {
            firework.removeMetadata(FIREWORK_META_KEY, plugin);
            UUID shooterUuid = (UUID) metadata.value();
            return Bukkit.getPlayer(shooterUuid);
        }

        return null;
    }
}