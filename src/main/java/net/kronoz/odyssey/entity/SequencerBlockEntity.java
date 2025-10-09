package net.kronoz.odyssey.entity;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.kronoz.odyssey.block.SequencerRegistry;
import net.kronoz.odyssey.block.custom.SequencerBlock;
import net.kronoz.odyssey.systems.cinematics.runtime.CutsceneManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LightBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SequencerBlockEntity extends BlockEntity {
    private static final Gson GSON = new Gson();

    public static final class Step {
        public double at;
        public String type;
        public String arg;
        public double value;
    }

    private final List<Step> steps = new ArrayList<>();
    private boolean running = false;
    private long startGameTime = 0L;
    private int fired = 0;
    private double lengthSec = 0.0;
    private boolean loop = false;

    public SequencerBlockEntity(BlockPos pos, BlockState state) {
        super(SequencerRegistry.SEQUENCER_BE, pos, state);
    }

    public void start() {
        if (!(world instanceof ServerWorld)) return;
        running = true;
        startGameTime = world.getTime();
        fired = 0;
        updateRedstone(0);
        markDirty();
    }

    public void stop() {
        running = false;
        updateRedstone(0);
        markDirty();
    }

    public void reset() {
        running = false;
        steps.clear();
        lengthSec = 0;
        loop = false;
        fired = 0;
        updateRedstone(0);
        markDirty();
    }

    // ---------- Veil Light runtime ----------
    private VeilPulse veilPulse = null;

    public void spawnVeilLight(Direction face) {
        if (!(world instanceof ServerWorld sw)) return;
        if (veilPulse == null) veilPulse = new VeilPulse();
        veilPulse.start(sw, this.pos, face);
    }

    private void tickVeilLight(ServerWorld sw) {
        if (veilPulse != null) {
            veilPulse.tick(sw);
            if (!veilPulse.active) {
                veilPulse = null;
            }
        }
    }

    private static final class VeilPulse {
        boolean active = false;
        BlockPos center;        // centre = bloc adjacent (face cliquée)
        long startTick;
        long lastStepTick;
        int step = 0;           // rayon courant
        final int maxTicks = 20 * 20;    // 20s
        final int stepEvery = 40;        // 2s -> 40 ticks
        Set<BlockPos> placed = new HashSet<>();

        void start(ServerWorld sw, BlockPos origin, Direction face) {
            this.center = origin.offset(face);
            this.startTick = sw.getTime();
            this.lastStepTick = startTick - stepEvery; // force un placement immédiat au tick suivant
            this.step = -1; // pour que le premier tick passe à 0
            this.active = true;
        }

        void tick(ServerWorld sw) {
            if (!active) return;
            long now = sw.getTime();
            long elapsed = now - startTick;

            if (elapsed >= maxTicks) {
                stop(sw);
                return;
            }
            if (now - lastStepTick >= stepEvery) {
                step++;
                lastStepTick = now;
                expand(sw, step);
            }
        }

        void expand(ServerWorld sw, int radius) {
            // place uniquement les positions NON déjà posées, dans une sphère de rayon 'radius'
            int r = radius;
            BlockState lightState = Blocks.LIGHT.getDefaultState().with(LightBlock.LEVEL_15, 15);
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        double distSq = dx*dx + dy*dy + dz*dz;
                        if (distSq > r*r) continue;
                        BlockPos p = center.add(dx, dy, dz);
                        if (placed.contains(p)) continue;

                        BlockState cur = sw.getBlockState(p);
                        // On pose seulement si c’est de l’air ou un autre light (pour rafraîchir)
                        if (cur.isAir() || cur.isOf(Blocks.LIGHT)) {
                            sw.setBlockState(p, lightState, Block.NOTIFY_LISTENERS);
                            placed.add(p);
                        }
                    }
                }
            }
        }

        void stop(ServerWorld sw) {
            // supprime tous les lights posés par nous
            for (BlockPos p : placed) {
                BlockState cur = sw.getBlockState(p);
                if (cur.isOf(Blocks.LIGHT)) {
                    sw.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
            placed.clear();
            active = false;
        }
    }
    // ---------- fin veil light ----------
    public void serverTick() {
        if (!running) return;
        if (!(world instanceof ServerWorld sw)) return;

        tickVeilLight(sw);
        double tSec = (sw.getTime() - startGameTime) / 20.0;
        while (fired < steps.size() && steps.get(fired).at <= tSec) {
            exec(steps.get(fired), sw);
            fired++;
        }

        int power = steps.isEmpty() ? 0 : Math.min(15, (int) Math.floor((tSec / Math.max(0.001, lengthSec)) * 15.0));
        updateRedstone(power);

        if (tSec >= lengthSec) {
            if (loop) {
                start();
            } else {
                stop();
                world.setBlockState(pos,
                        getCachedState().with(SequencerBlock.RUNNING, false).with(SequencerBlock.POWER, 0),
                        Block.NOTIFY_ALL);
            }
        }
    }

    private void exec(Step s, ServerWorld sw) {
        if ("cutscene".equalsIgnoreCase(s.type)) {
            Identifier id = s.arg.contains(":") ? Identifier.of(s.arg) : Identifier.of("odyssey", s.arg);
            CutsceneManager.I.play(id.getPath()); // your manager takes String
        } else if ("sound".equalsIgnoreCase(s.type)) {
            try {
                Identifier sid = s.arg.contains(":") ? Identifier.of(s.arg) : Identifier.of("minecraft", s.arg);
                SoundEvent se = Registries.SOUND_EVENT.get(sid);
                if (se != null) {
                    float pitch = (float) (s.value <= 0 ? 1.0 : s.value);
                    sw.playSound(null, pos, se, SoundCategory.BLOCKS, 1.0f, pitch);
                }
            } catch (Exception ignored) {}
        } else if ("power".equalsIgnoreCase(s.type)) {
            updateRedstone((int) Math.max(0, Math.min(15, Math.round(s.value))));
        }
    }

    private void updateRedstone(int p) {
        BlockState state = getCachedState();
        if (state.get(SequencerBlock.POWER) != p) {
            world.setBlockState(pos, state.with(SequencerBlock.POWER, p), Block.NOTIFY_ALL);
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
        nbt.putBoolean("running", running);
        nbt.putLong("start", startGameTime);
        nbt.putInt("fired", fired);
        nbt.putDouble("length", lengthSec);
        nbt.putBoolean("loop", loop);
        nbt.putString("steps", GSON.toJson(steps));
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        running = nbt.getBoolean("running");
        startGameTime = nbt.getLong("start");
        fired = nbt.getInt("fired");
        lengthSec = nbt.getDouble("length");
        loop = nbt.getBoolean("loop");
        steps.clear();
        String json = nbt.getString("steps");
        if (json != null && !json.isEmpty()) {
            Type t = new TypeToken<List<Step>>(){}.getType();
            List<Step> list = GSON.fromJson(json, t);
            if (list != null) steps.addAll(list);
        }
    }

    public void setSequenceJson(String json, boolean loopFlag) {
        steps.clear();
        try {
            Type t = new TypeToken<List<Step>>(){}.getType();
            List<Step> list = GSON.fromJson(json, t);
            if (list != null) steps.addAll(list);
        } catch (Exception ignored) {}
        steps.sort((a, b) -> Double.compare(a.at, b.at));
        lengthSec = steps.isEmpty() ? 0.0 : steps.get(steps.size() - 1).at;
        loop = loopFlag;
        markDirty();
    }

    public String getSequenceJson() {
        return GSON.toJson(steps);
    }
}
