package ru.leymooo.antirelog.manager;

import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.codemc.worldguardwrapper.WorldGuardWrapper;
import org.codemc.worldguardwrapper.region.IWrappedRegion;
import ru.leymooo.antirelog.Antirelog;
import ru.leymooo.antirelog.config.Settings;
import ru.leymooo.antirelog.event.PvpPreStartEvent;
import ru.leymooo.antirelog.event.PvpPreStartEvent.PvPStatus;
import ru.leymooo.antirelog.event.PvpStartedEvent;
import ru.leymooo.antirelog.event.PvpStoppedEvent;
import ru.leymooo.antirelog.event.PvpTimeUpdateEvent;
import ru.leymooo.antirelog.util.ActionBar;
import ru.leymooo.antirelog.util.CommandMapUtils;
import ru.leymooo.antirelog.util.Utils;

import java.time.Duration;
import java.util.*;

public class PvPManager {

    private final Settings settings;
    private final Antirelog plugin;

    private final Map<Player, Integer> pvpMap = new HashMap<>();
    private final Map<Player, Integer> silentPvpMap = new HashMap<>();

    @Getter
    private final PowerUpsManager powerUpsManager;

    @Getter
    private final BossbarManager bossbarManager;

    private final Set<String> whiteListedCommands = new HashSet<>();
    private BukkitTask pvpTimerTask;

    public PvPManager(Settings settings, Antirelog plugin) {
        this.settings = settings;
        this.plugin = plugin;
        this.powerUpsManager = new PowerUpsManager(settings);
        this.bossbarManager = new BossbarManager(settings);
        onPluginEnable();
    }

    public void onPluginDisable() {
        if (pvpTimerTask != null) {
            pvpTimerTask.cancel();
            pvpTimerTask = null;
        }

        pvpMap.clear();
        silentPvpMap.clear();
        bossbarManager.clearBossbars();
    }

    public void onPluginEnable() {
        loadWhitelistedCommands();
        startPvpTimer();
        bossbarManager.createBossBars();
    }

    private void loadWhitelistedCommands() {
        whiteListedCommands.clear();

        if (!settings.isDisableCommandsInPvp() || settings.getWhiteListedCommands().isEmpty()) {
            return;
        }

        for (String commandName : settings.getWhiteListedCommands()) {
            String lowerCaseName = commandName.toLowerCase();
            whiteListedCommands.add(lowerCaseName);

            Optional<Command> commandOptional = CommandMapUtils.getCommand(commandName);
            commandOptional.ifPresent(command -> {
                whiteListedCommands.add(command.getName().toLowerCase());
                command.getAliases().forEach(alias ->
                        whiteListedCommands.add(alias.toLowerCase())
                );
            });
        }
    }

    private void startPvpTimer() {
        if (pvpTimerTask != null) {
            pvpTimerTask.cancel();
        }

        pvpTimerTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (pvpMap.isEmpty() && silentPvpMap.isEmpty()) {
                return;
            }

            updatePvpTimers(pvpMap, false);
            updatePvpTimers(silentPvpMap, true);
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
                continue;
            }

            int currentTime = bypassed ? getTimeRemainingInPvPSilent(player) : getTimeRemainingInPvP(player);
            int timeRemaining = currentTime - 1;

