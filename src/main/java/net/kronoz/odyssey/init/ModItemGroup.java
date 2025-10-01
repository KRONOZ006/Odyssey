package net.kronoz.odyssey.init;

import net.kronoz.odyssey.Odyssey;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ModItemGroup {

    public static final ItemGroup ODYSSEY = Registry.register(Registries.ITEM_GROUP,
            Identifier.of(Odyssey.MODID, "odyssey"),
            ItemGroup.create(null, -1)
                    .displayName(Text.translatable("itemgroup.odyssey.odyssey"))
                    .icon(() -> new ItemStack(ModItems.XARIS))
                    .entries((displayContext, entries) -> {
                        entries.add(ModItems.TOMAHAWK);
                        entries.add(ModBlocks.EXAMPLE_BLOCK);
                        entries.add(ModBlocks.FACILITY_PILLAR_BLOCK);
                        entries.add(ModBlocks.LIGHT1);

                    })
                    .build());

    public static void registerItemGroups() {
        Odyssey.LOGGER.info("Registering Item Groups for " + Odyssey.MODID);
    }
}