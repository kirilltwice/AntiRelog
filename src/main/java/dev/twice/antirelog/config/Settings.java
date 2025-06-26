package dev.twice.antirelog.config;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import ru.leymooo.annotatedyaml.Annotations.*;
import ru.leymooo.annotatedyaml.Configuration;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static lombok.AccessLevel.PRIVATE;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = PRIVATE)
public final class Settings extends Configuration {

    @Final
    @Key("config-version")
    String configVersion = "1.7";

    Messages messages = new Messages();

    @Comment("Кулдавн для обычных золотых яблок во время пвп")
    @Key("golden-apple-cooldown")
    int goldenAppleCooldown = 30;

    @Comment({
            "Кулдавн для зачарованных золотых яблок во время пвп",
            "Значение 0 отключает кулдаун; -1 отключает использование во время пвп"
    })
    @Key("enchanted-golden-apple-cooldown")
    int enchantedGoldenAppleCooldown = 60;

    @Comment({
            "Кулдавн для жемчугов края во время пвп",
            "Значение 0 отключает кулдаун; -1 отключает использование во время пвп"
    })
    @Key("ender-pearl-cooldown")
    int enderPearlCooldown = 15;

    @Comment({
            "Кулдавн для корусов во время пвп",
            "https://minecraft-ru.gamepedia.com/Плод_коруса",
            "Значение 0 отключает кулдаун; -1 отключает использование во время пвп"
    })
    @Key("chorus-cooldown")
    int chorusCooldown = 7;

    @Comment({
            "Кулдавн для фейверков во время пвп (чтобы не убегали на элитрах)",
            "Значение 0 отключает кулдаун; -1 отключает использование во время пвп"
    })
    @Key("firework-cooldown")
    int fireworkCooldown = 60;

    @Comment({
            "Кулдавн для тотемов бесмертия во время пвп",
            "Значение 0 отключает кулдаун; -1 отключает использование во время пвп"
    })
    @Key("totem-cooldown")
    int totemCooldown = 60;

    @Comment("Длительность пвп")
    @Key("pvp-time")
    int pvpTime = 12;

    @Comment("Отключить ли возможность писать команды в пвп?")
    @Key("disable-commands-in-pvp")
    boolean disableCommandsInPvp = true;

    @Comment({
            "Команды которые можно писать во время пвп",
            "Команды писать без '/' (кол-во '/' - 1)",
            "Плагин будет пытаться сам определить алиасы для команд (msg,tell,m)",
            "но для некоторых команд возможно придется самому прописать алиасы"
    })
    @Key("commands-whitelist")
    List<String> whiteListedCommands = List.of();

    @Key("cancel-interact-with-entities")
    @Comment("Отменять ли взаимодействие с энтити во время пвп")
    boolean cancelInteractWithEntities = false;

    @Comment("Убивать ли игрока если он вышел во время пвп?")
    @Key("kill-on-leave")
    boolean killOnLeave = true;

    @Comment("Убивать ли игрока если его кикнули во время пвп?")
    @Key("kill-on-kick")
    boolean killOnKick = true;

    @Comment("Выполнять ли команды, если игрока кикнули во время пвп?")
    @Key("run-commands-on-kick")
    boolean runCommandsOnKick = true;

    @Comment({
            "Какой текст должен быть в причине кика, чтобы его убило/выполнились команды",
            "Если пусто, то будет убивать/выполняться команды всегда"
    })
    @Key("kick-messages")
    List<String> kickMessages = List.of("спам", "реклама", "анти-чит");

    @Comment("Какие команды запускать от консоли при выходе игрока во время пвп?")
    @Key("commands-on-leave")
    List<String> commandsOnLeave = List.of();

    @Comment("Отключать ли у игрока который ударил FLY, GM, GOD, VANISH?")
    @Key("disable-powerups")
    boolean disablePowerups = true;

    @Comment({
            "Какие команды выполнять, если были отключены усиления у игрока",
            "Данную настройку можно использовать например для того,",
            "чтобы наложить на игрока отрицательный эффект, если он начал пвп в ГМ/ФЛАЕ/и тд"
    })
    @Key("commands-on-powerups-disable")
    List<String> commandsOnPowerupsDisable = List.of();

    @Comment("Отключать ли возможность телепортироваться во время пвп?")
    @Key("disable-teleports-in-pvp")
    boolean disableTeleportsInPvp = true;

    @Comment("Игнорировать ли PVP deny во время пвп между игроками?")
    @Key("ignore-worldguard")
    boolean ignoreWorldGuard = true;

    @Comment({
            "Включать ли игроку, который не участвует в пвп и ударил другого игрока в pvp, pvp режим",
            "Если два игрока дерутся на территории где PVP deny и их ударить,",
            "то у того кто ударил также включится PVP режим"
    })
    @Key("join-pvp-in-worldguard")
    boolean joinPvPInWorldGuard = false;

    @Comment("В каких регионах не будет работать плагин")
    @Key("ignored-worldguard-regions")
    List<String> ignoredWgRegions = List.of();

    @Ignore
    Set<String> ignoredWgRegionsCache = ConcurrentHashMap.newKeySet();

    @Comment("Отключать ли активный ПВП режим когда игрок заходит в игнорируемый регион?")
    @Key("disable-pvp-in-ignored-region")
    boolean disablePvpInIgnoredRegion = false;

    @Comment("Скрывать ли сообщения о заходе игроков?")
    @Key("hide-join-message")
    boolean hideJoinMessage = false;

    @Comment("Скрывать ли сообщения о выходе игроков?")
    @Key("hide-leave-message")
    boolean hideLeaveMessage = false;

    @Comment("Скрывать ли сообщение о смерти игроков?")
    @Key("hide-death-message")
    boolean hideDeathMessage = false;

    @Comment("Отключать ли Эндер-сундук во время пвп?")
    @Key("disable-enderchest-in-pvp")
    boolean disableEnderchestInPvp = true;

    @Comment("Миры в которых плагин не работает")
    @Key("disabled-worlds")
    List<String> disabledWorlds = List.of("world1", "world2");

    @Ignore
    Set<String> disabledWorldsCache = ConcurrentHashMap.newKeySet();

    @Override
    public void loaded() {
        this.ignoredWgRegionsCache = ConcurrentHashMap.newKeySet();
        this.disabledWorldsCache = ConcurrentHashMap.newKeySet();

        ignoredWgRegions.parallelStream()
                .map(String::toLowerCase)
                .forEach(ignoredWgRegionsCache::add);

        disabledWorlds.parallelStream()
                .map(String::toLowerCase)
                .forEach(disabledWorldsCache::add);
    }

    public Set<String> getIgnoredWgRegions() {
        return Set.copyOf(ignoredWgRegionsCache);
    }

    public Set<String> getDisabledWorlds() {
        return Set.copyOf(disabledWorldsCache);
    }

    public boolean isWorldDisabled(String worldName) {
        return disabledWorldsCache.contains(worldName.toLowerCase());
    }

    public boolean isRegionIgnored(String regionName) {
        return ignoredWgRegionsCache.contains(regionName.toLowerCase());
    }

    public boolean isItemCooldownDisabled(int cooldown) {
        return cooldown == -1;
    }

    public boolean isItemCooldownEnabled(int cooldown) {
        return cooldown > 0;
    }
}