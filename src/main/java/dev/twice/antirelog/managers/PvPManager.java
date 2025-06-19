package dev.twice.antirelog.managers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.Title.Times;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import dev.twice.antirelog.Main;
import dev.twice.antirelog.config.Settings;
import dev.twice.antirelog.events.PvpPreStartEvent;
import dev.twice.antirelog.events.PvpStartedEvent;
import dev.twice.antirelog.events.PvpStoppedEvent;
import dev.twice.antirelog.events.PvpTimeUpdateEvent;
import dev.twice.antirelog.events.PvpPreStartEvent.PvPStatus;
import org.codemc.worldguardwrapper.WorldGuardWrapper;
import org.codemc.worldguardwrapper.region.IWrappedRegion;
import dev.twice.antirelog.util.CommandMapUtils;
import dev.twice.antirelog.util.Utils;

public class PvPManager {
    private final Settings settings;
    private final Main plugin;
    private final Map<Player, Integer> pvpMap = new HashMap<>();
    private final Map<Player, Integer> silentPvpMap = new HashMap<>();
    private final PowerUpsManager powerUpsManager;
    private final BossbarManager bossbarManager;
    private final Set<String> whiteListedCommands = new HashSet<>();
    private final Set<String> cachedLowerCaseIgnoredWgRegions = new HashSet<>();
    private final Set<String> cachedLowerCaseDisabledWorlds = new HashSet<>();
    private BukkitTask pvpTimerTask;

    public PvPManager(Settings settings, Main plugin) {
        this.settings = settings;
        this.plugin = plugin;
        this.powerUpsManager = new PowerUpsManager(settings);
        this.bossbarManager = new BossbarManager(settings);
        this.onPluginEnable();
    }

    public void onPluginDisable() {
        if (this.pvpTimerTask != null && !this.pvpTimerTask.isCancelled()) {
            this.pvpTimerTask.cancel();
            this.pvpTimerTask = null;
        }
        this.pvpMap.clear();
        this.silentPvpMap.clear();
        this.bossbarManager.clearBossbars();
        this.whiteListedCommands.clear();
        this.cachedLowerCaseIgnoredWgRegions.clear();
        this.cachedLowerCaseDisabledWorlds.clear();
    }

    public void onPluginEnable() {
        this.loadWhitelistedCommands();
        this.cacheDisabledWorldsAndRegions();
        this.startPvpTimer();
        this.bossbarManager.createBossBars();
    }

    private void loadWhitelistedCommands() {
        this.whiteListedCommands.clear();
        if (!this.settings.isDisableCommandsInPvp() || this.settings.getWhiteListedCommands() == null || this.settings.getWhiteListedCommands().isEmpty()) {
            return;
        }

        for (String commandName : this.settings.getWhiteListedCommands()) {
            if (commandName == null) continue;
            String lowerCaseName = commandName.toLowerCase(Locale.ROOT);
            this.whiteListedCommands.add(lowerCaseName);
            Optional<Command> commandOptional = CommandMapUtils.getCommand(commandName);
            commandOptional.ifPresent(command -> {
                this.whiteListedCommands.add(command.getName().toLowerCase(Locale.ROOT));
                command.getAliases().forEach(alias -> this.whiteListedCommands.add(alias.toLowerCase(Locale.ROOT)));
            });
        }
    }

    private void cacheDisabledWorldsAndRegions() {
        this.cachedLowerCaseDisabledWorlds.clear();
        if (this.settings.getDisabledWorlds() != null) {
            for (String worldName : this.settings.getDisabledWorlds()) {
                if (worldName != null) {
                    this.cachedLowerCaseDisabledWorlds.add(worldName.toLowerCase(Locale.ROOT));
                }
            }
        }

        this.cachedLowerCaseIgnoredWgRegions.clear();
        if (this.settings.getIgnoredWgRegions() != null) {
            for (String regionName : this.settings.getIgnoredWgRegions()) {
                if (regionName != null) {
                    this.cachedLowerCaseIgnoredWgRegions.add(regionName.toLowerCase(Locale.ROOT));
                }
            }
        }
    }

