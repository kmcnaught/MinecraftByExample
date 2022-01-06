package minecraftbyexample.mbe81_entity_projectile;

import net.minecraft.block.Block;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.monster.BlazeEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.projectile.ProjectileItemEntity;
import net.minecraft.item.Foods;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.IPacket;
import net.minecraft.particles.IParticleData;
import net.minecraft.particles.ItemParticleData;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.network.NetworkHooks;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Created by TGG on 24/06/2020.
 *
 * Heavily based on the vanilla SnowballEntity
 */
public class EmojiEntity extends ProjectileItemEntity {

	public EmojiEntity(EntityType<? extends EmojiEntity> entityType, World world) {
		super(entityType, world);
	}

	public EmojiEntity(World world, LivingEntity livingEntity) {
		super(StartupCommon.emojiEntityType, livingEntity, world);
	}

	public EmojiEntity(World world, double x, double y, double z) {
		super(StartupCommon.emojiEntityType, x, y, z, world);
	}

	// If you forget to override this method, the default vanilla method will be
	// called.
	// This sends a vanilla spawn packet, which is then silently discarded when it
	// reaches the client.
	// Your entity will be present on the server and can cause effects, but the
	// client will not have a copy of the entity
	// and hence it will not render.
	@Nonnull
	@Override
	public IPacket<?> createSpawnPacket() {
		return NetworkHooks.getEntitySpawningPacket(this);
	}

	// ProjectileItemEntity::setItem uses this to save storage space. It only stores
	// the itemStack if the itemStack is not
	// the default item.
	@Override
	protected Item getDefaultItem() {
		return StartupCommon.emojiItemHappy;
	}

	// We hit something (entity or block).
	@Override
	protected void onImpact(RayTraceResult rayTraceResult) {

		// if we hit an entity, apply an effect to it depending on the emoji's mood
		if (rayTraceResult.getType() == RayTraceResult.Type.ENTITY) {

			EntityRayTraceResult entityRayTraceResult = (EntityRayTraceResult) rayTraceResult;
			Entity entity = entityRayTraceResult.getEntity();

			// If we hit a living entity (rather than non-living entity like a boat of
			// minecart)
			if (entity instanceof LivingEntity) {

				LivingEntity livingEntity = (LivingEntity) entity; // this casts the "Entity" to a "LivingEntity"
																	// instance

				EmojiItem.EmojiMood mood = getMoodFromMyItem();
				if (mood != null) {

					// Choose an effect to apply
					EffectInstance effect = null;
					if (mood == EmojiItem.EmojiMood.HAPPY) {						
						effect = new EffectInstance(Effects.POISON, 100, 1);
					} else {
						System.out.println("Grumpy!");
						effect = new EffectInstance(Effects.REGENERATION, 100, 1);
					}					
					
					// Add the effect to the entity we hit
					livingEntity.addPotionEffect(effect);
				}				

				/* // UNCOMMENT THIS BLOCK LATER
				// Make the entity jump! 
				if (livingEntity instanceof PigEntity) {
					double x = Math.random();
					double y = 1.0;
					double z = Math.random();
					livingEntity.addVelocity(x, y, z);
					livingEntity.setGlowing(true);
					livingEntity.setNoGravity(true);
					// livingEntity.setFire(10);
				}
				*/
			}
		}

		if (!this.world.isRemote) {
			this.world.setEntityState(this, VANILLA_IMPACT_STATUS_ID); // calls handleStatusUpdate which tells the
																		// client to render particles
			this.remove();
		}
	}

	// not needed here, but can be useful as a breakpoint location to check whether
	// the entity was spawned properly, and to debug the behaviour / lifetime
	@Override
	public void tick() {
		super.tick();
	}

	private static final byte VANILLA_IMPACT_STATUS_ID = 3;

	/*
	 * see https://wiki.vg/Entity_statuses make a cloud of particles at the impact
	 * point
	 */
	@Override
	public void handleStatusUpdate(byte statusID) {
		if (statusID == VANILLA_IMPACT_STATUS_ID) {
			IParticleData particleData = this.makeParticle();

			for (int i = 0; i < 8; ++i) {
				this.world.addParticle(particleData, this.getPosX(), this.getPosY(), this.getPosZ(), 0.0D, 0.0D, 0.0D);
			}
		}
	}

	private IParticleData makeParticle() {
		EmojiItem.EmojiMood mood = getMoodFromMyItem();
		if (mood == null) {
			return ParticleTypes.SNEEZE;
		} else {
			return (mood == EmojiItem.EmojiMood.HAPPY) ? ParticleTypes.HEART : ParticleTypes.ANGRY_VILLAGER;
		}
	}

	/**
	 * Look at the ItemStack stored by this entity and determine its mood
	 * 
	 * @return The mood, or empty if no mood defined (for some unknown reason...)
	 */
	private EmojiItem.EmojiMood getMoodFromMyItem() {
		EmojiItem.EmojiMood mood = null;
		ItemStack itemStackForThisEntity = this.func_213882_k(); // returns air if the entity is storing the default
																	// item (HAPPY in this case)
		Item item;
		if (itemStackForThisEntity.isEmpty()) {
			item = getDefaultItem();
		} else {
			item = itemStackForThisEntity.getItem();
		}

		if (item instanceof EmojiItem) {
			mood = ((EmojiItem) item).getEmojiMood();

		}
		return mood;
	}
}
