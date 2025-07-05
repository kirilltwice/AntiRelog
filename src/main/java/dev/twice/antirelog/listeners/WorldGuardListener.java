package dev.twice.antirelog.listeners;

import dev.twice.antirelog.config.Settings;
import dev.twice.antirelog.managers.CombatManager;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.codemc.worldguardwrapper.event.WrappedDisallowedPVPEvent;

@RequiredArgsConstructor
public class WorldGuardListener implements Listener {
    private final Settings settings;
    private final CombatManager combatManager;

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvP(WrappedDisallowedPVPEvent event) {
        if (!combatManager.isCombatModeEnabled() || !settings.isIgnoreWorldGuard()) return;

        Player attacker = event.getAttacker();
        Player defender = event.getDefender();
        if (attacker == null || defender == null) return;

        boolean attackerInCombat = isPlayerInAnyCombat(attacker);
        boolean defenderInCombat = isPlayerInAnyCombat(defender);

        if (shouldUpholdWorldGuardDisallow(attackerInCombat, defenderInCombat)) {
            event.setCancelled(true);
        }
    }

    private boolean isPlayerInAnyCombat(Player player) {
        return combatManager.isInCombat(player) || combatManager.isInSilentCombat(player);
    }

    private boolean shouldUpholdWorldGuardDisallow(boolean attackerInCombat, boolean defenderInCombat) {
        return defenderInCombat && (attackerInCombat || settings.isJoinCombatInWorldGuard());
    }
}
