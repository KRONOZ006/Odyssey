package net.kronoz.odyssey.entity;

import net.kronoz.odyssey.block.SequencerRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

public class SequencerBlockEntity extends BlockEntity {

    public static class Step {
        public int second;
        public String key;
        public Step(int s, String k) { this.second = s; this.key = k; }
    }

    private final List<Step> steps = new ArrayList<>();
    private final BitSet fired = new BitSet();
    private boolean running = false;
    private long startTick = 0;
    private boolean loop = false;

    public SequencerBlockEntity(BlockPos pos, BlockState state, BlockEntityType<?> type) {
        super(type, pos, state);
    }

    public void setSteps(List<Step> list) {
        steps.clear();
        steps.addAll(list);
        steps.sort(Comparator.comparingInt(s -> s.second));
        fired.clear();
    }

    public void addStep(int second, String key) {
        steps.add(new Step(second, key));
        steps.sort(Comparator.comparingInt(s -> s.second));
    }

    public void setLoop(boolean loop) { this.loop = loop; }

    public void start(World world) {
        if (world.isClient) return;
        running = true;
        fired.clear();
        startTick = world.getTime();
        markDirty();
    }

    public void stop(World world) {
        if (world.isClient) return;
        running = false;
        markDirty();
    }

    public static void tick(World world, BlockPos pos, BlockState state, SequencerBlockEntity be) {
        if (world.isClient || !be.running || be.steps.isEmpty()) return;
        long elapsedTicks = world.getTime() - be.startTick;
        if (elapsedTicks < 0) return;
        int elapsedSec = (int)(elapsedTicks / 20L);

        for (int i = 0; i < be.steps.size(); i++) {
            if (be.fired.get(i)) continue;
            Step s = be.steps.get(i);
            if (elapsedSec >= s.second) {
                SequencerRegistry.fire(s.key, (ServerWorld) world, pos);
                be.fired.set(i);
            }
        }

        if (be.fired.nextClearBit(0) >= be.steps.size()) {
            if (be.loop) {
                be.startTick = world.getTime();
                be.fired.clear();
            } else {
                be.running = false;
            }
            be.markDirty();
        }
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
        steps.clear();
        NbtList list = nbt.getList("steps", NbtElement.COMPOUND_TYPE);
        for (NbtElement e : list) {
            NbtCompound c = (NbtCompound) e;
            steps.add(new Step(c.getInt("sec"), c.getString("key")));
        }
        running = nbt.getBoolean("running");
        startTick = nbt.getLong("start");
        loop = nbt.getBoolean("loop");
        fired.clear();
    }

    @Override
    public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.writeNbt(nbt, registryLookup);
        NbtList list = new NbtList();
        for (Step s : steps) {
            NbtCompound c = new NbtCompound();
            c.putInt("sec", s.second);
            c.putString("key", s.key);
            list.add(c);
        }
        nbt.put("steps", list);
        nbt.putBoolean("running", running);
        nbt.putLong("start", startTick);
        nbt.putBoolean("loop", loop);
    }
}
