package dev.twice.antirelog.managers;

import dev.twice.antirelog.Main;
import dev.twice.antirelog.api.events.CombatEndEvent;
import dev.twice.antirelog.api.events.CombatStartEvent;
import dev.twice.antirelog.api.events.CombatTickEvent;
import dev.twice.antirelog.config.Settings;
import dev.twice.antirelog.scoreboard.ScoreboardManager;
import dev.twice.antirelog.util.Utils;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.codemc.worldguardwrapper.WorldGuardWrapper;
import org.codemc.worldguardwrapper.region.IWrappedRegion;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class CombatManager {
    private final Settings settings;
    private final Main plugin;
    private final Map<Player, Integer> combatMap = new ConcurrentHashMap<>();
    private final Map<Player, Integer> silentCombatMap = new ConcurrentHashMap<>();
    private final Map<Player, Set<Player>> opponents = new ConcurrentHashMap<>();
    private final Map<Player, Boolean> infiniteCombat = new ConcurrentHashMap<>();
    private final Set<String> whiteListedCommands = ConcurrentHashMap.newKeySet();
    private final Set<String> cachedLowerCaseIgnoredWgRegions = ConcurrentHashMap.newKeySet();
    private final Set<String> cachedLowerCaseDisabledWorlds = ConcurrentHashMap.newKeySet();

    @Setter private CooldownManager cooldownManager;
    @Setter private PowerUpsManager powerUpsManager;
    @Setter private BossbarManager bossbarManager;

    private BukkitTask combatTimerTask;

    public CombatManager(Settings settings, Main plugin) {
        this.settings = settings;
        this.plugin = plugin;
    }

    public void initialize() {
        this.bossbarManager = new BossbarManager(settings);
        loadWhitelistedCommands();
        cacheDisabledWorldsAndRegions();
        startCombatTimer();
        bossbarManager.createBossBars();

        plugin.getServer().getPluginManager().registerEvents(bossbarManager, plugin);
    }

    public void shutdown() {
        if (combatTimerTask != null && !combatTimerTask.isCancelled()) {
            combatTimerTask.cancel();
        }
        combatMap.clear();
        silentCombatMap.clear();
        opponents.clear();
        infiniteCombat.clear();
        if (bossbarManager != null) bossbarManager.clearBossbars();
        whiteListedCommands.clear();
        cachedLowerCaseIgnoredWgRegions.clear();
        cachedLowerCaseDisabledWorlds.clear();
    }

    private void loadWhitelistedCommands() {
        whiteListedCommands.clear();
        if (!settings.isDisableCommandsInCombat() || settings.getWhiteListedCommands().isEmpty()) return;

        for (String commandName : settings.getWhiteListedCommands()) {
            if (commandName == null) continue;
            String lowerCaseName = commandName.toLowerCase(Locale.ROOT);
            whiteListedCommands.add(lowerCaseName);

            var command = Bukkit.getCommandMap().getCommand(commandName);
            if (command != null) {
                whiteListedCommands.add(command.getName().toLowerCase(Locale.ROOT));
                command.getAliases().forEach(alias ->
                        whiteListedCommands.add(alias.toLowerCase(Locale.ROOT)));
            }
        }
    }

    private void cacheDisabledWorldsAndRegions() {
        cachedLowerCaseDisabledWorlds.clear();
        settings.getDisabledWorlds().stream()
                .filter(Objects::nonNull)
                .map(String::toLowerCase)
                .forEach(cachedLowerCaseDisabledWorlds::add);

        cachedLowerCaseIgnoredWgRegions.clear();
        settings.getIgnoredWgRegions().stream()
                .filter(Objects::nonNull)
                .map(String::toLowerCase)
                .forEach(cachedLowerCaseIgnoredWgRegions::add);
    }

    private void startCombatTimer() {
        if (combatTimerTask != null && !combatTimerTask.isCancelled()) {
            combatTimerTask.cancel();
        }

        combatTimerTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!combatMap.isEmpty() || !silentCombatMap.isEmpty()) {
                updateCombatTimers(combatMap, false);
                updateCombatTimers(silentCombatMap, true);
            }
        }, 20L, 20L);
    }

    private void updateCombatTimers(Map<Player, Integer> map, boolean isSilentMap) {
        if (map.isEmpty()) return;

        List<Player> playersToUpdate = new ArrayList<>(map.keySet());
        for (Player player : playersToUpdate) {
            if (!player.isOnline()) {
                removeCombat(player, isSilentMap);
                continue;
            }

            int currentTime = map.getOrDefault(player, 0);

            if (infiniteCombat.containsKey(player)) {
                Bukkit.getPluginManager().callEvent(new CombatTickEvent(player));
                continue;
            }

            int timeRemaining = currentTime - 1;

            if (timeRemaining <= 0 || (settings.isDisableCombatInIgnoredRegion() && isInIgnoredRegion(player))) {
                if (isSilentMap) {
                    stopCombatSilent(player);
                } else {
                    stopCombat(player);
                }
            } else {
                map.put(player, timeRemaining);
                if (!isSilentMap) {
                    if (bossbarManager != null) bossbarManager.setBossBar(player, timeRemaining);
                    ScoreboardManager.setScoreboard(player, String.valueOf(timeRemaining), getOpponents(player));

                    String actionBar = settings.getMessages().getInCombatActionbar();
                    if (actionBar != null && !actionBar.isEmpty()) {
                        sendActionBar(player, Utils.color(Utils.replaceTime(actionBar, timeRemaining)));
                    }
                }
                Bukkit.getPluginManager().callEvent(new CombatTickEvent(player));
            }
        }
    }

    public void playerDamagedByPlayer(Player attacker, Player defender) {
        if (attacker == null || defender == null || attacker.equals(defender) ||
                attacker.getWorld() != defender.getWorld()) return;

        if (defender.getGameMode() == GameMode.CREATIVE ||
                attacker.hasMetadata("NPC") || defender.hasMetadata("NPC")) return;

        if (defender.isDead() || attacker.isDead()) return;

        CombatStartEvent event = new CombatStartEvent(attacker, defender);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        tryStartCombat(attacker, defender);
    }

    public void forceStartCombat(Player player) {
        if (player == null || !player.isOnline() || isInCombat(player) || isInSilentCombat(player)) return;

        boolean bypassed = hasBypassPermission(player);
        startCombatForPlayer(player, bypassed, true);
        player.setNoDamageTicks(0);
    }

    public void setInfiniteCombat(Player player, boolean infinite) {
        if (infinite) {
            infiniteCombat.put(player, true);
            if (!isInCombat(player) && !isInSilentCombat(player)) {
                forceStartCombat(player);
            }
        } else {
            infiniteCombat.remove(player);
        }
    }

    public boolean hasInfiniteCombat(Player player) {
        return infiniteCombat.containsKey(player);
    }

    private void tryStartCombat(Player attacker, Player defender) {
        if (isInIgnoredWorld(attacker) || isInIgnoredRegion(attacker) || isInIgnoredRegion(defender)) return;

        addOpponent(attacker, defender);
        addOpponent(defender, attacker);

        if (!isCombatModeEnabled() && settings.isDisablePowerups() && powerUpsManager != null) {
            if (!hasBypassPermission(attacker)) {
                powerUpsManager.disablePowerUpsWithRunCommands(attacker);
            }
            if (!hasBypassPermission(defender)) {
                powerUpsManager.disablePowerUps(defender);
            }
            return;
        }

        if (isCombatModeEnabled()) {
            boolean attackerBypassed = hasBypassPermission(attacker);
            boolean defenderBypassed = hasBypassPermission(defender);

            if (attackerBypassed && defenderBypassed) return;

            boolean attackerAlreadyInCombat = isInCombat(attacker) || isInSilentCombat(attacker);
            boolean defenderAlreadyInCombat = isInCombat(defender) || isInSilentCombat(defender);

            if (!attackerAlreadyInCombat) {
                startCombatForPlayer(attacker, attackerBypassed, true);
            } else {
                updateCombatMode(attacker, attackerBypassed, settings.getCombatTime());
            }

            if (!defenderAlreadyInCombat) {
                startCombatForPlayer(defender, defenderBypassed, false);
            } else {
                updateCombatMode(defender, defenderBypassed, settings.getCombatTime());
            }
        }
    }

    private void startCombatForPlayer(Player player, boolean bypassed, boolean showMessages) {
        if (infiniteCombat.containsKey(player)) return;

        if (!bypassed && showMessages) {
            sendStartMessage(player);
            if (settings.isDisablePowerups() && powerUpsManager != null) {
                powerUpsManager.disablePowerUpsWithRunCommands(player);
            }
            sendTitles(player, true);
        } else if (!bypassed && !showMessages) {
            if (settings.isDisablePowerups() && powerUpsManager != null) {
                powerUpsManager.disablePowerUps(player);
            }
        }

        updateCombatMode(player, bypassed, settings.getCombatTime());
    }

    private void updateCombatMode(Player player, boolean bypassed, int newTime) {
        (bypassed ? silentCombatMap : combatMap).put(player, newTime);
        if (!bypassed) {
            if (bossbarManager != null) bossbarManager.setBossBar(player, newTime);
            ScoreboardManager.setScoreboard(player, String.valueOf(newTime), getOpponents(player));

            String actionBar = settings.getMessages().getInCombatActionbar();
            if (actionBar != null && !actionBar.isEmpty()) {
                sendActionBar(player, Utils.color(Utils.replaceTime(actionBar, newTime)));
            }
            if (settings.isDisablePowerups() && powerUpsManager != null) {
                powerUpsManager.disablePowerUps(player);
            }
        }
    }

    public void stopCombat(Player player) {
        if (player == null) return;

        boolean wasInCombat = combatMap.containsKey(player);
        removeCombat(player, false);
        removeCombat(player, true);

        if (wasInCombat && player.isOnline()) {
            sendTitles(player, false);
            sendStopMessage(player);

            String actionBar = settings.getMessages().getCombatStoppedActionbar();
            if (actionBar != null && !actionBar.isEmpty()) {
                sendActionBar(player, Utils.color(actionBar));
            }
        }

        Bukkit.getPluginManager().callEvent(new CombatEndEvent(player));
    }

    public void stopCombatSilent(Player player) {
        if (player == null) return;

        boolean removedFromCombat = removeCombat(player, false);
        boolean removedFromSilentCombat = removeCombat(player, true);

        if (removedFromCombat || removedFromSilentCombat) {
            if (bossbarManager != null) bossbarManager.clearBossbar(player);
            ScoreboardManager.resetScoreboard(player);
            Bukkit.getPluginManager().callEvent(new CombatEndEvent(player));
        }
    }

    private boolean removeCombat(Player player, boolean isSilent) {
        boolean removed = (isSilent ? silentCombatMap : combatMap).remove(player) != null;
        if (removed) {
            opponents.remove(player);
            infiniteCombat.remove(player);
            if (!isSilent) {
                if (bossbarManager != null) bossbarManager.clearBossbar(player);
                ScoreboardManager.resetScoreboard(player);
            }
        }
        return removed;
    }

    private void addOpponent(Player player, Player opponent) {
        opponents.computeIfAbsent(player, k -> ConcurrentHashMap.newKeySet()).add(opponent);
    }

    public Set<Player> getOpponents(Player player) {
        return opponents.getOrDefault(player, Set.of());
    }

    private void sendStartMessage(Player player) {
        String message = settings.getMessages().getCombatStarted();
        if (message != null && !message.isEmpty()) {
            player.sendMessage(Utils.colorize(message));
        }
    }

    private void sendStopMessage(Player player) {
        String message = settings.getMessages().getCombatStopped();
        if (message != null && !message.isEmpty()) {
            player.sendMessage(Utils.colorize(message));
        }
    }

    private void sendTitles(Player player, boolean isCombatStarted) {
        String titleTextKey = isCombatStarted ?
                settings.getMessages().getCombatStartedTitle() :
                settings.getMessages().getCombatStoppedTitle();
        String subtitleTextKey = isCombatStarted ?
                settings.getMessages().getCombatStartedSubtitle() :
                settings.getMessages().getCombatStoppedSubtitle();

        if ((titleTextKey == null || titleTextKey.isEmpty()) &&
                (subtitleTextKey == null || subtitleTextKey.isEmpty())) return;

        Component title = (titleTextKey == null || titleTextKey.isEmpty()) ?
                Component.empty() : Utils.colorize(titleTextKey);
        Component subtitle = (subtitleTextKey == null || subtitleTextKey.isEmpty()) ?
                Component.empty() : Utils.colorize(subtitleTextKey);

        var durations = settings.getMessages().getTitleDurations();
        Title.Times times = Title.Times.times(
                Duration.ofMillis(durations.getFadeIn()),
                Duration.ofMillis(durations.getStay()),
                Duration.ofMillis(durations.getFadeOut())
        );

        player.showTitle(Title.title(title, subtitle, times));
    }

    private void sendActionBar(Player player, String message) {
        if (message == null || message.isEmpty() || !player.isOnline()) return;
        player.sendActionBar(Utils.colorize(message));
    }

    public boolean isInCombat(Player player) {
        return player != null && combatMap.containsKey(player);
    }

    public boolean isInSilentCombat(Player player) {
        return player != null && silentCombatMap.containsKey(player);
    }

    public int getTimeRemainingInCombat(Player player) {
        return player != null ? combatMap.getOrDefault(player, 0) : 0;
    }

    public int getTimeRemainingInSilentCombat(Player player) {
        return player != null ? silentCombatMap.getOrDefault(player, 0) : 0;
    }

    public boolean isCommandWhiteListed(String command) {
        return command != null && whiteListedCommands.contains(command.toLowerCase(Locale.ROOT));
    }

    public boolean isCombatModeEnabled() {
        return settings.getCombatTime() > 0;
    }

    public boolean isBypassed(Player player) {
        return player != null && (hasBypassPermission(player) || isInIgnoredWorld(player));
    }

    public boolean hasBypassPermission(Player player) {
        return player != null && player.hasPermission("antirelog.bypass");
    }

    public boolean isInIgnoredWorld(Player player) {
        return player != null && player.getWorld() != null &&
                cachedLowerCaseDisabledWorlds.contains(player.getWorld().getName().toLowerCase(Locale.ROOT));
    }

    public boolean isInIgnoredRegion(Player player) {
        if (player == null || !player.isOnline() || cachedLowerCaseIgnoredWgRegions.isEmpty()) return false;

        try {
            Set<IWrappedRegion> playerRegions = WorldGuardWrapper.getInstance().getRegions(player.getLocation());
            if (playerRegions == null || playerRegions.isEmpty()) return false;

            return playerRegions.stream()
                    .filter(Objects::nonNull)
                    .map(region -> region.getId().toLowerCase(Locale.ROOT))
                    .anyMatch(cachedLowerCaseIgnoredWgRegions::contains);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isDisableCommandsInPvp() {
        return settings.isDisableCommandsInCombat();
    }

    public boolean isDisablePvpInIgnoredRegion() {
        return settings.isDisableCombatInIgnoredRegion();
    }

    public boolean isPvPModeEnabled() {
        return isCombatModeEnabled();
    }

    public boolean isInPvP(Player player) {
        return isInCombat(player);
    }

    public boolean isInSilentPvP(Player player) {
        return isInSilentCombat(player);
    }

    public int getTimeRemainingInPvP(Player player) {
        return getTimeRemainingInCombat(player);
    }

    public int getTimeRemainingInPvPSilent(Player player) {
        return getTimeRemainingInSilentCombat(player);
    }

    public void stopPvP(Player player) {
        stopCombat(player);
    }

    public void stopPvPSilent(Player player) {
        stopCombatSilent(player);
    }

    public boolean isWorldguardEnabled() {
        try {
            WorldGuardWrapper.getInstance();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}