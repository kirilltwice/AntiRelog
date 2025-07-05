package dev.twice.antirelog.listeners;

import dev.twice.antirelog.Main;
import dev.twice.antirelog.listeners.impl.CombatListener;
import dev.twice.antirelog.listeners.impl.CooldownListener;
import lombok.RequiredArgsConstructor;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class ListenerManager {
    private final Main plugin;
    private final List<Listener> listeners = new ArrayList<>();

    public void registerAll() {
        registerListener(new CombatListener(plugin));
        registerListener(new CooldownListener(plugin));
    }

    public void shutdown() {
        listeners.forEach(HandlerList::unregisterAll);
        listeners.clear();
    }

    private void registerListener(Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        listeners.add(listener);
    }
}
