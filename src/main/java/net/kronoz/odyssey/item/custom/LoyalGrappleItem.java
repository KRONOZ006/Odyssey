package net.kronoz.odyssey.item.custom;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class LoyalGrappleItem extends Item {

    private static final float THROW_SPEED = 2.5F;
    private static final float INACCURACY  = 1.0F;

    public LoyalGrappleItem(Settings settings) {
        super(settings);
    }

    @Override public UseAction getUseAction(ItemStack stack) { return UseAction.NONE; }
    @Override public int getMaxUseTime(ItemStack stack, LivingEntity user) { return 0; }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack inHand = user.getStackInHand(hand);

        if (!world.isClient) {
            ItemStack thrownStack = new ItemStack(Items.TRIDENT);

            ItemEnchantmentsComponent ench = thrownStack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
            ItemEnchantmentsComponent.Builder b = new ItemEnchantmentsComponent.Builder(ench);
            b.add((RegistryEntry<Enchantment>) Enchantments.LOYALTY, 3);
            thrownStack.set(DataComponentTypes.ENCHANTMENTS, b.build());

            TridentEntity trident = new TridentEntity(world, user, thrownStack);
            Vec3d look = user.getRotationVec(1.0F).normalize();
            Vec3d pos  = user.getCameraPosVec(1.0F).add(look.multiply(0.75));
            trident.refreshPositionAndAngles(pos.x, pos.y, pos.z, user.getYaw(), user.getPitch());

            trident.setVelocity(user, user.getPitch(), user.getYaw(), 0.0F, THROW_SPEED, INACCURACY);

            trident.setOwner(user);

            trident.pickupType = TridentEntity.PickupPermission.CREATIVE_ONLY;

            world.spawnEntity(trident);

            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.ITEM_TRIDENT_THROW, SoundCategory.PLAYERS, 1.0F, 1.0F);
        }

        return TypedActionResult.success(inHand, world.isClient);
    }
}
