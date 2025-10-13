package net.kronoz.odyssey.systems.cam;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LightningEntity;

public final class ShakeEvents {
    private ShakeEvents() {}

    public static void registerClient() {
        ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!(entity instanceof LightningEntity)) return;
            var mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null) return;
            double d2 = mc.player.squaredDistanceTo(entity);
            RapidShake.configure(
                    1.75f,
                    16f,
                    0.40f,
                    0.40f,
                    0.8f,
                    0.65f
            );
            RapidShake.setOutputSmoothing(0.25f);
            RapidShake.enableTimed(5.0f, 20);
        });
    }
}
