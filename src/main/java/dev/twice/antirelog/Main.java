package dev.twice.antirelog;

import dev.rollczi.litecommands.LiteCommands;
import dev.rollczi.litecommands.bukkit.LiteBukkitFactory;
import dev.rollczi.litecommands.bukkit.LiteBukkitMessages;
import dev.twice.antirelog.api.AntiRelogAPI;
import dev.twice.antirelog.command.AntiRelogCommand;
import dev.twice.antirelog.config.Settings;
import dev.twice.antirelog.core.PluginInitializer;
import dev.twice.antirelog.managers.CombatManager;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public class Main extends JavaPlugin {
    @Setter private Settings settings;
    @Setter private CombatManager combatManager;
    private LiteCommands<CommandSender> liteCommands;
    private PluginInitializer initializer;

    @Override
    public void onEnable() {
        this.initializer = new PluginInitializer(this);
        this.initializer.initialize();
        setupCommands();
        AntiRelogAPI.setPlugin(this);
    }

    @Override
    public void onDisable() {
        if (liteCommands != null) liteCommands.unregister();
        if (initializer != null) initializer.shutdown();
    }

    private void setupCommands() {
        this.liteCommands = LiteBukkitFactory.builder("antirelog", this)
                .commands(new AntiRelogCommand(this, combatManager))
                .message(LiteBukkitMessages.MISSING_PERMISSIONS, settings.getMessages().getNoPermission())
                .message(LiteBukkitMessages.PLAYER_NOT_FOUND, settings.getMessages().getPlayerNotFound())
                .message(LiteBukkitMessages.INVALID_USAGE, settings.getMessages().getInvalidUsage())
                .build();
    }

    public void reload() {
        initializer.reload();
    }
}