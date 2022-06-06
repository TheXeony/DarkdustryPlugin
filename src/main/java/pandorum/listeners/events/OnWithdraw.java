package pandorum.listeners.events;

import arc.func.Cons;
import mindustry.game.EventType.WithdrawEvent;
import pandorum.features.history.entry.HistoryEntry;
import pandorum.features.history.entry.WithdrawEntry;

import static pandorum.PluginVars.history;
import static pandorum.PluginVars.historyEnabled;

public class OnWithdraw implements Cons<WithdrawEvent> {

    public void get(WithdrawEvent event) {
        if (historyEnabled()) {
            HistoryEntry entry = new WithdrawEntry(event);
            event.tile.tile.getLinkedTiles(tile -> history[tile.x][tile.y].add(entry));
        }
    }
}
