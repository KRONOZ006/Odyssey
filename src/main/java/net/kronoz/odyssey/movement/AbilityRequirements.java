package net.kronoz.odyssey.movement;

import net.kronoz.odyssey.init.ModItems;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

public final class AbilityRequirements {
    private AbilityRequirements() {
    }

    public static boolean canDash(PlayerEntity player) {
        if (player == null) {
            return false;
        }
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isOf(ModItems.XARIS)) {
                return true;
            }
        }
        return false;
    }
}
