package net.kronoz.odyssey.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.Block;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class CollisionShapeHelper {
    private static final Pattern UNR = Pattern.compile("col+ision_\\d+");
    private static final Pattern DIR = Pattern.compile("col+ision_[neswud]\\d+");

    private static final Map<String, VoxelShape> UNR_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Map<Direction, VoxelShape>> DIR_CACHE = new ConcurrentHashMap<>();

    private CollisionShapeHelper() {}

    /* =========================
       PUBLIC API (BACK-COMPAT)
       ========================= */

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
        UNR_CACHE.put(key, out.simplify());
        return out;
    }

    public static VoxelShape loadUnrotatedCollisionPart(String namespace, String path, String partName) {
        JsonArray elements = readElements(namespace, path);
        if (elements == null) return VoxelShapes.empty();
        String want = partName.toLowerCase(Locale.ROOT);
        List<VoxelShape> parts = new ArrayList<>();
        for (JsonElement el : elements) {
            JsonObject obj = el.getAsJsonObject();
            if (!obj.has("name")) continue;
            String name = obj.get("name").getAsString().toLowerCase(Locale.ROOT);
            if (!name.equals(want)) continue;
            VoxelShape s = readCuboid(obj);
            if (s != null) parts.add(s);
        }
        return parts.isEmpty() ? VoxelShapes.empty() : combine(parts).simplify();
    }

    /* =========================
       NEW: DIRECTIONAL API
       ========================= */


    public static Map<Direction, VoxelShape> loadDirectionalCollisionFromModelJson(String namespace, String path) {
        String key = namespace + ":" + path;
        Map<Direction, VoxelShape> cached = DIR_CACHE.get(key);
        if (cached != null) return cached;

        JsonArray elements = readElements(namespace, path);
        Map<Direction, List<VoxelShape>> buckets = new EnumMap<>(Direction.class);
        for (Direction d : Direction.values()) buckets.put(d, new ArrayList<>());

        boolean foundDirectional = false;
        if (elements != null) {
            for (JsonElement element : elements) {
                JsonObject obj = element.getAsJsonObject();
                if (!obj.has("name")) continue;
                String raw = obj.get("name").getAsString().toLowerCase(Locale.ROOT);
                if (!DIR.matcher(raw).matches()) continue;

                char c = raw.charAt(raw.indexOf('_') + 1);
                Direction dir = switch (c) {
                    case 'n' -> Direction.NORTH;
                    case 'e' -> Direction.EAST;
                    case 's' -> Direction.SOUTH;
                    case 'w' -> Direction.WEST;
                    case 'u' -> Direction.UP;
                    case 'd' -> Direction.DOWN;
                    default -> null;
                };
                if (dir == null) continue;

                VoxelShape s = readCuboid(obj);
                if (s != null) {
                    buckets.get(dir).add(s);
                    foundDirectional = true;
                }
            }
        }

        Map<Direction, VoxelShape> result = new EnumMap<>(Direction.class);
        if (foundDirectional) {
            for (Direction d : Direction.values()) {
                List<VoxelShape> list = buckets.get(d);
                result.put(d, list.isEmpty() ? VoxelShapes.empty() : combine(list).simplify());
            }
        } else {
            VoxelShape north = loadUnrotatedCollisionFromModelJson(namespace, path);
            result.put(Direction.NORTH, north);
            result.put(Direction.EAST, rotateY(north, 1));
            result.put(Direction.SOUTH, rotateY(north, 2));
            result.put(Direction.WEST, rotateY(north, 3));
            result.put(Direction.UP, rotateX(north, 1));
            result.put(Direction.DOWN, rotateX(north, 3));
        }

        EnumMap<Direction, VoxelShape> simplified = new EnumMap<>(Direction.class);
        for (var e : result.entrySet()) simplified.put(e.getKey(), e.getValue().simplify());
        DIR_CACHE.put(key, simplified);
        return simplified;
    }

    public static VoxelShape getDirectionalShape(String namespace, String path, Direction facing) {
        return loadDirectionalCollisionFromModelJson(namespace, path).getOrDefault(facing, VoxelShapes.empty());
    }

    /* =========================
       INTERNAL UTILS
       ========================= */

    private static VoxelShape combine(List<VoxelShape> list) {
        if (list.isEmpty()) return VoxelShapes.empty();
        VoxelShape out = list.get(0);
        for (int i = 1; i < list.size(); i++) out = VoxelShapes.union(out, list.get(i));
        return out;
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

    private static VoxelShape rotateY(VoxelShape shape, int quarterTurns) {
        VoxelShape[] buffer = new VoxelShape[]{shape, VoxelShapes.empty()};
        for (int i = 0; i < quarterTurns; i++) {
            buffer[1] = VoxelShapes.empty();
            buffer[0].forEachBox((minX, minY, minZ, maxX, maxY, maxZ) -> {
                double nMinX = 1.0 - maxZ;
                double nMinZ = minX;
                double nMaxX = 1.0 - minZ;
                double nMaxZ = maxX;
                buffer[1] = VoxelShapes.union(buffer[1], VoxelShapes.cuboid(nMinX, minY, nMinZ, nMaxX, maxY, nMaxZ));
            });
            buffer[0] = buffer[1];
        }
        return buffer[0];
    }

    private static VoxelShape rotateX(VoxelShape shape, int quarterTurns) {
        VoxelShape[] buffer = new VoxelShape[]{shape, VoxelShapes.empty()};
        for (int i = 0; i < quarterTurns; i++) {
            buffer[1] = VoxelShapes.empty();
            buffer[0].forEachBox((minX, minY, minZ, maxX, maxY, maxZ) -> {
                double nMinY = 1.0 - maxZ;
                double nMinZ = minY;
                double nMaxY = 1.0 - minZ;
                double nMaxZ = maxY;
                buffer[1] = VoxelShapes.union(buffer[1], VoxelShapes.cuboid(minX, nMinY, nMinZ, maxX, nMaxY, nMaxZ));
            });
            buffer[0] = buffer[1];
        }
        return buffer[0];
    }
}
