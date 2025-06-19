package dev.twice.antirelog.events;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
public class PvpTimeUpdateEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final int oldTimeSeconds;
    private final int newTimeSeconds;

    @Setter
    @Nullable
    private Player targetPlayer;

    @Setter
    @Nullable
    private Player sourcePlayer;

    public PvpTimeUpdateEvent(@NotNull Player player, int oldTimeSeconds, int newTimeSeconds) {
        super();
        this.player = player;
        this.oldTimeSeconds = oldTimeSeconds;
        this.newTimeSeconds = newTimeSeconds;
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
