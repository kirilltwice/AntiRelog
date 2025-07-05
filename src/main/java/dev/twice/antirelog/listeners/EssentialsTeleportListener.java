package dev.twice.antirelog.listeners;

import dev.twice.antirelog.config.Settings;
import dev.twice.antirelog.managers.CombatManager;
import lombok.RequiredArgsConstructor;
import net.ess3.api.events.teleport.PreTeleportEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

@RequiredArgsConstructor
public class EssentialsTeleportListener implements Listener {
    private final CombatManager combatManager;
    private final Settings settings;

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPreTeleport(PreTeleportEvent event) {
        if (!settings.isDisableTeleportsInCombat()) return;

        Player player = event.getTeleportee().getBase();
        if (combatManager.isInCombat(player)) {
            event.setCancelled(true);
        }
    }
}