package pandorum;

import static mindustry.Vars.dataDirectory;
import static mindustry.Vars.netServer;
import static mindustry.Vars.world;
import static mindustry.Vars.content;
import static mindustry.Vars.state;
import static pandorum.Misc.bundled;
import static pandorum.Misc.findLocale;
import static pandorum.Misc.sendToChat;
import static pandorum.events.ActionFilter.call;
import static pandorum.events.BlockBuildEndEvent.call;
import static pandorum.events.BuildSelectEvent.call;
import static pandorum.events.ChatFilter.call;
import static pandorum.events.ConfigEvent.call;
import static pandorum.events.DepositEvent.call;
import static pandorum.events.GameOverEvent.call;
import static pandorum.events.PlayerBanEvent.call;
import static pandorum.events.PlayerJoinEvent.call;
import static pandorum.events.PlayerLeaveEvent.call;
import static pandorum.events.PlayerUnbanEvent.call;
import static pandorum.events.ServerLoadEvent.call;
import static pandorum.events.TapEvent.call;
import static pandorum.events.TriggerUpdate.call;
import static pandorum.events.WorldLoadEvent.call;

import java.awt.Color;
import java.util.Objects;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.math.Mathf;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.ArcRuntimeException;
import arc.util.CommandHandler;
import arc.util.Interval;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Structs;
import arc.util.Timer;
import arc.util.Time;
import arc.util.io.Streams;
import club.minnced.discord.webhook.send.WebhookEmbed;
import club.minnced.discord.webhook.send.WebhookEmbedBuilder;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.EventType.BlockBuildEndEvent;
import mindustry.game.EventType.BuildSelectEvent;
import mindustry.game.EventType.ConfigEvent;
import mindustry.game.EventType.DepositEvent;
import mindustry.game.EventType.GameOverEvent;
import mindustry.game.EventType.PlayerBanEvent;
import mindustry.game.EventType.PlayerJoin;
import mindustry.game.EventType.PlayerLeave;
import mindustry.game.EventType.PlayerUnbanEvent;
import mindustry.game.EventType.ServerLoadEvent;
import mindustry.game.EventType.TapEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.game.Team;
import mindustry.game.Teams.TeamData;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.maps.Map;
import mindustry.mod.Plugin;
import mindustry.net.Administration;
import mindustry.type.*;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;

import pandorum.comp.Bundle;
import pandorum.comp.Config;
import pandorum.comp.Config.PluginType;
import pandorum.comp.DiscordWebhookManager;
import pandorum.comp.IpInfo;
import pandorum.entry.HistoryEntry;
import pandorum.entry.ConfigEntry;
import pandorum.struct.CacheSeq;
import pandorum.struct.Tuple2;
import pandorum.vote.VoteLoadSession;
import pandorum.vote.VoteMapSession;
import pandorum.vote.VoteMode;
import pandorum.vote.VoteSaveSession;
import pandorum.vote.VoteSession;
import pandorum.vote.VoteKickSession;

public final class PandorumPlugin extends Plugin{

