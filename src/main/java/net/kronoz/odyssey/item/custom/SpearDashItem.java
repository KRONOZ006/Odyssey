package net.kronoz.odyssey.item.custom;

import net.kronoz.odyssey.init.ModNetworking;
import net.kronoz.odyssey.net.DashC2SPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;

public class SpearDashItem extends Item implements GeoAnimatable {
    public SpearDashItem(Settings settings) { super(settings); }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient) {
            var p = user;
            var opts = MinecraftClient.getInstance().options;

            boolean f = opts.forwardKey.isPressed();
            boolean b = opts.backKey.isPressed();
            boolean l = opts.leftKey.isPressed();
            boolean r = opts.rightKey.isPressed();
            boolean up = opts.jumpKey.isPressed();
            boolean dn = opts.sneakKey.isPressed();

            Vec3d look = p.getRotationVec(1.0f).normalize();
            Vec3d fwdFlat = new Vec3d(look.x, 0, look.z);
            if (fwdFlat.lengthSquared() < 1e-6) fwdFlat = new Vec3d(0, 0, 1);
            fwdFlat = fwdFlat.normalize();
            Vec3d right = new Vec3d(fwdFlat.z, 0, -fwdFlat.x);

            double ax = (r ? 1 : 0) - (l ? 1 : 0);
            double az = (f ? 1 : 0) - (b ? 1 : 0);
            double keyVy = (up ? 1 : 0) - (dn ? 1 : 0);

            double pitchVy = MathHelper.clamp(look.y, -1.0, 1.0);
            double vy = keyVy != 0 ? keyVy : pitchVy;

            Vec3d horiz = fwdFlat.multiply(az).add(right.multiply(ax));
            if (horiz.lengthSquared() < 1e-6) horiz = fwdFlat;
            horiz = horiz.normalize();

            double hMag = Math.sqrt(Math.max(0.0, 1.0 - vy * vy));
            Vec3d dir = new Vec3d(horiz.x * hMag, vy, horiz.z * hMag).normalize();

            float spd = p.isSprinting() ? 1.2f : 0.95f;
            float tinyUp = p.isOnGround() ? 0.04f : 0.0f;

            ModNetworking.send(new DashC2SPayload((float)dir.x, (float)dir.y, (float)dir.z, spd, tinyUp));
            p.getItemCooldownManager().set(this, 14);
        } else {
            user.getItemCooldownManager().set(this, 14);
        }
        return TypedActionResult.success(user.getStackInHand(hand), world.isClient);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {

    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return null;
    }

    @Override
    public double getTick(Object o) {
        return 0;
    }
}
