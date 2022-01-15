package pandorum.models;

import arc.func.Boolf;
import arc.func.Cons;
import com.mongodb.BasicDBObject;
import pandorum.database.MongoDataBridge;

public class PlayerModel extends MongoDataBridge<PlayerModel> {
    public String UUID;
    public String locale = "off";
    public Boolean hellomsg = true;
    public boolean alerts = true;
    public long playTime = 0L;
    public int buildingsBuilt = 0;
    public int buildingsDeconstructed = 0;
    public int gamesPlayed = 0;
    public int rank = 0;

    public static void find(String UUID, Cons<PlayerModel> cons) {
        findAndApplySchema(PlayerModel.class, new BasicDBObject("UUID", UUID), cons);
    }

    public static void find(String UUID, Boolf<PlayerModel> boolf, Cons<PlayerModel> cons) {
        find(UUID, playerModel -> {
            if (boolf.get(playerModel)) cons.get(playerModel);
        });
    }
}


