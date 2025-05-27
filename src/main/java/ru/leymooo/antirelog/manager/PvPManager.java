package ru.leymooo.antirelog.manager;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.Title.Times;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.leymooo.antirelog.Antirelog;
import ru.leymooo.antirelog.config.Settings;
import ru.leymooo.antirelog.event.PvpPreStartEvent;
import ru.leymooo.antirelog.event.PvpStartedEvent;
import ru.leymooo.antirelog.event.PvpStoppedEvent;
import ru.leymooo.antirelog.event.PvpTimeUpdateEvent;
import ru.leymooo.antirelog.event.PvpPreStartEvent.PvPStatus;
import org.codemc.worldguardwrapper.WorldGuardWrapper;
import org.codemc.worldguardwrapper.region.IWrappedRegion;
import ru.leymooo.antirelog.util.ActionBar;
import ru.leymooo.antirelog.util.CommandMapUtils;
import ru.leymooo.antirelog.util.Utils;

public class PvPManager {
    private final Settings settings;
    private final Antirelog plugin;
    private final Map<Player, Integer> pvpMap = new HashMap<>();
    private final Map<Player, Integer> silentPvpMap = new HashMap<>();
    private final PowerUpsManager powerUpsManager;
    private final BossbarManager bossbarManager;
    private final Set<String> whiteListedCommands = new HashSet<>();
    private BukkitTask pvpTimerTask;

    public PvPManager(Settings settings, Antirelog plugin) {
        this.settings = settings;
        this.plugin = plugin;
        this.powerUpsManager = new PowerUpsManager(settings);
        this.bossbarManager = new BossbarManager(settings);
        this.onPluginEnable();
    }

    public void onPluginDisable() {
        if (this.pvpTimerTask != null) {
            this.pvpTimerTask.cancel();
            this.pvpTimerTask = null;
        }
        this.pvpMap.clear();
        this.silentPvpMap.clear();
        this.bossbarManager.clearBossbars();
    }

    public void onPluginEnable() {
        this.loadWhitelistedCommands();
        this.startPvpTimer();
        this.bossbarManager.createBossBars();
    }

    private void loadWhitelistedCommands() {
        this.whiteListedCommands.clear();
        if (!this.settings.isDisableCommandsInPvp() || this.settings.getWhiteListedCommands().isEmpty()) {
            return;
        }

        for (String commandName : this.settings.getWhiteListedCommands()) {
            String lowerCaseName = commandName.toLowerCase();
            this.whiteListedCommands.add(lowerCaseName);
            Optional<Command> commandOptional = CommandMapUtils.getCommand(commandName);
            commandOptional.ifPresent(command -> {
                this.whiteListedCommands.add(command.getName().toLowerCase());
                command.getAliases().forEach(alias -> this.whiteListedCommands.add(alias.toLowerCase()));
            });
        }
    }

