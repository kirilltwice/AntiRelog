package ru.leymooo.antirelog.command;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import ru.leymooo.antirelog.Antirelog;
import ru.leymooo.antirelog.manager.PvPManager;
import ru.leymooo.antirelog.util.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class AntiRelogCommand implements CommandExecutor, TabCompleter {

    private final Antirelog plugin;
    private final PvPManager pvpManager;

    private static final String PERMISSION_GIVE = "antirelog.give";
    private static final String PERMISSION_RELOAD = "antirelog.reload";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        switch (subCommand) {
            case "give" -> handleGiveCommand(sender, args);
            case "reload" -> handleReloadCommand(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleGiveCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_GIVE)) {
            sender.sendMessage(Utils.color("&cУ вас нет прав для использования этой команды"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Utils.color("&cИспользование: /antirelog give <игрок|all>"));
            return;
        }

        String targetName = args[1];
        if ("all".equalsIgnoreCase(targetName)) {
            givePvPToAll(sender);
        } else {
            givePvPToPlayer(sender, targetName);
        }
    }

    private void handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission(PERMISSION_RELOAD)) {
            sender.sendMessage(Utils.color("&cУ вас нет прав для использования этой команды"));
            return;
        }

        Plugin pluginInstance = (this.plugin instanceof Plugin) ? (Plugin) this.plugin : Bukkit.getPluginManager().getPlugin("AntiRelog");
        if (pluginInstance == null) {
            sender.sendMessage(Utils.color("&cОшибка: Плагин не найден для выполнения асинхронной задачи."));
            return;
        }

        pluginInstance.getServer().getAsyncScheduler().runNow(pluginInstance, task -> {
            this.plugin.reloadSettings();
            pluginInstance.getServer().getScheduler().runTask(pluginInstance, () -> {
                sender.sendMessage(Utils.color("&aКонфигурация перезагружена"));
            });
        });
    }

    private void givePvPToPlayer(CommandSender sender, String playerName) {
        Player target = Bukkit.getPlayerExact(playerName);

        if (target == null) {
            sender.sendMessage(Utils.color("&cИгрок &e" + playerName + " &cне найден"));
            return;
        }

        if (pvpManager.isInPvP(target)) {
            sender.sendMessage(Utils.color("&cИгрок &e" + target.getName() + " &cуже в режиме боя"));
            return;
        }

        activatePvPForPlayer(target);
        sender.sendMessage(Utils.color("&aРежим боя выдан игроку &e" + target.getName()));
        target.sendMessage(Utils.color("&cВы вошли в пвп!"));
    }

    private void givePvPToAll(CommandSender sender) {
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!pvpManager.isInPvP(player) && !pvpManager.isHasBypassPermission(player)) {
                activatePvPForPlayer(player);
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

    private void activatePvPForPlayer(Player player) {
        pvpManager.forceStartPvP(player);
    }

    private void sendHelp(CommandSender sender) {
        List<String> helpMessages = new ArrayList<>();
        helpMessages.add("&6=== AntiRelog ===");

        boolean hasAnyPerm = false;
        if (sender.hasPermission(PERMISSION_GIVE)) {
            helpMessages.add("&e/antirelog give <игрок> &7- Выдать режим боя игроку");
            helpMessages.add("&e/antirelog give all &7- Выдать режим боя всем игрокам");
            hasAnyPerm = true;
        }

        if (sender.hasPermission(PERMISSION_RELOAD)) {
            helpMessages.add("&e/antirelog reload &7- Перезагрузить конфигурацию");
            hasAnyPerm = true;
        }

        if (!hasAnyPerm) {
            helpMessages.add("&cУ вас нет прав для использования команд");
        }

        helpMessages.forEach(message -> sender.sendMessage(Utils.color(message)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }

        final String currentArgLower = args[args.length - 1].toLowerCase(Locale.ROOT);

        if (args.length == 1) {
            List<String> subcommands = new ArrayList<>();
            if (sender.hasPermission(PERMISSION_GIVE)) {
                subcommands.add("give");
            }
            if (sender.hasPermission(PERMISSION_RELOAD)) {
                subcommands.add("reload");
            }
            return subcommands.stream()
                    .filter(sub -> sub.toLowerCase(Locale.ROOT).startsWith(currentArgLower))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && "give".equalsIgnoreCase(args[0]) && sender.hasPermission(PERMISSION_GIVE)) {
            List<String> completions = new ArrayList<>();
            if ("all".startsWith(currentArgLower)) {
                completions.add("all");
            }
            Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(currentArgLower))
                    .forEach(completions::add);
            return completions;
        }
        return Collections.emptyList();
    }
}