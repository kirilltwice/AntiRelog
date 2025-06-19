package dev.twice.antirelog.listeners;

import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import dev.twice.antirelog.config.Settings;
import dev.twice.antirelog.managers.PvPManager;
import dev.twice.antirelog.util.Utils;

@RequiredArgsConstructor
public class EnderChestListener implements Listener {

    private final PvPManager pvpManager;
    private final Settings settings;

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!settings.isDisableEnderchestInPvp()) {
            return;
        }

        if (event.getInventory().getType() != InventoryType.ENDER_CHEST) {
            return;
        }

        if (event.getPlayer() instanceof Player player) {
            if (pvpManager.isInPvP(player)) {
                event.setCancelled(true);
                sendDisabledInPvPMessage(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!settings.isDisableEnderchestInPvp()) {
            return;
        }

        Player player = event.getPlayer();
        if (!pvpManager.isInPvP(player)) {
            return;
        }

        if (event.getClickedBlock() != null && event.getClickedBlock().getType() == Material.ENDER_CHEST) {
            event.setCancelled(true);
            sendDisabledInPvPMessage(player);
        }
    }

    private void sendDisabledInPvPMessage(Player player) {
        String messageKey = settings.getMessages().getItemDisabledInPvp();
        if (messageKey != null && !messageKey.isEmpty()) {
            Component messageComponent = LegacyComponentSerializer.legacySection().deserialize(Utils.color(messageKey));
            player.sendMessage(messageComponent);
        }
    }
}
