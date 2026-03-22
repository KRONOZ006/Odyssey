package net.kronoz.odyssey.item.custom;

import net.kronoz.odyssey.init.ModBlocks;
import net.kronoz.odyssey.systems.reset.ResetZoneSystem;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class ResetLinkToolItem extends Item {
    private static final String NBT_PENDING_ZONE = "odyssey_pending_reset_zone";
    private static final String NBT_PENDING_DIM = "odyssey_pending_reset_zone_dim";

    public ResetLinkToolItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }
        if (!(world instanceof ServerWorld serverWorld)) {
            return ActionResult.CONSUME;
        }

        PlayerEntity player = context.getPlayer();
        if (player == null) {
            return ActionResult.CONSUME;
        }

        ItemStack stack = context.getStack();
        BlockPos clicked = context.getBlockPos().toImmutable();
        var state = world.getBlockState(clicked);

        if (state.isOf(ModBlocks.RESET_ZONE)) {
            if (player.isSneaking()) {
                ResetZoneSystem.unlinkZone(serverWorld, clicked);
                clearPending(stack);
                player.sendMessage(Text.literal("Reset zone link removed"), true);
                return ActionResult.SUCCESS;
            }
            writePendingZone(stack, clicked, serverWorld);
            player.sendMessage(Text.literal("Reset zone selected"), true);
            return ActionResult.SUCCESS;
        }

        if (state.isOf(ModBlocks.RESPAWN_POINT)) {
            if (player.isSneaking()) {
                int removed = ResetZoneSystem.unlinkRespawn(serverWorld, clicked);
                clearPending(stack);
                player.sendMessage(Text.literal("Removed " + removed + " reset link(s) from respawn point"), true);
                return ActionResult.SUCCESS;
            }
            BlockPos pending = readPendingZone(stack);
            if (pending == null) {
                player.sendMessage(Text.literal("Select a reset zone first"), true);
                return ActionResult.SUCCESS;
            }
            if (!isPendingDimensionMatch(stack, serverWorld)) {
                clearPending(stack);
                player.sendMessage(Text.literal("Pending zone was from another dimension, selection cleared"), true);
                return ActionResult.SUCCESS;
            }
            ResetZoneSystem.link(serverWorld, pending, clicked);
            clearPending(stack);
            player.sendMessage(Text.literal("Reset zone linked to respawn point"), true);
            return ActionResult.SUCCESS;
        }

        if (player.isSneaking()) {
            clearPending(stack);
            player.sendMessage(Text.literal("Reset link selection cleared"), true);
            return ActionResult.SUCCESS;
        }

        player.sendMessage(Text.literal("Use on a reset zone or respawn point"), true);
        return ActionResult.SUCCESS;
    }

    private static void writePendingZone(ItemStack stack, BlockPos pos, ServerWorld world) {
        NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        nbt.putLong(NBT_PENDING_ZONE, pos.asLong());
        nbt.putString(NBT_PENDING_DIM, world.getRegistryKey().getValue().toString());
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    private static @Nullable BlockPos readPendingZone(ItemStack stack) {
        NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        if (nbt == null || !nbt.contains(NBT_PENDING_ZONE, NbtElement.LONG_TYPE)) {
            return null;
        }
        return BlockPos.fromLong(nbt.getLong(NBT_PENDING_ZONE));
    }

    private static void clearPending(ItemStack stack) {
        NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        if (nbt == null) {
            return;
        }
        nbt.remove(NBT_PENDING_ZONE);
        nbt.remove(NBT_PENDING_DIM);
        if (nbt.isEmpty()) {
            stack.remove(DataComponentTypes.CUSTOM_DATA);
            return;
        }
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    private static boolean isPendingDimensionMatch(ItemStack stack, ServerWorld world) {
        NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        if (nbt == null || !nbt.contains(NBT_PENDING_DIM, NbtElement.STRING_TYPE)) {
            return true;
        }
        String pendingDim = nbt.getString(NBT_PENDING_DIM);
        return world.getRegistryKey().getValue().toString().equals(pendingDim);
    }
}
