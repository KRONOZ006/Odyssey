package net.kronoz.odyssey.systems.physics;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.kronoz.odyssey.Odyssey;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class LightDustPinger {
    private static final int SCAN_INTERVAL_TICKS = 10;
    private static final int SCAN_RADIUS = 32;
    private static final int MAX_PINGS = 256;

    private static final int R=210, G=210, B=210;
    private static final int CAPACITY = 220;
    private static final double EMIT = 40.0;

    private final Block LIGHT_BLOCK;
    private int tick = 0;

    public LightDustPinger() {
        LIGHT_BLOCK = Registries.BLOCK.get(Identifier.of(Odyssey.MODID, "example_block"));// made it work on example block just caus me job pc don't have the lamp....  -Dark
    }

    public void install() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (++tick % SCAN_INTERVAL_TICKS != 0) return;
            if (mc == null || mc.world == null || mc.player == null) return;
            if (LIGHT_BLOCK == null) return;

            var world = mc.world;
            BlockPos center = mc.player.getBlockPos();
            int r = SCAN_RADIUS;

            int sent = 0;
            for (int y=-r; y<=r; y++) for (int x=-r; x<=r; x++) for (int z=-r; z<=r; z++) {
                if (sent >= MAX_PINGS) return;
                BlockPos bp = center.add(x,y,z);
                if (!world.isChunkLoaded(bp)) continue;
                if (world.getBlockState(bp).getBlock() != LIGHT_BLOCK) continue;

                Vec3d origin = new Vec3d(bp.getX()+0.5, bp.getY()+0.8, bp.getZ()+0.5);

                DustManager.INSTANCE.pingLightDust(world, bp, origin, R, G, B, CAPACITY, EMIT);
                sent++;
            }
        });
    }
}
