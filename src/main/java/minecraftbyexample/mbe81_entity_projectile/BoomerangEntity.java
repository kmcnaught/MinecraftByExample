package minecraftbyexample.mbe81_entity_projectile;

import minecraftbyexample.usefultools.NBTtypesMBE;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileHelper;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.network.IPacket;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.particles.IParticleData;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import net.minecraftforge.fml.network.NetworkHooks;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * Created by TGG on 24/06/2020.
 *
 * Heavily based on the vanilla SnowballEntity and ThrowableEntity
 */
public class BoomerangEntity extends Entity implements IEntityAdditionalSpawnData {
  public BoomerangEntity(EntityType<? extends BoomerangEntity> entityType, World world) {
    super(entityType, world);
  }

  /** Create a new BoomerangEntity
   * @param startPosition  the spawn point of the flight path
   * @param apexYaw the yaw angle in degrees (compass direction of the apex relative to the thrower).  0 degrees is south and increases clockwise.
   * @param distanceToApex number of blocks to the apex of the flight path
   * @param maximumSidewaysDeflection maximum sideways deflection from the straight line from thrower to apex
   * @param anticlockwise is the flight path clockwise or anticlockwise
   * @param flightSpeed speed of the flight in blocks per second
   */
  public BoomerangEntity(World world, @Nullable LivingEntity thrower,
                         Vec3d startPosition,
                         float apexYaw, float distanceToApex,
                         float maximumSidewaysDeflection,
                         boolean anticlockwise,
                         float flightSpeed) {
    super(StartupCommon.boomerangEntityType, world);
    this.thrower = thrower;
    if (thrower != null) {
      throwerID = thrower.getUniqueID();
    }
    boomerangFlightPath = new BoomerangFlightPath(startPosition, apexYaw, distanceToApex,
            maximumSidewaysDeflection, anticlockwise, flightSpeed);
  }

  protected void registerData() {
    this.getDataManager().register(IN_FLIGHT, Boolean.TRUE);
  }

    // Is this boomerang in flight?  (true = yes, false = no (it has hit something))
  private static final DataParameter<Boolean> IN_FLIGHT =
          EntityDataManager.createKey(BoomerangEntity.class, DataSerializers.BOOLEAN);

  protected LivingEntity thrower;
  private UUID throwerID = null;

  private BoomerangFlightPath boomerangFlightPath;

  // If you forget to override this method, the default vanilla method will be called.
  // This sends a vanilla spawn packet, which is then silently discarded when it reaches the client.
  //  Your entity will be present on the server and can cause effects, but the client will not have a copy of the entity
  //    and hence it will not render.
  @Nonnull
  @Override
  public IPacket<?> createSpawnPacket() {
    return NetworkHooks.getEntitySpawningPacket(this);
  }

  // We hit something (entity or block).
  @Override
  protected void onImpact(RayTraceResult rayTraceResult) {
    // if we hit an entity, apply an effect to it depending on the emoji mood
    if (rayTraceResult.getType() == RayTraceResult.Type.ENTITY) {
      EntityRayTraceResult entityRayTraceResult = (EntityRayTraceResult)rayTraceResult;
      Entity entity = entityRayTraceResult.getEntity();
      if (entity instanceof LivingEntity) {
        LivingEntity livingEntity = (LivingEntity)entity;
        Optional<EmojiItem.EmojiMood> mood = getMoodFromMyItem();
        if (mood.isPresent()) {
          EffectInstance effect = (mood.get() == EmojiItem.EmojiMood.HAPPY) ?
                  new EffectInstance(Effects.REGENERATION, 100, 1) :
                  new EffectInstance(Effects.POISON, 10, 0);
          livingEntity.addPotionEffect(effect);
        }
      }
    }

    if (!this.world.isRemote) {
      this.world.setEntityState(this, VANILLA_IMPACT_STATUS_ID);  // calls handleStatusUpdate which tells the client to render particles
      this.remove();
    }
  }


  private static final byte VANILLA_IMPACT_STATUS_ID = 3;

  /*   see https://wiki.vg/Entity_statuses
       make a cloud of particles at the impact point
   */
  @Override
  public void handleStatusUpdate(byte statusID) {
    if (statusID == VANILLA_IMPACT_STATUS_ID) {
      IParticleData particleData = this.makeParticle();

      for(int i = 0; i < 8; ++i) {
        this.world.addParticle(particleData, this.getPosX(), this.getPosY(), this.getPosZ(), 0.0D, 0.0D, 0.0D);
      }
    }
  }

  private final String THROWER_NBT = "thrower";
  private final String IN_FLIGHT_NBT = "inflight";
  private final String FLIGHT_PATH_NBT = "flightpath";

  public void writeAdditional(CompoundNBT compound) {
    if (this.throwerID != null) {
      compound.put("thrower", NBTUtil.writeUniqueId(this.throwerID));
    }
    compound.putBoolean(IN_FLIGHT_NBT, this.dataManager.get(IN_FLIGHT));
    compound.put(FLIGHT_PATH_NBT, boomerangFlightPath.serializeNBT());
  }

