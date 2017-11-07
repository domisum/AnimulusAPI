package de.domisum.lib.animulus.npc;

import com.mojang.authlib.GameProfile;
import de.domisum.lib.animulus.AnimulusLib;
import de.domisum.lib.auxilium.util.java.ThreadUtil;
import de.domisum.lib.auxilium.util.java.annotations.API;
import de.domisum.lib.auxilium.util.java.annotations.DeserializationNoArgsConstructor;
import de.domisum.lib.auxiliumspigot.util.LocationUtil;
import de.domisum.lib.auxiliumspigot.util.PacketUtil;
import de.domisum.lib.auxiliumspigot.util.ReflectionUtil;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.server.v1_9_R1.*;
import net.minecraft.server.v1_9_R1.PacketPlayOutEntity.PacketPlayOutEntityLook;
import net.minecraft.server.v1_9_R1.PacketPlayOutEntity.PacketPlayOutRelEntityMoveLook;
import net.minecraft.server.v1_9_R1.PacketPlayOutPlayerInfo.EnumPlayerInfoAction;
import net.minecraft.server.v1_9_R1.PacketPlayOutPlayerInfo.PlayerInfoData;
import net.minecraft.server.v1_9_R1.WorldSettings.EnumGamemode;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_9_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_9_R1.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_9_R1.util.CraftChatMessage;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StateNPC
{

	// CONSTANTS
	private static final double VISIBILITY_RANGE = 60d;
	private static final long TABLIST_REMOVE_DELAY_MS = 4000;
	private static final long RELATIVE_MOVE_TELEPORT_INTERVAL = 10;

	// STATUS
	@Getter @Setter private transient String id;
	@Getter private transient int entityId;
	@API protected transient GameProfile gameProfile;

	protected transient Location location;
	@Getter @Setter private transient ItemStack itemInHand = null;
	@Getter @Setter private transient ItemStack itemInOffHand = null;
	@Getter @Setter private transient ItemStack[] armor = new ItemStack[4]; // 0: boots, 1: leggings, 2: chestplate, 3: helmet

	@Getter @Setter private transient boolean isOnFire = false;
	@Getter @Setter private transient boolean isCrouched = false;
	@Getter @Setter private transient boolean isSprinting = false;
	@Getter @Setter private transient boolean isEating = false;
	@Getter @Setter private transient boolean isDrinking = false;
	@Getter @Setter private transient boolean isBlocking = false;
	@Getter @Setter private transient boolean isGlowing = false;

	@Getter @Setter private transient int numberOfArrowsInBody = 0;

	// PLAYERS
	private transient Set<Player> visibleTo = new HashSet<>();

	// TEMP
	private transient int moveTeleportCounter = 0;


	// INIT
	@DeserializationNoArgsConstructor public StateNPC()
	{

	}

	protected StateNPC(GameProfile gameProfile, Location location)
	{
		this.gameProfile = gameProfile;
		this.location = location.clone();

		initialize();
	}

	@API protected void initialize()
	{
		this.entityId = getUnusedEntityId();
	}

	protected void terminate()
	{
		sendRemoveToPlayer(getPlayersVisibleToArray());
		this.visibleTo.clear();
	}

	@API public void despawn()
	{
		AnimulusLib.getNPCManager().removeNPC(this);
		// terminate(); the method above already calls terminate
	}


	// GETTERS
	@API public Location getLocation()
	{
		return this.location.clone();
	}

	@API public double getEyeHeight()
	{
		if(this.isCrouched)
			return 1.54;

		return 1.62;
	}

	@API public Location getEyeLocation()
	{
		return this.location.clone().add(0, getEyeHeight(), 0);
	}


	@API public Set<Player> getPlayersVisibleTo()
	{
		return new HashSet<>(this.visibleTo);
	}

	@API public boolean isVisibleTo(Player player)
	{
		return this.visibleTo.contains(player);
	}

	@API public boolean isVisibleToSomebody()
	{
		return !this.visibleTo.isEmpty();
	}

	@API public Player[] getPlayersVisibleToArray()
	{
		return this.visibleTo.toArray(new Player[this.visibleTo.size()]);
	}


	@API public boolean isHandActive()
	{
		return isEating() || isDrinking() || isBlocking();
	}

	private DataWatcher getMetadata()
	{
		DataWatcher metadata = new DataWatcher(null);
		// http://wiki.vg/Entities#Entity_Metadata_Format

		// base information
		byte metadataBaseInfo = 0;
		if(this.isOnFire)
			metadataBaseInfo |= 0x01;
		if(this.isCrouched)
			metadataBaseInfo |= 0x02;
		if(this.isSprinting)
			metadataBaseInfo |= 0x08;
		// this seems to be unused now, using the byte with key 5
		/*if(this.isEating || this.isDrinking || this.isBlocking)
			metadataBaseInfo |= 0x10;*/
		if(this.isGlowing)
			metadataBaseInfo |= 0x40;
		/*if(this.isFlyingWithElytra)
			metadataBaseInfo |= 0x80;*/
		metadata.register(new DataWatcherObject<>(0, DataWatcherRegistry.a), metadataBaseInfo);

		// hand active
		metadata.register(new DataWatcherObject<>(5, DataWatcherRegistry.a), (byte) (isHandActive() ? 1 : 0));

		// arrows in body
		metadata.register(new DataWatcherObject<>(9, DataWatcherRegistry.b), this.numberOfArrowsInBody);
		// !!! seems to have changed to 10 in v1.9/v1.10

		// skin parts
		byte skinParts = 0b01111111; // all parts displayed (first bit is unused)
		metadata.register(new DataWatcherObject<>(12, DataWatcherRegistry.a), skinParts);

		// TEST VALUES
		/*metadata.register(new DataWatcherObject<>(1, DataWatcherRegistry.b), 300);
		metadata.register(new DataWatcherObject<>(2, DataWatcherRegistry.d), "");
		metadata.register(new DataWatcherObject<>(3, DataWatcherRegistry.h), false);
		metadata.register(new DataWatcherObject<>(4, DataWatcherRegistry.h), false);
		metadata.register(new DataWatcherObject<>(6, DataWatcherRegistry.c), 20.0f);
		metadata.register(new DataWatcherObject<>(7, DataWatcherRegistry.b), 0);
		metadata.register(new DataWatcherObject<>(8, DataWatcherRegistry.h), false);
		//metadata.register(new DataWatcherObject<>(9, DataWatcherRegistry.a), 0);
		metadata.register(new DataWatcherObject<>(10, DataWatcherRegistry.c), 0.0f);
		metadata.register(new DataWatcherObject<>(11, DataWatcherRegistry.b), 13);
		//metadata.register(new DataWatcherObject<>(12, DataWatcherRegistry.a), 127);
		metadata.register(new DataWatcherObject<>(13, DataWatcherRegistry.a), (byte) 1);*/

		return metadata;
	}


	// SETTERS
	@API public void setArmor(int slot, ItemStack itemStack)
	{
		this.armor[slot] = itemStack;

		sendEntityEquipmentChange(getArmorItemSlot(slot), itemStack, getPlayersVisibleToArray());
	}


	// PLAYERS
	@API protected void updateVisibleForPlayers()
	{
		for(Player p : Bukkit.getOnlinePlayers())
			updateVisibilityForPlayer(p);
	}

	@API protected void updateVisibilityForPlayer(Player player)
	{
		boolean sameWorld = player.getWorld() == this.location.getWorld();
		if(sameWorld && player.getLocation().distanceSquared(getLocation()) < (VISIBILITY_RANGE*VISIBILITY_RANGE))
		{
			if(!isVisibleTo(player))
				becomeVisibleFor(player);
		}
		else
		{
			if(isVisibleTo(player))
				becomeInvisibleFor(player, true);
		}
	}

	@API public void becomeVisibleFor(Player player)
	{
		this.visibleTo.add(player);
		sendToPlayer(player);
	}

	@API public void becomeInvisibleFor(Player player, boolean sendPackets)
	{
		this.visibleTo.remove(player);

		if(sendPackets)
			sendRemoveToPlayer(player);
	}


	// INTERACTION
	@API public void playerLeftClick(Player player)
	{
		// to be overridden
	}

	@API public void playerRightClick(Player player)
	{
		// to be overridden
	}


	// ACTION
	@API public void swingArm()
	{
		sendAnimation(0, getPlayersVisibleToArray());
	}

	@API public void swingOffhandArm()
	{
		sendAnimation(3, getPlayersVisibleToArray());
	}


	// MOVEMENT
	@API public void moveToNearby(Location target)
	{
		moveToNearby(target, false);
	}

	@API public void moveToNearby(Location target, boolean forceExact)
	{
		if(target.equals(this.location))
			return;

		if((this.moveTeleportCounter++%RELATIVE_MOVE_TELEPORT_INTERVAL) == 0 || forceExact)
		{
			this.location = target;
			sendTeleport(getPlayersVisibleToArray());
			sendLookHeadRotation(getPlayersVisibleToArray());
			return;
		}

		sendRelativeMoveLook(target, getPlayersVisibleToArray());
		this.location = target;
		sendHeadRotation(getPlayersVisibleToArray());
	}

	@API public void teleport(Location target)
	{
		this.location = target;

		// this sends despawn packets if the npc is too far away now
		updateVisibleForPlayers();

		sendTeleport(getPlayersVisibleToArray());
		sendLookHeadRotation(getPlayersVisibleToArray());
	}


	public void setYawPitch(float yaw, float pitch)
	{
		this.location.setYaw(yaw%360);
		this.location.setPitch(pitch%360);

		sendLookHeadRotation(getPlayersVisibleToArray());
	}

	@API public void lookAt(Location lookAt)
	{
		this.location = LocationUtil.lookAt(this.location, lookAt);
		sendLookHeadRotation(getPlayersVisibleToArray());
	}


	@API public boolean canStandAt(Location location)
	{
		if(location.getBlock().getType().isSolid())
			return false;

		if(location.clone().add(0, 1, 0).getBlock().getType().isSolid())
			return false;

		return location.clone().add(0, -1, 0).getBlock().getType().isSolid();

	}


	// UPDATING
	@API protected void update()
	{
		// to be overridden
	}


	// PACKETS
	private void sendToPlayer(Player... players)
	{
		sendPlayerInfo(players);
		sendEntitySpawn(players);
		sendHeadRotation(players);

		sendEntityMetadata(players);
		sendEntityEquipment(players);

		sendPlayerInfoRemove(players);
	}

	private void sendEntityEquipment(Player... players)
	{
		for(int slot = 0; slot < 4; slot++)
			sendEntityEquipmentChange(getArmorItemSlot(slot), this.armor[slot], players);

		sendEntityEquipmentChange(EnumItemSlot.MAINHAND, this.itemInHand, players);
		sendEntityEquipmentChange(EnumItemSlot.OFFHAND, this.itemInOffHand, players);
	}


	private void sendRemoveToPlayer(Player... players)
	{
		sendEntityDespawn(players);
	}


	// player info
	private PlayerInfoData getPlayerInfoData(PacketPlayOutPlayerInfo packetPlayOutPlayerInfo)
	{
		return packetPlayOutPlayerInfo.new PlayerInfoData(this.gameProfile, 0, EnumGamemode.NOT_SET,
				CraftChatMessage.fromString("")[0]);
	}

	private void sendPlayerInfo(Player... players)
	{
		PacketPlayOutPlayerInfo packet = new PacketPlayOutPlayerInfo();
		ReflectionUtil.setDeclaredFieldValue(packet, "a", EnumPlayerInfoAction.ADD_PLAYER);
		@SuppressWarnings("unchecked") List<PlayerInfoData> b = (List<PlayerInfoData>) ReflectionUtil
				.getDeclaredFieldValue(packet, "b");
		b.add(getPlayerInfoData(packet));

		for(Player p : players)
			((CraftPlayer) p).getHandle().playerConnection.sendPacket(packet);
	}

	private void sendPlayerInfoRemove(Player... players)
	{
		PacketPlayOutPlayerInfo packet = new PacketPlayOutPlayerInfo();
		ReflectionUtil.setDeclaredFieldValue(packet, "a", EnumPlayerInfoAction.REMOVE_PLAYER);
		@SuppressWarnings("unchecked") List<PlayerInfoData> b = (List<PlayerInfoData>) ReflectionUtil
				.getDeclaredFieldValue(packet, "b");
		b.add(getPlayerInfoData(packet));

		ThreadUtil.runDelayed(()->
		{
			for(Player p : players)
				((CraftPlayer) p).getHandle().playerConnection.sendPacket(packet);
		}, TABLIST_REMOVE_DELAY_MS);
	}


	// entity spawn
	private void sendEntitySpawn(Player... players)
	{
		PacketPlayOutNamedEntitySpawn packet = new PacketPlayOutNamedEntitySpawn();
		ReflectionUtil.setDeclaredFieldValue(packet, "a", this.entityId);
		ReflectionUtil.setDeclaredFieldValue(packet, "b", this.gameProfile.getId());
		ReflectionUtil.setDeclaredFieldValue(packet, "c", this.location.getX());
		ReflectionUtil.setDeclaredFieldValue(packet, "d", this.location.getY());
		ReflectionUtil.setDeclaredFieldValue(packet, "e", this.location.getZ());
		ReflectionUtil.setDeclaredFieldValue(packet, "f", PacketUtil.toPacketAngle(this.location.getYaw()));
		ReflectionUtil.setDeclaredFieldValue(packet, "g", PacketUtil.toPacketAngle(this.location.getPitch()));
		ReflectionUtil.setDeclaredFieldValue(packet, "h", getMetadata());

		for(Player p : players)
			((CraftPlayer) p).getHandle().playerConnection.sendPacket(packet);
	}


	// entity despawn
	private void sendEntityDespawn(Player... players)
	{
		PacketPlayOutEntityDestroy packet = new PacketPlayOutEntityDestroy();
		ReflectionUtil.setDeclaredFieldValue(packet, "a", new int[] {this.entityId});

		for(Player p : players)
			((CraftPlayer) p).getHandle().playerConnection.sendPacket(packet);
	}


	// entity change
	private void sendEntityEquipmentChange(EnumItemSlot slot, ItemStack itemStack, Player... players)
	{
		PacketPlayOutEntityEquipment packet = new PacketPlayOutEntityEquipment(this.entityId, slot,
				CraftItemStack.asNMSCopy(itemStack));

		for(Player p : players)
			((CraftPlayer) p).getHandle().playerConnection.sendPacket(packet);
	}

	private void sendEntityMetadata(Player... players)
	{
		// the boolean determines the metadata sent to the player;
		// true = all metadata
		// false = the dirty metadata
		PacketPlayOutEntityMetadata packet = new PacketPlayOutEntityMetadata(this.entityId, getMetadata(), true);

		for(Player p : players)
			((CraftPlayer) p).getHandle().playerConnection.sendPacket(packet);
	}


	// movement
	private void sendRelativeMoveLook(Location target, Player... players)
	{
		double dX = target.getX()-this.location.getX();
		double dY = target.getY()-this.location.getY();
		double dZ = target.getZ()-this.location.getZ();

		// @formatter:off
        PacketPlayOutRelEntityMoveLook packet = new PacketPlayOutRelEntityMoveLook(
                this.entityId,
                PacketUtil.toPacketDistance(dX),
                PacketUtil.toPacketDistance(dY),
                PacketUtil.toPacketDistance(dZ),
                PacketUtil.toPacketAngle(target.getYaw()),
                PacketUtil.toPacketAngle(target.getPitch()),
                true);
        // @formatter:on

		for(Player p : players)
			((CraftPlayer) p).getHandle().playerConnection.sendPacket(packet);
	}

	@API protected void sendTeleport(Player... players)
	{
		PacketPlayOutEntityTeleport packet = new PacketPlayOutEntityTeleport();
		ReflectionUtil.setDeclaredFieldValue(packet, "a", this.entityId);
		ReflectionUtil.setDeclaredFieldValue(packet, "b", this.location.getX());
		ReflectionUtil.setDeclaredFieldValue(packet, "c", this.location.getY());
		ReflectionUtil.setDeclaredFieldValue(packet, "d", this.location.getZ());
		ReflectionUtil.setDeclaredFieldValue(packet, "e", PacketUtil.toPacketAngle(this.location.getYaw()));
		ReflectionUtil.setDeclaredFieldValue(packet, "f", PacketUtil.toPacketAngle(this.location.getPitch()));
		ReflectionUtil.setDeclaredFieldValue(packet, "g", true);

		for(Player p : players)
			((CraftPlayer) p).getHandle().playerConnection.sendPacket(packet);
	}

	private void sendLookHeadRotation(Player... players)
	{
		sendLook(players);
		sendHeadRotation(players);
	}

	private void sendLook(Player... players)
	{
		PacketPlayOutEntityLook packet = new PacketPlayOutEntityLook(this.entityId,
				PacketUtil.toPacketAngle(this.location.getYaw()), PacketUtil.toPacketAngle(this.location.getPitch()), true);

		for(Player p : players)
			((CraftPlayer) p).getHandle().playerConnection.sendPacket(packet);
	}

	private void sendHeadRotation(Player... players)
	{
		PacketPlayOutEntityHeadRotation packet = new PacketPlayOutEntityHeadRotation();
		ReflectionUtil.setDeclaredFieldValue(packet, "a", this.entityId);
		ReflectionUtil.setDeclaredFieldValue(packet, "b", PacketUtil.toPacketAngle(this.location.getYaw()));

		for(Player p : players)
			((CraftPlayer) p).getHandle().playerConnection.sendPacket(packet);
	}


	// action
	@API protected void sendAnimation(int animationId, Player... players)
	{
		PacketPlayOutAnimation packet = new PacketPlayOutAnimation();
		ReflectionUtil.setDeclaredFieldValue(packet, "a", this.entityId);
		ReflectionUtil.setDeclaredFieldValue(packet, "b", animationId);

		for(Player p : players)
			((CraftPlayer) p).getHandle().playerConnection.sendPacket(packet);
	}


	// UTIL
	private static int getUnusedEntityId()
	{
		// the entityId of a new (net.minecraft.server.)Entity is set like this:
		// this.id = (entityCount++);
		// since the ++ is after the variable, the value of id is set to the current value of entityCount and
		// entity count is increased by one for the next entity
		// this means returning the entityCount as a new entityId is safe, if we increase the variable before returning

		int entityCount = (int) ReflectionUtil.getDeclaredFieldValue(Entity.class, null, "entityCount");
		ReflectionUtil.setDeclaredFieldValue(Entity.class, null, "entityCount", entityCount+1);

		return entityCount;
	}

	private static EnumItemSlot getArmorItemSlot(int slot)
	{
		if(slot == 0)
			return EnumItemSlot.FEET;
		else if(slot == 1)
			return EnumItemSlot.LEGS;
		else if(slot == 2)
			return EnumItemSlot.CHEST;
		else if(slot == 3)
			return EnumItemSlot.HEAD;

		return null;
	}

}
