package ru.leymooo.antirelog;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.codemc.worldguardwrapper.WorldGuardWrapper;
import ru.leymooo.annotatedyaml.Configuration;
import ru.leymooo.annotatedyaml.ConfigurationProvider;
import ru.leymooo.annotatedyaml.provider.BukkitConfigurationProvider;
import ru.leymooo.antirelog.config.Settings;
import ru.leymooo.antirelog.listeners.CooldownListener;
import ru.leymooo.antirelog.listeners.EssentialsTeleportListener;
import ru.leymooo.antirelog.listeners.PvPListener;
import ru.leymooo.antirelog.listeners.WorldGuardListener;
import ru.leymooo.antirelog.manager.BossbarManager;
import ru.leymooo.antirelog.manager.CooldownManager;
import ru.leymooo.antirelog.manager.PowerUpsManager;
import ru.leymooo.antirelog.manager.PvPManager;
import ru.leymooo.antirelog.command.AntiRelogCommand;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Stream;

public class Antirelog extends JavaPlugin {
    private Settings settings;
    private PvPManager pvpManager;
    private CooldownManager cooldownManager;
    private CooldownListener cooldownListener;
    private boolean worldguard;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();

        this.pvpManager = new PvPManager(settings, this);
        this.cooldownManager = new CooldownManager(settings);

        detectPlugins();

        this.cooldownListener = new CooldownListener(this, cooldownManager, pvpManager, settings);
        this.cooldownListener.initializeListeners();
        getServer().getPluginManager().registerEvents(this.cooldownListener, this);

        getServer().getPluginManager().registerEvents(new PvPListener(this, pvpManager, settings), this);

        AntiRelogCommand commandHandler = new AntiRelogCommand(this, pvpManager);
        getCommand("antirelog").setExecutor(commandHandler);
        getCommand("antirelog").setTabCompleter(commandHandler);
    }

    @Override
    public void onDisable() {
        if (pvpManager != null) {
            pvpManager.onPluginDisable();
        }
        if (cooldownManager != null) {
            cooldownManager.shutdown();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return false;
    }

    private void loadConfig() {
        fixFolder();
        settings = Configuration.builder(Settings.class)
                .file(new File(getDataFolder(), "config.yml"))
                .provider(BukkitConfigurationProvider.class).build();
        ConfigurationProvider provider = settings.getConfigurationProvider();
        provider.reloadFileFromDisk();
        File file = provider.getConfigFile();
        if (file.exists() && provider.get("config-version") == null) {
            try {
                Files.move(file.toPath(), new File(file.getParentFile(), "config.old." + System.nanoTime()).toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                getLogger().log(Level.WARNING, "Не удалось переместить старый config.yml", e);
            }
            provider.reloadFileFromDisk();
        }
        if (!file.exists()) {
            settings.save();
            settings.loaded();
            getLogger().info("config.yml успешно создан.");
        } else if (provider.isFileSuccessfullyLoaded()) {
            if (settings.load()) {
                Object configVersionObj = provider.get("config-version");
                String configVersionOnDisk = configVersionObj instanceof String ? (String) configVersionObj : null;
                if (configVersionOnDisk == null || !configVersionOnDisk.equals(settings.getConfigVersion())) {
                    getLogger().info("Конфигурация была обновлена. Проверьте новые значения.");
                    settings.save();
                }
                getLogger().info("Конфигурация успешно загружена.");
            } else {
                getLogger().warning("Не удалось загрузить конфигурацию. Используются значения по умолчанию.");
                settings.loaded();
            }
        } else {
            getLogger().warning("Не удалось загрузить настройки из файла (возможно, он поврежден), используются значения по умолчанию...");
            settings.loaded();
        }
    }

    private void fixFolder() {
        File oldFolder = new File(getDataFolder().getParentFile(), "Antirelog");
        if (!oldFolder.exists()) {
            return;
        }
        try {
            File actualFolder = oldFolder.getCanonicalFile();
            if (actualFolder.exists() && actualFolder.isDirectory() && actualFolder.getName().equals("Antirelog")) {
                File oldConfig = new File(actualFolder, "config.yml");
                List<String> oldConfigLines = null;
                if (oldConfig.exists()) {
                    oldConfigLines = Files.readAllLines(oldConfig.toPath(), StandardCharsets.UTF_8);
                }

                deleteFolder(actualFolder.toPath());

                File newFolder = getDataFolder();
                if (!newFolder.exists()) {
                    if(!newFolder.mkdirs()) {
                        getLogger().warning("Не удалось создать новую папку плагина: " + newFolder.getPath());
                        return;
                    }
                }

                if (oldConfigLines != null) {
                    String firstLine = oldConfigLines.isEmpty() ? null : oldConfigLines.get(0);
                    File targetConfigFile = new File(newFolder, "config.yml");
                    File backupConfigFile = new File(newFolder, "config.old.moved." + System.nanoTime() + ".yml");

                    if (targetConfigFile.exists()) {
                        Files.move(targetConfigFile.toPath(), backupConfigFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        getLogger().info("Существующий config.yml в папке 'AntiRelog' был сохранен как: " + backupConfigFile.getName());
                    }

                    Files.write(targetConfigFile.toPath(), oldConfigLines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                    getLogger().info("Старый config.yml из папки 'Antirelog' был перемещен в папку 'AntiRelog'.");
                } else {
                    getLogger().info("Старая папка 'Antirelog' была удалена, так как не содержала config.yml.");
                }
            }
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Произошла ошибка при перемещении данных из старой папки 'Antirelog' в 'AntiRelog'", e);
        }
    }

    private void deleteFolder(Path folder) throws IOException {
        try (Stream<Path> walk = Files.walk(folder)) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    public void reloadSettings() {
        settings.getConfigurationProvider().reloadFileFromDisk();
        if (settings.getConfigurationProvider().isFileSuccessfullyLoaded()) {
            settings.load();
        } else {
            getLogger().warning("Не удалось перезагрузить файл конфигурации с диска во время reloadSettings.");
        }

        getServer().getScheduler().cancelTasks(this);
        if (pvpManager != null) {
            pvpManager.onPluginDisable();
            pvpManager.onPluginEnable();
        }
        if (cooldownManager != null) {
            cooldownManager.clearAllPlayerData();
        }
        if (pvpManager != null && pvpManager.getBossbarManager() != null) {
            pvpManager.getBossbarManager().createBossBars();
        }
    }

    public boolean isWorldguardEnabled() {
        return worldguard;
    }

    private void detectPlugins() {
        PluginManager pluginManager = Bukkit.getPluginManager();
        if (pluginManager.isPluginEnabled("WorldGuard")) {
            try {
                WorldGuardWrapper.getInstance();
                pluginManager.registerEvents(new WorldGuardListener(settings, pvpManager), this);
                worldguard = true;
            } catch (Throwable e) {
                worldguard = false;
            }
        } else {
            worldguard = false;
        }
        try {
            Class.forName("net.ess3.api.events.teleport.PreTeleportEvent");
            pluginManager.registerEvents(new EssentialsTeleportListener(pvpManager, settings), this);
        } catch (ClassNotFoundException e) {
        }
    }

    public Settings getPluginSettings() {
        return settings;
    }

    public PvPManager getPvpManager() {
        return pvpManager;
    }

    public PowerUpsManager getPowerUpsManager() {
        return pvpManager != null ? pvpManager.getPowerUpsManager() : null;
    }

    public BossbarManager getBossbarManager() {
        return pvpManager != null ? pvpManager.getBossbarManager() : null;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }
}