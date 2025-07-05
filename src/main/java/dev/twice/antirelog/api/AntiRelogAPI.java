package dev.twice.antirelog.api;

import dev.twice.antirelog.Main;
import lombok.experimental.UtilityClass;
import org.bukkit.entity.Player;

@UtilityClass
public class AntiRelogAPI {
    private static Main plugin;

    public static void setPlugin(Main plugin) {
        AntiRelogAPI.plugin = plugin;
    }

    public static boolean isInCombat(Player player) {
        return plugin != null && plugin.getCombatManager().isInCombat(player);
    }

    public static void startCombat(Player player) {
        if (plugin != null) {
            plugin.getCombatManager().forceStartCombat(player);
        }
    }

    public static void stopCombat(Player player) {
        if (plugin != null) {
            plugin.getCombatManager().stopCombat(player);
        }
    }

    public static void setInfiniteCombat(Player player, boolean infinite) {
        if (plugin != null) {
            plugin.getCombatManager().setInfiniteCombat(player, infinite);
        }
    }

    public static boolean hasInfiniteCombat(Player player) {
        return plugin != null && plugin.getCombatManager().hasInfiniteCombat(player);
    }

    public static int getCombatTimeRemaining(Player player) {
        return plugin != null ? plugin.getCombatManager().getTimeRemainingInCombat(player) : 0;
    }
}
