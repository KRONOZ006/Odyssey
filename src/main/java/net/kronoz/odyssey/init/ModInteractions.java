package net.kronoz.odyssey.init;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.kronoz.odyssey.block.custom.ElevatorBlock;
import net.kronoz.odyssey.entity.ElevatorBlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

public class ModInteractions {
    public static void init(){
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if(hand!=Hand.MAIN_HAND || hit==null) return ActionResult.PASS;
            var pos = hit.getBlockPos();
            var b = world.getBlockState(pos).getBlock();
            if(b instanceof ElevatorBlock){
                if(world.isClient) return ActionResult.SUCCESS;
                var be = world.getBlockEntity(pos);
                if(be instanceof ElevatorBlockEntity e){ e.start((ServerWorld)world, player.isSneaking()?-1:1); }
                return ActionResult.CONSUME;
            }
            return ActionResult.PASS;
        });
    }
}
