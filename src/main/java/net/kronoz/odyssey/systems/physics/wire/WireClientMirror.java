package net.kronoz.odyssey.systems.physics.wire;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public final class WireClientMirror {
    private static long lastPollMs = 0L;
    private static int lastHash = 0;

    public static void init(){
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            long now = System.currentTimeMillis();
            if (now - lastPollMs < 1000L) return;
            lastPollMs = now;
            pumpFromIntegratedServer();
        });
    }

    private static void pumpFromIntegratedServer(){
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null) return;
        MinecraftServer srv = mc.getServer();
        if (srv == null) return; // not singleplayer

        List<WireRecord> all = new ArrayList<>();
        for (ServerWorld sw : srv.getWorlds()) {
            WireStorage st = WireStorage.get(sw);
            all.addAll(st.all());
        }

        int h = hashRecords(all);
        if (h == lastHash) return;
        lastHash = h;

        WireManager.clearAllClient();
        for (WireRecord r : all) {
            WireManager.ensureFromRecordClient(r);
        }
    }

    private static int hashRecords(List<WireRecord> list){
        int h=1;
        for (WireRecord r : list){
            h = 31*h + r.id.hashCode();
            h = 31*h + r.defId.hashCode();
            h = 31*h + r.a.pos.hashCode();
            h = 31*h + r.b.pos.hashCode();
        }
        return h;
    }
}