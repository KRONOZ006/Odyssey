package net.kronoz.odyssey.item.custom;

import net.kronoz.odyssey.systems.physics.wire.WireAnchor;
import net.kronoz.odyssey.systems.physics.wire.WireNetworking;
import net.kronoz.odyssey.systems.physics.wire.WireRecord;
import net.kronoz.odyssey.systems.physics.wire.WireStorage;
import net.kronoz.odyssey.systems.physics.wire.WireToolMath;
import net.kronoz.odyssey.systems.physics.wire.WireToolState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.UUID;

public class WireToolItem extends Item {
    private static final Identifier DEFAULT_WIRE_DEF = Identifier.of("odyssey", "textures/effects/wire.png");
    private static final double MAX_WIRE_DISTANCE = 64.0;

    public WireToolItem(Settings settings) { super(settings); }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        World world = ctx.getWorld();
        PlayerEntity player = ctx.getPlayer();
        ItemStack stack = ctx.getStack();
        WireAnchor clicked = WireToolMath.anchorFromHit(ctx.getBlockPos(), ctx.getSide(), ctx.getHitPos());

        if (world.isClient) {
            handleClientPreview(player, stack, clicked);
            return ActionResult.SUCCESS;
        }

        if (!(world instanceof ServerWorld serverWorld)) {
            return ActionResult.CONSUME;
        }

        if (player != null && player.isSneaking()) {
            WireToolMath.writePendingAnchor(stack, null);
            player.sendMessage(Text.literal("Wire anchor cleared"), true);
            return ActionResult.SUCCESS;
        }

        WireAnchor pending = WireToolMath.readPendingAnchor(stack);
        if (pending == null) {
            WireToolMath.writePendingAnchor(stack, clicked);
            if (player != null) {
                player.sendMessage(Text.literal("Wire anchor A set"), true);
            }
            return ActionResult.SUCCESS;
        }

        if (WireToolMath.anchorCenter(pending).distanceTo(WireToolMath.anchorCenter(clicked)) > MAX_WIRE_DISTANCE) {
            WireToolMath.writePendingAnchor(stack, clicked);
            if (player != null) {
                player.sendMessage(Text.literal("Wire was too long, anchor A replaced"), true);
            }
            return ActionResult.SUCCESS;
        }

        WireRecord created = new WireRecord(UUID.randomUUID(), DEFAULT_WIRE_DEF, pending, true, clicked, true);
        WireStorage storage = WireStorage.get(serverWorld);
        if (storage.hasEquivalent(created.a, created.b)) {
            WireToolMath.writePendingAnchor(stack, null);
            if (player != null) {
                player.sendMessage(Text.literal("Wire already exists"), true);
            }
            return ActionResult.SUCCESS;
        }

        storage.put(created);
        WireNetworking.broadcastUpsert(serverWorld, created);
        WireToolMath.writePendingAnchor(stack, null);
        if (player != null) {
            player.sendMessage(Text.literal("Wire linked"), true);
        }
        return ActionResult.SUCCESS;
    }

    private static void handleClientPreview(PlayerEntity player, ItemStack stack, WireAnchor clicked) {
        if (player != null && player.isSneaking()) {
            WireToolState.clientClearPending();
            return;
        }
        WireAnchor pending = WireToolMath.readPendingAnchor(stack);
        if (pending == null) {
            WireToolState.clientSetPending(clicked);
        } else {
            WireToolState.clientClearPending();
        }
    }

}
