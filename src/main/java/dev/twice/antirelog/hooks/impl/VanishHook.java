package dev.twice.antirelog.hooks.impl;

import dev.twice.antirelog.Main;
import dev.twice.antirelog.hooks.PluginHook;
import lombok.RequiredArgsConstructor;
import org.bukkit.plugin.PluginManager;

@RequiredArgsConstructor
public class VanishHook implements PluginHook {
    private final Main plugin;
    private final PluginManager pluginManager;

    @Override
    public boolean isEnabled() {
        return pluginManager.isPluginEnabled("SuperVanish") ||
                pluginManager.isPluginEnabled("PremiumVanish") ||
                pluginManager.isPluginEnabled("VanishNoPacket");
    }

    @Override
    public void initialize() {}

    @Override
    public String getPluginName() {
        return "Vanish";
    }
}