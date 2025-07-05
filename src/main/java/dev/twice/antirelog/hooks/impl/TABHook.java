package dev.twice.antirelog.hooks.impl;

import dev.twice.antirelog.Main;
import dev.twice.antirelog.hooks.PluginHook;
import lombok.RequiredArgsConstructor;
import org.bukkit.plugin.PluginManager;

@RequiredArgsConstructor
public class TABHook implements PluginHook {
    private final Main plugin;
    private final PluginManager pluginManager;

    @Override
    public boolean isEnabled() {
        if (!pluginManager.isPluginEnabled("TAB")) {
            return false;
        }

        try {
            Class.forName("me.neznamy.tab.api.TabAPI");
            return true;
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            return false;
        }
    }

    @Override
    public void initialize() {
        plugin.getLogger().info("TAB интеграция включена");
    }

    @Override
    public String getPluginName() {
        return "TAB";
    }
}
