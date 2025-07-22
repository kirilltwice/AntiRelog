package dev.twice.antirelog.command;

import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import dev.twice.antirelog.Main;
import dev.twice.antirelog.managers.CombatManager;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@Command(name = "antirelog", aliases = {"ar", "combat"})
@RequiredArgsConstructor
public class AntiRelogCommand {

    private final Main plugin;
    private final CombatManager combatManager;

    @Execute
    @Permission("antirelog.admin")
    public void execute(@Context CommandSender sender) {
        sender.sendMessage("§7Используйте /ar reload для перезагрузки конфига");
        sender.sendMessage("§7Используйте /ar start <игрок|*> для принудительного входа в бой");
        sender.sendMessage("§7Используйте /ar stop <игрок|*> для выхода из боя");
        sender.sendMessage("§7Используйте * для применения ко всем онлайн игрокам");
    }

    @Execute(name = "reload")
    @Permission("antirelog.admin")
    public void reload(@Context CommandSender sender) {
        plugin.reload();
        sender.sendMessage("§aКонфиг успешно перезагружен!");
    }

    @Execute(name = "start")
    @Permission("antirelog.admin")
    public void startAll(@Context CommandSender sender) {
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            combatManager.forceStartCombat(player);
            count++;
        }
        sender.sendMessage("§a" + count + " игроков принудительно вошли в боевой режим!");
    }

    @Execute(name = "start")
    @Permission("antirelog.admin")
    public void start(@Context CommandSender sender, @Arg("player") String playerName) {
        if (playerName.equals("*")) {
            startAll(sender);
            return;
        }

        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            sender.sendMessage("§cИгрок не найден!");
            return;
        }

        combatManager.forceStartCombat(target);
        sender.sendMessage("§aИгрок " + target.getName() + " принудительно вошел в боевой режим!");
        target.sendMessage("§aВы принудительно вошли в боевой режим!");
    }

    @Execute(name = "start")
    @Permission("antirelog.admin")
    public void startSelf(@Context Player player) {
        combatManager.forceStartCombat(player);
        player.sendMessage("§aВы принудительно вошли в боевой режим!");
    }

    @Execute(name = "stop")
    @Permission("antirelog.admin")
    public void stopAll(@Context CommandSender sender) {
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (combatManager.isInCombat(player) || combatManager.isInSilentCombat(player)) {
                combatManager.stopCombat(player);
                count++;
            }
        }
        sender.sendMessage("§a" + count + " игроков вышли из боевого режима!");
    }

    @Execute(name = "stop")
    @Permission("antirelog.admin")
    public void stop(@Context CommandSender sender, @Arg("player") String playerName) {
        if (playerName.equals("*")) {
            stopAll(sender);
            return;
        }

        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            sender.sendMessage("§cИгрок не найден!");
            return;
        }

        combatManager.stopCombat(target);
        sender.sendMessage("§aИгрок " + target.getName() + " вышел из боевого режима!");
        target.sendMessage("§aВы вышли из боевого режима!");
    }

    @Execute(name = "stop")
    @Permission("antirelog.admin")
    public void stopSelf(@Context Player player) {
        combatManager.stopCombat(player);
        player.sendMessage("§aВы вышли из боевого режима!");
    }
}