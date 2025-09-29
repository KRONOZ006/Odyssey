package net.kronoz.odyssey.item;

import net.kronoz.odyssey.Odyssey;
import net.kronoz.odyssey.item.custom.TomahawkItem;
import net.kronoz.odyssey.item.custom.XarisArm;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModItems {

    public static final Item TOMAHAWK = registerItem("tomahawk", new TomahawkItem(new Item.Settings().maxCount(1).fireproof()));
    public static final Item XARIS = registerItem("xaris", new XarisArm(new Item.Settings().maxCount(1).fireproof()));

    private static Item registerItem(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(Odyssey.MODID, name), item);
    }

    public static void registerModItems(){
        Odyssey.LOGGER.info("register items for" + Odyssey.MODID);
    }
}
