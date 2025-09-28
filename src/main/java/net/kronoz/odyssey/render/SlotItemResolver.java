package net.kronoz.odyssey.render;

import net.kronoz.odyssey.cca.ModComponents;
import net.kronoz.odyssey.data.BodyPartRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

import java.util.Map;

/**
 * Résout l'item à afficher par slot :
 * 1) si un "part" est équipé et a displayItem -> on l'utilise
 * 2) sinon fallback code (constants)
 */
public final class SlotItemResolver {

    private static final Map<String, Identifier> FALLBACK = Map.of(
            //"right_arm", Identifier.of("odyssey","tomahawk")
            // "left_arm", Identifier.of("odyssey","yo_item"),
            // "head",     Identifier.of("minecraft","carved_pumpkin"),
            // "torso",    Identifier.of("odyssey","chest_core"),
            // "right_leg",Identifier.of("odyssey","leg_module_r"),
            // "left_leg", Identifier.of("odyssey","leg_module_l")
    );

    private SlotItemResolver(){}

    public static Identifier resolve(String slot, PlayerEntity player) {
        var comp = ModComponents.BODY.get(player);
        if (comp != null) {
            var partId = comp.getEquipped().get(slot);
            if (partId != null) {
                var def = BodyPartRegistry.get(partId);
                if (def != null && def.displayItem != null && !def.displayItem.isBlank()) {
                    var id = Identifier.tryParse(def.displayItem);
                    if (id != null) return id;
                    System.out.println("[Odyssey/Body] slot "+slot+" displayItem invalide: "+def.displayItem);
                }
            }
        }
        return FALLBACK.get(slot);
    }
}
