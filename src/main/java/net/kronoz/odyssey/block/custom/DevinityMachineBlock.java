package net.kronoz.odyssey.block.custom;

import net.kronoz.odyssey.entity.arcangel.ArcangelEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Comparator;

public class DevinityMachineBlock extends Block {
    public DevinityMachineBlock(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.SUCCESS;
        if (sp.isCreative() || sp.isSpectator()) return ActionResult.SUCCESS;

        sp.damage(((ServerWorld) world).getDamageSources().magic(), 8f);

        ArcangelEntity nearest = world.getEntitiesByClass(ArcangelEntity.class, player.getBoundingBox().expand(64), e -> true)
                .stream().min(Comparator.comparingDouble(e -> e.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5))).orElse(null);

        if (nearest != null) {
            nearest.addBlood(25);
            world.playSound(null, pos, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.BLOCKS, 1f, 0.8f);
        }
        return ActionResult.SUCCESS;
    }
}
