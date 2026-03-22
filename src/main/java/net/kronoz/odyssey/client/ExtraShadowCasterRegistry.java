package net.kronoz.odyssey.client;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Box;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class ExtraShadowCasterRegistry {
    private static final List<Caster> CASTERS = new CopyOnWriteArrayList<>();

    private ExtraShadowCasterRegistry() {
    }

    public static void register(Caster caster) {
        if (caster != null) {
            CASTERS.add(caster);
        }
    }

    public static void unregister(Caster caster) {
        CASTERS.remove(caster);
    }

    public static void collect(ClientWorld world, Box gridBounds, Consumer<Box> sink) {
        if (world == null || gridBounds == null || sink == null) {
            return;
        }
        for (Caster caster : CASTERS) {
            try {
                caster.collect(world, gridBounds, sink);
            } catch (Throwable ignored) {
            }
        }
    }

    @FunctionalInterface
    public interface Caster {
        void collect(ClientWorld world, Box gridBounds, Consumer<Box> sink);
    }
}
