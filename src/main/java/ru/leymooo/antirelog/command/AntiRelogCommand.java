package ru.leymooo.antirelog.command;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.leymooo.antirelog.Antirelog;
import ru.leymooo.antirelog.manager.PvPManager;
import ru.leymooo.antirelog.util.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class AntiRelogCommand implements CommandExecutor, TabCompleter {

    private final Antirelog plugin;
    private final PvPManager pvpManager;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "give" -> handleGiveCommand(sender, args);
            case "reload" -> handleReloadCommand(sender);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleGiveCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("antirelog.give")) {
            sender.sendMessage(Utils.color("&cУ вас нет прав для использования этой команды"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Utils.color("&cИспользование: /antirelog give <игрок|all>"));
            return;
        }

        String target = args[1];

        if (target.equalsIgnoreCase("all")) {
            givePvPToAll(sender);
        } else {
            givePvPToPlayer(sender, target);
        }
    }

    private void handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission("antirelog.reload")) {
            sender.sendMessage(Utils.color("&cУ вас нет прав для использования этой команды"));
            return;
        }

        plugin.reloadSettings();
        sender.sendMessage(Utils.color("&aКонфигурация перезагружена"));
    }

    private void givePvPToPlayer(CommandSender sender, String playerName) {
        Player target = Bukkit.getPlayer(playerName);

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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subcommands = new ArrayList<>();

            if (sender.hasPermission("antirelog.give")) {
                subcommands.add("give");
            }

            if (sender.hasPermission("antirelog.reload")) {
                subcommands.add("reload");
            }

            return subcommands.stream()
                    .filter(sub -> sub.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("give") && sender.hasPermission("antirelog.give")) {
            completions.add("all");

            completions.addAll(Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList()));
        }

        return completions;
    }
}