package net.kronoz.odyssey.item.custom;

import net.kronoz.odyssey.entity.GrappleHookEntity;
import net.kronoz.odyssey.init.ModEntities;
import net.kronoz.odyssey.systems.grapple.GrappleServerLogic;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class GrappleItem extends Item {

    public GrappleItem(Settings settings) { super(settings); }

    @Override
    public UseAction getUseAction(ItemStack stack) { return UseAction.NONE; }

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) { return 0; }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (!world.isClient) {
            if (GrappleServerLogic.hasHook(user)) {
                GrappleServerLogic.detach(user); // toggle off
            } else {
                GrappleHookEntity hook = new GrappleHookEntity(ModEntities.GRAPPLE_HOOK, world, user);
                Vec3d look = user.getRotationVec(1f).normalize();
                Vec3d pos = user.getCameraPosVec(1f).add(look.multiply(0.75));
                hook.refreshPositionAndAngles(pos.x, pos.y, pos.z, user.getYaw(), user.getPitch());
                hook.setVelocity(look.multiply(2.75));
                world.spawnEntity(hook);
                GrappleServerLogic.setHook(user, hook); // toggle on
            }
        }
        return TypedActionResult.success(stack, world.isClient);
    }
}
