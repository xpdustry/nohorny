package fr.xpdustry.template;

import arc.util.CommandHandler;
import arc.util.Log;
import mindustry.mod.Plugin;
import org.jetbrains.annotations.NotNull;


/** Template plugin. */
@SuppressWarnings("unused")
public class TemplatePlugin extends Plugin {
    /** This method is called when game initializes. */
    @Override public void init() {
        Log.info("Bonjour !");
    }

    /** This method is called when the game register the server-side commands. */
    @Override public void registerServerCommands(@NotNull CommandHandler handler) {
    }

    /** This method is called when the game register the client-side commands. */
    @Override public void registerClientCommands(@NotNull CommandHandler handler) {
    }
}
