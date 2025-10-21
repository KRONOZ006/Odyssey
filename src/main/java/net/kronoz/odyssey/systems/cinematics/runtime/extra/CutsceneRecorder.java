// src/main/java/net/kronoz/odyssey/client/cs/CutsceneRecorder.java
package net.kronoz.odyssey.client.cs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class CutsceneRecorder {

    public static final CutsceneRecorder I = new CutsceneRecorder();

    public static final class Origin {
        public double x, y, z;
        public float yaw, pitch;
    }

    public static final class Keyframe {
        // Temps relatif (secondes) pour atteindre CETTE keyframe depuis la précédente
        public double duration = 1.0;

        // Position relative à l’origine (en blocs)
        public double rx, ry, rz;

        // Rotations ABSOLUES (yaw/pitch en degrés) capturées
        public float yaw, pitch;

        // Easing (chaînes libres, ton player les interprétera)
        public String easePos = "linear";
        public String easeRot = "linear";
    }

    //CutsceneRecorder.play = Identifier.of( "odyssey", "cutscene_recorder" );

    public static final class Export {
        public int version = 1;
        public boolean positionRelativeToOrigin = true;
        public boolean rotationAbsolute = true;

        public Origin origin = new Origin();
        public List<Keyframe> keyframes = new ArrayList<>();

        // Defaults (si un KF omet un easing)
        public String defaultEasePos = "linear";
        public String defaultEaseRot = "linear";
    }

    private final MinecraftClient MC = MinecraftClient.getInstance();
    private final Export data = new Export();
    private boolean hasOrigin = false;

    private CutsceneRecorder() {}

    public void setOriginFromPlayer() {
        if (MC.player == null) return;
        var p = MC.player;
        data.origin.x = p.getX();
        data.origin.y = p.getY();
        data.origin.z = p.getZ();
        data.origin.yaw = p.getYaw();
        data.origin.pitch = p.getPitch();
        hasOrigin = true;
    }

    public boolean hasOrigin() { return hasOrigin; }

    public void clear() {
        data.keyframes.clear();
        hasOrigin = false;
    }

    public List<Keyframe> list() {
        return Collections.unmodifiableList(data.keyframes);
    }

    public void addKey(double durationSeconds, String easePos, String easeRot) {
        if (MC.cameraEntity == null || !hasOrigin) return;

        Vec3d cam = MC.cameraEntity.getCameraPosVec(1.0f);
        Keyframe k = new Keyframe();
        k.duration = Math.max(0.0, durationSeconds);

        // position relative à l’origine
        k.rx = cam.x - data.origin.x;
        k.ry = cam.y - data.origin.y;
        k.rz = cam.z - data.origin.z;

        // rotations absolues
        k.yaw = MC.cameraEntity.getYaw();
        k.pitch = MC.cameraEntity.getPitch();

        if (easePos != null && !easePos.isEmpty()) k.easePos = easePos;
        if (easeRot != null && !easeRot.isEmpty()) k.easeRot = easeRot;

        data.keyframes.add(k);
    }

    public Path exportJson(String name) throws IOException {
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("odyssey/cutscenes");
        Files.createDirectories(dir);
        Path file = dir.resolve(name + ".json");

        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();

        String json = gson.toJson(data);
        Files.writeString(file, json);
        return file;
    }

    // ----- PREVIEW (optionnel) -----
    private boolean previewActive = false;
    private int previewTick = 0;
    private int previewTotalTicks = 0;
    private int currentSegment = 0;

    public void startPreview() {
        if (!hasOrigin || data.keyframes.isEmpty() || MC.player == null) return;
        previewActive = true;
        previewTick = 0;
        currentSegment = 0;
        previewTotalTicks = (int) Math.round(totalDurationSeconds() * 20.0);
        // On lock le joueur local (simple mais efficace)
        MC.player.setSprinting(false);
        MC.player.setVelocity(Vec3d.ZERO);
    }

    public boolean isPreviewActive() { return previewActive; }

    private double totalDurationSeconds() {
        double s = 0.0;
        for (Keyframe k : data.keyframes) s += k.duration;
        return s;
    }

    public void tickPreview() {
        if (!previewActive || MC.player == null) return;
        if (currentSegment >= data.keyframes.size()) {
            previewActive = false;
            return;
        }

        // On interpole entre “point de départ” (origin + somme des précédents) et la keyframe courante
        // modèle simple: chaque keyframe est une cible en "duration" secondes, easePos/easeRot libres.
        Keyframe target = data.keyframes.get(currentSegment);

        // calc temps segment
        int segTicks = Math.max(1, (int)Math.round(target.duration * 20.0));
        int segTick = previewTick % segTicks;
        double t = (double) segTick / segTicks;

        // easing POS
        double ep = switch (target.easePos.toLowerCase(Locale.ROOT)) {
            case "easein", "quad_in" -> t*t;
            case "easeout", "quad_out" -> 1 - (1 - t)*(1 - t);
            case "cubic", "cubic_inout" -> { // smoothstep-like
                double x = t; yield x*x*(3 - 2*x);
            }
            case "bounceout" -> { // mini-bounce simple
                double x = t;
                if (x < 4/11.0) yield 121*x*x/16.0;
                else if (x < 8/11.0) yield 363/40.0*x*x - 99/10.0*x + 17/5.0;
                else if (x < 9/10.0) yield 4356/361.0*x*x - 35442/1805.0*x + 16061/1805.0;
                else yield 10.8*x*x - 20.52*x + 10.72;
            }
            default -> t; // linear
        };

        // easing ROT
        double er = switch (target.easeRot.toLowerCase(Locale.ROOT)) {
            case "cubic_inout" -> { double x=t; yield x*x*(3 - 2*x); }
            case "easein" -> t*t;
            case "easeout" -> 1 - (1 - t)*(1 - t);
            default -> t;
        };

        // position de départ = soit origin (ou la position finale précédente)
        Vec3d startPos = getAccumulatedPosUpTo(currentSegment - 1);
        float startYaw = getAccumulatedYawUpTo(currentSegment - 1);
        float startPitch = getAccumulatedPitchUpTo(currentSegment - 1);

        Vec3d endPos = new Vec3d(target.rx, target.ry, target.rz);
        float endYaw = target.yaw;
        float endPitch = target.pitch;

        Vec3d interp = startPos.lerp(endPos, ep);
        float yaw = (float) (startYaw + (endYaw - startYaw) * er);
        float pitch = (float) (startPitch + (endPitch - startPitch) * er);

        // appliquer à la caméra (on téléporte la player camera localement)
        // -> côté client, on peut simplement request une “vue fixe” en forçant yaw/pitch et en gardant la pos
        var camEnt = MC.player;
        camEnt.setYaw(yaw);
        camEnt.setPitch(pitch);
        camEnt.updatePosition(data.origin.x + interp.x, data.origin.y + interp.y, data.origin.z + interp.z);

        // avance
        previewTick++;
        if (segTick >= segTicks - 1) {
            currentSegment++;
        }
        if (previewTick >= previewTotalTicks || currentSegment >= data.keyframes.size()) {
            previewActive = false;
        }
    }

    private Vec3d getAccumulatedPosUpTo(int idxInclusive) {
        if (idxInclusive < 0) return Vec3d.ZERO;
        return new Vec3d(
                data.keyframes.get(idxInclusive).rx,
                data.keyframes.get(idxInclusive).ry,
                data.keyframes.get(idxInclusive).rz
        );
    }
    private float getAccumulatedYawUpTo(int idxInclusive) {
        if (idxInclusive < 0) return data.origin.yaw;
        return data.keyframes.get(idxInclusive).yaw;
    }
    private float getAccumulatedPitchUpTo(int idxInclusive) {
        if (idxInclusive < 0) return data.origin.pitch;
        return data.keyframes.get(idxInclusive).pitch;
    }
}
