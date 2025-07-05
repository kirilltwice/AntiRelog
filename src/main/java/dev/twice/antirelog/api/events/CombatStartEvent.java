package dev.twice.antirelog.api.events;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

@Getter
public class CombatStartEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private final Player attacker;
    private final Player defender;
    @Setter private boolean cancelled;

    public CombatStartEvent(Player attacker, Player defender) {
        this.attacker = attacker;
        this.defender = defender;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}