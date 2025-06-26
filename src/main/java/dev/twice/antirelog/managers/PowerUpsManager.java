package dev.twice.antirelog.managers;

import com.Zrips.CMI.CMI;
import com.Zrips.CMI.Containers.CMIUser;
import de.myzelyam.api.vanish.VanishAPI;
import lombok.RequiredArgsConstructor;
import me.libraryaddict.disguise.DisguiseAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import dev.twice.antirelog.config.Settings;
import dev.twice.antirelog.util.Utils;

@RequiredArgsConstructor
public class PowerUpsManager {

    private final Settings settings;

    private boolean vanishAPIEnabled;
    private boolean libsDisguisesEnabled;
    private boolean cmiEnabled;
    private Object vanishNoPacketPluginInstance;
    private Object essentialsPluginInstance;

    {
        detectPlugins();
    }

    public boolean disablePowerUps(Player player) {
        if (player.hasPermission("antirelog.bypass.checks")) {
            return false;
        }

        boolean disabledSomething = false;
        disabledSomething |= disableCreativeMode(player);
        disabledSomething |= disableFlight(player);
        disabledSomething |= disableEssentialsFeatures(player);
        disabledSomething |= disableCMIFeatures(player);
        disabledSomething |= disableVanishFeatures(player);
        disabledSomething |= disableDisguiseFeatures(player);

        return disabledSomething;
    }

    public void disablePowerUpsWithRunCommands(Player player) {
        if (!disablePowerUps(player) || settings.getCommandsOnPowerupsDisable().isEmpty()) {
            return;
        }

        String playerName = player.getName();
        settings.getCommandsOnPowerupsDisable()
                .forEach(command -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        command.replace("%player%", playerName)));

        String message = settings.getMessages().getPvpStartedWithPowerups();
        if (message != null && !message.isEmpty() && player.isOnline()) {
            Component component = LegacyComponentSerializer.legacySection().deserialize(Utils.color(message));
            player.sendMessage(component);
        }
    }

    public void detectPlugins() {
        PluginManager pluginManager = Bukkit.getPluginManager();

        this.vanishAPIEnabled = isPluginEnabled(pluginManager, "SuperVanish", "PremiumVanish");
        this.vanishNoPacketPluginInstance = getVanishNoPacketPluginInstance(pluginManager);
        this.essentialsPluginInstance = getEssentialsPluginInstance(pluginManager);
        this.libsDisguisesEnabled = pluginManager.isPluginEnabled("LibsDisguises");
        this.cmiEnabled = pluginManager.isPluginEnabled("CMI");
    }

    private boolean disableCreativeMode(Player player) {
        if (player.getGameMode() != GameMode.CREATIVE) {
            return false;
        }
        player.setGameMode(Bukkit.getDefaultGameMode() == GameMode.ADVENTURE ? GameMode.ADVENTURE : GameMode.SURVIVAL);
        return true;
    }

    private boolean disableFlight(Player player) {
        if (!player.isFlying() && !player.getAllowFlight()) {
            return false;
        }
        player.setFlying(false);
        player.setAllowFlight(false);
        return true;
    }

    private boolean disableEssentialsFeatures(Player player) {
        if (this.essentialsPluginInstance == null) {
            return false;
        }
        try {
            Object user = this.essentialsPluginInstance.getClass().getMethod("getUser", Player.class).invoke(this.essentialsPluginInstance, player);
            if (user == null) return false;

            boolean disabled = false;
            Object isVanishedResult = user.getClass().getMethod("isVanished").invoke(user);
            if (isVanishedResult instanceof Boolean && (Boolean) isVanishedResult) {
                user.getClass().getMethod("setVanished", boolean.class).invoke(user, false);
                disabled = true;
            }

            Object isGodModeResult = user.getClass().getMethod("isGodModeEnabled").invoke(user);
            if (isGodModeResult instanceof Boolean && (Boolean) isGodModeResult) {
                user.getClass().getMethod("setGodModeEnabled", boolean.class).invoke(user, false);
                disabled = true;
            }
            return disabled;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean disableCMIFeatures(Player player) {
        if (!this.cmiEnabled) {
            return false;
        }
        try {
            CMIUser user = CMI.getInstance().getPlayerManager().getUser(player);
            if (user == null) {
                return false;
            }
            boolean disabled = false;
            if (user.isGod()) {
                CMI.getInstance().getNMS().changeGodMode(player, false);
                user.setTgod(0L);
                disabled = true;
            }
            if (user.isVanished()) {
                user.setVanished(false);
                disabled = true;
            }
            return disabled;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean disableVanishFeatures(Player player) {
        boolean disabled = false;
        if (this.vanishAPIEnabled) {
            try {
                if (VanishAPI.isInvisible(player)) {
                    VanishAPI.showPlayer(player);
                    disabled = true;
                }
            } catch (Exception ignored) {}
        }
        if (this.vanishNoPacketPluginInstance != null) {
            try {
                Object manager = this.vanishNoPacketPluginInstance.getClass().getMethod("getManager").invoke(this.vanishNoPacketPluginInstance);
                if (manager == null) return disabled;
                Object isVanishedResult = manager.getClass().getMethod("isVanished", Player.class).invoke(manager, player);
                if (isVanishedResult instanceof Boolean && (Boolean) isVanishedResult) {
                    manager.getClass().getMethod("toggleVanishQuiet", Player.class, boolean.class).invoke(manager, player, false);
                    disabled = true;
                }
            } catch (Exception ignored) {}
        }
        return disabled;
    }

    private boolean disableDisguiseFeatures(Player player) {
        if (!this.libsDisguisesEnabled) {
            return false;
        }
        try {
            if (DisguiseAPI.isSelfDisguised(player)) {
                DisguiseAPI.undisguiseToAll(player);
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean isPluginEnabled(PluginManager manager, String... pluginNames) {
        for (String name : pluginNames) {
            if (manager.isPluginEnabled(name)) {
                return true;
            }
        }
        return false;
    }

    private Object getEssentialsPluginInstance(PluginManager manager) {
        if (!manager.isPluginEnabled("Essentials")) {
            return null;
        }
        try {
            Plugin plugin = manager.getPlugin("Essentials");
            Class<?> essentialsClass = Class.forName("com.earth2me.essentials.Essentials");
            return essentialsClass.isInstance(plugin) ? plugin : null;
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private Object getVanishNoPacketPluginInstance(PluginManager manager) {
        if (!manager.isPluginEnabled("VanishNoPacket")) {
            return null;
        }
        try {
            Plugin plugin = manager.getPlugin("VanishNoPacket");
            Class<?> vanishPluginClass = Class.forName("org.kitteh.vanish.VanishPlugin");
            return vanishPluginClass.isInstance(plugin) ? plugin : null;
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}