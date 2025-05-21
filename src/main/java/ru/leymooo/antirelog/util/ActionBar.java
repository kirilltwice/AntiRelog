package ru.leymooo.antirelog.util;

import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

@UtilityClass
public class ActionBar {

    public void sendAction(Player player, String message) {
        if (player == null || !player.isOnline()) {
            return;
        }

        Component component = LegacyComponentSerializer.legacySection().deserialize(message);
        player.sendActionBar(component);
    }

    public CompletableFuture<Void> sendActionAsync(Player player, String message) {
        return CompletableFuture.runAsync(() -> sendAction(player, message));
    }

    public void sendTemporaryAction(Plugin plugin, Player player, String message, Duration duration) {
        if (player == null || !player.isOnline()) {
            return;
        }

        Component component = LegacyComponentSerializer.legacySection().deserialize(message);
        player.sendActionBar(component);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.sendActionBar(Component.empty());
            }
        }, duration.toMillis() / 50);
    }
}