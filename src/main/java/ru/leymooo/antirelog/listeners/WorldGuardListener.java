package ru.leymooo.antirelog.listeners;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.codemc.worldguardwrapper.event.WrappedDisallowedPVPEvent;
import ru.leymooo.antirelog.config.Settings;
import ru.leymooo.antirelog.manager.PvPManager;

@RequiredArgsConstructor
public class WorldGuardListener implements Listener {

    private final Settings settings;
    private final PvPManager pvpManager;

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvP(WrappedDisallowedPVPEvent event) {
        if (!pvpManager.isPvPModeEnabled() || !settings.isIgnoreWorldGuard()) {
            return;
        }

        Player attacker = event.getAttacker();
        Player defender = event.getDefender();

        boolean attackerInPvp = isPlayerInAnyPvP(attacker);
        boolean defenderInPvp = isPlayerInAnyPvP(defender);

        if (shouldCancelEvent(attackerInPvp, defenderInPvp)) {
            event.setCancelled(true);
            event.setResult(Result.DENY);
        }
    }

    private boolean isPlayerInAnyPvP(Player player) {
        return pvpManager.isInPvP(player) || pvpManager.isInSilentPvP(player);
    }

    private boolean shouldCancelEvent(boolean attackerInPvp, boolean defenderInPvp) {
        return (attackerInPvp && defenderInPvp) ||
                (settings.isJoinPvPInWorldGuard() && defenderInPvp);
    }
}