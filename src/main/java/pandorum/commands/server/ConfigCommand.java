package pandorum.commands.server;

import arc.Core;
import arc.util.Log;
import arc.util.Strings;
import mindustry.net.Administration.Config;

public class ConfigCommand {
    public static void run(final String[] args) {
        if (args.length == 0) {
            Log.info("Все значения конфигурации:");
            for (Config config : Config.all) {
                Log.info("&lk| @: @", config.name(), "&lc&fi" + config.get());
                Log.info("&lk| | &lw" + config.description);
                Log.info("&lk|");
            }
            return;
        }

        try {
            Config config = Config.valueOf(args[0]);
            if (args.length == 1) {
                Log.info("Конфигурация '@' сейчас имеет значение '@'.", config.name(), config.get());
            } else {
                if (args[1].equalsIgnoreCase("default") || args[1].equalsIgnoreCase("reset")) {
                    config.set(config.defaultValue);
                } else if (config.isBool()) {
                    config.set(args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("true"));
                } else if (config.isNum()) {
                    config.set(Strings.parseInt(args[1], config.num()));
                } else if (config.isString()) {
                    config.set(args[1].replace("\\n", "\n"));
                }

                Log.info("Конфигурации '@' присвоено значение '@'.", config.name(), config.get());
                Core.settings.forceSave();
            }
        } catch (Exception e) {
            Log.err("Неизвестная конфигурация: '@'.", args[0]);
        }
    }
}
