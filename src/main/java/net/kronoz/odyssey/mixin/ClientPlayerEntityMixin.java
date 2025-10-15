// src/main/java/net/kronoz/odyssey/mixin/ClientPlayerEntityMixin.java
package net.kronoz.odyssey.mixin;

import net.kronoz.odyssey.movement.MovementVisuals;
import net.kronoz.odyssey.movement.WallRun;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
    @Unique private final WallRun.WallState odyssey$wall = new WallRun.WallState();

    @Inject(method = "tickMovement", at = @At("TAIL"))
    private void odyssey$clientWallRun(CallbackInfo ci) {
        ClientPlayerEntity self = (ClientPlayerEntity)(Object)this;
        WallRun.tick(self, odyssey$wall);
        MovementVisuals.updateWallTilt(self, odyssey$wall);
    }
}
