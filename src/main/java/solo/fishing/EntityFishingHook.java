package solo.fishing;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.entity.projectile.EntityProjectile;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.particle.BubbleParticle;
import cn.nukkit.level.particle.WaterParticle;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.DoubleTag;
import cn.nukkit.nbt.tag.FloatTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.protocol.AddEntityPacket;
import cn.nukkit.network.protocol.EntityEventPacket;

import java.util.Random;

public class EntityFishingHook extends EntityProjectile {

	public static final int NETWORK_ID = 77;

	public static final int WAIT_CHANCE = 100;
	public static final int CHANCE = 40;

	public boolean chance = false;
	public int waitChance = WAIT_CHANCE * 2;
	public boolean attracted = false;
	public int attractTimer = 0;
	public boolean coughted = false;
	public int coughtTimer = 0;

	public Vector3 fish = null;


	public EntityFishingHook(FullChunk chunk, CompoundTag nbt) {
		this(chunk, nbt, null);
	}

	public EntityFishingHook(FullChunk chunk, CompoundTag nbt, Entity shootingEntity) {
		super(chunk, nbt, shootingEntity);
	}

	@Override
	public int getNetworkId() {
		return NETWORK_ID;
	}

	@Override
	public float getWidth() {
		return 0.25f;
	}

	@Override
	public float getLength() {
		return 0.25f;
	}

	@Override
	public float getHeight() {
		return 0.25f;
	}

	@Override
	public float getGravity() {
		return 0.1f;
	}

	@Override
	public float getDrag() {
		return 0.05f;
	}

	public void initEntity() {
		super.initEntity();
	}

	@Override
	public boolean onUpdate(int currentTick) {
		if (this.closed) {
			return false;
		}

		this.timing.startTiming();

		boolean hasUpdate = super.onUpdate(currentTick);

		if (this.isInsideOfWater()) {
			this.motionX = 0;
			this.motionY -= getGravity() * -0.02;
			this.motionZ = 0;
			this.motionChanged = true;
			hasUpdate = true;
		} else if (this.isCollided && this.keepMovement) {
			this.motionX = 0;
			this.motionY = 0;
			this.motionZ = 0;
			this.motionChanged = true;
			this.keepMovement = false;
			hasUpdate = true;
		} else if (this.isOnGround()) {
			this.motionX = 0;
			this.motionY = getGravity();
			this.motionZ = 0;
			hasUpdate = true;
		}


		// FISHING Part
		Random random = new Random();

		if (this.isInsideOfWater()) {
			if (!this.attracted){ // wait fish attract...
				if (this.waitChance > 0) {
					--this.waitChance;
				}
				if (this.waitChance == 0) {
					if (new Random().nextInt(100) < 90) { // if chance success, fish attract
						this.attractTimer = (random.nextInt(40) + 20);
						this.spawnFish();
						this.coughted = false;
						this.attracted = true;
					} else {
						this.waitChance = WAIT_CHANCE;
					}
				}
			} else if (!this.coughted) { // fish attracted
				if (this.attractFish()) {
					this.coughtTimer = (random.nextInt(20) + 30);
					this.fishBites();
					this.coughted = true;
				}
			} else { // fish coughted
				if (this.coughtTimer > 0) {
					--this.coughtTimer;
				}
				if (this.coughtTimer == 0) {
					this.attracted = false;
					this.coughted = false;
					this.waitChance = WAIT_CHANCE * 3;
				}
			}
		}
		this.timing.stopTiming();

		return hasUpdate;
	}

	public int getWaterHeight() {
		for (int y = this.getFloorY(); y < 256; y++) {
			int id = this.level.getBlockIdAt(this.getFloorX(), y, this.getFloorZ());
			if (id == Block.WATER) {
				continue;
			} else if (id == Block.AIR) {
				return y;
			}
		}
		return this.getFloorY();
	}

	public void fishBites() {
		EntityEventPacket pk = new EntityEventPacket();
		pk.eid = this.getId();
		pk.event = EntityEventPacket.FISH_HOOK_HOOK;
		Server.broadcastPacket(this.level.getPlayers().values(), pk);

		EntityEventPacket bubblePk = new EntityEventPacket();
		bubblePk.eid = this.getId();
		bubblePk.event = EntityEventPacket.FISH_HOOK_BUBBLE;
		Server.broadcastPacket(this.level.getPlayers().values(), bubblePk);

		EntityEventPacket teasePk = new EntityEventPacket();
		teasePk.eid = this.getId();
		teasePk.event = EntityEventPacket.FISH_HOOK_TEASE;
		Server.broadcastPacket(this.level.getPlayers().values(), teasePk);

		Random random = new Random();
		for (int i = 0; i < 5; i++) {
			this.level.addParticle(new BubbleParticle(this.setComponents(
					this.x + random.nextDouble() * 0.5 - 0.25,
					this.getWaterHeight(),
					this.z + random.nextDouble() * 0.5 - 0.25
				)));
		}
	}

