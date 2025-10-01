package net.kronoz.odyssey.systems.render;

public final class Pose {
    public final float baseX, baseY, baseZ;
    public final float swingX, swingY, swingZ;
    public final float equipX, equipY, equipZ;
    public final float sx, sy, sz;
    public final float rotXdeg, rotYdeg, rotZdeg;

    public Pose(float baseX, float baseY, float baseZ,
                float swingX, float swingY, float swingZ,
                float equipX, float equipY, float equipZ,
                float sx, float sy, float sz,
                float rotXdeg, float rotYdeg, float rotZdeg) {
        this.baseX=baseX; this.baseY=baseY; this.baseZ=baseZ;
        this.swingX=swingX; this.swingY=swingY; this.swingZ=swingZ;
        this.equipX=equipX; this.equipY=equipY; this.equipZ=equipZ;
        this.sx=sx; this.sy=sy; this.sz=sz;
        this.rotXdeg=rotXdeg; this.rotYdeg=rotYdeg; this.rotZdeg=rotZdeg;
    }
}