            if (timeRemaining <= 0 || (settings.isDisablePvpInIgnoredRegion() && isInIgnoredRegion(player))) {
                if (bypassed) {
                    stopPvPSilent(player);
                } else {
                    stopPvP(player);
                }
            } else {
                updatePvpMode(player, bypassed, timeRemaining);
                callUpdateEvent(player, currentTime, timeRemaining);
            }
        }
    }

    public boolean isInPvP(Player player) {
        return player != null && pvpMap.containsKey(player);
    }

    public boolean isInSilentPvP(Player player) {
        return player != null && silentPvpMap.containsKey(player);
    }

    public int getTimeRemainingInPvP(Player player) {
        return player != null ? pvpMap.getOrDefault(player, 0) : 0;
    }

    public int getTimeRemainingInPvPSilent(Player player) {
        return player != null ? silentPvpMap.getOrDefault(player, 0) : 0;
    }

    public void playerDamagedByPlayer(Player attacker, Player defender) {
        if (attacker == null || defender == null || attacker.equals(defender) ||
                !attacker.getWorld().equals(defender.getWorld())) {
            return;
        }

        if (defender.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        if (attacker.hasMetadata("NPC") || defender.hasMetadata("NPC")) {
            return;
        }

        if (defender.isDead() || attacker.isDead()) {
            return;
        }

        tryStartPvP(attacker, defender);
    }

    private void tryStartPvP(Player attacker, Player defender) {
        if (isInIgnoredWorld(attacker) || isInIgnoredRegion(attacker) || isInIgnoredRegion(defender)) {
            return;
        }

        if (!isPvPModeEnabled() && settings.isDisablePowerups()) {
            if (!isHasBypassPermission(attacker)) {
                powerUpsManager.disablePowerUpsWithRunCommands(attacker);
            }
            if (!isHasBypassPermission(defender)) {
                powerUpsManager.disablePowerUps(defender);
            }
            return;
        }

        if (!isPvPModeEnabled()) {
            return;
        }

        boolean attackerBypassed = isHasBypassPermission(attacker);
        boolean defenderBypassed = isHasBypassPermission(defender);

        if (attackerBypassed && defenderBypassed) {
            return;
        }

        boolean attackerInPvp = isInPvP(attacker) || isInSilentPvP(attacker);
        boolean defenderInPvp = isInPvP(defender) || isInSilentPvP(defender);

        PvPStatus pvpStatus;

        if (attackerInPvp && defenderInPvp) {
            updateAttackerAndCallEvent(attacker, defender, attackerBypassed);
            updateDefenderAndCallEvent(defender, attacker, defenderBypassed);
            return;
        } else if (attackerInPvp) {
            pvpStatus = PvPStatus.ATTACKER_IN_PVP;
        } else if (defenderInPvp) {
            pvpStatus = PvPStatus.DEFENDER_IN_PVP;
        } else {
            pvpStatus = PvPStatus.ALL_NOT_IN_PVP;
        }

        if (pvpStatus == PvPStatus.ATTACKER_IN_PVP || pvpStatus == PvPStatus.DEFENDER_IN_PVP) {
            if (callPvpPreStartEvent(defender, attacker, pvpStatus)) {
                if (attackerInPvp) {
                    updateAttackerAndCallEvent(attacker, defender, attackerBypassed);
                    startPvp(defender, defenderBypassed, false);
                } else {
                    updateDefenderAndCallEvent(defender, attacker, defenderBypassed);
                    startPvp(attacker, attackerBypassed, true);
                }
                Bukkit.getPluginManager().callEvent(new PvpStartedEvent(defender, attacker, settings.getPvpTime(), pvpStatus));
            }
            return;
        }

        if (callPvpPreStartEvent(defender, attacker, pvpStatus)) {
            startPvp(attacker, attackerBypassed, true);
            startPvp(defender, defenderBypassed, false);
            Bukkit.getPluginManager().callEvent(new PvpStartedEvent(defender, attacker, settings.getPvpTime(), pvpStatus));
        }
    }

    private void startPvp(Player player, boolean bypassed, boolean attacker) {
        if (!bypassed) {
            String message = settings.getMessages().getPvpStarted();
            if (!message.isEmpty()) {
                String coloredMessage = Utils.color(message);
                Component component = LegacyComponentSerializer.legacySection().deserialize(coloredMessage);
                player.sendMessage(component);
            }

            if (attacker && settings.isDisablePowerups()) {
                powerUpsManager.disablePowerUpsWithRunCommands(player);
            }

            sendTitles(player, true);
        }

        updatePvpMode(player, bypassed, settings.getPvpTime());
        player.setNoDamageTicks(0);
    }

    private void updatePvpMode(Player player, boolean bypassed, int newTime) {
        if (bypassed) {
            silentPvpMap.put(player, newTime);
        } else {
            pvpMap.put(player, newTime);

            bossbarManager.setBossBar(player, newTime);

            String actionBar = settings.getMessages().getInPvpActionbar();
            if (!actionBar.isEmpty()) {
                String formattedActionBar = Utils.color(Utils.replaceTime(actionBar, newTime));
                sendActionBar(player, formattedActionBar);
            }

            if (settings.isDisablePowerups()) {
                powerUpsManager.disablePowerUps(player);
            }
        }
    }

    private boolean callPvpPreStartEvent(Player defender, Player attacker, PvPStatus pvpStatus) {
        PvpPreStartEvent pvpPreStartEvent = new PvpPreStartEvent(defender, attacker, settings.getPvpTime(), pvpStatus);
        Bukkit.getPluginManager().callEvent(pvpPreStartEvent);
        return !pvpPreStartEvent.isCancelled();
    }

    private void updateAttackerAndCallEvent(Player attacker, Player defender, boolean bypassed) {
        int oldTime = bypassed ? getTimeRemainingInPvPSilent(attacker) : getTimeRemainingInPvP(attacker);
        updatePvpMode(attacker, bypassed, settings.getPvpTime());

        PvpTimeUpdateEvent pvpTimeUpdateEvent = new PvpTimeUpdateEvent(attacker, oldTime, settings.getPvpTime());
        Bukkit.getPluginManager().callEvent(pvpTimeUpdateEvent);
    }

    private void updateDefenderAndCallEvent(Player defender, Player attackedBy, boolean bypassed) {
        int oldTime = bypassed ? getTimeRemainingInPvPSilent(defender) : getTimeRemainingInPvP(defender);
        updatePvpMode(defender, bypassed, settings.getPvpTime());

        PvpTimeUpdateEvent pvpTimeUpdateEvent = new PvpTimeUpdateEvent(defender, oldTime, settings.getPvpTime());
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

        stopPvPSilent(player);

        sendTitles(player, false);

        String message = settings.getMessages().getPvpStopped();
        if (!message.isEmpty()) {
            String coloredMessage = Utils.color(message);
            Component component = LegacyComponentSerializer.legacySection().deserialize(coloredMessage);
            player.sendMessage(component);
        }

        String actionBar = settings.getMessages().getPvpStoppedActionbar();
        if (!actionBar.isEmpty()) {
            String coloredActionBar = Utils.color(actionBar);
            sendActionBar(player, coloredActionBar);
        }
    }

    public void stopPvPSilent(Player player) {
        if (player == null) {
            return;
        }

        pvpMap.remove(player);
        silentPvpMap.remove(player);
        bossbarManager.clearBossbar(player);

        Bukkit.getPluginManager().callEvent(new PvpStoppedEvent(player));
    }

    public boolean isCommandWhiteListed(String command) {
        if (command == null || whiteListedCommands.isEmpty()) {
            return false;
        }
        return whiteListedCommands.contains(command.toLowerCase());
    }

    private void sendTitles(Player player, boolean isPvpStarted) {
        String titleText = isPvpStarted ?
                settings.getMessages().getPvpStartedTitle() :
                settings.getMessages().getPvpStoppedTitle();

        String subtitleText = isPvpStarted ?
                settings.getMessages().getPvpStartedSubtitle() :
                settings.getMessages().getPvpStoppedSubtitle();

        if (titleText.isEmpty() && subtitleText.isEmpty()) {
            return;
        }

        Component title = titleText.isEmpty() ? Component.empty() :
                LegacyComponentSerializer.legacySection().deserialize(Utils.color(titleText));
        Component subtitle = subtitleText.isEmpty() ? Component.empty() :
                LegacyComponentSerializer.legacySection().deserialize(Utils.color(subtitleText));

        Title titleObj = Title.title(title, subtitle,
                Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(1500), Duration.ofMillis(500)));

        player.showTitle(titleObj);
    }

    private void sendActionBar(Player player, String message) {
        ActionBar.sendAction(player, message);
    }

    public boolean isPvPModeEnabled() {
        return settings.getPvpTime() > 0;
    }

    public boolean isBypassed(Player player) {
        return player != null && (isHasBypassPermission(player) || isInIgnoredWorld(player));
    }

    public boolean isHasBypassPermission(Player player) {
        return player != null && player.hasPermission("antirelog.bypass");
    }

    public boolean isInIgnoredWorld(Player player) {
        return player != null &&
                player.getWorld() != null &&
                settings.getDisabledWorlds().contains(player.getWorld().getName().toLowerCase());
    }

    public boolean isInIgnoredRegion(Player player) {
        if (player == null ||
                !plugin.isWorldguardEnabled() ||
                settings.getIgnoredWgRegions().isEmpty()) {
            return false;
        }

        Set<String> ignoredRegions = settings.getIgnoredWgRegions();
        Set<IWrappedRegion> playerRegions = WorldGuardWrapper.getInstance().getRegions(player.getLocation());

        if (playerRegions.isEmpty()) {
            return false;
        }

        for (IWrappedRegion region : playerRegions) {
            if (region != null && ignoredRegions.contains(region.getId().toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}