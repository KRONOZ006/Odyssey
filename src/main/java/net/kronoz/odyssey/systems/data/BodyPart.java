package net.kronoz.odyssey.systems.data;

import net.minecraft.util.Identifier;

public class BodyPart {
    public Identifier id;
    public String slot;
    public double speed;
    public double attack;

    // rendu “legacy” (cube d’attache) — optionnels
    public String geckolibModel;
    public String geckolibTexture;
    public String geckolibAnim;
    public boolean emissive;

    // NOUVEAU: rendre un item directement
    public String displayItem; // ex: "minecraft:iron_block" ou "odyssey:arm_plate_mk1"
}
