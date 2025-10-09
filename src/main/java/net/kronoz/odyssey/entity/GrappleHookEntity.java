package net.kronoz.odyssey.entity;

import net.kronoz.odyssey.systems.grapple.GrappleServerLogic;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class GrappleHookEntity extends ProjectileEntity {
    private static final TagKey<net.minecraft.block.Block> GRAPPLE_TARGETS =
            TagKey.of(RegistryKeys.BLOCK, Identifier.of("odyssey", "grapple_targets"));

    /** Qui a tiré */
    public Entity ownerPlayer;

    /** État d’ancrage */
    public boolean latched = false;
    public Vec3d anchor = Vec3d.ZERO;          // ancre fixe (bloc) OU dernière position suivie si entité
    public int latchedEntityId = -1;           // -1 si bloc, sinon id d’entité
    public Vec3d entityAnchorOffset = Vec3d.ZERO; // offset relatif au centre entité lors de l’impact

    /** Corde */
    public double ropeLength = 6.0;            // longueur actuelle
    public double minRopeLength = 1.5;         // longueur mini atteignable

    /** Tunables physiques */
    private static final double MAX_PULL_PER_TICK = 0.45; // traction max par tick
    private static final double REEL_SPEED_HOLD   = 0.20; // “ré-enroulage” par tick en tenant clic
    private static final double DAMPING           = 0.90; // amortissement
    private static final double MAX_SPEED         = 1.30; // plafond de vitesse du joueur
    private static final double DETACH_IF_ANCHOR_SPEED = 2.0; // si ancre-entity part trop vite → detach

    public GrappleHookEntity(EntityType<? extends ProjectileEntity> type, World world) { super(type, world); }
    public GrappleHookEntity(EntityType<? extends ProjectileEntity> type, World world, Entity ownerPlayer) {
        this(type, world);
        this.ownerPlayer = ownerPlayer;
    }


    @Override
    protected void initDataTracker(DataTracker.Builder builder) {

    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) return;

        if (ownerPlayer == null || !ownerPlayer.isAlive()) {
            this.discard();
            return;
        }

        if (!latched) {
            HitResult hit = ProjectileUtil.getCollision(this, this::canHit);
            if (hit != null && hit.getType() != HitResult.Type.MISS) onCollision(hit);

            if (!latched) {
                this.setPosition(this.getPos().add(this.getVelocity()));
                this.setVelocity(this.getVelocity().multiply(0.99));
                if (this.age > 200) this.discard();
            }
        } else {
            applyRopePhysics();
            if (!ownerPlayer.isAlive() || ownerPlayer.distanceTo(this) > 64) {
                if (ownerPlayer instanceof ServerPlayerEntity sp) GrappleServerLogic.detach(sp);
            }
        }
    }

    @Override
    protected void onCollision(HitResult hit) {
        if (this.getWorld().isClient) return;

        if (hit instanceof BlockHitResult bhr) {
            BlockPos pos = bhr.getBlockPos();
            BlockState state = getWorld().getBlockState(pos);
            if (!state.isIn(GRAPPLE_TARGETS)) return;

            this.latched = true;
            // Ancre légèrement “dans” la face touchée
            this.anchor = Vec3d.ofCenter(pos).add(Vec3d.of(bhr.getSide().getVector()).multiply(0.35));
            this.setVelocity(Vec3d.ZERO);
            this.noClip = true;

            // Longueur de corde initiale = distance actuelle (cap à 6)
            this.ropeLength = Math.min(6.0, ownerPlayer.getPos().distanceTo(anchor));
            this.latchedEntityId = -1;
            this.entityAnchorOffset = Vec3d.ZERO;

            GrappleServerLogic.syncToClient(ownerPlayer, this);
        } else if (hit instanceof EntityHitResult ehr) {
            Entity e = ehr.getEntity();
            if (e == ownerPlayer) return;

            this.latched = true;
            this.latchedEntityId = e.getId();

            // On fige un offset relatif au point d’impact (pour éviter d’accrocher le “centre”)
            Vec3d impact = ehr.getPos();
            this.entityAnchorOffset = impact.subtract(e.getPos());
            this.anchor = impact;

            this.setVelocity(Vec3d.ZERO);
            this.noClip = true;
            this.ropeLength = Math.min(6.0, ownerPlayer.getPos().distanceTo(anchor));

            GrappleServerLogic.syncToClient(ownerPlayer, this);
        }
    }

    @Override
    protected boolean canHit(Entity entity) { return entity.isAlive() && entity != ownerPlayer; }

    private void applyRopePhysics() {
        if (!(getWorld() instanceof ServerWorld sw)) return;
        if (ownerPlayer == null) return;

        // 1) Détermination de la position d’ancre
        Vec3d anchorNow = this.anchor;
        if (latchedEntityId != -1) {
            Entity ent = sw.getEntityById(latchedEntityId);
            if (ent == null || !ent.isAlive()) {
                // entité morte/retirée → detach
                if (ownerPlayer instanceof ServerPlayerEntity sp) GrappleServerLogic.detach(sp);
                return;
            }
            // ancre suit l’entité, mais si elle bouge trop vite on lâche (sinon “vols”)
            Vec3d nextAnchor = ent.getPos().add(entityAnchorOffset);
            if (nextAnchor.distanceTo(anchor) > DETACH_IF_ANCHOR_SPEED) {
                if (ownerPlayer instanceof ServerPlayerEntity sp) GrappleServerLogic.detach(sp);
                return;
            }
            anchorNow = nextAnchor;
            this.anchor = nextAnchor; // on garde la trace
        }

        // 2) Ré-enroulage si joueur tient l’usage de l’item
        boolean reeling = (ownerPlayer instanceof net.minecraft.entity.LivingEntity le) && le.isUsingItem();
        if (reeling) {
            ropeLength = Math.max(minRopeLength, ropeLength - REEL_SPEED_HOLD);
        }

        // 3) Contrainte de corde (correction UNIQUEMENT radiale, pas de boost tangent → pas de “vol”)
        Vec3d pPos = ownerPlayer.getPos();
        Vec3d diff = pPos.subtract(anchorNow);
        double dist = diff.length();
        if (dist > ropeLength) {
            Vec3d dir = diff.normalize();
            double excess = Math.min(dist - ropeLength, MAX_PULL_PER_TICK);

            // Ajouter une vitesse vers l’ancre, plafonnée
            Vec3d add = dir.multiply(-excess * 0.9); // 0.9 = rigidité
            ownerPlayer.addVelocity(add.x, add.y, add.z);
            ownerPlayer.velocityModified = true;

            // Plafond de vitesse global
            Vec3d vel = ownerPlayer.getVelocity();
            double v = vel.length();
            if (v > MAX_SPEED) {
                ownerPlayer.setVelocity(vel.multiply(MAX_SPEED / v));
            }
        }

        // 4) Amortissement léger (stabilise énormément)
        ownerPlayer.setVelocity(ownerPlayer.getVelocity().multiply(DAMPING));
        ownerPlayer.fallDistance = 0f;

        // 5) Position visuelle du hook = ancre (pour la corde)
        this.setPos(anchorNow.x, anchorNow.y, anchorNow.z);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.latched = nbt.getBoolean("latched");
        this.anchor = new Vec3d(nbt.getDouble("ax"), nbt.getDouble("ay"), nbt.getDouble("az"));
        this.ropeLength = nbt.getDouble("len");
        this.latchedEntityId = nbt.getInt("aeid");
        this.entityAnchorOffset = new Vec3d(nbt.getDouble("ox"), nbt.getDouble("oy"), nbt.getDouble("oz"));
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putBoolean("latched", latched);
        nbt.putDouble("ax", anchor.x);
        nbt.putDouble("ay", anchor.y);
        nbt.putDouble("az", anchor.z);
        nbt.putDouble("len", ropeLength);
        nbt.putInt("aeid", latchedEntityId);
        nbt.putDouble("ox", entityAnchorOffset.x);
        nbt.putDouble("oy", entityAnchorOffset.y);
        nbt.putDouble("oz", entityAnchorOffset.z);
    }
}
