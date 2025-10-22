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

import java.util.Map;
import java.util.WeakHashMap;

public class TransformingAlterBlock extends Block {
    private static final RegistryKey<World> VOID_DIM =
            RegistryKey.of(RegistryKeys.WORLD, Identifier.of("odyssey", "void"));

    private static final double TX = 328.5;
    private static final double TY = 147.0;
    private static final double TZ = 183.5;
    private static final long PROTECT_TICKS = 50 * 20; // 50 seconds at 20 TPS

    private static final Map<ServerPlayerEntity, Long> FALL_PROTECT = new WeakHashMap<>();

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

        doTeleport(sp, dest);
        return ActionResult.SUCCESS;
    }

    @Override
    public void onSteppedOn(World world, BlockPos pos, BlockState state, Entity entity) {
        if (world.isClient) return;
        if (!(entity instanceof ServerPlayerEntity sp)) return;

        ServerWorld dest = sp.getServer().getWorld(VOID_DIM);
        if (dest == null) return;

        doTeleport(sp, dest);
    }

    private static void doTeleport(ServerPlayerEntity sp, ServerWorld dest) {
        sp.fallDistance = 0.0f;
        sp.teleport(dest, TX, TY, TZ, sp.getYaw(), sp.getPitch());
        sp.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 40, 0, false, false));

        FALL_PROTECT.put(sp, dest.getTime() + PROTECT_TICKS);
    }
}
