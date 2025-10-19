package net.kronoz.odyssey.init;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.kronoz.odyssey.Odyssey;
import net.kronoz.odyssey.block.custom.*;
import net.kronoz.odyssey.block.custom.LightBlock;
import net.kronoz.odyssey.entity.MapBlockEntity;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import static net.kronoz.odyssey.Odyssey.id;

public class ModBlocks {

    public static final Block ALARM  = registerBlock("alarm", new AlarmBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK)));
    public static final Block LIGHT2  = registerBlock("light_2", new Light2Block(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK)));
    public static final Block VERDIGRIS_BLOCK  = registerBlock("verdigris_block", new Block(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK)));
    public static final Block VERDIGRIS_PANNEL  = registerBlock("verdigris_pannel", new Block(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK)));
    public static final Block VERDIGRIS_PLATES  = registerBlock("verdigris_plates", new Block(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK)));
    public static final Block VERDIGRIS_SHEATHING  = registerBlock("verdigris_sheathing", new PillarBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK)));
    public static final Block SCARRED_VERDIGRIS_SHEATHING  = registerBlock("scarred_verdigris_sheathing", new PillarBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK)));
    public static final Block FACILITY_PILLAR_BLOCK  = registerBlock("facility_pillar", new FacilityPIllarBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK)));
    public static final Block LARGE_FACILITY_PILLAR_BLOCK = registerBlock("large_facility_pillar", new PillarBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).nonOpaque()));
    public static final Block FACILITY_TILES = registerBlock("facility_tiles", new PillarBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK)));
    public static final Block FACILITY_REBAR_BLOCK = registerBlock("facility_rebar", new Block(AbstractBlock.Settings.copy(Blocks.WAXED_COPPER_GRATE)));
    public static final Block ELEVATOR  = registerBlock("elevator", new ElevatorBlock(FabricBlockSettings.create().strength(2.0f)));
    public static final Block SLIDING_DOOR  = registerBlock("sliding_door", new SlidingDoorBlock(FabricBlockSettings.create().strength(2.0f)));
    public static final Block LIGHT1  = registerBlock("light_1", new LightBlock(FabricBlockSettings.create().strength(2.0f)));
    public static final Block PYROXENE  = registerBlock("pyroxene", new Block(AbstractBlock.Settings.copy(Blocks.TUFF)));
    public static final Block SEQUENCEB  = registerBlock("sequencer", new SequencerBlock(AbstractBlock.Settings.copy(Blocks.TUFF)));
    public static final Block SPEDBLOCK  = registerBlock("spedblock", new SpedLigtBlock(AbstractBlock.Settings.copy(Blocks.TUFF)));
    public static final Block ENERGY_EMITTER  = registerBlock("energy_emitter", new EnergyEmitterBlock(AbstractBlock.Settings.copy(Blocks.TUFF)));
    public static final Block ENERGY_BARRIER  = registerBlock("energy_barrier", new EnergyBarrierBlock(AbstractBlock.Settings.create().nonOpaque().strength(-1.0f, 3600000f).pistonBehavior(PistonBehavior.BLOCK).emissiveLighting((state, world, pos) -> true)
            .strength(-1.0f).luminance(state -> state.get(EnergyBarrierBlock.LIGHT_LEVEL))));
    public static final Block SHELF1 = registerBlock("shelf1", new Shelf1Block(AbstractBlock.Settings.copy(Blocks.TUFF).nonOpaque()));
    public static final Block STASISPOD = Registry.register(
            Registries.BLOCK, Identifier.of(Odyssey.MODID,"stasispod"),
            new StasisPodBlock(AbstractBlock.Settings.create().strength(3.0f).nonOpaque())
    );
    public static final Block RAILING = Registry.register(
            Registries.BLOCK, Identifier.of("odyssey","railing"),
            new RailingBlock(AbstractBlock.Settings.create().nonOpaque().strength(2.0f, 3.0f))
    );public static final Block TERMINAL = Registry.register(
            Registries.BLOCK, Identifier.of("odyssey","terminal"),
            new TerminalBlock(AbstractBlock.Settings.create().nonOpaque().strength(2.0f, 3.0f))
    );
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
            entries.add(ModBlocks.ALARM);
            entries.add(ModBlocks.FACILITY_PILLAR_BLOCK);
            entries.add(ModBlocks.FACILITY_REBAR_BLOCK);
            entries.add(ModBlocks.LARGE_FACILITY_PILLAR_BLOCK);
            entries.add(ModBlocks.SLIDING_DOOR);

        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.NATURAL).register(entries -> {
            entries.add(ModBlocks.PYROXENE);


        });


    }
}
