package net.kronoz.odyssey.world;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;

public final class WorldEventHooks {
    private WorldEventHooks() {}
    public static void init() {
        ServerWorldEvents.LOAD.register((server, world) -> FixedStructurePlacer.onWorldLoaded(world));
        ServerTickEvents.END_SERVER_TICK.register(FixedStructurePlacer::tick);
    }
}
