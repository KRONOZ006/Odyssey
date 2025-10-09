package net.kronoz.odyssey.systems.cinematics;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.kronoz.odyssey.systems.cinematics.runtime.BootstrapScenes;
import net.kronoz.odyssey.systems.cinematics.runtime.CutsceneManager;

public final class CineClient {
    private CineClient(){}

    public static void init(){
        BootstrapScenes.registerAll();


        ClientTickEvents.END_CLIENT_TICK.register(client -> CutsceneManager.I.clientTick());
    }
}