  /** Read further NBT information to initialise the entity (after loading from disk or transmission from server)
   */
  public void readAdditional(CompoundNBT compound) {
    this.thrower = null;
    if (compound.contains(THROWER_NBT, NBTtypesMBE.COMPOUND_NBT_ID)) {
      this.throwerID = NBTUtil.readUniqueId(compound.getCompound(THROWER_NBT));
    }
    boomerangFlightPath.deserializeNBT(compound);
  }

  /**
   * Who threw this boomerang?
   * @return
   */
  @Nullable
  public LivingEntity getThrower() {
    if ((this.thrower == null || this.thrower.removed)
            && this.throwerID != null
            && this.world instanceof ServerWorld) {
      Entity entity = ((ServerWorld)this.world).getEntityByUuid(this.throwerID);
      if (entity instanceof LivingEntity) {
        this.thrower = (LivingEntity)entity;
      } else {
        this.thrower = null;
      }
    }
    return this.thrower;
  }

  /**
   * Updates the entity motion clientside, called by packets from the server
   */
  @OnlyIn(Dist.CLIENT)
  public void setVelocity(double x, double y, double z) {
    this.setMotion(x, y, z);
    if (this.prevRotationPitch == 0.0F && this.prevRotationYaw == 0.0F) {
      float f = MathHelper.sqrt(x * x + z * z);
      this.rotationYaw = (float)(MathHelper.atan2(x, z) * (double)(180F / (float)Math.PI));
      this.rotationPitch = (float)(MathHelper.atan2(y, (double)f) * (double)(180F / (float)Math.PI));
      this.prevRotationYaw = this.rotationYaw;
      this.prevRotationPitch = this.rotationPitch;
    }

  }


  private Entity ignoreEntity = null;
  private int ignoreEntityTime = 0;
  private int ticksSpentInFlight = 0;

  private static boolean canBeCollidedWith(Entity entityToCheck) {
    return !entityToCheck.isSpectator() && entityToCheck.canBeCollidedWith();
  }

  /**
   * Called to update the entity's position/logic.
   * We are using some unusual logic to create the particular flight path that we want:
   *
   * 1) If the boomerang is in flight, every tick we force the entity position to a defined position on the curve.
   * 2) If the boomerang is not in flight (has hit something and is falling to the ground), then we use vanilla
   *    mechanics i.e. set motion (velocity) and let gravity act on the entity
   *
   *
   *
   */
  public void tick() {
    super.tick();

    // calculate the AxisAlignedBoundingBox which corresponds to movement of the boomerang:
    //   this is the region of space that we have to check to see if we collide with anything

    final float TICKS_PER_SECOND = 20F;
    boolean isInFlight = this.dataManager.get(IN_FLIGHT);
    Vec3d motion;
    if (isInFlight) {
      Vec3d startPosition = this.getPositionVec();
      Vec3d endPosition = boomerangFlightPath.getPosition((ticksSpentInFlight + 1) / TICKS_PER_SECOND);
      motion = endPosition.subtract(startPosition);
    } else {
      motion = this.getMotion();
    }
    AxisAlignedBB aabbCollisionZone = this.getBoundingBox().expand(motion).grow(1.0D);


//    List<Entity> possibleCollisions = this.world.getEntitiesInAABBexcluding(this,
//            axisalignedbb, BoomerangEntity::canBeCollidedWith);
//    for (Entity entity : possibleCollisions) {
//      if (entity == this.ignoreEntity) {
//        ++this.ignoreEntityTime;
//        break;
//      }
//
//      if (this.thrower != null && this.ticksExisted < 2 && this.ignoreEntity == null) {
//        this.ignoreEntity = entity;
//        this.ignoreEntityTime = 3;
//        break;
//      }
//    }

    // Calculate the first object that the boomerang hits (if any)
    RayTraceResult raytraceresult = ProjectileHelper.rayTrace(this, aabbCollisionZone,
            this::canEntityBeCollidedWith, RayTraceContext.BlockMode.OUTLINE, true);

    switch (raytraceresult.getType()) {
      case BLOCK: {
        BlockRayTraceResult blockRayTraceResult = (BlockRayTraceResult)raytraceresult;
        BlockPos blockHitPos = blockRayTraceResult.getPos();
        if (world.getBlockState(blockHitPos).getBlock() == Blocks.NETHER_PORTAL) {
          this.setPortal(blockHitPos);
        } else {
          if (!net.minecraftforge.event.ForgeEventFactory.onProjectileImpact(this, raytraceresult)){
            this.onImpact(raytraceresult);
            isInFlight = false;
          }
        }
        break;
      }
      case ENTITY: {
        if (!net.minecraftforge.event.ForgeEventFactory.onProjectileImpact(this, raytraceresult)){
          this.onImpact(raytraceresult);
          isInFlight = false;
        }
        break;
      }
      case MISS: {
        break;
      }
    }

    // calculate the new yaw and pitch

    if (isInFlight) {
      this.rotationYaw = boomerangFlightPath.getYaw((ticksSpentInFlight + 1) / TICKS_PER_SECOND);
      this.rotationPitch = 0;  // later - rotation  todo
    }

//    this.rotationPitch = (float)(MathHelper.atan2(vec3d.y, (double)horizontalSpeed) * (double)(180F / (float)Math.PI));

    // ensure proper wraparound to avoid jerkiness due to partialTick interpolation

    while (this.rotationPitch - this.prevRotationPitch < -180.0F) {
      this.prevRotationPitch -= 360.0F;
    }
    while(this.rotationPitch - this.prevRotationPitch >= 180.0F) {
      this.prevRotationPitch += 360.0F;
    }

    while(this.rotationYaw - this.prevRotationYaw < -180.0F) {
      this.prevRotationYaw -= 360.0F;
    }
    while(this.rotationYaw - this.prevRotationYaw >= 180.0F) {
      this.prevRotationYaw += 360.0F;
    }

    this.rotationPitch = MathHelper.lerp(0.2F, this.prevRotationPitch, this.rotationPitch);
    this.rotationYaw = MathHelper.lerp(0.2F, this.prevRotationYaw, this.rotationYaw);

    Vec3d newPosition = this.getPositionVec().add(motion);
    float FRICTION_MULTIPLER;

    if (this.isInWater()) {
      for (int i = 0; i < 4; ++i) {
        final float TRAIL_DISTANCE_FACTOR = 0.25F;
        Vec3d distanceBackFromNewPosition = motion.scale(TRAIL_DISTANCE_FACTOR);
        Vec3d bubbleSpawnPosition = newPosition.subtract(distanceBackFromNewPosition);
        this.world.addParticle(ParticleTypes.BUBBLE,
                bubbleSpawnPosition.getX(), bubbleSpawnPosition.getY(), bubbleSpawnPosition.getZ(),
                motion.getX(), motion.getY(), motion.getZ());
      }
      FRICTION_MULTIPLER = 0.8F;
    } else {
      FRICTION_MULTIPLER = 0.99F;
    }
    if (!isInFlight) {
      motion = motion.scale(FRICTION_MULTIPLER);
    }
    if (!this.hasNoGravity()) {
      motion = motion.subtract(0, getGravityVelocity(), 0);
    }
    this.setMotion(motion);

    this.setPosition(newPosition.getX(), newPosition.getY(), newPosition.getZ());
    if (isInFlight) {
      ++ticksSpentInFlight;
    }
  }

