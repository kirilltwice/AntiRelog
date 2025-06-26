package dev.twice.antirelog.util;

import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;

@UtilityClass
public class ActionBar {

    public void sendAction(Player player, String message) {
        if (player == null || !player.isOnline() || message == null) {
            return;
        }
        Component component = LegacyComponentSerializer.legacySection().deserialize(message);
        player.sendActionBar(component);
    }

    public void sendTemporaryAction(Plugin plugin, Player player, String message, Duration duration) {
        if (player == null || !player.isOnline() || message == null || duration == null || duration.isNegative() || duration.isZero()) {
            return;
        }

        Component component = LegacyComponentSerializer.legacySection().deserialize(message);
        player.sendActionBar(component);

        long delayTicks = duration.toMillis() / 50;
        if (delayTicks <= 0) {
            return;
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.sendActionBar(Component.empty());
            }
        }, delayTicks);
    }
}
