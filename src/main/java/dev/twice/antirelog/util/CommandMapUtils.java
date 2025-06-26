package dev.twice.antirelog.util;

import lombok.experimental.UtilityClass;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;

import java.util.Optional;
import java.util.logging.Level;

@UtilityClass
public class CommandMapUtils {

    private static final CommandMap commandMap;

    static {
        CommandMap tempCommandMap = null;
        try {
            tempCommandMap = Bukkit.getServer().getCommandMap();
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.WARNING, "[AntiRelog] Could not initialize command map", e);
        }
        commandMap = tempCommandMap;
    }

    public CommandMap getCommandMap() {
        return commandMap;
    }

    public Optional<Command> getCommand(String commandName) {
        return Optional.ofNullable(getCommandMap())
                .map(map -> map.getCommand(commandName));
    }

    public boolean hasCommand(String commandName) {
        return getCommand(commandName).isPresent();
    }
}
