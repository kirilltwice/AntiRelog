package ru.leymooo.antirelog.manager;

import com.Zrips.CMI.CMI;
import com.Zrips.CMI.Containers.CMIUser;
import de.myzelyam.api.vanish.VanishAPI;
import me.libraryaddict.disguise.DisguiseAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import ru.leymooo.antirelog.config.Settings;
import ru.leymooo.antirelog.util.Utils;


public class PowerUpsManager {

    private final Settings settings;

    private boolean vanishAPIEnabled;
    private boolean libsDisguisesEnabled;
    private boolean cmiEnabled;
    private Object vanishNoPacket;
    private Object essentials;

    public PowerUpsManager(Settings settings) {
        this.settings = settings;
        detectPlugins();
    }

    public boolean disablePowerUps(Player player) {
        if (player.hasPermission("antirelog.bypass.checks")) {
            return false;
        }

        boolean disabled = false;

        disabled |= disableCreativeMode(player);
        disabled |= disableFlight(player);
        disabled |= disableEssentialsFeatures(player);
        disabled |= disableCMIFeatures(player);
        disabled |= disableVanishFeatures(player);
        disabled |= disableDisguiseFeatures(player);

        return disabled;
    }

    public void disablePowerUpsWithRunCommands(Player player) {
        if (!disablePowerUps(player) || settings.getCommandsOnPowerupsDisable().isEmpty()) {
            return;
        }

        settings.getCommandsOnPowerupsDisable()
                .forEach(command -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        command.replace("%player%", player.getName())));

        String message = settings.getMessages().getPvpStartedWithPowerups();
        if (!message.isEmpty() && player.isOnline()) {
            String coloredMessage = Utils.color(message);
            Component component = LegacyComponentSerializer.legacySection().deserialize(coloredMessage);
            player.sendMessage(component);
        }
    }

    public void detectPlugins() {
        PluginManager pluginManager = Bukkit.getPluginManager();

        this.vanishAPIEnabled = isPluginEnabled(pluginManager, "SuperVanish", "PremiumVanish");
        this.vanishNoPacket = getVanishNoPacketPlugin(pluginManager);
        this.essentials = getEssentialsPlugin(pluginManager);
        this.libsDisguisesEnabled = pluginManager.isPluginEnabled("LibsDisguises");
        this.cmiEnabled = pluginManager.isPluginEnabled("CMI");
    }

    private boolean disableCreativeMode(Player player) {
        if (player.getGameMode() != GameMode.CREATIVE) {
            return false;
        }

        GameMode targetMode = Bukkit.getDefaultGameMode() == GameMode.ADVENTURE
                ? GameMode.ADVENTURE
                : GameMode.SURVIVAL;

        player.setGameMode(targetMode);
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
        if (essentials == null) {
            return false;
        }

        try {
            Object user = essentials.getClass().getMethod("getUser", Player.class).invoke(essentials, player);
            boolean disabled = false;

            Boolean isVanished = (Boolean) user.getClass().getMethod("isVanished").invoke(user);
            if (isVanished) {
                user.getClass().getMethod("setVanished", boolean.class).invoke(user, false);
                disabled = true;
            }

            Boolean isGodMode = (Boolean) user.getClass().getMethod("isGodModeEnabled").invoke(user);
            if (isGodMode) {
                user.getClass().getMethod("setGodModeEnabled", boolean.class).invoke(user, false);
                disabled = true;
            }

            return disabled;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean disableCMIFeatures(Player player) {
        if (!cmiEnabled) {
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

        if (vanishAPIEnabled) {
            try {
                if (VanishAPI.isInvisible(player)) {
                    VanishAPI.showPlayer(player);
                    disabled = true;
                }
            } catch (Exception ignored) {}
        }

        if (vanishNoPacket != null) {
            try {
                Object manager = vanishNoPacket.getClass().getMethod("getManager").invoke(vanishNoPacket);
                boolean isVanished = (Boolean) manager.getClass().getMethod("isVanished", Player.class).invoke(manager, player);
                if (isVanished) {
                    manager.getClass().getMethod("toggleVanishQuiet", Player.class, boolean.class)
                            .invoke(manager, player, false);
                    disabled = true;
                }
            } catch (Exception ignored) {}
        }

        return disabled;
    }

    private boolean disableDisguiseFeatures(Player player) {
        if (!libsDisguisesEnabled) {
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

    private Object getEssentialsPlugin(PluginManager manager) {
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

    private Object getVanishNoPacketPlugin(PluginManager manager) {
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