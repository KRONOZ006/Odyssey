package net.kronoz.odyssey.mixin;

import net.kronoz.odyssey.init.ModComponents;
import net.kronoz.odyssey.systems.model._MountItem;
import net.kronoz.odyssey.systems.render.SlotItemResolver;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, M extends EntityModel<T>> {

    @Shadow protected M model;

    @Unique private boolean saved;
    @Unique private boolean pHead, pHat, pBody, pJacket, pRA, pLA, pRLeg, pLLeg, pRSleeve, pLSleeve, pRPants, pLPants;

    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"))
    private void ody$hideHead(T entity, float f, float g, MatrixStack matrices, VertexConsumerProvider providers, int light, CallbackInfo ci) {
        if (!(entity instanceof PlayerEntity player) || !(this.model instanceof PlayerEntityModel<?> m)) return;

        var eq = ModComponents.BODY.get(player).getEquipped();
        boolean hideHead     = eq.containsKey("head");
        boolean hideTorso    = eq.containsKey("torso") || eq.containsKey("body");
        boolean hideRightArm = eq.containsKey("right_arm");
        boolean hideLeftArm  = eq.containsKey("left_arm");
        boolean hideRightLeg = eq.containsKey("right_leg") || eq.containsKey("legs");
        boolean hideLeftLeg  = eq.containsKey("left_leg")  || eq.containsKey("legs");

        pHead=m.head.visible; pHat=m.hat.visible; pBody=m.body.visible; pJacket=m.jacket.visible;
        pRA=m.rightArm.visible; pLA=m.leftArm.visible;
        pRLeg=m.rightLeg.visible; pLLeg=m.leftLeg.visible;
        pRSleeve=m.rightSleeve.visible; pLSleeve=m.leftSleeve.visible;
        pRPants=m.rightPants.visible; pLPants=m.leftPants.visible; saved=true;

        m.head.visible      = !hideHead;
        m.hat.visible       = !hideHead;
        m.body.visible      = !hideTorso;
        m.jacket.visible    = !hideTorso;
        m.rightArm.visible  = !hideRightArm; m.rightSleeve.visible = !hideRightArm;
        m.leftArm.visible   = !hideLeftArm;  m.leftSleeve.visible  = !hideLeftArm;
        m.rightLeg.visible  = !hideRightLeg; m.rightPants.visible  = !hideRightLeg;
        m.leftLeg.visible   = !hideLeftLeg;  m.leftPants.visible   = !hideLeftLeg;
    }

    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/model/EntityModel;setAngles(Lnet/minecraft/entity/Entity;FFFFF)V",
                    shift = At.Shift.AFTER))
    private void ody$mountAfterAngles(T entity, float f, float g, MatrixStack matrices, VertexConsumerProvider providers, int light, CallbackInfo ci) {
        if (!(entity instanceof PlayerEntity player) || !(this.model instanceof PlayerEntityModel<?> pmRaw)) return;
        @SuppressWarnings("unchecked")
        PlayerEntityModel<PlayerEntity> pModel = (PlayerEntityModel<PlayerEntity>) pmRaw;

        var headItem  = SlotItemResolver.resolve("head", player);
        var torsoItem = SlotItemResolver.resolve("torso", player);
        var raItem    = SlotItemResolver.resolve("right_arm", player);
        var laItem    = SlotItemResolver.resolve("left_arm", player);
        var rlItem    = SlotItemResolver.resolve("right_leg", player);
        var llItem    = SlotItemResolver.resolve("left_leg", player);

        if (headItem  != null) _MountItem.onHead(headItem,   player, pModel, matrices, providers, light);
        if (torsoItem != null) _MountItem.onTorso(torsoItem, player, pModel, matrices, providers, light);
        if (raItem    != null) _MountItem.onRightArm(raItem, player, pModel, matrices, providers, light);
        if (laItem    != null) _MountItem.onLeftArm(laItem,  player, pModel, matrices, providers, light);
        if (rlItem    != null) _MountItem.onRightLeg(rlItem, player, pModel, matrices, providers, light);
        if (llItem    != null) _MountItem.onLeftLeg(llItem,  player, pModel, matrices, providers, light);
    }

    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("TAIL"))
    private void ody$restoreTail(T entity, float f, float g, MatrixStack matrices, VertexConsumerProvider providers, int light, CallbackInfo ci) {
        if (!saved || !(this.model instanceof PlayerEntityModel<?> m)) return;
        saved=false;
        m.head.visible=pHead; m.hat.visible=pHat; m.body.visible=pBody; m.jacket.visible=pJacket;
        m.rightArm.visible=pRA; m.leftArm.visible=pLA;
        m.rightLeg.visible=pRLeg; m.leftLeg.visible=pLLeg;
        m.rightSleeve.visible=pRSleeve; m.leftSleeve.visible=pLSleeve;
        m.rightPants.visible=pRPants; m.leftPants.visible=pLPants;
    }
}
