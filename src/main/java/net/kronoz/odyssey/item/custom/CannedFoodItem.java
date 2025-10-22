package net.kronoz.odyssey.item.custom;

import net.kronoz.odyssey.init.ModSounds;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

import java.util.concurrent.ThreadLocalRandom;

public class CannedFoodItem extends Item {

    public static final FoodComponent FOOD = new FoodComponent.Builder()
            .nutrition(6)
            .saturationModifier(0.6f)
            .alwaysEdible()
            .build();

    public CannedFoodItem(Settings settings) {
        super(settings.food(FOOD));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        //if (!world.isClient) {
        //    ServerWorld sw = (ServerWorld) world;
        //    FirstHoldState state = FirstHoldState.get(sw);
        //    if (!state.hasHeard(user.getUuid())) {
        //        sw.playSound(null, user.getX(), user.getY(), user.getZ(),
        //                ModSounds.SOUP3, SoundCategory.PLAYERS, 1.0f, 1.0f);
        //        state.markHeard(user.getUuid());
        //    }
        //}

        user.setCurrentHand(hand); // start eating anim
        return TypedActionResult.consume(stack);
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.EAT;
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        // On successful consume: 5% chance to play SOUP1 or SOUP2 (NEVER SOUP3 here)
     //   if (!world.isClient) {
     //       if (ThreadLocalRandom.current().nextDouble() < 0.05) {
     //           boolean soup1 = ThreadLocalRandom.current().nextBoolean();
     //           ((ServerWorld) world).playSound(
     //                   null, user.getX(), user.getY(), user.getZ(),
     //                   soup1 ? ModSounds.SOUP1 : ModSounds.SOUP2,
     //                   SoundCategory.PLAYERS, 1.0f, 1.0f
     //           );
     //       }
     //   }

        return super.finishUsing(stack, world, user);
    }
}
