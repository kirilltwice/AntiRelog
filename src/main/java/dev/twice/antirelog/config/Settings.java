package dev.twice.antirelog.config;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import ru.leymooo.annotatedyaml.Annotations.*;
import ru.leymooo.annotatedyaml.Configuration;

import java.util.*;

import static lombok.AccessLevel.PRIVATE;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = PRIVATE)
public final class Settings extends Configuration {

    @Final
    @Key("config-version")
    String configVersion = "2.0";

    @Comment("Настройки всех текстовых сообщений плагина")
    Messages messages = new Messages();

    @Key("combat-time")
    @Comment("Время боевого режима в секундах")
    int combatTime = 12;

    @Key("disable-commands-in-combat")
    @Comment("Запретить использование команд в боевом режиме")
    boolean disableCommandsInCombat = true;

    @Key("commands-whitelist")
    @Comment("Список команд, разрешенных в боевом режиме (без /)")
    List<String> whiteListedCommands = List.of();

    @Key("kill-on-leave")
    @Comment("Убивать игрока при выходе из игры в боевом режиме")
    boolean killOnLeave = true;

    @Key("kill-on-kick")
    @Comment("Убивать игрока при кике в боевом режиме")
    boolean killOnKick = true;

    @Key("run-commands-on-kick")
    @Comment("Выполнять команды при кике в боевом режиме")
    boolean runCommandsOnKick = true;

    @Key("commands-on-leave")
    @Comment("Команды, выполняемые при выходе в боевом режиме")
    List<String> commandsOnLeave = List.of();

    @Key("golden-apple-cooldown")
    @Comment("Кулдаун золотых яблок в секундах (0 = отключен, -1 = запрещен в бою)")
    int goldenAppleCooldown = 30;

    @Key("enchanted-golden-apple-cooldown")
    @Comment("Кулдаун зачарованных золотых яблок в секундах")
    int enchantedGoldenAppleCooldown = 60;

    @Key("ender-pearl-cooldown")
    @Comment("Кулдаун жемчуга Края в секундах")
    int enderPearlCooldown = 15;

    @Key("chorus-cooldown")
    @Comment("Кулдаун плода коруса в секундах")
    int chorusCooldown = 7;

    @Key("firework-cooldown")
    @Comment("Кулдаун фейерверков в секундах (против элитр)")
    int fireworkCooldown = 60;

    @Key("totem-cooldown")
    @Comment("Кулдаун тотемов бессмертия в секундах")
    int totemCooldown = 60;

    @Key("disable-powerups")
    @Comment("Отключать ли усиления (креатив, полет, ванише) при входе в бой")
    boolean disablePowerups = true;

    @Key("commands-on-powerups-disable")
    @Comment("Команды, выполняемые при отключении усилений")
    List<String> commandsOnPowerupsDisable = List.of();

    @Key("disable-teleports-in-combat")
    @Comment("Запретить телепортацию в боевом режиме")
    boolean disableTeleportsInCombat = true;

    @Key("disable-enderchest-in-combat")
    @Comment("Запретить использование сундука Края в боевом режиме")
    boolean disableEnderchestInCombat = true;

    @Key("cancel-interact-with-entities")
    @Comment("Отменять взаимодействие с сущностями в боевом режиме")
    boolean cancelInteractWithEntities = false;

    @Key("max-teleport-distance")
    @Comment("Максимальное расстояние телепортации в блоках")
    int maxTeleportDistance = 10;

    @Key("ignore-worldguard")
    @Comment("Игнорировать запрет PvP от WorldGuard для игроков в бою")
    boolean ignoreWorldGuard = true;

    @Key("join-combat-in-worldguard")
    @Comment("Включать боевой режим при ударе в зоне с запретом PvP")
    boolean joinCombatInWorldGuard = false;

    @Key("ignored-worldguard-regions")
    @Comment("Регионы WorldGuard, где плагин не работает")
    List<String> ignoredWgRegions = List.of();

    @Key("disable-combat-in-ignored-region")
    @Comment("Отключать боевой режим при входе в игнорируемый регион")
    boolean disableCombatInIgnoredRegion = false;

    @Key("disabled-worlds")
    @Comment("Миры, где плагин не работает")
    List<String> disabledWorlds = List.of("world1", "world2");

    @Key("hide-join-message")
    @Comment("Скрывать сообщения о входе игроков")
    boolean hideJoinMessage = false;

