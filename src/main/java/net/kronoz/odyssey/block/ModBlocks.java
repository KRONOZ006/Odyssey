package net.kronoz.odyssey.block;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.kronoz.odyssey.Odyssey;
import net.kronoz.odyssey.block.custom.ExampleBlock;
import net.kronoz.odyssey.block.custom.FacilityPIllarBlock;
import net.minecraft.block.*;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

public class ModBlocks {

    public static final Block EXAMPLE_BLOCK  = registerBlock("example_block", new ExampleBlock(AbstractBlock.Settings.copy(Blocks.STONE)));
    public static final Block FACILITY_PILLAR_BLOCK  = registerBlock("facility_pillar", new FacilityPIllarBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK)));

    private static Block registerBlock ( String name, Block block) {
        registerBlockItem(name, block);
    return Registry.register(Registries.BLOCK, Identifier.of(Odyssey.MODID, name), block);
    }


    private static void registerBlockItem (String name, Block block) {
        Registry.register(Registries.ITEM, Identifier.of(Odyssey.MODID, name),
                new BlockItem(block, new Item.Settings()));
    }

    public static void registerModBlocks() {
        Odyssey.LOGGER.info("Registering Mod Blocks for " + Odyssey.MODID);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(entries -> {
            entries.add(ModBlocks.EXAMPLE_BLOCK);


        });


    }
}
