package net.dark.spv_addon.init.helper;

import com.google.gson.*;
import net.minecraft.block.Block;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class CollisionShapeHelper {
    private static final Pattern UNR = Pattern.compile("collision_\\d+");
    private static final Map<String, VoxelShape> UNR_CACHE = new ConcurrentHashMap<>();

    private CollisionShapeHelper() {}

    public static VoxelShape loadUnrotatedCollisionFromModelJson(String namespace, String path) {
        String key = namespace + ":" + path;
        VoxelShape c = UNR_CACHE.get(key);
        if (c != null) return c;
        List<VoxelShape> shapes = new ArrayList<>();
        JsonArray elements = readElements(namespace, path);
        if (elements != null) {
            for (JsonElement element : elements) {
                JsonObject obj = element.getAsJsonObject();
                if (!obj.has("name")) continue;
                String name = obj.get("name").getAsString().toLowerCase(Locale.ROOT);
                if (!UNR.matcher(name).matches()) continue;
                VoxelShape s = readCuboid(obj);
                if (s != null) shapes.add(s);
            }
        }
        VoxelShape out = shapes.isEmpty() ? VoxelShapes.empty() : combine(shapes);
        UNR_CACHE.put(key, out);
        return out;
    }

    private static VoxelShape combine(List<VoxelShape> list) {
        if (list.isEmpty()) return VoxelShapes.empty();
        VoxelShape out = list.get(0);
        for (int i = 1; i < list.size(); i++) out = VoxelShapes.union(out, list.get(i));
        return out.simplify();
    }

    private static JsonArray readElements(String namespace, String path) {
        Identifier id = Identifier.of(namespace, "models/block/" + path + ".json");
        String resPath = "assets/" + id.getNamespace() + "/" + id.getPath();
        InputStream stream = tryOpen(resPath);
        if (stream == null) return null;
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            if (!json.has("elements")) return null;
            return json.getAsJsonArray("elements");
        } catch (Exception e) {
            return null;
        }
    }

    private static InputStream tryOpen(String resourcePath) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        InputStream in = cl != null ? cl.getResourceAsStream(resourcePath) : null;
        if (in != null) return in;
        return CollisionShapeHelper.class.getClassLoader().getResourceAsStream(resourcePath);
    }

    private static VoxelShape readCuboid(JsonObject element) {
        if (!element.has("from") || !element.has("to")) return null;
        JsonArray from = element.getAsJsonArray("from");
        JsonArray to = element.getAsJsonArray("to");
        if (from.size() != 3 || to.size() != 3) return null;
        double x1 = clamp16(from.get(0).getAsDouble());
        double y1 = clamp16(from.get(1).getAsDouble());
        double z1 = clamp16(from.get(2).getAsDouble());
        double x2 = clamp16(to.get(0).getAsDouble());
        double y2 = clamp16(to.get(1).getAsDouble());
        double z2 = clamp16(to.get(2).getAsDouble());
        return Block.createCuboidShape(x1, y1, z1, x2, y2, z2);
    }

    private static double clamp16(double v) {
        return MathHelper.clamp(v, 0.0, 16.0);
    }
}