  /**
   * Called when the arrow hits a block or an entity
   */
  protected void onHit(RayTraceResult raytraceResultIn) {
    RayTraceResult.Type raytraceresult$type = raytraceResultIn.getType();
    if (raytraceresult$type == RayTraceResult.Type.ENTITY) {
      this.onEntityHit((EntityRayTraceResult)raytraceResultIn);
    } else if (raytraceresult$type == RayTraceResult.Type.BLOCK) {
      BlockRayTraceResult blockraytraceresult = (BlockRayTraceResult)raytraceResultIn;
      BlockState blockstate = this.world.getBlockState(blockraytraceresult.getPos());
      this.inBlockState = blockstate;
      Vec3d vec3d = blockraytraceresult.getHitVec().subtract(this.getPosX(), this.getPosY(), this.getPosZ());
      this.setMotion(vec3d);
      Vec3d vec3d1 = vec3d.normalize().scale((double)0.05F);
      this.setRawPosition(this.getPosX() - vec3d1.x, this.getPosY() - vec3d1.y, this.getPosZ() - vec3d1.z);
      this.playSound(this.getHitGroundSound(), 1.0F, 1.2F / (this.rand.nextFloat() * 0.2F + 0.9F));
      this.inGround = true;
      this.arrowShake = 7;
      this.setIsCritical(false);
      this.setPierceLevel((byte)0);
      this.setHitSound(SoundEvents.ENTITY_ARROW_HIT);
      this.setShotFromCrossbow(false);
      this.func_213870_w();
      blockstate.onProjectileCollision(this.world, blockstate, blockraytraceresult, this);
    }

  }

  /**
   * Gets the amount of gravity to apply to the thrown entity with each tick.
   */
  protected float getGravityVelocity() {
    return 0.03F;
  }


  @Override
  public void writeSpawnData(PacketBuffer buffer) {
    CompoundNBT nbt = boomerangFlightPath.serializeNBT();
    buffer.writeCompoundTag(nbt);
  }

  @Override
  public void readSpawnData(PacketBuffer additionalData) {
    CompoundNBT nbt = additionalData.readCompoundTag();
    boomerangFlightPath.deserializeNBT(nbt);
  }

  private final int INITIAL_NON_COLLISION_TICKS = 2;
  private boolean canEntityBeCollidedWith(Entity entityToTest) {
    return !entityToTest.isSpectator() && entityToTest.canBeCollidedWith()
            && !(entityToTest == this.thrower && this.ticksExisted <= INITIAL_NON_COLLISION_TICKS);
  }
}
