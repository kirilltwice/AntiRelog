package dev.twice.antirelog.command;

import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import dev.twice.antirelog.Main;
import dev.twice.antirelog.managers.CombatManager;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;

@Command(name = "antirelog", aliases = {"ar", "combat"})
@RequiredArgsConstructor
public class AntiRelogCommand {

    private final Main plugin;
    private final CombatManager combatManager;

    @Execute
    @Permission("antirelog.admin")
    public void execute(@Context Player player) {
        player.sendMessage("§7Используйте /ar reload для перезагрузки конфига");
    }

    @Execute(name = "reload")
    @Permission("antirelog.admin")
    public void reload(@Context Player player) {
        plugin.reload();
        player.sendMessage("§aКонфиг успешно перезагружен!");
    }

    @Execute(name = "start")
    @Permission("antirelog.admin")
    public void start(@Context Player player) {
        combatManager.forceStartCombat(player);
        player.sendMessage("§aВы принудительно вошли в боевой режим!");
    }

    @Execute(name = "stop")
    @Permission("antirelog.admin")
    public void stop(@Context Player player) {
        combatManager.stopCombat(player);
        player.sendMessage("§aВы вышли из боевого режима!");
    }

    public void reloadSettings() {
        plugin.reload();
    }
}