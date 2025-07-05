package dev.twice.antirelog.hooks.impl;

import dev.twice.antirelog.Main;
import dev.twice.antirelog.hooks.PluginHook;
import dev.twice.antirelog.listeners.EssentialsTeleportListener;
import lombok.RequiredArgsConstructor;
import org.bukkit.plugin.PluginManager;

@RequiredArgsConstructor
public class EssentialsHook implements PluginHook {
    private final Main plugin;
    private final PluginManager pluginManager;

    @Override
    public boolean isEnabled() {
        return pluginManager.isPluginEnabled("Essentials");
    }

    @Override
    public void initialize() {
        pluginManager.registerEvents(new EssentialsTeleportListener(plugin.getCombatManager(), plugin.getSettings()), plugin);
    }

    @Override
    public String getPluginName() {
        return "Essentials";
    }
}
