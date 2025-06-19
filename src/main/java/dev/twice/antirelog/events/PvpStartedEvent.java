package dev.twice.antirelog.events;

import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@Getter
public class PvpStartedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player defender;
    private final Player attacker;
    private final int pvpTimeSeconds;
    private final PvpPreStartEvent.PvPStatus pvpStatus;

    public PvpStartedEvent(@NotNull Player defender, @NotNull Player attacker, int pvpTimeSeconds, @NotNull PvpPreStartEvent.PvPStatus pvpStatus) {
        super();
        this.defender = defender;
        this.attacker = attacker;
        this.pvpTimeSeconds = pvpTimeSeconds;
        this.pvpStatus = pvpStatus;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
