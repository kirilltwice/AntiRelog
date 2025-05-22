package ru.leymooo.antirelog.listeners;

import lombok.RequiredArgsConstructor;
import net.ess3.api.events.teleport.PreTeleportEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import ru.leymooo.antirelog.config.Settings;
import ru.leymooo.antirelog.manager.PvPManager;

@RequiredArgsConstructor
public class EssentialsTeleportListener implements Listener {

    private final PvPManager pvpManager;
    private final Settings settings;

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPreTeleport(PreTeleportEvent event) {
        if (!settings.isDisableTeleportsInPvp()) {
            return;
        }

        Player player = event.getTeleportee().getBase();
        if (pvpManager.isInPvP(player)) {
            event.setCancelled(true);
        }
    }
}