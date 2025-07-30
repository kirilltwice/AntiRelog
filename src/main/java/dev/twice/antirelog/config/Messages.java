package dev.twice.antirelog.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import ru.leymooo.annotatedyaml.Annotations.*;
import ru.leymooo.annotatedyaml.ConfigurationSection;

import static lombok.AccessLevel.PRIVATE;

@Data
@NoArgsConstructor
@FieldDefaults(level = PRIVATE)
public final class Messages implements ConfigurationSection {

    @Key("no-permission")
    @Comment("Сообщение при отсутствии прав")
    String noPermission = "&cУ вас нет прав для использования этой команды";

    @Key("player-not-found")
    @Comment("Сообщение когда игрок не найден")
    String playerNotFound = "&cИгрок не найден";

    @Key("invalid-usage")
    @Comment("Сообщение при неправильном использовании команды")
    String invalidUsage = "&cНеправильное использование команды";

    @Key("combat-started")
    @Comment("Сообщение при входе в боевой режим")
    String combatStarted = "&bВы начали &e&lCOMBAT&b!";

    @Key("combat-stopped")
    @Comment("Сообщение при выходе из боевого режима")
    String combatStopped = "&e&lCOMBAT &bокончено";

    @Key("combat-left")
    @Comment("Сообщение всем игрокам когда кто-то вышел в бою. %player% - имя игрока")
    String combatLeft = "&aИгрок &c&l%player% &aпокинул игру во время &b&lCOMBAT&a и был наказан.";

    @Key("combat-started-title")
    @Comment("Заголовок титра при входе в бой")
    String combatStartedTitle = "&bAntiRelog";

    @Key("combat-started-subtitle")
    @Comment("Подзаголовок титра при входе в бой")
    String combatStartedSubtitle = "Вы вошли в режим &eCOMBAT&a!";

    @Key("combat-stopped-title")
    @Comment("Заголовок титра при выходе из боя")
    String combatStoppedTitle = "&bAntiRelog";

    @Key("combat-stopped-subtitle")
    @Comment("Подзаголовок титра при выходе из боя")
    String combatStoppedSubtitle = "Вы вышли из режима &eCOMBAT&a!";

    @Key("in-combat-actionbar")
    @Comment("Текст в экшн баре во время боя. %time% - время, %formated-sec% - секунды")
    String inCombatActionbar = "&r&lРежим &c&lCOMBAT&r&l, не выходите из игры &a&l%time% &r&l%formated-sec%.";

    @Key("combat-stopped-actionbar")
    @Comment("Текст в экшн баре при окончании боя")
    String combatStoppedActionbar = "&e&lCOMBAT &aокончено, Вы снова можете использовать команды и выходить из игры!";

    @Key("commands-disabled")
    @Comment("Сообщение при попытке использовать команду в бою")
    String commandsDisabled = "&b&lВы не можете использовать команды в &e&lCombat&b&l. &b&lПодождите &a&l%time% &b&l%formated-sec%.";

    @Key("item-disabled-in-combat")
    @Comment("Сообщение при попытке использовать запрещенный предмет в бою")
    String itemDisabledInCombat = "&b&lВы не можете использовать этот предмет в &e&lCOMBAT &b&lрежиме";

    @Key("item-cooldown")
    @Comment("Сообщение о кулдауне предмета. %time% - время ожидания")
    String itemCooldown = "&b&lВы сможете использовать этот предмет через &a&l%time% &b&l%formated-sec%.";

    @Key("totem-cooldown")
    @Comment("Сообщение о кулдауне тотема")
    String totemCooldown = "&b&lТотем не был использован, т.к был недавно использован. Тотем будет доступен через &a&l%time% &b&l%formated-sec%.";

    @Key("totem-disabled-in-combat")
    @Comment("Сообщение при запрете тотема в бою")
    String totemDisabledInCombat = "&b&lТотем не был использован, т.к он отключен в &e&lCOMBAT &b&lрежиме";

    @Key("combat-started-with-powerups")
    @Comment("Сообщение при входе в бой с активными усилениями")
    String combatStartedWithPowerups = "&c&lВы начали combat с включенным GM/FLY/и тд и за это получили негативный эффект";

    @Key("title-fade-in")
    @Comment("Время появления тайтла в миллисекундах")
    int titleFadeIn = 500;

    @Key("title-stay")
    @Comment("Время показа тайтла в миллисекундах")
    int titleStay = 1500;

    @Key("title-fade-out")
    @Comment("Время исчезновения тайтла в миллисекундах")
    int titleFadeOut = 500;

    public TitleDurations getTitleDurations() {
        return new TitleDurations(titleFadeIn, titleStay, titleFadeOut);
    }

    public static class TitleDurations {
        private final int fadeIn;
        private final int stay;
        private final int fadeOut;

        public TitleDurations(int fadeIn, int stay, int fadeOut) {
            this.fadeIn = fadeIn;
            this.stay = stay;
            this.fadeOut = fadeOut;
        }

        public int getFadeIn() { return fadeIn; }
        public int getStay() { return stay; }
        public int getFadeOut() { return fadeOut; }
    }
}