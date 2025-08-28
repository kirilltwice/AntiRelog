# 🛡️ AntiRelog

<div align="center">

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.4-brightgreen)
![Paper](https://img.shields.io/badge/Paper-Compatible-blue)
![Java](https://img.shields.io/badge/Java-21+-red)

**Продвинутая система предотвращения выхода из игры во время PvP боя**

</div>

---

## ⚙️ Конфигурация

Полная конфигурация доступна по ссылке: [https://pastebin.com/m0Q4Ci6p](https://pastebin.com/m0Q4Ci6p)

## 🔧 Команды и права

### Команды

| Команда | Алиасы | Описание | Права |
|---------|--------|----------|-------|
| `/antirelog` | `/ar`, `/combat` | Показать помощь | `antirelog.admin` |
| `/antirelog reload` | `/ar reload` | Перезагрузить конфигурацию | `antirelog.admin` |
| `/antirelog start` | `/ar start` | Принудительно войти в боевой режим | `antirelog.admin` |
| `/antirelog stop` | `/ar stop` | Выйти из боевого режима | `antirelog.admin` |

### Права доступа

| Право | Описание |
|-------|----------|
| `antirelog.bypass` | Обход системы AntiRelog |
| `antirelog.admin` | Доступ ко всем командам плагина |

## 🔌 API для разработчиков

### Основные методы

```java
import dev.twice.antirelog.api.AntiRelogAPI;

// Проверка статуса
boolean inCombat = AntiRelogAPI.isInCombat(player);
int timeLeft = AntiRelogAPI.getCombatTimeRemaining(player);
boolean infiniteCombat = AntiRelogAPI.hasInfiniteCombat(player);

// Управление боем
AntiRelogAPI.startCombat(player);
AntiRelogAPI.stopCombat(player);
AntiRelogAPI.setInfiniteCombat(player, true);
```

### События

```java
@EventHandler
public void onCombatEnd(CombatEndEvent event) {
    Player player = event.getPlayer();
    // Бой закончен
}

@EventHandler
public void onCombatTick(CombatTickEvent event) {
    Player player = event.getPlayer();
    // Каждый тик боя
}

@EventHandler
public void onPlayerLeaveInCombat(PlayerLeaveInCombatEvent event) {
    Player player = event.getPlayer();
    // Игрок вышел в бою
}

@EventHandler
public void onPlayerKickInCombat(PlayerKickInCombatEvent event) {
    Player player = event.getPlayer();
    String reason = event.getReason();
    // Игрок кикнут в бою
}
```

## 📞 Поддержка

- **Telegram**: [t.me/kirilltwice](https://t.me/kirilltwice)
- **Issues**: [GitHub Issues](https://github.com/kirilltwice/AntiRelog/issues)

---

<div align="center">

**⭐ Поставьте звезду, если проект вам помог!**

Made with ❤️ by [Twice](https://github.com/kirilltwice)

</div>
