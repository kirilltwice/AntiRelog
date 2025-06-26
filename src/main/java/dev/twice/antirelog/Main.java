package dev.twice.antirelog;

import dev.rollczi.litecommands.LiteCommands;
import dev.rollczi.litecommands.bukkit.LiteBukkitFactory;
import dev.rollczi.litecommands.bukkit.LiteBukkitMessages;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.codemc.worldguardwrapper.WorldGuardWrapper;
import ru.leymooo.annotatedyaml.Configuration;
import ru.leymooo.annotatedyaml.ConfigurationProvider;
import ru.leymooo.annotatedyaml.provider.BukkitConfigurationProvider;
import dev.twice.antirelog.config.Settings;
import dev.twice.antirelog.listeners.CooldownListener;
import dev.twice.antirelog.listeners.EssentialsTeleportListener;
import dev.twice.antirelog.listeners.PvPListener;
import dev.twice.antirelog.listeners.WorldGuardListener;
import dev.twice.antirelog.managers.BossbarManager;
import dev.twice.antirelog.managers.CooldownManager;
import dev.twice.antirelog.managers.PowerUpsManager;
import dev.twice.antirelog.managers.PvPManager;
import dev.twice.antirelog.command.AntiRelogCommand;

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

public class Main extends JavaPlugin {
    @Getter
    private Settings pluginSettings;
    @Getter
    private PvPManager pvpManager;
    @Getter
    private CooldownManager cooldownManager;
    private CooldownListener cooldownListener;
    @Getter
    private boolean worldguardEnabled;
    private LiteCommands<CommandSender> liteCommands;

    @Override
    public void onEnable() {
        loadConfig();

        this.pvpManager = new PvPManager(pluginSettings, this);
        this.cooldownManager = new CooldownManager(pluginSettings);

        detectPlugins();

        this.cooldownListener = new CooldownListener(this, cooldownManager, pvpManager, pluginSettings);
        this.cooldownListener.initializeListeners();
        getServer().getPluginManager().registerEvents(this.cooldownListener, this);

        getServer().getPluginManager().registerEvents(new PvPListener(this, pvpManager, pluginSettings), this);

        this.liteCommands = LiteBukkitFactory.builder("antirelog", this)
                .commands(new AntiRelogCommand(this, pvpManager))
                .message(LiteBukkitMessages.MISSING_PERMISSIONS, "§cУ вас нет прав для использования этой команды")
                .message(LiteBukkitMessages.PLAYER_NOT_FOUND, "§cИгрок не найден")
                .message(LiteBukkitMessages.INVALID_USAGE, "§cНеправильное использование команды")
                .build();
    }

    @Override
    public void onDisable() {
        if (this.liteCommands != null) {
            this.liteCommands.unregister();
        }
        if (pvpManager != null) {
            pvpManager.onPluginDisable();
        }
        if (cooldownManager != null) {
            cooldownManager.shutdown();
        }
    }

    private void loadConfig() {
        fixFolder();
        pluginSettings = Configuration.builder(Settings.class)
                .file(new File(getDataFolder(), "config.yml"))
                .provider(BukkitConfigurationProvider.class).build();
        ConfigurationProvider provider = pluginSettings.getConfigurationProvider();
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
            pluginSettings.save();
            pluginSettings.loaded();
            getLogger().info("config.yml успешно создан.");
        } else if (provider.isFileSuccessfullyLoaded()) {
            if (pluginSettings.load()) {
                Object configVersionObj = provider.get("config-version");
                String configVersionOnDisk = configVersionObj instanceof String ? (String) configVersionObj : null;
                if (configVersionOnDisk == null || !configVersionOnDisk.equals(pluginSettings.getConfigVersion())) {
                    getLogger().info("Конфигурация была обновлена. Проверьте новые значения.");
                    pluginSettings.save();
                }
                getLogger().info("Конфигурация успешно загружена.");
            } else {
                getLogger().warning("Не удалось загрузить конфигурацию. Используются значения по умолчанию.");
                pluginSettings.loaded();
            }
        } else {
            getLogger().warning("Не удалось загрузить настройки из файла (возможно, он поврежден), используются значения по умолчанию...");
            pluginSettings.loaded();
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
        pluginSettings.getConfigurationProvider().reloadFileFromDisk();
        if (pluginSettings.getConfigurationProvider().isFileSuccessfullyLoaded()) {
            pluginSettings.load();
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

    private void detectPlugins() {
        PluginManager pluginManager = Bukkit.getPluginManager();

        worldguardEnabled = initializeWorldGuard(pluginManager);
        initializeEssentials(pluginManager);
    }

    private boolean initializeWorldGuard(PluginManager pluginManager) {
        if (!pluginManager.isPluginEnabled("WorldGuard")) {
            return false;
        }

        try {
            WorldGuardWrapper.getInstance();
            pluginManager.registerEvents(new WorldGuardListener(pluginSettings, pvpManager), this);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void initializeEssentials(PluginManager pluginManager) {
        if (pluginManager.isPluginEnabled("Essentials")) {
            pluginManager.registerEvents(new EssentialsTeleportListener(pvpManager, pluginSettings), this);
        }
    }

    public PowerUpsManager getPowerUpsManager() {
        return pvpManager != null ? pvpManager.getPowerUpsManager() : null;
    }

    public BossbarManager getBossbarManager() {
        return pvpManager != null ? pvpManager.getBossbarManager() : null;
    }
}