	public void spawnFish() {
		Random random = new Random();
		this.fish = new Vector3(
				this.x + (random.nextDouble() * 1.2 + 1) * (random.nextBoolean() ? -1 : 1),
				this.getWaterHeight(),
				this.z + (random.nextDouble() * 1.2 + 1) * (random.nextBoolean() ? -1 : 1)
			);
	}

	public boolean attractFish() {
		double multiply = 0.1;
		this.fish.setComponents(
				this.fish.x + (this.x - this.fish.x) * multiply,
				this.fish.y,
				this.fish.z + (this.z - this.fish.z) * multiply
			);
		if (new Random().nextInt(100) < 85) {
			this.level.addParticle(new WaterParticle(this.fish));
		}
		double dist = Math.abs(Math.sqrt(this.x * this.x + this.z * this.z) - Math.sqrt(this.fish.x * this.fish.x + this.fish.z * this.fish.z));
		if (dist < 0.15) {
			return true;
		}
		return false;
	}

	public void reelLine() {
		if (this.shootingEntity instanceof Player && this.coughted) {
			String code = FishSelector.select();
			Item item = FishSelector.getFish(code);
			int experience = FishSelector.getExperience(code);

			Random random = new Random();
			Vector3 motion = this.shootingEntity == null ? new Vector3(0, 0, 0) : new Vector3(this.shootingEntity.x - this.x, this.shootingEntity.y - this.y, this.shootingEntity.z - this.z).multiply(0.08);

			CompoundTag itemTag = NBTIO.putItemHelper(item);
			itemTag.setName("Item");

			EntityItem itemEntity = new EntityItem(
					this.level.getChunk((int) this.x >> 4, (int) this.z >> 4, true),
					new CompoundTag()
							.putList(new ListTag<DoubleTag>("Pos")
									.add(new DoubleTag("", this.getX()))
									.add(new DoubleTag("", this.getWaterHeight()))
									.add(new DoubleTag("", this.getZ())))
							.putList(new ListTag<DoubleTag>("Motion")
									.add(new DoubleTag("", motion.x))
									.add(new DoubleTag("", motion.y))
									.add(new DoubleTag("", motion.z)))
							.putList(new ListTag<FloatTag>("Rotation")
									.add(new FloatTag("", random.nextFloat() * 360))
									.add(new FloatTag("", 0)))
							.putShort("Health", 5).putCompound("Item", itemTag).putShort("PickupDelay", 1));

			if (this.shootingEntity != null && this.shootingEntity instanceof Player) {
				itemEntity.setOwner(this.shootingEntity.getName());
			}
			itemEntity.spawnToAll();

			Player player = (Player) this.shootingEntity;
			if (experience > 0) {
				player.addExperience(experience);
			}
		}
		if (this.shootingEntity instanceof Player) {
			EntityEventPacket pk = new EntityEventPacket();
			pk.eid = this.getId();
			pk.event = EntityEventPacket.FISH_HOOK_TEASE;
			Server.broadcastPacket(this.level.getPlayers().values(), pk);
		}
		if (!this.closed) {
			this.kill();
			this.close();
		}
	}

	@Override
	public void spawnTo(Player player) {
		AddEntityPacket pk = new AddEntityPacket();
		pk.entityRuntimeId = this.getId();
		pk.entityUniqueId = this.getId();
		pk.type = NETWORK_ID;
		pk.x = (float) this.x;
		pk.y = (float) this.y;
		pk.z = (float) this.z;
		pk.speedX = (float) this.motionX;
		pk.speedY = (float) this.motionY;
		pk.speedZ = (float) this.motionZ;
		pk.yaw = (float) this.yaw;
		pk.pitch = (float) this.pitch;

		long ownerId = -1;
		if (this.shootingEntity != null) {
			ownerId = this.shootingEntity.getId();
			if (this.shootingEntity.getId() == player.getId()) {
				ownerId = 0;
			}
		}
		pk.metadata = this.dataProperties.putLong(DATA_OWNER_EID, ownerId);
		player.dataPacket(pk);
		super.spawnTo(player);
	}
}
