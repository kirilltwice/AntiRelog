package dev.twice.antirelog.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import dev.twice.antirelog.config.Messages;
import dev.twice.antirelog.config.Settings;
import dev.twice.antirelog.managers.PvPManager;
import dev.twice.antirelog.util.Utils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class PvPListener implements Listener {

    private static final String FIREWORK_META_KEY = "ar-f-shooter";
    private static final double MAX_TELEPORT_DISTANCE_SQUARED = 100.0D;
    private static final int TELEPORT_GRACE_PERIOD_TICKS = 5;
    private static final int MAX_DAMAGER_SEARCH_DEPTH = 10;

    private final Plugin plugin;
    private final PvPManager pvpManager;
    private final Settings settings;
    private final Messages messages;

    private final Map<Player, AtomicInteger> allowedTeleports = new ConcurrentHashMap<>();

    public PvPListener(Plugin plugin, PvPManager pvpManager, Settings settings) {
        this.plugin = plugin;
        this.pvpManager = pvpManager;
        this.settings = settings;
        this.messages = settings.getMessages();
        startTeleportCleanupTask();
    }

    private void startTeleportCleanupTask() {
        Runnable taskRunnable = () -> {
            allowedTeleports.forEach((player, counter) -> counter.incrementAndGet());
            allowedTeleports.entrySet().removeIf(entry -> entry.getValue().get() >= TELEPORT_GRACE_PERIOD_TICKS);
        };
        this.plugin.getServer().getScheduler().runTaskTimerAsynchronously(this.plugin, taskRunnable, 1L, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }
        Player damager = getDamagerFromEntity(event.getDamager());
        if (damager != null && !damager.equals(target)) {
            this.pvpManager.playerDamagedByPlayer(damager, target);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (this.settings.isCancelInteractWithEntities() && this.pvpManager.isPvPModeEnabled() && this.pvpManager.isInPvP(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityCombustByEntity(EntityCombustByEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }
        Player damager = getDamagerFromEntity(event.getCombuster());
        if (damager != null && !damager.equals(target)) {
            this.pvpManager.playerDamagedByPlayer(damager, target);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (event.getProjectile() instanceof Firework && event.getEntity() instanceof Player shooter) {
            event.getProjectile().setMetadata(FIREWORK_META_KEY, new FixedMetadataValue(this.plugin, shooter.getUniqueId()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        if (!(event.getPotion().getShooter() instanceof Player shooter)) {
            return;
        }

        boolean hasHarmfulEffect = false;
        for (PotionEffect effect : event.getPotion().getEffects()) {
            if (effect.getType().getEffectCategory() == PotionEffectType.Category.HARMFUL) {
                hasHarmfulEffect = true;
                break;
            }
        }

        if (hasHarmfulEffect) {
            for (LivingEntity entity : event.getAffectedEntities()) {
                if (entity instanceof Player target && !target.equals(shooter)) {
                    this.pvpManager.playerDamagedByPlayer(shooter, target);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (!this.settings.isDisableTeleportsInPvp() || !this.pvpManager.isInPvP(player)) {
            return;
        }

        if (this.allowedTeleports.containsKey(player)) {
            return;
        }

        TeleportCause cause = event.getCause();
        if (cause == TeleportCause.CHORUS_FRUIT || cause == TeleportCause.ENDER_PEARL) {
            this.allowedTeleports.put(player, new AtomicInteger(0));
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();

        if (from == null || to == null || from.getWorld() == null || to.getWorld() == null) {
            event.setCancelled(true);
            return;
        }

        if (!from.getWorld().equals(to.getWorld())) {
            event.setCancelled(true);
        } else if (from.distanceSquared(to) > MAX_TELEPORT_DISTANCE_SQUARED) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!this.settings.isDisableCommandsInPvp() || !this.pvpManager.isInPvP(player)) {
            return;
        }
        String command = extractCommand(event.getMessage());
        if (!this.pvpManager.isCommandWhiteListed(command)) {
            event.setCancelled(true);
            this.sendCommandDisabledMessage(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerKick(PlayerKickEvent event) {
        Player player = event.getPlayer();
        if (this.pvpManager.isInSilentPvP(player) || this.pvpManager.isInPvP(player)) {
            this.pvpManager.stopPvPSilent(player);
            if (this.shouldKillOnKick(event)) {
                this.handleKickedInPvp(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        this.allowedTeleports.remove(player);

        if (this.settings.isHideLeaveMessage()) {
            event.quitMessage(null);
        }

        if (this.pvpManager.isInPvP(player)) {
            this.handlePlayerLeave(player);
        } else if (this.pvpManager.isInSilentPvP(player)) {
            this.pvpManager.stopPvPSilent(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        if (this.settings.isHideDeathMessage()) {
            event.deathMessage(null);
        }

        if (this.pvpManager.isInSilentPvP(player) || this.pvpManager.isInPvP(player)) {
            this.pvpManager.stopPvPSilent(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (this.settings.isHideJoinMessage()) {
            event.joinMessage(null);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerInteractEnderChest(PlayerInteractEvent event) {
        if (!this.settings.isDisableEnderchestInPvp()) {
            return;
        }
        Player player = event.getPlayer();
        if (!this.pvpManager.isInPvP(player)) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || clickedBlock.getType() != Material.ENDER_CHEST) {
            return;
        }
        event.setCancelled(true);
        String messageText = this.messages.getItemDisabledInPvp();
        if (messageText != null && !messageText.isEmpty()) {
            Component message = LegacyComponentSerializer.legacySection().deserialize(Utils.color(messageText));
            player.sendMessage(message);
        }
    }

    private String extractCommand(String rawMessage) {
        if (rawMessage == null || rawMessage.isEmpty()) {
            return "";
        }
        String[] parts = rawMessage.split(" ", 2);
        String commandPart = parts[0];
        return commandPart.startsWith("/") ? commandPart.substring(1).toLowerCase(Locale.ROOT) : commandPart.toLowerCase(Locale.ROOT);
    }

    private void sendCommandDisabledMessage(Player player) {
        String messageText = this.messages.getCommandsDisabled();
        if (messageText != null && !messageText.isEmpty()) {
            String formattedMessage = Utils.color(Utils.replaceTime(messageText, this.pvpManager.getTimeRemainingInPvP(player)));
            Component component = LegacyComponentSerializer.legacySection().deserialize(formattedMessage);
            player.sendMessage(component);
        }
    }

    private boolean shouldKillOnKick(PlayerKickEvent event) {
        List<String> configuredKillReasons = this.settings.getKickMessages();
        if (configuredKillReasons.isEmpty()) {
            return true;
        }
        Component reasonComponent = event.reason();
        if (reasonComponent == null) {
            return false;
        }
        String reasonText = LegacyComponentSerializer.legacySection().serialize(reasonComponent).toLowerCase(Locale.ROOT);
        for (String configuredKillReason : configuredKillReasons) {
            if (reasonText.contains(configuredKillReason.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private void handleKickedInPvp(Player player) {
        if (this.settings.isKillOnKick()) {
            player.setHealth(0.0D);
            this.sendPlayerLeftMessage(player, this.messages.getPvpLeaved());
        }
        if (this.settings.isRunCommandsOnKick()) {
            this.executeLeaveCommands(player);
        }
    }

    private void handlePlayerLeave(Player player) {
        this.pvpManager.stopPvPSilent(player);
        if (this.settings.isKillOnLeave()) {
            player.setHealth(0.0D);
            this.sendPlayerLeftMessage(player, this.messages.getPvpLeaved());
        }
        this.executeLeaveCommands(player);
    }

    private void sendPlayerLeftMessage(Player leaver, String messageKey) {
        if (messageKey == null || messageKey.isEmpty()) {
            return;
        }
        String rawMessage = messageKey.replace("%player%", leaver.getName());
        Component messageComponent = LegacyComponentSerializer.legacySection().deserialize(Utils.color(rawMessage));
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.sendMessage(messageComponent);
        }
    }

    private void executeLeaveCommands(Player player) {
        List<String> commandsToRun = this.settings.getCommandsOnLeave();
        if (commandsToRun != null && !commandsToRun.isEmpty()) {
            String playerName = player.getName();
            for (String command : commandsToRun) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", playerName));
            }
        }
    }

    private Player getDamagerFromEntity(Entity initialDamager) {
        Entity currentEntity = initialDamager;
        for (int i = 0; i < MAX_DAMAGER_SEARCH_DEPTH && currentEntity != null; i++) {
            if (currentEntity instanceof Player player) {
                return player;
            }

            if (currentEntity instanceof Projectile projectile) {
                ProjectileSource shooter = projectile.getShooter();
                if (shooter instanceof Player playerShooter) {
                    return playerShooter;
                }
                if (shooter instanceof Entity entityShooter) {
                    currentEntity = entityShooter;
                    continue;
                }
                return null;
            }

            if (currentEntity instanceof TNTPrimed tnt) {
                Entity source = tnt.getSource();
                if (source != null && source != tnt && source instanceof Entity) {
                    currentEntity = source;
                    continue;
                }
                return null;
            }

            if (currentEntity instanceof AreaEffectCloud cloud) {
                ProjectileSource source = cloud.getSource();
                if (source instanceof Player playerSource) {
                    return playerSource;
                }
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
            if (this.plugin.equals(meta.getOwningPlugin())) {
                firework.removeMetadata(FIREWORK_META_KEY, this.plugin);
                if (meta.value() instanceof UUID shooterUuid) {
                    return Bukkit.getPlayer(shooterUuid);
                }
                return null;
            }
        }
        return null;
    }
}
