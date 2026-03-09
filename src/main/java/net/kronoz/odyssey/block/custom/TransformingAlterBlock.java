package net.kronoz.odyssey.block.custom;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class TransformingAlterBlock extends Block {
    private static final RegistryKey<World> VOID_DIM =
            RegistryKey.of(RegistryKeys.WORLD, Identifier.of("odyssey", "void"));

    private static final double TX = 328.5;
    private static final double TY = 147.0;
    private static final double TZ = 183.5;
    private static final long PROTECT_TICKS = 50 * 20; // 50 seconds at 20 TPS
    private static final long TELEPORT_COOLDOWN_TICKS = 20;

    private static final Map<ServerPlayerEntity, Long> FALL_PROTECT = new WeakHashMap<>();
    private static final Map<ServerPlayerEntity, Long> TELEPORT_COOLDOWN = new WeakHashMap<>();
    private static final Set<ServerPlayerEntity> TELEPORT_LOCK = Collections.newSetFromMap(new WeakHashMap<>());

    public TransformingAlterBlock(Settings settings) {
        super(settings);

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity sp) {
                if (source.isIn(DamageTypeTags.IS_FALL)) {
                    Long until = FALL_PROTECT.get(sp);
                    if (until != null && sp.getWorld().getTime() < until) {
                        return false;
                    }
                }
            }
            return true;
        });
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;

        ServerWorld dest = sp.getServer().getWorld(VOID_DIM);
        if (dest == null) return ActionResult.CONSUME;

        queueTeleport(sp, dest);
        return ActionResult.SUCCESS;
    }

    @Override
    public void onSteppedOn(World world, BlockPos pos, BlockState state, Entity entity) {
        if (world.isClient) return;
        if (!(entity instanceof ServerPlayerEntity sp)) return;

        ServerWorld dest = sp.getServer().getWorld(VOID_DIM);
        if (dest == null) return;

        queueTeleport(sp, dest);
    }

    private static void queueTeleport(ServerPlayerEntity sp, ServerWorld dest) {
        if (sp == null || dest == null || sp.isRemoved()) {
            return;
        }

        long now = sp.getServerWorld().getTime();
        Long cooldownUntil = TELEPORT_COOLDOWN.get(sp);
        if (cooldownUntil != null && now < cooldownUntil) {
            return;
        }
        if (TELEPORT_LOCK.contains(sp)) {
            return;
        }

        // Avoid self-teleport loops if this block also exists in destination.
        if (sp.getWorld() == dest && sp.squaredDistanceTo(TX, TY, TZ) < 4.0) {
            return;
        }

        TELEPORT_LOCK.add(sp);
        TELEPORT_COOLDOWN.put(sp, now + TELEPORT_COOLDOWN_TICKS);
        sp.getServer().execute(() -> {
            try {
                doTeleportNow(sp, dest);
            } finally {
                TELEPORT_LOCK.remove(sp);
            }
        });
    }

    private static void doTeleportNow(ServerPlayerEntity sp, ServerWorld dest) {
        if (sp == null || dest == null || sp.isRemoved() || sp.getServer() == null) {
            return;
        }
        if (sp.getWorld() == dest && sp.squaredDistanceTo(TX, TY, TZ) < 4.0) {
            return;
        }

        sp.fallDistance = 0.0f;
        if (sp.getWorld() == dest) {
            sp.teleport(TX, TY, TZ, false);
        } else {
            sp.teleport(dest, TX, TY, TZ, sp.getYaw(), sp.getPitch());
        }
        if (sp.isRemoved()) {
            return;
        }
        sp.fallDistance = 0.0f;
        sp.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 40, 0, false, false));

        FALL_PROTECT.put(sp, dest.getTime() + PROTECT_TICKS);
    }
}
