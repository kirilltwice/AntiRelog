package dev.twice.antirelog.events;

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
