package net.kronoz.odyssey.world;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BucketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

public final class StructureSanctuaryGuard {
    private static final int STRUCTURE_ENTITY_CLEANUP_INTERVAL_ACTIVE = Integer.getInteger("odyssey.structure.cleanup_interval", 20);
    private static final int STRUCTURE_ENTITY_CLEANUP_INTERVAL_IDLE = Integer.getInteger("odyssey.structure.cleanup_interval_idle", 160);
    private static final double STRUCTURE_PLAYER_NEAR_MARGIN = Double.parseDouble(System.getProperty("odyssey.structure.cleanup_player_margin", "96"));

    private StructureSanctuaryGuard() {}

    public static void init() {
        UseBlockCallback.EVENT.register(StructureSanctuaryGuard::onUseBlock);
        AttackBlockCallback.EVENT.register(StructureSanctuaryGuard::onAttackBlock);
        PlayerBlockBreakEvents.BEFORE.register(StructureSanctuaryGuard::onBeforeBreak);
        ServerTickEvents.END_WORLD_TICK.register(StructureSanctuaryGuard::onWorldTick);
    }

    private static ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hitResult) {
        if (world.isClient || hand != Hand.MAIN_HAND || hitResult == null) return ActionResult.PASS;
        if (!(world instanceof ServerWorld serverWorld) || serverWorld.getRegistryKey() != World.OVERWORLD) return ActionResult.PASS;

        ItemStack stack = player.getStackInHand(hand);
        if (!(stack.getItem() instanceof BlockItem) && !(stack.getItem() instanceof BucketItem)) {
            return ActionResult.PASS;
        }

        BlockPos targetPos = hitResult.getBlockPos().offset(hitResult.getSide());
        if (isProtectedPlacement(serverWorld, targetPos)) {
            return ActionResult.FAIL;
        }
        return ActionResult.PASS;
    }

    private static ActionResult onAttackBlock(PlayerEntity player, World world, Hand hand, BlockPos pos, net.minecraft.util.math.Direction direction) {
        if (world.isClient) return ActionResult.PASS;
        if (!(world instanceof ServerWorld serverWorld) || serverWorld.getRegistryKey() != World.OVERWORLD) return ActionResult.PASS;
        if (isProtectedPlacement(serverWorld, pos)) {
            return ActionResult.FAIL;
        }
        return ActionResult.PASS;
    }

    private static boolean onBeforeBreak(World world, PlayerEntity player, BlockPos pos, net.minecraft.block.BlockState state, net.minecraft.block.entity.BlockEntity blockEntity) {
        if (world.isClient) return true;
        if (!(world instanceof ServerWorld serverWorld) || serverWorld.getRegistryKey() != World.OVERWORLD) return true;
        return !isProtectedPlacement(serverWorld, pos);
    }

    private static void onWorldTick(ServerWorld world) {
        if (world == null || world.getRegistryKey() != World.OVERWORLD) return;

        var state = FixedStructurePlacerOverworld.getState(world);
        if (!state.hasSpawn()) return;

        enforceSpawnSanctuary(world, state);

        if (!state.hasProtectedBounds()) return;

        Box structureBox = state.protectedBoundsBox();
        if (structureBox.maxX <= structureBox.minX || structureBox.maxZ <= structureBox.minZ) return;
        Box nearbyBox = structureBox.expand(STRUCTURE_PLAYER_NEAR_MARGIN);
        boolean playerNearby = !world.getEntitiesByClass(PlayerEntity.class, nearbyBox, PlayerEntity::isAlive).isEmpty();
        int cleanupInterval = playerNearby
                ? Math.max(5, STRUCTURE_ENTITY_CLEANUP_INTERVAL_ACTIVE)
                : Math.max(20, STRUCTURE_ENTITY_CLEANUP_INTERVAL_IDLE);
        if (world.getTime() % cleanupInterval != 0) return;

        for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class, structureBox, e -> true)) {
            item.discard();
        }
        for (ExperienceOrbEntity orb : world.getEntitiesByClass(ExperienceOrbEntity.class, structureBox, e -> true)) {
            orb.discard();
        }
        for (FallingBlockEntity falling : world.getEntitiesByClass(FallingBlockEntity.class, structureBox, e -> true)) {
            falling.discard();
        }
        for (MobEntity mob : world.getEntitiesByClass(MobEntity.class, structureBox, e -> !e.isPersistent())) {
            mob.discard();
        }
    }

    private static void enforceSpawnSanctuary(ServerWorld world, FixedStructurePlacerOverworld.StructuresPlacedState state) {
        int sx = state.spawnX;
        int sy = state.spawnY;
        int sz = state.spawnZ;
        int radius = FixedStructurePlacerOverworld.SPAWN_SANCTUARY_RADIUS;
        int top = sy + FixedStructurePlacerOverworld.SPAWN_SANCTUARY_Y_ABOVE;

        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int x = sx - radius; x <= sx + radius; x++) {
            for (int y = sy; y <= top; y++) {
                for (int z = sz - radius; z <= sz + radius; z++) {
                    cursor.set(x, y, z);
                    if (!world.isAir(cursor)) {
                        world.setBlockState(cursor, net.minecraft.block.Blocks.AIR.getDefaultState(), 2);
                    }
                }
            }
        }

        Box spawnBox = new Box(
                sx - radius,
                sy,
                sz - radius,
                sx + radius + 1,
                top + 1,
                sz + radius + 1
        );
        for (Entity entity : world.getOtherEntities(null, spawnBox, e -> !(e instanceof PlayerEntity))) {
            entity.discard();
        }
    }

    private static boolean isProtectedPlacement(ServerWorld world, BlockPos pos) {
        return FixedStructurePlacerOverworld.isSpawnSanctuaryPos(world, pos)
                || FixedStructurePlacerOverworld.isProtectedStructurePos(world, pos);
    }
}
