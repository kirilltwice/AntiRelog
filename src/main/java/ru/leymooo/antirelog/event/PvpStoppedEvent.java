package ru.leymooo.antirelog.event;

import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@Getter
public class PvpStoppedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;

    public PvpStoppedEvent(@NotNull Player player) {
        super();
        this.player = player;
    }

    @Deprecated
    public PvpStoppedEvent(boolean isAsync, @NotNull Player player) {
        super(isAsync);
        this.player = player;
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