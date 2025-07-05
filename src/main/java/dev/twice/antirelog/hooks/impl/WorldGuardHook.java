package dev.twice.antirelog.hooks.impl;

import dev.twice.antirelog.Main;
import dev.twice.antirelog.hooks.PluginHook;
import dev.twice.antirelog.listeners.WorldGuardListener;
import lombok.RequiredArgsConstructor;
import org.bukkit.plugin.PluginManager;
import org.codemc.worldguardwrapper.WorldGuardWrapper;

@RequiredArgsConstructor
public class WorldGuardHook implements PluginHook {
    private final Main plugin;
    private final PluginManager pluginManager;

    @Override
    public boolean isEnabled() {
        return pluginManager.isPluginEnabled("WorldGuard");
    }

    @Override
    public void initialize() {
        try {
            WorldGuardWrapper.getInstance();
            pluginManager.registerEvents(new WorldGuardListener(plugin.getSettings(), plugin.getCombatManager()), plugin);
        } catch (Exception ignored) {}
    }

    @Override
    public String getPluginName() {
        return "WorldGuard";
    }
}