    private void startPvpTimer() {
        if (this.pvpTimerTask != null && !this.pvpTimerTask.isCancelled()) {
            this.pvpTimerTask.cancel();
        }

        this.pvpTimerTask = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, () -> {
            if (!this.pvpMap.isEmpty() || !this.silentPvpMap.isEmpty()) {
                this.updatePvpTimers(this.pvpMap, false);
                this.updatePvpTimers(this.silentPvpMap, true);
            }
        }, 20L, 20L);
    }

    private void updatePvpTimers(Map<Player, Integer> map, boolean isSilentMap) {
        if (map.isEmpty()) {
            return;
        }
        List<Player> playersToUpdate = new ArrayList<>(map.keySet());

        for (Player player : playersToUpdate) {
            if (!player.isOnline()) {
                map.remove(player);
                if (!isSilentMap) {
                    bossbarManager.clearBossbar(player);
                }
                continue;
            }

            int currentTime = map.getOrDefault(player, 0);
            int timeRemaining = currentTime - 1;

            if (timeRemaining <= 0 || (this.settings.isDisablePvpInIgnoredRegion() && this.isInIgnoredRegion(player))) {
                if (isSilentMap) {
                    this.stopPvPSilent(player);
                } else {
                    this.stopPvP(player);
                }
            } else {
                map.put(player, timeRemaining);
                if (!isSilentMap) {
                    this.bossbarManager.setBossBar(player, timeRemaining);
                    String actionBar = this.settings.getMessages().getInPvpActionbar();
                    if (actionBar != null && !actionBar.isEmpty()) {
                        this.sendActionBar(player, Utils.color(Utils.replaceTime(actionBar, timeRemaining)));
                    }
                }
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
        if (attacker == null || defender == null || attacker.equals(defender) || attacker.getWorld() != defender.getWorld()) {
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
        PvPStatus statusForEvent = PvPStatus.ALL_NOT_IN_PVP;

        PvpPreStartEvent pvpPreStartEvent = new PvpPreStartEvent(player, player, this.settings.getPvpTime(), statusForEvent);
        Bukkit.getPluginManager().callEvent(pvpPreStartEvent);
        if (pvpPreStartEvent.isCancelled()) {
            return;
        }

        if (!bypassed) {
            String message = this.settings.getMessages().getPvpStarted();
            if (message != null && !message.isEmpty()) {
                player.sendMessage(LegacyComponentSerializer.legacySection().deserialize(Utils.color(message)));
            }
            if (this.settings.isDisablePowerups()) {
                this.powerUpsManager.disablePowerUpsWithRunCommands(player);
            }
            this.sendTitles(player, true);
        }

        this.updatePvpMode(player, bypassed, this.settings.getPvpTime());
        player.setNoDamageTicks(0);
        Bukkit.getPluginManager().callEvent(new PvpStartedEvent(player, player, this.settings.getPvpTime(), statusForEvent));
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

            boolean attackerInAnyPvp = this.isInPvP(attacker) || this.isInSilentPvP(attacker);
            boolean defenderInAnyPvp = this.isInPvP(defender) || this.isInSilentPvP(defender);

            if (attackerInAnyPvp && defenderInAnyPvp) {
                this.updateAttackerAndCallEvent(attacker, defender, attackerBypassed);
                this.updateDefenderAndCallEvent(defender, attacker, defenderBypassed);
            } else {
                PvPStatus pvpStatus;
                if (attackerInAnyPvp) {
                    pvpStatus = PvPStatus.ATTACKER_IN_PVP;
                } else if (defenderInAnyPvp) {
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

    private void startPvp(Player player, boolean bypassed, boolean isAttackerLogicContext) {
        if (!bypassed) {
            String message = this.settings.getMessages().getPvpStarted();
            if (message != null && !message.isEmpty()) {
                player.sendMessage(LegacyComponentSerializer.legacySection().deserialize(Utils.color(message)));
            }
            if (this.settings.isDisablePowerups()) {
                this.powerUpsManager.disablePowerUpsWithRunCommands(player);
            }
            this.sendTitles(player, true);
        }
        this.updatePvpMode(player, bypassed, this.settings.getPvpTime());
        player.setNoDamageTicks(0);
    }

    private void updateAttackerAndCallEvent(Player attacker, Player defender, boolean bypassed) {
        int oldTime = bypassed ? this.getTimeRemainingInPvPSilent(attacker) : this.getTimeRemainingInPvP(attacker);
        this.updatePvpMode(attacker, bypassed, this.settings.getPvpTime());
        PvpTimeUpdateEvent pvpTimeUpdateEvent = new PvpTimeUpdateEvent(attacker, oldTime, this.settings.getPvpTime());
        pvpTimeUpdateEvent.setSourcePlayer(defender);
        pvpTimeUpdateEvent.setTargetPlayer(attacker);
        Bukkit.getPluginManager().callEvent(pvpTimeUpdateEvent);
    }

    private void updateDefenderAndCallEvent(Player defender, Player attacker, boolean bypassed) {
        int oldTime = bypassed ? this.getTimeRemainingInPvPSilent(defender) : this.getTimeRemainingInPvP(defender);
        this.updatePvpMode(defender, bypassed, this.settings.getPvpTime());
        PvpTimeUpdateEvent pvpTimeUpdateEvent = new PvpTimeUpdateEvent(defender, oldTime, this.settings.getPvpTime());
        pvpTimeUpdateEvent.setSourcePlayer(attacker);
        pvpTimeUpdateEvent.setTargetPlayer(defender);
        Bukkit.getPluginManager().callEvent(pvpTimeUpdateEvent);
    }

    private void updatePvpMode(Player player, boolean bypassed, int newTime) {
        (bypassed ? this.silentPvpMap : this.pvpMap).put(player, newTime);
        if (!bypassed) {
            this.bossbarManager.setBossBar(player, newTime);
            String actionBar = this.settings.getMessages().getInPvpActionbar();
            if (actionBar != null && !actionBar.isEmpty()) {
                this.sendActionBar(player, Utils.color(Utils.replaceTime(actionBar, newTime)));
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

    private void callUpdateEvent(Player player, int oldTime, int newTime) {
        PvpTimeUpdateEvent pvpTimeUpdateEvent = new PvpTimeUpdateEvent(player, oldTime, newTime);
        Bukkit.getPluginManager().callEvent(pvpTimeUpdateEvent);
    }

    public void stopPvP(Player player) {
        if (player == null) return;

        boolean wasInPvpBeforeSilentStop = this.pvpMap.containsKey(player);
        if (!player.isOnline() && !wasInPvpBeforeSilentStop && !this.silentPvpMap.containsKey(player)) {
            this.stopPvPSilent(player);
            return;
        }

        this.stopPvPSilent(player);

        if (wasInPvpBeforeSilentStop && player.isOnline()) {
            this.sendTitles(player, false);
            String message = this.settings.getMessages().getPvpStopped();
            if (message != null && !message.isEmpty()) {
                player.sendMessage(LegacyComponentSerializer.legacySection().deserialize(Utils.color(message)));
            }
            String actionBar = this.settings.getMessages().getPvpStoppedActionbar();
            if (actionBar != null && !actionBar.isEmpty()) {
                this.sendActionBar(player, Utils.color(actionBar));
            }
        }
    }

    public void stopPvPSilent(Player player) {
        if (player == null) {
            return;
        }
        boolean removedFromPvp = this.pvpMap.remove(player) != null;
        boolean removedFromSilentPvp = this.silentPvpMap.remove(player) != null;

        if (removedFromPvp || removedFromSilentPvp) {
            this.bossbarManager.clearBossbar(player);
            Bukkit.getPluginManager().callEvent(new PvpStoppedEvent(player));
        }
    }

    public boolean isCommandWhiteListed(String command) {
        if (command == null || this.whiteListedCommands.isEmpty()) {
            return false;
        }
        return this.whiteListedCommands.contains(command.toLowerCase(Locale.ROOT));
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
        if (message == null || message.isEmpty() || !player.isOnline()) {
            return;
        }
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(message));
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
                this.cachedLowerCaseDisabledWorlds.contains(player.getWorld().getName().toLowerCase(Locale.ROOT));
    }

    public boolean isInIgnoredRegion(Player player) {
        if (player == null || !player.isOnline() || !this.plugin.isWorldguardEnabled() || this.cachedLowerCaseIgnoredWgRegions.isEmpty()) {
            return false;
        }

        Set<IWrappedRegion> playerRegions = WorldGuardWrapper.getInstance().getRegions(player.getLocation());
        if (playerRegions == null || playerRegions.isEmpty()) {
            return false;
        }

        return playerRegions.stream()
                .filter(Objects::nonNull)
                .map(region -> region.getId().toLowerCase(Locale.ROOT))
                .anyMatch(this.cachedLowerCaseIgnoredWgRegions::contains);
    }

    public PowerUpsManager getPowerUpsManager() {
        return this.powerUpsManager;
    }

    public BossbarManager getBossbarManager() {
        return this.bossbarManager;
    }
}