    @Key("hide-leave-message")
    @Comment("Скрывать сообщения о выходе игроков")
    boolean hideLeaveMessage = false;

    @Key("hide-death-message")
    @Comment("Скрывать сообщения о смерти игроков")
    boolean hideDeathMessage = false;

    @Key("kick-messages")
    @Comment("Причины кика, при которых игрок будет убит/наказан")
    List<String> kickMessages = List.of("спам", "реклама", "анти-чит");

    @Key("teleport-grace-period-ticks")
    @Comment("Время задержки телепортации в тиках")
    int teleportGracePeriodTicks = 5;

    @Key("max-damager-search-depth")
    @Comment("Максимальная глубина поиска атакующего")
    int maxDamagerSearchDepth = 10;

    @Key("bossbar-enabled")
    @Comment("Включить отображение боссбара в боевом режиме")
    boolean bossbarEnabled = true;

    @Key("bossbar-color")
    @Comment("Цвет боссбара: RED, BLUE, GREEN, YELLOW, PINK, PURPLE, WHITE")
    String bossbarColor = "RED";

    @Key("bossbar-style")
    @Comment("Стиль боссбара: SOLID, SEGMENTED_6, SEGMENTED_10, SEGMENTED_12, SEGMENTED_20")
    String bossbarStyle = "SOLID";

    @Key("bossbar-title")
    @Comment("Текст боссбара с поддержкой цветов (&a, &b) и плейсхолдеров (%time%, %formated-sec%)")
    String bossbarTitle = "&r&lРежим &c&lCOMBAT &r&l- &a&l%time% &r&l%formated-sec%.";

    @Key("scoreboard-enabled")
    @Comment("Включить отображение скорборда в боевом режиме (требует плагин TAB)")
    boolean scoreboardEnabled = false;

    @Key("scoreboard-provider")
    @Comment("Провайдер скорборда: TAB")
    String scoreboardProvider = "TAB";

    @Key("scoreboard-title")
    @Comment("Заголовок скорборда с поддержкой цветов")
    String scoreboardTitle = "&cРежим боя";

    @Key("scoreboard-lines")
    @Comment("Строки скорборда. Используйте {time} для времени, {opponents} для списка противников")
    List<String> scoreboardLines = List.of(
            "",
            "&fПодождите &c{time}",
            "&fПеред выходом с сервера",
            "",
            "&cПротивники:",
            "{opponents}",
            ""
    );

    @Key("scoreboard-opponent")
    @Comment("Формат отображения противника. {player} - имя, {health} - здоровье, {ping} - пинг")
    String scoreboardOpponent = "&f{player} &c{health}❤ &9{ping}⇄";

    @Key("scoreboard-empty")
    @Comment("Сообщение, когда нет противников")
    String scoreboardEmpty = "&7Отсутствуют";

    public BossbarConfig getBossbar() {
        return new BossbarConfig(bossbarEnabled, bossbarColor, bossbarStyle, bossbarTitle);
    }

    public ScoreboardConfig getScoreboard() {
        return new ScoreboardConfig(scoreboardEnabled, scoreboardProvider, scoreboardTitle,
                scoreboardLines, scoreboardOpponent, scoreboardEmpty);
    }

    public static class BossbarConfig {
        private final boolean enabled;
        private final String color;
        private final String style;
        private final String title;

        public BossbarConfig(boolean enabled, String color, String style, String title) {
            this.enabled = enabled;
            this.color = color;
            this.style = style;
            this.title = title;
        }

        public boolean isEnabled() { return enabled; }
        public String getColor() { return color; }
        public String getStyle() { return style; }
        public String getTitle() { return title; }
    }

    public static class ScoreboardConfig {
        private final boolean enabled;
        private final String provider;
        private final String title;
        private final List<String> lines;
        private final String opponent;
        private final String empty;

        public ScoreboardConfig(boolean enabled, String provider, String title,
                                List<String> lines, String opponent, String empty) {
            this.enabled = enabled;
            this.provider = provider;
            this.title = title;
            this.lines = lines;
            this.opponent = opponent;
            this.empty = empty;
        }

        public boolean isEnabled() { return enabled; }
        public String getProvider() { return provider; }
        public String getTitle() { return title; }
        public List<String> getLines() { return lines; }
        public String getOpponent() { return opponent; }
        public String getEmpty() { return empty; }
    }
}
