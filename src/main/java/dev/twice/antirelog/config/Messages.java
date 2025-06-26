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
@Comment("Для отключения сообщения оставьте его пустым")
public final class Messages implements ConfigurationSection {

    @Key("pvp-started")
    String pvpStarted = "&bВы начали &e&lPVP&b!";

    @Key("pvp-started-title")
    String pvpStartedTitle = "&bAntiRelog";

    @Key("pvp-started-subtitle")
    String pvpStartedSubtitle = "Вы вошли в режим &ePVP&a!";

    @Key("pvp-stopped")
    String pvpStopped = "&e&lPVP &bокончено";

    @Key("pvp-stopped-title")
    String pvpStoppedTitle = "&bAntiRelog";

    @Key("pvp-stopped-subtitle")
    String pvpStoppedSubtitle = "Вы вышли из режима &ePVP&a!";

    @Key("pvp-stopped-actionbar")
    String pvpStoppedActionbar = "&e&lPVP &aокончено, Вы снова можете использовать команды и выходить из игры!";

    @Key("in-pvp-bossbar")
    String inPvpBossbar = "&r&lРежим &c&lPVP &r&l- &a&l%time% &r&l%formated-sec%.";

    @Key("in-pvp-actionbar")
    String inPvpActionbar = "&r&lРежим &c&lPVP&r&l, не выходите из игры &a&l%time% &r&l%formated-sec%.";

    @Key("pvp-leaved")
    String pvpLeaved = "&aИгрок &c&l%player% &aпокинул игру во время &b&lПВП&a и был наказан.";

    @Key("commands-disabled")
    String commandsDisabled = "&b&lВы не можете использовать команды в &e&lPvP&b&l. &b&lПодождите &a&l%time% &b&l%formated-sec%.";

    @Key("item-cooldown")
    String itemCooldown = "&b&lВы сможете использовать этот предмет через &a&l%time% &b&l%formated-sec%.";

    @Key("item-disabled-in-pvp")
    String itemDisabledInPvp = "&b&lВы не можете использовать этот предмет в &e&lPVP &b&lрежиме";

    @Key("totem-cooldown")
    String totemCooldown = "&b&lТотем не был использован, т.к был недавно использован. Тотем будет доступен через &a&l%time% &b&l%formated-sec%.";

    @Key("totem-disabled-in-pvp")
    String totemDisabledInPvp = "&b&lТотем не был использован, т.к он отключен в &e&lPVP &b&lрежиме";

    @Key("pvp-started-with-powerups")
    @Comment("Сообщение появляется при включенной функции 'commands-on-powerups-disable'")
    String pvpStartedWithPowerups = "&c&lВы начали пвп с включенным GM/FLY/и тд и за это получили негативный эффект";
}