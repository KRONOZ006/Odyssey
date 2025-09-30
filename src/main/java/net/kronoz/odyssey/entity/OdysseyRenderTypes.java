package net.kronoz.odyssey.entity;

import foundry.veil.api.client.render.rendertype.VeilRenderType;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public final class OdysseyRenderTypes {
    private OdysseyRenderTypes() {}

    // Change these ids to your Veil shader pipeline ids (defined in your resources/veil JSONs)
    public static final Identifier MAP_HEIGHTMAP_TEXTURE = Identifier.of("odyssey", "mirror/map_texture");
    public static final Identifier MAP_HEIGHTMAP_TESSELLATION = Identifier.of("odyssey", "mirror/map_tess");
    public static final Identifier MAP_MIRROR = Identifier.of("odyssey", "mirror/mirror");

    public static @Nullable RenderLayer mapHeightmap(boolean tessellation) {
        Identifier id = tessellation ? MAP_HEIGHTMAP_TESSELLATION : MAP_HEIGHTMAP_TEXTURE;
        RenderLayer v = VeilRenderType.get(id);
        return v != null ? v : RenderLayer.getCutout();
    }

    public static @Nullable RenderLayer mapMirror() {
        RenderLayer v = VeilRenderType.get(MAP_MIRROR);
        return v != null ? v : RenderLayer.getTranslucent();
    }
}
