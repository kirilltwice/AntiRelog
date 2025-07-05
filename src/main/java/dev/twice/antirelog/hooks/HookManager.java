package dev.twice.antirelog.hooks;

import dev.twice.antirelog.Main;
import dev.twice.antirelog.hooks.impl.*;
import lombok.RequiredArgsConstructor;
import org.bukkit.plugin.PluginManager;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class HookManager {
    private final Main plugin;
    private final List<PluginHook> hooks = new ArrayList<>();

    public void initializeAll() {
        PluginManager pm = plugin.getServer().getPluginManager();

        registerHook(new WorldGuardHook(plugin, pm));
        registerHook(new EssentialsHook(plugin, pm));
        registerHook(new CMIHook(plugin, pm));
        registerHook(new VanishHook(plugin, pm));
        registerHook(new LibsDisguisesHook(plugin, pm));
        registerHook(new TABHook(plugin, pm));
    }

    private void registerHook(PluginHook hook) {
        if (hook.isEnabled()) {
            hook.initialize();
            hooks.add(hook);
        }
    }

    public <T extends PluginHook> T getHook(Class<T> hookClass) {
        return hooks.stream()
                .filter(hookClass::isInstance)
                .map(hookClass::cast)
                .findFirst()
                .orElse(null);
    }
}
