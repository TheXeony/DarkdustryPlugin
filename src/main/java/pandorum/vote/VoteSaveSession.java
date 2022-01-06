package pandorum.vote;

import arc.Core;
import arc.files.Fi;
import arc.util.Timer;
import arc.util.Timer.Task;
import mindustry.gen.Player;
import mindustry.io.SaveIO;

import static pandorum.Misc.sendToChat;
import static pandorum.PluginVars.config;

public class VoteSaveSession extends VoteSession {
    protected final Fi target;

    public VoteSaveSession(VoteSession[] session, Fi target) {
        super(session);
        this.target = target;
    }

    @Override
    protected Task start() {
        return Timer.schedule(() -> {
            if (!checkPass()) {
                sendToChat("commands.nominate.save.failed", target);
                stop();
            }
        }, config.voteDuration);
    }

    @Override
    public void vote(Player player, int sign) {
        votes += sign;
        voted.add(player.uuid());
        sendToChat("commands.nominate.save.vote", player.coloredName(), target, votes, votesRequired());
        checkPass();
    }

    @Override
    protected boolean checkPass() {
        if (votes >= votesRequired()) {
            sendToChat("commands.nominate.save.passed", target);
            stop();
            Core.app.post(() -> SaveIO.save(target));
            return true;
        }
        return false;
    }
}
