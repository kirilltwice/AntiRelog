package dev.twice.antirelog.core;

import dev.twice.antirelog.Main;
import dev.twice.antirelog.config.ConfigLoader;
import dev.twice.antirelog.config.Settings;
import dev.twice.antirelog.hooks.HookManager;
import dev.twice.antirelog.listeners.ListenerManager;
import dev.twice.antirelog.managers.CombatManager;
import dev.twice.antirelog.managers.CooldownManager;
import dev.twice.antirelog.managers.PowerUpsManager;
import dev.twice.antirelog.scoreboard.ScoreboardManager;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PluginInitializer {
    private final Main plugin;
    private ConfigLoader configLoader;
    private HookManager hookManager;
    private ListenerManager listenerManager;

    public void initialize() {
        loadConfig();
        initializeManagers();
        initializeHooks();
        initializeListeners();
        initializeScoreboard();
    }

    public void reload() {
        shutdown();
        initialize();
    }

    public void shutdown() {
        if (listenerManager != null) listenerManager.shutdown();
        if (plugin.getCombatManager() != null) plugin.getCombatManager().shutdown();
    }

    private void loadConfig() {
        this.configLoader = new ConfigLoader(plugin);
        plugin.setSettings(configLoader.loadSettings());
    }

    private void initializeManagers() {
        Settings settings = plugin.getSettings();
        CombatManager combatManager = new CombatManager(settings, plugin);
        plugin.setCombatManager(combatManager);

        CooldownManager cooldownManager = new CooldownManager(settings);
        PowerUpsManager powerUpsManager = new PowerUpsManager(settings);

        combatManager.initialize();
        powerUpsManager.initialize();

        combatManager.setCooldownManager(cooldownManager);
        combatManager.setPowerUpsManager(powerUpsManager);
    }

    private void initializeHooks() {
        this.hookManager = new HookManager(plugin);
        hookManager.initializeAll();
    }

    private void initializeListeners() {
        this.listenerManager = new ListenerManager(plugin);
        listenerManager.registerAll();
    }

    private void initializeScoreboard() {
        Settings.ScoreboardConfig scoreboardConfig = plugin.getSettings().getScoreboard();
        ScoreboardManager.setConfig(scoreboardConfig);
        ScoreboardManager.setProvider(scoreboardConfig.getProvider());
    }
}
