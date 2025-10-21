package net.kronoz.odyssey.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public final class GeoCollisionHelper {
    private static final Map<String, VoxelShape[]> CACHE = new HashMap<>();
    private static final Pattern ACCEPT = Pattern.compile("collision(?:[ _-]?\\d+)?", Pattern.CASE_INSENSITIVE);

    private GeoCollisionHelper() {}

    // geoPath is the path RELATIVE to assets/<ns>/geo/ (example: "block/stasispod")
    public static VoxelShape partShape(String ns, String geoPath, int partIndex, Direction facing) {
        VoxelShape[] parts = load(ns, geoPath);
        if (parts == null) return VoxelShapes.empty();
        VoxelShape base = parts[Math.max(0, Math.min(parts.length - 1, partIndex))];
        if (base == null || base.isEmpty()) return VoxelShapes.empty();
        return rotateY(base, turns(facing));
    }

    private static VoxelShape[] load(String ns, String geoPath) {
        String key = ns + ":" + geoPath;
        if (CACHE.containsKey(key)) return CACHE.get(key);

        Identifier id = Identifier.of(ns, "geo/" + geoPath + ".geo.json");
        String res = "assets/" + id.getNamespace() + "/" + id.getPath();
        InputStream in = tryOpen(res);
        if (in == null) return null;

        JsonObject root;
        try (InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(r).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }

        JsonArray geos = root.getAsJsonArray("minecraft:geometry");
        if (geos == null || geos.isEmpty()) return null;
        JsonObject g0 = geos.get(0).getAsJsonObject();
        JsonArray bones = g0.getAsJsonArray("bones");
        if (bones == null) return null;

        VoxelShape global = VoxelShapes.empty();
        for (JsonElement be : bones) {
            JsonObject bone = be.getAsJsonObject();
            if (!bone.has("name") || !bone.has("cubes")) continue;
            String name = bone.get("name").getAsString();
            if (!ACCEPT.matcher(name).matches()) continue;

            JsonArray cubes = bone.getAsJsonArray("cubes");
            for (JsonElement ce : cubes) {
                JsonObject cube = ce.getAsJsonObject();
                JsonArray origin = cube.getAsJsonArray("origin");
                JsonArray size = cube.getAsJsonArray("size");
                if (origin == null || size == null || origin.size() != 3 || size.size() != 3) continue;

                double ox = origin.get(0).getAsDouble();
                double oy = origin.get(1).getAsDouble();
                double oz = origin.get(2).getAsDouble();
                double sx = size.get(0).getAsDouble();
                double sy = size.get(1).getAsDouble();
                double sz = size.get(2).getAsDouble();

                // convert Bedrock units -> [0,1] block space (Bedrock Y up, Z forward, origin at -8 X)
                double x1 = (ox + 8.0) / 16.0;
                double y1 = oy / 16.0;
                double z1 = (8.0 - (oz + sz)) / 16.0;
                double x2 = (ox + sx + 8.0) / 16.0;
                double y2 = (oy + sy) / 16.0;
                double z2 = (8.0 - oz) / 16.0;

                global = VoxelShapes.union(global, VoxelShapes.cuboid(clamp01(x1), clamp01(y1), clamp01(z1),
                        clamp01(x2), clamp01(y2), clamp01(z2)));
            }
        }

        VoxelShape[] parts = new VoxelShape[45];
        for (int i = 0; i < 45; i++) parts[i] = VoxelShapes.empty();

        // slice into 3x5x3 cells centered on controller: x,z in [-1..1], y in [0..4]
        global.forEachBox((minX, minY, minZ, maxX, maxY, maxZ) -> {
            int bx1 = clampi((int)Math.floor(minX), -1, 1);
            int by1 = clampi((int)Math.floor(minY),  0,  4);
            int bz1 = clampi((int)Math.floor(minZ), -1, 1);
            int bx2 = clampi((int)Math.floor(Math.nextDown(maxX)), -1, 1);
            int by2 = clampi((int)Math.floor(Math.nextDown(maxY)),  0,  4);
            int bz2 = clampi((int)Math.floor(Math.nextDown(maxZ)), -1, 1);

            for (int by = by1; by <= by2; by++) {
                for (int bz = bz1; bz <= bz2; bz++) {
                    for (int bx = bx1; bx <= bx2; bx++) {
                        int idx = by * 9 + (bz + 1) * 3 + (bx + 1);
                        double lx1 = clamp01(minX - bx), ly1 = clamp01(minY - by), lz1 = clamp01(minZ - bz);
                        double lx2 = clamp01(maxX - bx), ly2 = clamp01(maxY - by), lz2 = clamp01(maxZ - bz);
                        parts[idx] = VoxelShapes.union(parts[idx], VoxelShapes.cuboid(lx1, ly1, lz1, lx2, ly2, lz2));
                    }
                }
            }
        });

        for (int i = 0; i < 45; i++) parts[i] = parts[i].simplify();
        CACHE.put(key, parts);
        return parts;
    }

    private static InputStream tryOpen(String rp) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        InputStream in = cl != null ? cl.getResourceAsStream(rp) : null;
        return in != null ? in : GeoCollisionHelper.class.getClassLoader().getResourceAsStream(rp);
    }

    private static double clamp01(double v) { return v < 0 ? 0 : Math.min(v, 1); }
    private static int clampi(int v, int lo, int hi) { return v < lo ? lo : Math.min(v, hi); }
    private static int turns(Direction f) { return switch (f) { case NORTH -> 0; case EAST -> 1; case SOUTH -> 2; case WEST -> 3; default -> 0; }; }

    private static VoxelShape rotateY(VoxelShape s, int q) {
        VoxelShape[] buf = new VoxelShape[]{s, VoxelShapes.empty()};
        for (int i = 0; i < (q & 3); i++) {
            final VoxelShape src = buf[0];
            buf[1] = VoxelShapes.empty();
            src.forEachBox((minX, minY, minZ, maxX, maxY, maxZ) -> {
                double nMinX = 1.0 - maxZ, nMinZ = minX, nMaxX = 1.0 - minZ, nMaxZ = maxX;
                buf[1] = VoxelShapes.union(buf[1], VoxelShapes.cuboid(nMinX, minY, nMinZ, nMaxX, maxY, nMaxZ));
            });
            buf[0] = buf[1];
        }
        return buf[0];
    }
}
