package net.kronoz.odyssey.systems.physics.dust;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Map;

public enum DustManager {
    INSTANCE;

    private static final Identifier WHITE = Identifier.of("minecraft","textures/misc/white.png");

    private static final class Key {
        final String dim; final BlockPos pos;
        Key(String d, BlockPos p){ this.dim=d; this.pos=p.toImmutable(); }
        @Override public boolean equals(Object o){ return o instanceof Key k && k.dim.equals(dim) && k.pos.equals(pos); }
        @Override public int hashCode(){ return dim.hashCode()*31 + pos.hashCode(); }
    }
    private static final class Entry {
        final Key key; final DustField field;
        Vec3d origin; long lastSeenTime;
        Entry(Key k, DustField f, Vec3d o){ key=k; field=f; origin=o; lastSeenTime=0L; }
    }

    private final Map<Key, Entry> entries = new Object2ObjectOpenHashMap<>();
    private static final long KEEP_ALIVE_TICKS = 200L;

    public void installHooks() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc == null || mc.world == null) return;
            var world = mc.world;
            long t = world.getTime();

            String dimKey = world.getRegistryKey().getValue().toString();
            entries.values().removeIf(e -> {
                if (!e.key.dim.equals(dimKey)) return false;
                long idle = t - e.lastSeenTime;
                if (idle > KEEP_ALIVE_TICKS) return true;
                double dt = 1.0/20.0;
                e.field.update(dt, e.origin, world, t);
                return false;
            });
        });

        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            MatrixStack ms = ctx.matrixStack();
            VertexConsumerProvider vcp = ctx.consumers();
            float tickDelta = ctx.camera().getLastTickDelta();

            var mc = MinecraftClient.getInstance();
            if (mc == null || mc.world == null) return;
            String dimKey = mc.world.getRegistryKey().getValue().toString();

            var cam = ctx.camera().getPos();
            ms.push();
            ms.translate(-cam.x, -cam.y, -cam.z);

            VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityTranslucent(WHITE));
            for (Entry e : entries.values()) {
                if (!e.key.dim.equals(dimKey)) continue;
                e.field.render(ms, vc, Vec3d.ZERO, tickDelta, mc.world);
            }

            ms.pop();
        });
    }

    public void pingLightDust(net.minecraft.world.World world, BlockPos pos, Vec3d origin,
                              int r, int g, int b,
                              int capacity, double emitRatePerSec) {
        String dimKey = world.getRegistryKey().getValue().toString();
        Key k = new Key(dimKey, pos);
        Entry e = entries.get(k);
        if (e == null) {
            DustField f = new DustField(capacity, r, g, b, emitRatePerSec);
            e = new Entry(k, f, origin);
            entries.put(k, e);
        } else {
            e.origin = origin;
        }
        e.lastSeenTime = world.getTime();
    }
}
