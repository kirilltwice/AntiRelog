package ru.leymooo.antirelog.util;

import lombok.experimental.UtilityClass;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;

import java.util.Optional;
import java.util.logging.Level;

@UtilityClass
public class CommandMapUtils {

    @Getter
    private static CommandMap commandMap;
    private static boolean initialized = false;

    public CommandMap getCommandMap() {
        if (!initialized) {
            initialized = true;
            try {
                commandMap = Bukkit.getServer().getCommandMap();
            } catch (Exception e) {
                Bukkit.getLogger().log(Level.WARNING, "[AntiRelog] Could not initialize command map", e);
            }
        }
        return commandMap;
    }

    public Optional<Command> getCommand(String command) {
        return Optional.ofNullable(getCommandMap())
                .map(map -> map.getCommand(command));
    }

    public boolean hasCommand(String command) {
        return getCommand(command).isPresent();
    }
}