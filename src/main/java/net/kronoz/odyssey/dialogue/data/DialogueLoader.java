package net.kronoz.odyssey.dialogue.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.kronoz.odyssey.dialogue.Dialogue;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceReloader;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraft.util.Identifier;

import java.io.InputStreamReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public final class DialogueLoader {
    private static final Gson G = new Gson();
    private static final Map<Identifier, DialogueTree> TREES = new HashMap<>();

    public static void init() {
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new Reloader());
    }
    public static DialogueTree get(Identifier id){ return TREES.get(id); }

    static final class Reloader implements SynchronousResourceReloader, ResourceReloader, IdentifiableResourceReloadListener {
        @Override public Identifier getFabricId(){ return Identifier.of(Dialogue.MODID, "dialogue_loader"); }

        @Override
        public Collection<Identifier> getFabricDependencies() {
            return IdentifiableResourceReloadListener.super.getFabricDependencies();
        }

        @Override
        public void reload(ResourceManager manager) {
            TREES.clear();
            // PAS de slash final !!
            final String prefix = Dialogue.MODID + "/dialogue";

            manager.findResources(prefix, id -> id.getPath().endsWith(".json"))
                    .forEach((id, res) -> {
                        try (InputStreamReader r = new InputStreamReader(res.getInputStream())) {
                            JsonObject root = G.fromJson(r, JsonObject.class);

                            // id du fichier ou "id" explicite dans le json
                            Identifier treeId = root.has("id") ? Identifier.of(root.get("id").getAsString()) : id;
                            String start = root.get("start").getAsString();

                            Map<String, DialogueNode> nodes = new HashMap<>();
                            for (Map.Entry<String, JsonElement> n : root.getAsJsonObject("nodes").entrySet()) {
                                nodes.put(n.getKey(), DialogueNode.fromJson(n.getValue().getAsJsonObject(), n.getKey()));
                            }
                            TREES.put(treeId, new DialogueTree(treeId, start, nodes));
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    });
        }

    }
}