    public final Gson gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_DASHES)
            .setPrettyPrinting()
            .serializeNulls()
            .disableHtmlEscaping()
            .create();

    public static VoteSession[] current = {null};
    public static VoteKickSession[] currentlyKicking = {null};
    public static Config config;
    public static Seq<IpInfo> forbiddenIps;

    public static final ObjectMap<Team, ObjectSet<String>> surrendered = new ObjectMap<>();
    public static final ObjectSet<String> votesRTV = new ObjectSet<>();
    public static final ObjectSet<String> votesVNW = new ObjectSet<>();
    public static final ObjectSet<String> alertIgnores = new ObjectSet<>();
    public static final ObjectSet<String> activeHistoryPlayers = new ObjectSet<>();
    public static final Interval interval = new Interval(2);

    public static CacheSeq<HistoryEntry>[][] history;
    public static final Seq<RainbowPlayerEntry> rainbow = new Seq<>();
    public static final ObjectMap<Unit, Float> timer = new ObjectMap<>();

    private static final ObjectMap<String, String> codeLanguages = new ObjectMap<>();
    private static final OkHttpClient client = new OkHttpClient();

    public PandorumPlugin() {
        Fi cfg = dataDirectory.child("config.json");
        if(!cfg.exists()){
            cfg.writeString(gson.toJson(config = new Config()));
            Log.info("Файл config.json успешно сгенерирован!");
        } else {
            config = gson.fromJson(cfg.reader(), Config.class);
        }
        JSONArray languages = Translator.getAllLanguages();
        for (int i = 0; i < languages.length(); i++) {
            String codeAlpha = languages.getJSONObject(i).getString("code_alpha_1");
            String fullCode = languages.getJSONObject(i).getString("full_code");
            codeLanguages.put(codeAlpha, fullCode);
        }
    }

    @Override
    public void init() {

        try {
            forbiddenIps = Seq.with(Streams.copyString(Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("vpn-ipv4.txt"))).split(System.lineSeparator())).map(IpInfo::new);
        } catch(Exception e) {
            throw new ArcRuntimeException(t);
        }

        Administration.Config.showConnectMessages.set(false);
        Administration.Config.strict.set(true);
        Administration.Config.motd.set("off");

        netServer.admins.addActionFilter(action -> call(action));
        netServer.admins.addChatFilter((player, text) -> call(player, text));

        Events.on(PlayerUnbanEvent.class, event -> call(event));
        Events.on(PlayerBanEvent.class, event -> call(event));
        Events.on(ServerLoadEvent.class, event -> call(event));
        Events.on(WorldLoadEvent.class, event -> call(event));
        Events.on(BlockBuildEndEvent.class, event -> call(event));
        Events.on(ConfigEvent.class, event -> call(event));
        Events.on(TapEvent.class, event -> call(event));
        Events.on(DepositEvent.class, event -> call(event));
        Events.on(BuildSelectEvent.class, event -> call(event));
        Events.on(PlayerJoin.class, event -> call(event));
        Events.on(PlayerLeave.class, event -> call(event));
        Events.on(GameOverEvent.class, event -> call(event));
        Events.run(Trigger.update, () -> call());

        Timer.schedule(() -> rainbow.each(r -> Groups.player.contains(p -> p == r.player), r -> {
            int hue = r.hue;
            if(hue < 360) hue++;
            else hue = 0;

            String hex = "[#" + Integer.toHexString(Color.getHSBColor(hue / 360f, 1f, 1f).getRGB()).substring(2) + "]";
            r.player.name = hex + r.stripedName;
            r.hue = hue;
        }), 0f, 0.05f);
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {

        handler.register("reload-config", "Перезапустить файл конфигов.", args -> {
            config = gson.fromJson(dataDirectory.child("config.json").readString(), Config.class);
            Log.info("Перезагружено.");
        });

        handler.register("despw", "Убить всех юнитов на карте", args -> {
            int amount = Groups.unit.size();
            Groups.unit.each(unit -> unit.kill());
            Log.info("Ты убил " + amount + " юнитов!");
            WebhookEmbedBuilder despwEmbedBuilder = new WebhookEmbedBuilder()
                .setColor(0xFF0000)
                .setTitle(new WebhookEmbed.EmbedTitle("Все юниты убиты!", null));
            DiscordWebhookManager.client.send(despwEmbedBuilder.build());
        });

        handler.register("unban-all", "Разбанить всех", arg -> {
            netServer.admins.getBanned().each(unban -> netServer.admins.unbanPlayerID(unban.id));
            netServer.admins.getBannedIPs().each(ip -> netServer.admins.unbanPlayerIP(ip));
            Log.info("Все игроки разбанены!");
        });

        handler.register("rr", "Перезапустить сервер", args -> {
            Log.info("Перезапуск сервера...");
            WebhookEmbedBuilder banEmbedBuilder = new WebhookEmbedBuilder()
                .setColor(0xFF0000)
                .setTitle(new WebhookEmbed.EmbedTitle("Сервер выключился для перезапуска!", null));
            DiscordWebhookManager.client.send(banEmbedBuilder.build());
            
            Timer.schedule(() -> System.exit(2), 5f);
        });

        handler.removeCommand("say");
        handler.register("say", "<Сообщение...>", "Сказать от имени сервера.", arg -> {
            Call.sendMessage("[lime]Server[white]: " + arg[0]);
            Log.info("Server: &ly" + arg[0]);
            DiscordWebhookManager.client.send(String.format("**[Сервер]:** %s", arg[0].replaceAll("https?://|@", "")));
        });

        if(config.type == PluginType.sand || config.type == PluginType.anarchy) {
            handler.register("despawndelay", "[новое_значение]", "Изменить/показать текущую продолжительность жизни юнитов.", args -> {
                if (args.length == 0) {
                    Log.info("DespawnDelay сейчас: @", new Object[] { Core.settings.getFloat("despawndelay", defDelay) });
                    return;
                }
                final String value = args[0];
                if (!Strings.canParsePositiveInt(value)) {
                    Log.err("Новое значение должно быть положительным целым числом.", new Object[0]);
                    return;
                }
                Core.settings.put("despawndelay", (Object)Strings.parseFloat(value));
            });
        }
    }

    @Override
    public void registerClientCommands(CommandHandler handler){
        handler.removeCommand("a");
        handler.removeCommand("t");

        handler.removeCommand("help");
        handler.removeCommand("votekick");
        handler.removeCommand("vote");

        handler.<Player>register("help", "[page]", "Lists all commands.", (args, player) -> {
            if(args.length > 0 && !Strings.canParseInt(args[0])) {
                bundled(player, "commands.page-not-int");
                return;
            }
            int commandsPerPage = 6;
            int page = args.length > 0 ? Strings.parseInt(args[0]) : 1;
            int pages = Mathf.ceil((float)netServer.clientCommands.getCommandList().size / commandsPerPage);

            page--;

            if(page >= pages || page < 0){
                bundled(player, "commands.under-page", String.valueOf(pages));
                return;
            }

            StringBuilder result = new StringBuilder();
            result.append(Bundle.format("commands.help.page", findLocale(player.locale), page + 1, pages)).append("\n");

            for(int i = commandsPerPage * page; i < Math.min(commandsPerPage * (page + 1), netServer.clientCommands.getCommandList().size); i++){
                CommandHandler.Command command = netServer.clientCommands.getCommandList().get(i);
                String desc = Bundle.format("commands." + command.text + ".description", findLocale(player.locale));
                if(desc.startsWith("?")) {
                    desc = command.description;
                }
                result.append("[orange] /").append(command.text).append("[white] ").append(command.paramText).append("[lightgray] - ").append(desc).append("\n");
            }
            player.sendMessage(result.toString());
        });

        handler.<Player>register("a", "<message...>", "Send a message to admins.", (args, player) -> {
            if (!Misc.adminCheck(player)) return;
            Groups.player.each(Player::admin, otherPlayer -> bundled(otherPlayer, "commands.admin.a.chat", Misc.colorizedName(player), args[0]));
        });

        handler.<Player>register("t", "<message...>", "Send a message to teammates.", (args, player) -> {
            String teamColor = "[#" + player.team().color + "]";
            Groups.player.each(o -> o.team() == player.team(), otherPlayer -> bundled(otherPlayer, "commands.t.chat", teamColor, Misc.colorizedName(player), args[0]));
        });

        handler.<Player>register("history", "Переключение отображения истории при нажатии на тайл", (args, player) -> {
            String uuid = player.uuid();
            if(activeHistoryPlayers.contains(uuid)){
                activeHistoryPlayers.remove(uuid);
                bundled(player, "commands.history.off");
            }else{
                activeHistoryPlayers.add(uuid);
                bundled(player, "commands.history.on");
            }
        });

        handler.<Player>register("pl", "[page]", "Вывести список игроков и их ID", (args, player) -> {
            if(args.length > 0 && !Strings.canParseInt(args[0])){
                bundled(player, "commands.page-not-int");
                return;
            }

            int page = args.length > 0 ? Strings.parseInt(args[0]) : 1;
            int pages = Mathf.ceil((float)Groups.player.size() / 6);

            page--;

            if(page >= pages || page < 0){
                bundled(player, "commands.under-page", pages);
                return;
            }

            StringBuilder result = new StringBuilder();
            result.append(Bundle.format("commands.pl.page", findLocale(player.locale), page + 1, pages)).append("\n");

            for(int i = 6 * page; i < Math.min(6 * (page + 1), Groups.player.size()); i++){
                Player t = Groups.player.index(i);
                result.append("[#9c88ee]* ").append(t.name).append(" [accent]/ [cyan]ID: ").append(t.id());

                if(player.admin){
                    result.append(" [accent]/ [cyan]raw: ").append(t.name.replaceAll("\\[", "[["));
                }
                result.append("\n");
            }
            player.sendMessage(result.toString());
        });

        handler.<Player>register("despw", "Убить всех юнитов на карте", (args, player) -> {
            int amount = Groups.unit.size();
            if(!Misc.adminCheck(player)) return;
            Groups.unit.each(unit -> unit.kill());
            bundled(player, "commands.admin.despw.success", amount);
            WebhookEmbedBuilder despwEmbedBuilder = new WebhookEmbedBuilder()
                .setColor(0xFF0000)
                .setTitle(new WebhookEmbed.EmbedTitle("Все юниты убиты!", null))
                .addField(new WebhookEmbed.EmbedField(true, "Imposter", Strings.stripColors(player.name)));
            DiscordWebhookManager.client.send(despwEmbedBuilder.build());
        });

        if(config.type != PluginType.other) {
            handler.<Player>register("rtv", "Проголосовать за смену карты.", (args, player) -> {
                if(votesRTV.contains(player.uuid())){
                    bundled(player, "commands.already-voted");
                    return;
                }

                votesRTV.add(player.uuid());
                int cur = votesRTV.size;
                int req = (int)Math.ceil(config.voteRatio * Groups.player.size());
                sendToChat("commands.rtv.ok", Misc.colorizedName(player), cur, req);

                if(cur < req){
                    return;
                }

                sendToChat("commands.rtv.successful");
                votesRTV.clear();
                Events.fire(new GameOverEvent(Team.crux));
            });

            handler.<Player>register("vnw", "Проголосовать за пропуск волны.", (args, player) -> {
                if(votesVNW.contains(player.uuid())){
                    bundled(player, "commands.already-voted");
                    return;
                }

                votesVNW.add(player.uuid());
                int cur = votesVNW.size;
                int req = (int)Math.ceil(config.voteRatio * Groups.player.size());
                sendToChat("commands.vnw.ok", Misc.colorizedName(player), cur, req);

                if(cur < req){
                    return;
                }

                sendToChat("commands.vnw.successful");
                votesVNW.clear();
                Vars.logic.runWave();
            });

            handler.<Player>register("artv", "Принудительно завершить игру.", (args, player) -> {
                if(!Misc.adminCheck(player)) return;
                Events.fire(new GameOverEvent(Team.crux));
                sendToChat("commands.admin.artv.info");
                WebhookEmbedBuilder artvEmbedBuilder = new WebhookEmbedBuilder()
                    .setColor(0xFF0000)
                    .setTitle(new WebhookEmbed.EmbedTitle("Игра принудительно завершена!", null))
                    .addField(new WebhookEmbed.EmbedField(true, "Imposter", Strings.stripColors(player.name)));
                DiscordWebhookManager.client.send(artvEmbedBuilder.build());
            });

            handler.<Player>register("core", "<small/medium/big>", "Заспавнить ядро.", (args, player) -> {
                if(!Misc.adminCheck(player)) return;

                Block core = switch(args[0].toLowerCase()){
                    case "medium" -> Blocks.coreFoundation;
                    case "big" -> Blocks.coreNucleus;
                    default -> Blocks.coreShard;
                };

                Call.constructFinish(player.tileOn(), core, player.unit(), (byte)0, player.team(), false);

                bundled(player, player.tileOn().block() == core ? "commands.admin.core.success" : "commands.admin.core.failed");
            });

            handler.<Player>register("give", "<item> [count]", "Выдать ресурсы в ядро.", (args, player) -> {
                if(!Misc.adminCheck(player)) return;

                if(args.length > 1 && !Strings.canParseInt(args[1])){
                    bundled(player, "commands.non-int");
                    return;
                }

                int count = args.length > 1 ? Strings.parseInt(args[1]) : 1000;

                Item item = content.items().find(b -> b.name.equalsIgnoreCase(args[0]));
                if(item == null){
                    bundled(player, "commands.admin.give.item-not-found");
                    return;
                }

                TeamData team = state.teams.get(player.team());
                if(!team.hasCore()){
                    bundled(player, "commands.admin.give.core-not-found");
                    return;
                }

                team.core().items.add(item, count);

                bundled(player, "commands.admin.give.success");
            });

            handler.<Player>register("alert", "Включить или отключить предупреждения о постройке реакторов вблизи к ядру", (args, player) -> {
                if(alertIgnores.contains(player.uuid())){
                    alertIgnores.remove(player.uuid());
                    bundled(player, "commands.alert.on");
                }else{
                    alertIgnores.add(player.uuid());
                    bundled(player, "commands.alert.off");
                }
            });

            handler.<Player>register("team", "<team> [name]", "Смена команды для [scarlet]Админов", (args, player) -> {
                if(!Misc.adminCheck(player)) return;

                Team team = Structs.find(Team.all, t -> t.name.equalsIgnoreCase(args[0]));
                if(team == null){
                    bundled(player, "commands.teams");
                    return;
                }
            
                Player target = args.length > 1 ? Misc.findByName(args[1]) : player;
                if(target == null){
                    bundled(player, "commands.player-not-found");
                    return;
                }

                bundled(target, "commands.admin.team.success", Misc.colorizedTeam(team));
                target.team(team);
                String text = args.length > 1 ? "Команда игрока " + target.name() + " изменена на " + team + "." : "Команда изменена на " + team + ".";
                WebhookEmbedBuilder artvEmbedBuilder = new WebhookEmbedBuilder()
                    .setColor(0xFF0000)
                    .setTitle(new WebhookEmbed.EmbedTitle(text, null))
                    .addField(new WebhookEmbed.EmbedField(true, "Администратором", Strings.stripColors(player.name)));
                DiscordWebhookManager.client.send(artvEmbedBuilder.build());
            });

            handler.<Player>register("spectate", "Oh no", (args, player) -> {
                if(!Misc.adminCheck(player)) return;
                player.clearUnit();
                player.team(player.team() == Team.derelict ? Team.sharded : Team.derelict);
            });

            handler.<Player>register("map", "Информация о карте", (args, player) -> bundled(player, "commands.mapname", Vars.state.map.name(), Vars.state.map.author()));

            handler.<Player>register("maps", "[page]", "Вывести список карт.", (args, player) -> {
                if(args.length > 0 && !Strings.canParseInt(args[0])){
                    bundled(player, "commands.page-not-int");
                    return;
                }

                Seq<Map> mapList = Vars.maps.all();
                int page = args.length > 0 ? Strings.parseInt(args[0]) : 1;
                int pages = Mathf.ceil(mapList.size / 6f);

                if(--page >= pages || page < 0){
                    bundled(player, "commands.under-page", pages);
                    return;
                }

                StringBuilder result = new StringBuilder();
                result.append(Bundle.format("commands.maps.page", findLocale(player.locale), page + 1, pages)).append("\n");
                for(int i = 6 * page; i < Math.min(6 * (page + 1), mapList.size); i++){
                    result.append("[lightgray] ").append(i + 1).append("[orange] ").append(mapList.get(i).name()).append("[white] ").append("\n");
                }

                player.sendMessage(result.toString());
            });

            handler.<Player>register("saves", "[page]", "Вывести список сохранений.", (args, player) -> {
                if(args.length > 0 && !Strings.canParseInt(args[0])){
                    bundled(player, "commands.page-not-int");
                    return;
                }

                Seq<Fi> saves = Seq.with(Vars.saveDirectory.list()).filter(f -> Objects.equals(f.extension(), Vars.saveExtension));
                int page = args.length > 0 ? Strings.parseInt(args[0]) : 1;
                int pages = Mathf.ceil(saves.size / 6.0F);

                if(--page >= pages || page < 0){
                    bundled(player, "commands.under-page", pages);
                    return;
                }

                StringBuilder result = new StringBuilder();
                result.append(Bundle.format("commands.saves.page", findLocale(player.locale), page + 1, pages)).append("\n");
                for(int i = 6 * page; i < Math.min(6 * (page + 1), saves.size); i++){
                    result.append("[lightgray] ").append(i + 1).append("[orange] ").append(saves.get(i).nameWithoutExtension()).append("[white] ").append("\n");
                }

                player.sendMessage(result.toString());
            });

            handler.<Player>register("nominate", "<map/save/load> [name...]", "Начать голосование за смену карты/загрузку карты.", (args, player) -> {
                VoteMode mode;
                try{
                    mode = VoteMode.valueOf(args[0].toLowerCase());
                }catch(Throwable t){
                    bundled(player, "commands.nominate.incorrect-mode");
                    return;
                }

                if(current[0] != null){
                    bundled(player, "commands.vote-already-started");
                    return;
                }

                if(args.length == 1){
                    bundled(player, "commands.nominate.required-second-arg");
                    return;
                }

                switch(mode){
                    case map -> {
                        Map map = Misc.findMap(args[1]);
                        if(map == null){
                            bundled(player, "commands.nominate.map.not-found");
                            return;
                        }
                        VoteSession session = new VoteMapSession(current, map);
                        current[0] = session;
                        session.vote(player, 1);
                    }
                    case save -> {                    
                        VoteSession session = new VoteSaveSession(current, args[1]);
                        current[0] = session;
                        session.vote(player, 1);
                    }
                    case load -> {
                        Fi save = Misc.findSave(args[1]);
                        if(save == null){
                            player.sendMessage("commands.nominate.load.not-found");
                            return;
                        }
                        VoteSession session = new VoteLoadSession(current, save);
                        current[0] = session;
                        session.vote(player, 1);
                    }
                }
            });

            handler.<Player>register("y", "Проголосовать [lime]за", (args, player) -> {
                 if(current[0] == null) {
                     bundled(player, "commands.no-voting");
                     return;
                 }
                 if(current[0].voted().contains(player.uuid()) || current[0].voted().contains(netServer.admins.getInfo(player.uuid()).lastIP)){
                     bundled(player, "commands.already-voted");
                     return;
                 }
                 current[0].vote(player, 1);
             });

            handler.<Player>register("n", "Проголосовать [scarlet]против", (args, player) -> {
                if(current[0] == null) {
                    bundled(player, "commands.no-voting");
                    return;
                }
                if(current[0].voted().contains(player.uuid()) || current[0].voted().contains(netServer.admins.getInfo(player.uuid()).lastIP)){
                    bundled(player, "commands.already-voted");
                    return;
                }
                current[0].vote(player, -1);
            });
        }

        if(config.type == PluginType.pvp){
            handler.<Player>register("surrender", "Сдаться", (args, player) -> {
                String uuid = player.uuid();
                Team team = player.team();
                ObjectSet<String> uuids = surrendered.get(team, ObjectSet::new);
                if(uuids.contains(uuid)){
                    bundled(player, "commands.already-voted");
                    return;
                }

                uuids.add(uuid);
                int cur = uuids.size;
                int req = (int)Math.ceil(config.voteRatio * Groups.player.count(p -> p.team() == team));
                sendToChat("commands.surrender.ok", Misc.colorizedTeam(team), Misc.colorizedName(player), cur, req);

                if(cur < req){
                    return;
                }

                surrendered.remove(team);
                sendToChat("commands.surrender.successful", Misc.colorizedTeam(team));
                Groups.unit.each(u -> u.team == team, u -> Time.run(Mathf.random(360), u::kill));
                for(Tile tile : world.tiles){
                    if(tile.build != null && tile.team() == team){
                        Time.run(Mathf.random(360), tile.build::kill);
                    }
                }
            });
        }

        handler.<Player>register("rainbow", "RAINBOW!", (args, player) -> {
            RainbowPlayerEntry old = rainbow.find(r -> r.player.uuid().equals(player.uuid()));
            if(old != null){
                rainbow.remove(old);
                player.name = netServer.admins.getInfo(player.uuid()).lastName;
                bundled(player, "commands.rainbow.off");
                return;
            }
            bundled(player, "commands.rainbow.on");
            RainbowPlayerEntry entry = new RainbowPlayerEntry();
            entry.player = player;
            entry.stripedName = Strings.stripColors(player.name);
            rainbow.add(entry);
        });

        handler.<Player>register("js", "<script...>", "Load JavaScript script.", (args, player) -> {
            if (!Misc.adminCheck(player)) return;

            String output = Vars.mods.getScripts().runConsole(args[0]);
            player.sendMessage("[orange] > [accent]" + output);
        });

        handler.<Player>register("hub", "Выйти в Хаб.", (args, player) -> {
            Tuple2<String, Integer> hub = config.parseIp();
            Call.connect(player.con, hub.t1, hub.t2);
        });

        handler.<Player>register("spawn", "<unit> [count] [team]", "Заспавнить юнитов.", (args, player) -> {
            if (!Misc.adminCheck(player)) return;

            if(args.length > 1 && !Strings.canParseInt(args[1])){
                bundled(player, "commands.non-int");
                return;
            }

            int count = args.length > 1 ? Strings.parseInt(args[1]) : 1;
            if (count > 25 || count < 1) {
                bundled(player, "commands.admin.spawn.limit");
                return;
            }

            Team team = args.length > 2 ? Structs.find(Team.baseTeams, t -> t.name.equalsIgnoreCase(args[2])) : player.team();
            if (team == null) {
            	bundled(player, "commands.teams");
            	return;
            }

            UnitType unit = Vars.content.units().find(b -> b.name.equals(args[0]));
            if (unit == null) bundled(player, "commands.unit-not-found");
            else {
                for (int i = 0; count > i; i++) {
                    unit.spawn(team, player.x, player.y);
                }
                bundled(player, "commands.admin.spawn.success", count, unit.name, Misc.colorizedTeam(team));
                WebhookEmbedBuilder artvEmbedBuilder = new WebhookEmbedBuilder()
                    .setColor(0xFF0000)
                    .setTitle(new WebhookEmbed.EmbedTitle("Юниты заспавнены для команды " + team + ".", null))
                    .addField(new WebhookEmbed.EmbedField(true, "Администратором", Strings.stripColors(player.name)))
                    .addField(new WebhookEmbed.EmbedField(true, "Название", unit.name))
                    .addField(new WebhookEmbed.EmbedField(true, "Количетво", Integer.toString(count)));
                DiscordWebhookManager.client.send(artvEmbedBuilder.build());
            }
        });

        handler.<Player>register("units", "<all/change/name> [unit]", "Действия с юнитами.", (args, player) -> {
            if(args[0].equals("name")) {
                if (!player.dead()) bundled(player, "commands.unit-name", player.unit().type().name);
                else bundled(player, "commands.unit-name.null");
            } else if (args[0].equals("all")) {
                StringBuilder builder = new StringBuilder();
                content.units().each(unit -> {
                    if (!unit.name.equals("block")) builder.append(" " + ConfigEntry.icons.get(unit.name) + unit.name);
                });
                bundled(player, "commands.units.all", builder.toString());
            } else if (args[0].equals("change")) {
                if (!Misc.adminCheck(player)) return;
                if(args.length == 1 || args[1].equals("block")) {
                    bundled(player, "commands.units.incorrect");
                    return;
                }
                UnitType founded = Vars.content.units().find(u -> u.name.equals(args[1]));
                if (founded == null) {
                    bundled(player, "commands.unit-not-found");
                    return;
                }
                final Unit spawn = founded.spawn(player.team(), player.x(), player.y());
                spawn.spawnedByCore(true);
                player.unit(spawn);
                bundled(player, "commands.units.change.success");
            } else {
                bundled(player, "commands.units.incorrect");
            }
        });

        handler.<Player>register("unban", "<ip/ID>", "Разбанить игрока.", (args, player) -> {
            if(!Misc.adminCheck(player)) return;
            if(netServer.admins.unbanPlayerIP(args[0]) || netServer.admins.unbanPlayerID(args[0])) {
                bundled(player, "commands.admin.unban.success", netServer.admins.getInfo(args[0]).lastName);
            }else{
                bundled(player, "commands.admin.unban.not-banned");
            }
        });

        handler.<Player>register("votekick", "<player...>", "Проголосовать за кик игрока.", (args, player) -> {
            if(!Administration.Config.enableVotekick.bool()){
                bundled(player, "commands.votekick.disabled");
                return;
            }

            if(Groups.player.size() < 3){
                bundled(player, "commands.votekick.not-enough-players");
                return;
            }

            if(currentlyKicking[0] != null){
                bundled(player, "commands.vote-already-started");
                return;
            }

            Player found = Groups.player.find(p -> Strings.stripColors(p.name).equals(Strings.stripColors(args[0])));

            if(found != null){
                if(found.admin){
                    bundled(player, "commands.votekick.cannot-kick-admin");
                } else if(found.team() != player.team()) {
                    bundled(player, "commands.votekick.cannot-kick-another-team");
                } else {
                    VoteKickSession session = new VoteKickSession(currentlyKicking, found);
                    session.vote(player, 1);
                    currentlyKicking[0] = session;
                }
            } else {
                bundled(player, "commands.player-not-found");
            }
        });

        handler.<Player>register("vote", "<y/n>", "Решить судьбу игрока.", (arg, player) -> {
            if(currentlyKicking[0] == null) {
                bundled(player, "commands.no-voting");
                return;
            }

            if((currentlyKicking[0].voted().contains(player.uuid()) || currentlyKicking[0].voted().contains(netServer.admins.getInfo(player.uuid()).lastIP))){
                bundled(player, "commands.already-voted");
                return;
            }

            if(currentlyKicking[0].target() == player){
                bundled(player, "commands.vote.cannot-vote-for-yourself");
                return;
            }

            if(currentlyKicking[0].target().team() != player.team()){
                bundled(player, "commands.vote.cannot-vote-another-team");
                return;
            }

            int sign = switch(arg[0].toLowerCase()){
                case "y", "yes" ->  1;
                case "n", "no" -> -1;
                default -> 0;
            };

            if(sign == 0){
                bundled(player, "commands.vote.incorrect-args");
                return;
            }

            currentlyKicking[0].vote(player, sign);
        });

        handler.<Player>register("sync", "Синхронизация с сервером.", (args, player) -> {
            if(Time.timeSinceMillis(player.getInfo().lastSyncTime) < 1000 * 15) {
                bundled(player, "commands.sync.time");
                return;
            }

            player.getInfo().lastSyncTime = Time.millis();
            Call.worldDataBegin(player.con);
            netServer.sendWorldData(player);
        });
    }

    public static class RainbowPlayerEntry {
        public Player player;
        public int hue;
        public String stripedName;
    }
}
