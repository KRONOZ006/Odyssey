package net.kronoz.odyssey.block;

import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SequencerRegistry {
    @FunctionalInterface
    public interface SequencerAction {
        void run(ServerWorld world, BlockPos pos);
    }

    private static final Map<String, SequencerAction> ACTIONS = new ConcurrentHashMap<>();

    private SequencerRegistry() {}

    public static void register(String key, SequencerAction action) {
        ACTIONS.put(key, action);
    }

    public static boolean fire(String key, ServerWorld world, BlockPos pos) {
        SequencerAction a = ACTIONS.get(key);
        if (a == null) return false;
        a.run(world, pos);
        return true;
    }

    public static void bootstrapDefaults() {
        register("beep", (w, p) -> {
            w.playSound(null, p, net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(),
                    net.minecraft.sound.SoundCategory.BLOCKS, 1.0f, 1.0f);
        });
        register("flash", (w, p) -> w.syncWorldEvent(2001, p, net.minecraft.block.Block.getRawIdFromState(w.getBlockState(p))));
        register("particles", (w, p) -> w.spawnParticles(ParticleTypes.END_ROD,
                p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5, 12, 0.25, 0.25, 0.25, 0.0));
    }

    // alias for your Odyssey.init() call
    public static void init() {
        bootstrapDefaults();
    }
}
