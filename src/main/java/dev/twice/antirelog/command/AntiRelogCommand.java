package dev.twice.antirelog.command;

import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import dev.twice.antirelog.Main;
import dev.twice.antirelog.managers.PvPManager;
import dev.twice.antirelog.util.Utils;

@Command(name = "antirelog")
public class AntiRelogCommand {

    private final Main plugin;
    private final PvPManager pvpManager;

    public AntiRelogCommand(Main plugin, PvPManager pvpManager) {
        this.plugin = plugin;
        this.pvpManager = pvpManager;
    }

    @Execute
    void sendHelp(@Context CommandSender sender) {
        sender.sendMessage(Utils.color("&6=== AntiRelog ==="));

        if (sender.hasPermission("antirelog.give")) {
            sender.sendMessage(Utils.color("&e/antirelog give <игрок> &7- Выдать режим боя игроку"));
            sender.sendMessage(Utils.color("&e/antirelog give all &7- Выдать режим боя всем игрокам"));
        }

        if (sender.hasPermission("antirelog.reload")) {
            sender.sendMessage(Utils.color("&e/antirelog reload &7- Перезагрузить конфигурацию"));
        }

        if (!sender.hasPermission("antirelog.give") && !sender.hasPermission("antirelog.reload")) {
            sender.sendMessage(Utils.color("&cУ вас нет прав для использования команд"));
        }
    }

    @Execute(name = "give")
    @Permission("antirelog.give")
    void givePvPToPlayer(@Context CommandSender sender, @Arg Player target) {
        if (pvpManager.isInPvP(target)) {
            sender.sendMessage(Utils.color("&cИгрок &e" + target.getName() + " &cуже в режиме боя"));
            return;
        }

        pvpManager.forceStartPvP(target);
        sender.sendMessage(Utils.color("&aРежим боя выдан игроку &e" + target.getName()));
        target.sendMessage(Utils.color("&cВы вошли в пвп!"));
    }

    @Execute(name = "give")
    @Permission("antirelog.give")
    void givePvPToAll(@Context CommandSender sender, @Arg("all") String all) {
        if (!"all".equalsIgnoreCase(all)) {
            sender.sendMessage(Utils.color("&cИспользование: /antirelog give all"));
            return;
        }

        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!pvpManager.isInPvP(player) && !pvpManager.isHasBypassPermission(player)) {
                pvpManager.forceStartPvP(player);
                player.sendMessage(Utils.color("&cВсем игрокам выдан режим боя"));
                count++;
            }
        }

        if (count > 0) {
            sender.sendMessage(Utils.color("&aРежим боя выдан &e" + count + " &aигрокам"));
        } else {
            sender.sendMessage(Utils.color("&cНет игроков для выдачи режима боя"));
        }
    }

    @Execute(name = "reload")
    @Permission("antirelog.reload")
    void reloadConfig(@Context CommandSender sender) {
        Plugin pluginInstance = (this.plugin instanceof Plugin) ? (Plugin) this.plugin : Bukkit.getPluginManager().getPlugin("AntiRelog");
        if (pluginInstance == null) {
            sender.sendMessage(Utils.color("&cОшибка: Плагин не найден для выполнения."));
            return;
        }

        pluginInstance.getServer().getAsyncScheduler().runNow(pluginInstance, task -> {
            this.plugin.reloadSettings();
            pluginInstance.getServer().getScheduler().runTask(pluginInstance, () -> {
                sender.sendMessage(Utils.color("&aКонфигурация перезагружена"));
            });
        });
    }
}