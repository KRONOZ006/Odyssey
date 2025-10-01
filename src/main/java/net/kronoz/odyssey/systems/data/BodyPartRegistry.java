package net.kronoz.odyssey.systems.data;

import com.google.gson.Gson;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.kronoz.odyssey.Odyssey;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class BodyPartRegistry {
    public static final class Part extends BodyPart {}
    private static final Map<Identifier, Part> PARTS = new HashMap<>();
    private static final Gson GSON = new Gson();

    public static void init() {
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public Identifier getFabricId() { return Odyssey.id("body_parts"); }
            @Override public void reload(ResourceManager manager) {
                PARTS.clear();
                manager.findResources("body_parts", id -> id.getPath().endsWith(".json")).forEach((id, res) -> {
                    try (var in = res.getInputStream()) {
                        var part = GSON.fromJson(new InputStreamReader(in), Part.class);
                        if (part != null && part.slot != null) {
                            part.id = Identifier.of(id.getNamespace(), id.getPath().replace("body_parts/","").replace(".json",""));
                            PARTS.put(part.id, part);
                        }
                    } catch (Exception ignored) {}
                });
            }
        });
    }

    public static Part get(Identifier id){ return PARTS.get(id); }
    public static Map<Identifier, Part> all(){ return Collections.unmodifiableMap(PARTS); }
}
