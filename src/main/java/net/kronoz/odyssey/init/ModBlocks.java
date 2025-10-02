package net.kronoz.odyssey.init;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.kronoz.odyssey.Odyssey;
import net.kronoz.odyssey.block.custom.*;
import net.kronoz.odyssey.block.custom.LightBlock;
import net.kronoz.odyssey.entity.MapBlockEntity;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import static net.kronoz.odyssey.Odyssey.id;

public class ModBlocks {

    public static final Block EXAMPLE_BLOCK  = registerBlock("example_block", new ExampleBlock(AbstractBlock.Settings.copy(Blocks.STONE)));
    public static final Block FACILITY_PILLAR_BLOCK  = registerBlock("facility_pillar", new FacilityPIllarBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK)));
    public static final Block LARGE_FACILITY_PILLAR_BLOCK = registerBlock("large_facility_pillar", new PillarBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK)));
    public static final Block ELEVATOR  = registerBlock("elevator", new ElevatorBlock(FabricBlockSettings.create().strength(2.0f)));
    public static final Block LIGHT1  = registerBlock("light_1", new LightBlock(FabricBlockSettings.create().strength(2.0f)));
    public static final Block PYROXENE  = registerBlock("pyroxene", new Block(AbstractBlock.Settings.copy(Blocks.TUFF)));

    public static final Block MAP_BLOCK = registerBlock(
            "map_block",
            new MapBlock(Block.Settings.create()
                    .mapColor(MapColor.IRON_GRAY)
                    .strength(2.0f, 6.0f)
                    .nonOpaque())
    );

    public static final BlockEntityType<MapBlockEntity> MAP_BLOCK_ENTITY =
            Registry.register(
                    Registries.BLOCK_ENTITY_TYPE,
                    id("map_block_entity"),
                    BlockEntityType.Builder.create(MapBlockEntity::new, MAP_BLOCK).build(null)
            );

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
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.NATURAL).register(entries -> {
            entries.add(ModBlocks.PYROXENE);


        });


    }
}
