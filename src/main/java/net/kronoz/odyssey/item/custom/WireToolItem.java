package net.kronoz.odyssey.item.custom;

import net.kronoz.odyssey.systems.physics.wire.WireToolState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.world.World;

public class WireToolItem extends Item {
    public WireToolItem(Settings settings) { super(settings); }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        World world = ctx.getWorld();
        if (!world.isClient) return ActionResult.CONSUME;

        PlayerEntity player = ctx.getPlayer();
        if (player != null && player.isSneaking()) {
            WireToolState.clearPending();
            player.sendMessage(Text.literal("Anchor cleared"), true);
            return ActionResult.SUCCESS;
        }

        var pos   = ctx.getBlockPos();
        var face  = ctx.getSide();
        var block = world.getBlockState(pos).getBlock();

        if (!WireToolState.hasPending()) {
            WireToolState.setPending(pos, face, block);
            if (player != null) player.sendMessage(Text.literal("Anchor A set"), true);
        } else {
            var p = WireToolState.pending;
            WireToolState.spawnWire(p.pos, p.face, p.block, pos, face, block);
            WireToolState.clearPending();
            if (player != null) player.sendMessage(Text.literal("Wire placed"), true);
        }
        return ActionResult.SUCCESS;
    }
}
