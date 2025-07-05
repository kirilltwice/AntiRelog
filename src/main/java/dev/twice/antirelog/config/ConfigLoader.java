package dev.twice.antirelog.config;

import dev.twice.antirelog.Main;
import lombok.RequiredArgsConstructor;
import ru.leymooo.annotatedyaml.Configuration;
import ru.leymooo.annotatedyaml.ConfigurationProvider;
import ru.leymooo.annotatedyaml.provider.BukkitConfigurationProvider;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

@RequiredArgsConstructor
public class ConfigLoader {
    private final Main plugin;

    public Settings loadSettings() {
        fixFolder();
        Settings settings = Configuration.builder(Settings.class)
                .file(new File(plugin.getDataFolder(), "config.yml"))
                .provider(BukkitConfigurationProvider.class).build();

        ConfigurationProvider provider = settings.getConfigurationProvider();
        provider.reloadFileFromDisk();
        File file = provider.getConfigFile();

        if (file.exists() && provider.get("config-version") == null) {
            try {
                Files.move(file.toPath(),
                        new File(file.getParentFile(), "config.old." + System.nanoTime()).toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to move old config.yml");
            }
            provider.reloadFileFromDisk();
        }

        if (!file.exists()) {
            settings.save();
            settings.loaded();
        } else if (provider.isFileSuccessfullyLoaded()) {
            if (settings.load()) {
                Object configVersionObj = provider.get("config-version");
                String configVersionOnDisk = configVersionObj instanceof String ? (String) configVersionObj : null;
                if (configVersionOnDisk == null || !configVersionOnDisk.equals(settings.getConfigVersion())) {
                    settings.save();
                }
            } else {
                settings.loaded();
            }
        } else {
            settings.loaded();
        }

        return settings;
    }

    private void fixFolder() {
        File oldFolder = new File(plugin.getDataFolder().getParentFile(), "Antirelog");
        if (!oldFolder.exists()) return;

        try {
            File actualFolder = oldFolder.getCanonicalFile();
            if (actualFolder.exists() && actualFolder.isDirectory() && actualFolder.getName().equals("Antirelog")) {
                File oldConfig = new File(actualFolder, "config.yml");
                if (oldConfig.exists()) {
                    File newFolder = plugin.getDataFolder();
                    if (!newFolder.exists()) newFolder.mkdirs();

                    File targetConfig = new File(newFolder, "config.yml");
                    if (targetConfig.exists()) {
                        Files.move(targetConfig.toPath(),
                                new File(newFolder, "config.old.moved." + System.nanoTime() + ".yml").toPath(),
                                StandardCopyOption.REPLACE_EXISTING);
                    }
                    Files.copy(oldConfig.toPath(), targetConfig.toPath());
                }
                deleteFolder(actualFolder);
            }
        } catch (IOException ignored) {}
    }

    private void deleteFolder(File folder) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) deleteFolder(file);
                else file.delete();
            }
        }
        folder.delete();
    }
}