    private void startPvpTimer() {
        if (this.pvpTimerTask != null) {
            this.pvpTimerTask.cancel();
        }

        this.pvpTimerTask = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, () -> {
            if (!this.pvpMap.isEmpty() || !this.silentPvpMap.isEmpty()) {
                this.updatePvpTimers(this.pvpMap, false);
                this.updatePvpTimers(this.silentPvpMap, true);
            }
        }, 20L, 20L);
    }

    private void updatePvpTimers(Map<Player, Integer> map, boolean bypassed) {
        if (map.isEmpty()) {
            return;
        }

        List<Player> playersInPvp = new ArrayList<>(map.keySet());

        for (Player player : playersInPvp) {
            if (!player.isOnline()) {
                map.remove(player);
                if (!bypassed) {
                    bossbarManager.clearBossbar(player);
                }
                continue;
            }

            int currentTime = bypassed ? this.getTimeRemainingInPvPSilent(player) : this.getTimeRemainingInPvP(player);
            int timeRemaining = currentTime - 1;

            if (timeRemaining <= 0 || (this.settings.isDisablePvpInIgnoredRegion() && this.isInIgnoredRegion(player))) {
                if (bypassed) {
                    this.stopPvPSilent(player);
                } else {
                    this.stopPvP(player);
                }
            } else {
                this.updatePvpMode(player, bypassed, timeRemaining);
                this.callUpdateEvent(player, currentTime, timeRemaining);
            }
        }
    }

    public boolean isInPvP(Player player) {
        return player != null && this.pvpMap.containsKey(player);
    }

    public boolean isInSilentPvP(Player player) {
        return player != null && this.silentPvpMap.containsKey(player);
    }

    public int getTimeRemainingInPvP(Player player) {
        return player != null ? this.pvpMap.getOrDefault(player, 0) : 0;
    }

    public int getTimeRemainingInPvPSilent(Player player) {
        return player != null ? this.silentPvpMap.getOrDefault(player, 0) : 0;
    }

    public void playerDamagedByPlayer(Player attacker, Player defender) {
        if (attacker == null || defender == null || attacker.equals(defender) || !attacker.getWorld().equals(defender.getWorld())) {
            return;
        }
        if (defender.getGameMode() == GameMode.CREATIVE || attacker.hasMetadata("NPC") || defender.hasMetadata("NPC")) {
            return;
        }
        if (defender.isDead() || attacker.isDead()) {
            return;
        }
        this.tryStartPvP(attacker, defender);
    }

    public void forceStartPvP(Player player) {
        if (player == null || !player.isOnline() || this.isInPvP(player) || this.isInSilentPvP(player)) {
            return;
        }

        boolean bypassed = this.isHasBypassPermission(player);
        if (!bypassed) {
            String message = this.settings.getMessages().getPvpStarted();
            if (message != null && !message.isEmpty()) {
                Component component = LegacyComponentSerializer.legacySection().deserialize(Utils.color(message));
                player.sendMessage(component);
            }

            if (this.settings.isDisablePowerups()) {
                this.powerUpsManager.disablePowerUpsWithRunCommands(player);
            }
            this.sendTitles(player, true);
        }

        this.updatePvpMode(player, bypassed, this.settings.getPvpTime());
        player.setNoDamageTicks(0);
        Bukkit.getPluginManager().callEvent(new PvpStartedEvent(player, player, this.settings.getPvpTime(), PvPStatus.ALL_NOT_IN_PVP));
    }

    private void tryStartPvP(Player attacker, Player defender) {
        if (this.isInIgnoredWorld(attacker) || this.isInIgnoredRegion(attacker) || this.isInIgnoredRegion(defender)) {
            return;
        }

        if (!this.isPvPModeEnabled() && this.settings.isDisablePowerups()) {
            if (!this.isHasBypassPermission(attacker)) {
                this.powerUpsManager.disablePowerUpsWithRunCommands(attacker);
            }
            if (!this.isHasBypassPermission(defender)) {
                this.powerUpsManager.disablePowerUps(defender);
            }
            return;
        }

        if (this.isPvPModeEnabled()) {
            boolean attackerBypassed = this.isHasBypassPermission(attacker);
            boolean defenderBypassed = this.isHasBypassPermission(defender);

            if (attackerBypassed && defenderBypassed) {
                return;
            }

            boolean attackerInPvp = this.isInPvP(attacker) || this.isInSilentPvP(attacker);
            boolean defenderInPvp = this.isInPvP(defender) || this.isInSilentPvP(defender);

            if (attackerInPvp && defenderInPvp) {
                this.updateAttackerAndCallEvent(attacker, defender, attackerBypassed);
                this.updateDefenderAndCallEvent(defender, attacker, defenderBypassed);
            } else {
                PvPStatus pvpStatus;
                if (attackerInPvp) {
                    pvpStatus = PvPStatus.ATTACKER_IN_PVP;
                } else if (defenderInPvp) {
                    pvpStatus = PvPStatus.DEFENDER_IN_PVP;
                } else {
                    pvpStatus = PvPStatus.ALL_NOT_IN_PVP;
                }

                if (this.callPvpPreStartEvent(defender, attacker, pvpStatus)) {
                    if (pvpStatus == PvPStatus.ALL_NOT_IN_PVP) {
                        this.startPvp(attacker, attackerBypassed, true);
                        this.startPvp(defender, defenderBypassed, false);
                    } else if (pvpStatus == PvPStatus.ATTACKER_IN_PVP) {
                        this.updateAttackerAndCallEvent(attacker, defender, attackerBypassed);
                        this.startPvp(defender, defenderBypassed, false);
                    } else {
                        this.updateDefenderAndCallEvent(defender, attacker, defenderBypassed);
                        this.startPvp(attacker, attackerBypassed, true);
                    }
                    Bukkit.getPluginManager().callEvent(new PvpStartedEvent(defender, attacker, this.settings.getPvpTime(), pvpStatus));
                }
            }
        }
    }

    private void startPvp(Player player, boolean bypassed, boolean isAttackerLogic) {
        if (!bypassed) {
            String message = this.settings.getMessages().getPvpStarted();
            if (message != null && !message.isEmpty()) {
                Component component = LegacyComponentSerializer.legacySection().deserialize(Utils.color(message));
                player.sendMessage(component);
            }

            if (isAttackerLogic && this.settings.isDisablePowerups()) {
                this.powerUpsManager.disablePowerUpsWithRunCommands(player);
            }
            this.sendTitles(player, true);
        }
        this.updatePvpMode(player, bypassed, this.settings.getPvpTime());
        player.setNoDamageTicks(0);
    }

    private void updatePvpMode(Player player, boolean bypassed, int newTime) {
        if (bypassed) {
            this.silentPvpMap.put(player, newTime);
        } else {
            this.pvpMap.put(player, newTime);
            this.bossbarManager.setBossBar(player, newTime);
            String actionBar = this.settings.getMessages().getInPvpActionbar();
            if (actionBar != null && !actionBar.isEmpty()) {
                String formattedActionBar = Utils.color(Utils.replaceTime(actionBar, newTime));
                this.sendActionBar(player, formattedActionBar);
            }
            if (this.settings.isDisablePowerups()) {
                this.powerUpsManager.disablePowerUps(player);
            }
        }
    }

    private boolean callPvpPreStartEvent(Player defender, Player attacker, PvPStatus pvpStatus) {
        PvpPreStartEvent pvpPreStartEvent = new PvpPreStartEvent(defender, attacker, this.settings.getPvpTime(), pvpStatus);
        Bukkit.getPluginManager().callEvent(pvpPreStartEvent);
        return !pvpPreStartEvent.isCancelled();
    }

    private void updateAttackerAndCallEvent(Player attacker, Player defender, boolean bypassed) {
        int oldTime = bypassed ? this.getTimeRemainingInPvPSilent(attacker) : this.getTimeRemainingInPvP(attacker);
        this.updatePvpMode(attacker, bypassed, this.settings.getPvpTime());
        PvpTimeUpdateEvent pvpTimeUpdateEvent = new PvpTimeUpdateEvent(attacker, oldTime, this.settings.getPvpTime());
        Bukkit.getPluginManager().callEvent(pvpTimeUpdateEvent);
    }

    private void updateDefenderAndCallEvent(Player defender, Player attackedBy, boolean bypassed) {
        int oldTime = bypassed ? this.getTimeRemainingInPvPSilent(defender) : this.getTimeRemainingInPvP(defender);
        this.updatePvpMode(defender, bypassed, this.settings.getPvpTime());
        PvpTimeUpdateEvent pvpTimeUpdateEvent = new PvpTimeUpdateEvent(defender, oldTime, this.settings.getPvpTime());
        Bukkit.getPluginManager().callEvent(pvpTimeUpdateEvent);
    }

    private void callUpdateEvent(Player player, int oldTime, int newTime) {
        PvpTimeUpdateEvent pvpTimeUpdateEvent = new PvpTimeUpdateEvent(player, oldTime, newTime);
        Bukkit.getPluginManager().callEvent(pvpTimeUpdateEvent);
    }

    public void stopPvP(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        this.stopPvPSilent(player);
        this.sendTitles(player, false);

        String message = this.settings.getMessages().getPvpStopped();
        if (message != null && !message.isEmpty()) {
            Component component = LegacyComponentSerializer.legacySection().deserialize(Utils.color(message));
            player.sendMessage(component);
        }

        String actionBar = this.settings.getMessages().getPvpStoppedActionbar();
        if (actionBar != null && !actionBar.isEmpty()) {
            this.sendActionBar(player, Utils.color(actionBar));
        }
    }

    public void stopPvPSilent(Player player) {
        if (player == null) {
            return;
        }
        boolean wasInPvp = this.pvpMap.remove(player) != null;
        boolean wasInSilentPvp = this.silentPvpMap.remove(player) != null;

        if (wasInPvp || wasInSilentPvp) {
            this.bossbarManager.clearBossbar(player);
            Bukkit.getPluginManager().callEvent(new PvpStoppedEvent(player));
        }
    }

    public boolean isCommandWhiteListed(String command) {
        if (command == null || this.whiteListedCommands.isEmpty()) {
            return false;
        }
        return this.whiteListedCommands.contains(command.toLowerCase());
    }

    private void sendTitles(Player player, boolean isPvpStarted) {
        String titleTextKey = isPvpStarted ? this.settings.getMessages().getPvpStartedTitle() : this.settings.getMessages().getPvpStoppedTitle();
        String subtitleTextKey = isPvpStarted ? this.settings.getMessages().getPvpStartedSubtitle() : this.settings.getMessages().getPvpStoppedSubtitle();

        if ((titleTextKey == null || titleTextKey.isEmpty()) && (subtitleTextKey == null || subtitleTextKey.isEmpty())) {
            return;
        }

        Component title = (titleTextKey == null || titleTextKey.isEmpty()) ? Component.empty() : LegacyComponentSerializer.legacySection().deserialize(Utils.color(titleTextKey));
        Component subtitle = (subtitleTextKey == null || subtitleTextKey.isEmpty()) ? Component.empty() : LegacyComponentSerializer.legacySection().deserialize(Utils.color(subtitleTextKey));

        Times times = Times.times(Duration.ofMillis(500L), Duration.ofMillis(1500L), Duration.ofMillis(500L));
        Title titleObj = Title.title(title, subtitle, times);
        player.showTitle(titleObj);
    }

    private void sendActionBar(Player player, String message) {
        ActionBar.sendAction(player, message);
    }

    public boolean isPvPModeEnabled() {
        return this.settings.getPvpTime() > 0;
    }

    public boolean isBypassed(Player player) {
        return player != null && (this.isHasBypassPermission(player) || this.isInIgnoredWorld(player));
    }

    public boolean isHasBypassPermission(Player player) {
        return player != null && player.hasPermission("antirelog.bypass");
    }

    public boolean isInIgnoredWorld(Player player) {
        return player != null && player.getWorld() != null &&
                this.settings.getDisabledWorlds().contains(player.getWorld().getName().toLowerCase());
    }

    public boolean isInIgnoredRegion(Player player) {
        if (player == null || !this.plugin.isWorldguardEnabled() || this.settings.getIgnoredWgRegions().isEmpty()) {
            return false;
        }

        Set<String> ignoredRegionsLowerCase = this.settings.getIgnoredWgRegions().stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        Set<IWrappedRegion> playerRegions = WorldGuardWrapper.getInstance().getRegions(player.getLocation());
        if (playerRegions == null || playerRegions.isEmpty()) {
            return false;
        }

        return playerRegions.stream()
                .filter(Objects::nonNull)
                .map(region -> region.getId().toLowerCase())
                .anyMatch(ignoredRegionsLowerCase::contains);
    }

    public PowerUpsManager getPowerUpsManager() {
        return this.powerUpsManager;
    }

    public BossbarManager getBossbarManager() {
        return this.bossbarManager;
    }
}