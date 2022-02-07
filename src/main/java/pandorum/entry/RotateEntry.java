package pandorum.entry;

import arc.util.Time;
import mindustry.gen.Iconc;
import mindustry.gen.Player;
import mindustry.net.Administration.PlayerAction;
import pandorum.comp.Bundle;
import pandorum.comp.Icons;

import static mindustry.Vars.content;
import static pandorum.util.Search.findLocale;
import static pandorum.util.Utils.formatDate;

public class RotateEntry implements CacheEntry {
    public static final char[] sides = {Iconc.right, Iconc.up, Iconc.left, Iconc.down};

    public final String name;
    public final short blockID;
    public final int rotation;
    public final long time;

    public RotateEntry(PlayerAction action) {
        this.name = action.player.coloredName();
        this.blockID = action.tile.build.block.id;
        this.rotation = action.rotation;
        this.time = Time.millis();
    }

    @Override
    public String getMessage(Player player) {
        return Bundle.format("history.rotate", findLocale(player.locale), name, Icons.get(content.block(blockID).name), sides[rotation], formatDate(time));
    }
}
