package net.kronoz.odyssey.mixin;

import net.kronoz.odyssey.systems.slide.SlideClientState;
import net.kronoz.odyssey.systems.slide.SlideModelPose;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin<T extends LivingEntity> extends BipedEntityModel<T> {
    public PlayerEntityModelMixin(ModelPart root) { super(root); }

    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void odyssey$slidePose(T entity, float limbAngle, float limbDistance, float age, float headYaw, float headPitch, CallbackInfo ci) {
        if (!SlideClientState.isActive()) return;
        if (MinecraftClient.getInstance().player != entity) return;
        float k = SlideClientState.lerpFactor();
        setPart(this.body, SlideModelPose.BODY_PITCH, 0f, SlideModelPose.BODY_ROLL, k);
        setPart(this.head, SlideModelPose.HEAD_PITCH, SlideModelPose.HEAD_YAW, SlideModelPose.HEAD_ROLL, k);
        setPart(this.rightArm, SlideModelPose.RARM_PITCH, SlideModelPose.RARM_YAW, SlideModelPose.RARM_ROLL, k);
        setPart(this.leftArm, SlideModelPose.LARM_PITCH, SlideModelPose.LARM_YAW, SlideModelPose.LARM_ROLL, k);
        setPart(this.rightLeg, SlideModelPose.RLEG_PITCH, SlideModelPose.RLEG_YAW, SlideModelPose.RLEG_ROLL, k);
        setPart(this.leftLeg, SlideModelPose.LLEG_PITCH, SlideModelPose.LLEG_YAW, SlideModelPose.LLEG_ROLL, k);
    }

    @Unique
    private static void setPart(ModelPart part, float pitch, float yaw, float roll, float k) {
        part.pitch += (pitch - part.pitch) * k;
        part.yaw += (yaw - part.yaw) * k;
        part.roll += (roll - part.roll) * k;
    }
}
