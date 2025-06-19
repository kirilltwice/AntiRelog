package dev.twice.antirelog.listeners;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.codemc.worldguardwrapper.event.WrappedDisallowedPVPEvent;
import dev.twice.antirelog.config.Settings;
import dev.twice.antirelog.managers.PvPManager;

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

        if (attacker == null || defender == null) {
            return;
        }

        boolean attackerInPvp = isPlayerInAnyPvP(attacker);
        boolean defenderInPvp = isPlayerInAnyPvP(defender);

        if (shouldUpholdWorldGuardDisallow(attackerInPvp, defenderInPvp)) {
            event.setCancelled(true);
        }
    }

    private boolean isPlayerInAnyPvP(Player player) {
        return pvpManager.isInPvP(player) || pvpManager.isInSilentPvP(player);
    }

    private boolean shouldUpholdWorldGuardDisallow(boolean attackerInPvp, boolean defenderInPvp) {
        return defenderInPvp && (attackerInPvp || settings.isJoinPvPInWorldGuard());
    }
}
