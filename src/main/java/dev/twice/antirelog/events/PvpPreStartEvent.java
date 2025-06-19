package dev.twice.antirelog.events;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@Getter
public class PvpPreStartEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player defender;
    private final Player attacker;
    private final int pvpTimeSeconds;
    private final PvPStatus pvpStatus;

    @Setter
    private boolean cancelled;

    public PvpPreStartEvent(@NotNull Player defender, @NotNull Player attacker, int pvpTimeSeconds, @NotNull PvPStatus pvpStatus) {
        super();
        this.defender = defender;
        this.attacker = attacker;
        this.pvpTimeSeconds = pvpTimeSeconds;
        this.pvpStatus = pvpStatus;
        this.cancelled = false;
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

    public enum PvPStatus {
        ATTACKER_IN_PVP,
        DEFENDER_IN_PVP,
        ALL_NOT_IN_PVP
    }
}
