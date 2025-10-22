package net.kronoz.odyssey.init;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.kronoz.odyssey.block.custom.TransformingAlterBlock.ProtectionDataTracker;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;

public class FallDamageCanceller {
    public static void init() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity sp
                    && ProtectionDataTracker.canNegateFall(sp, source.getType())) {
                return false;
            }
            return true;
        });
    }
}
