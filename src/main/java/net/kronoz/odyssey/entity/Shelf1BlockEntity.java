package net.kronoz.odyssey.entity;

import net.kronoz.odyssey.init.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;

public class Shelf1BlockEntity extends BlockEntity implements GeoAnimatable {

    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(4, ItemStack.EMPTY);

    public Shelf1BlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.SHELF1, pos, state); }

    public int size() { return items.size(); }
    public int getFilledSlots() { int n = 0; for (ItemStack s : items) if (!s.isEmpty()) n++; return n; }
    public ItemStack getStack(int slot) { return (slot < 0 || slot >= items.size()) ? ItemStack.EMPTY : items.get(slot); }

    public void setStack(int slot, ItemStack stack) {
        if (slot < 0 || slot >= items.size()) return;
        items.set(slot, stack);
        syncDirty();
    }

    public void clearSlot(int slot) {
        if (slot < 0 || slot >= items.size()) return;
        items.set(slot, ItemStack.EMPTY);
        syncDirty();
    }

    public int firstEmptySlot() { for (int i=0;i<items.size();i++) if (items.get(i).isEmpty()) return i; return -1; }
    public int lastFilledSlot() { for (int i=items.size()-1;i>=0;i--) if (!items.get(i).isEmpty()) return i; return -1; }

    private void syncDirty() {
        markDirty();
        if (world == null || world.isClient) return;

        var sw = (net.minecraft.server.world.ServerWorld) world;
        var pkt = net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket.create(this);
        for (var player : net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(sw, pos)) {
            player.networkHandler.sendPacket(pkt);
        }

        world.updateListeners(pos, getCachedState(), getCachedState(),
                net.minecraft.block.Block.NOTIFY_LISTENERS | net.minecraft.block.Block.REDRAW_ON_MAIN_THREAD);
        sw.getChunkManager().markForUpdate(pos);
    }



    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
        Inventories.writeNbt(nbt, items, lookup);
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        Inventories.readNbt(nbt, items, lookup);

        if (world != null && world.isClient) {
            var state = getCachedState();
            ((net.minecraft.client.world.ClientWorld) world).scheduleBlockRerenderIfNeeded(pos, state, state);
        }
    }



    @Nullable @Override
    public BlockEntityUpdateS2CPacket toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup lookup) {
        NbtCompound n = new NbtCompound();
        Inventories.writeNbt(n, items, lookup);
        return n;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {

    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return null;
    }

    @Override
    public double getTick(Object o) {
        return 0;
    }
}
