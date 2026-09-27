// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import arc.Core;
import arc.Settings;
import arc.files.Fi;
import arc.mock.MockFiles;
import mindustry.Vars;
import mindustry.ai.BlockIndexer;
import mindustry.core.ContentLoader;
import mindustry.core.GameState;
import mindustry.core.World;
import mindustry.gen.Groups;
import mindustry.logic.GlobalVars;
import mindustry.net.Net;

// The bare minimum of a Mindustry server to create real worlds and buildings
final class HeadlessMindustry {

    private static boolean initialized = false;

    private HeadlessMindustry() {}

    static synchronized void init() {
        if (initialized) {
            return;
        }
        Vars.headless = true;
        Core.settings = new Settings();
        Core.settings.setDataDirectory(Fi.tempDirectory("nohorny-jmh"));
        Core.files = new MockFiles();
        Groups.init();
        Vars.content = new ContentLoader();
        Vars.content.createBaseContent();
        Vars.content.init();
        Vars.state = new GameState();
        Vars.net = new Net(null);
        Vars.logicVars = new GlobalVars();
        Vars.logicVars.init();
        Vars.world = new World();
        Vars.indexer = new BlockIndexer();
        initialized = true;
    }
}
