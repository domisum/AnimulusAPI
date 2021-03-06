package de.domisum.lib.animulus.npc;

import com.mojang.authlib.GameProfile;
import de.domisum.lib.auxilium.data.container.math.Vector3D;
import de.domisum.lib.auxilium.util.java.annotations.API;
import de.domisum.lib.auxilium.util.java.annotations.DeserializationNoArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.server.v1_9_R1.AxisAlignedBB;
import net.minecraft.server.v1_9_R1.WorldServer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_9_R1.CraftWorld;

import java.util.List;

@API
public class PhysicsNPC extends StateNPC
{

	// CONSTANTS
	private static final double GRAVITATIONAL_ACCELERATION = -0.08;
	private static final double AABB_XZ_LENGTH = 0.6;
	private static final double AABB_Y_LENGTH = 1.8;

	private static final double HOVER_HEIGHT = 0.001;
	private static final double STEP_HEIGHT = 0.5;
	public static final double CLIMBING_BLOCKS_PER_SECOND = 3;

	// PROPERTIES
	@Getter @Setter private Vector3D velocity = new Vector3D();
	@Getter private boolean onGround = false;
	private int ticksOnGround = 0;

	// REFERENCES
	private AxisAlignedBB baseAABB = new AxisAlignedBB(-AABB_XZ_LENGTH/2d, 0, -AABB_XZ_LENGTH/2d, AABB_XZ_LENGTH/2d,
			AABB_Y_LENGTH, AABB_XZ_LENGTH/2d);


	// CONSTRUCTOR
	@DeserializationNoArgsConstructor public PhysicsNPC()
	{
		super();
	}

	@API public PhysicsNPC(GameProfile gameProfile, Location location)
	{
		super(gameProfile, location);
	}


	// GETTERS
	@API public double getWalkSpeed()
	{
		if(isSprinting())
			return 5.6/20d;

		if(isCrouched())
			return 1.3/20d;

		return 4.0/20d; // normal value: 4.3/20d;
	}


	// UPDATING
	@Override public void update()
	{
		super.update();

		applyPhysics();
	}

	private void applyPhysics()
	{
		applyGravity();

		moveWithCollisions();

		applyDrag();
	}

	private void applyGravity()
	{
		Material materialAtFeet = this.location.getBlock().getType();
		if(materialAtFeet == Material.LADDER || materialAtFeet == Material.VINE)
			return;

		this.velocity = this.velocity.add(0, GRAVITATIONAL_ACCELERATION, 0);

		/*if(materialAtFeet == Material.LADDER || materialAtFeet == Material.VINE)
		{
			double climbingSpeedPerTick = CLIMBING_BLOCKS_PER_SECOND/20d;

			if(this.velocity.y < -climbingSpeedPerTick)
				this.velocity = new Vector3D(this.velocity.x, -climbingSpeedPerTick, this.velocity.z);
		}*/
	}

	private void applyDrag()
	{
		double newVX = this.velocity.x*0.6;
		double newVY = this.velocity.y*0.98;
		double newVZ = this.velocity.z*0.6;

		this.velocity = new Vector3D(newVX, newVY, newVZ);
	}

	private void moveWithCollisions()
	{
		// general collision
		WorldServer nmsWorld = ((CraftWorld) this.location.getWorld()).getHandle();

		// method #c() means offset
		// method #a() means addCoord
		AxisAlignedBB aabb = this.baseAABB.c(this.location.getX(), this.location.getY(), this.location.getZ());
		AxisAlignedBB aabbStartCopy = aabb;
		AxisAlignedBB movedAABB = aabb.a(this.velocity.x, this.velocity.y, this.velocity.z);
		List<AxisAlignedBB> nearbyBlockAABBs = nmsWorld.getCubes(null, movedAABB);


		double mX = this.velocity.x;
		double mY = this.velocity.y;
		double mZ = this.velocity.z;

		// y-collision, method #b() means y-offset
		for(AxisAlignedBB a : nearbyBlockAABBs)
			mY = a.b(aabb, mY);
		aabb = aabb.c(0, mY, 0);

		// x-collision, method #a() means x-offset
		for(AxisAlignedBB a : nearbyBlockAABBs)
			mX = a.a(aabb, mX);
		aabb = aabb.c(mX, 0, 0);

		// z-collision, method #c() means z-offset
		for(AxisAlignedBB a : nearbyBlockAABBs)
			mZ = a.c(aabb, mZ);
		/*aabb = aabb.c(0, 0, mZ);*/


		boolean onGroundBefore = this.onGround;
		this.onGround = mY >= this.velocity.y && this.velocity.y <= 0;
		if(this.onGround)
			this.ticksOnGround++;
		else
			this.ticksOnGround = 0;

		// copy velocity at this point so moving up slabs doesn't accelerate the character upwards
		Vector3D newVelocity = new Vector3D(mX, mY, mZ);

		// moving up stairs and slabs
		boolean verticalCollision = mX != this.velocity.x || mZ != this.velocity.z;
		if((this.onGround || onGroundBefore) && verticalCollision)
		{
			double mXBackup = mX;
			double mYBackup = mY;
			double mZBackup = mZ;

			/* this aabb is used for resetting the bounding box if the step climbing is unsuccessful
			since no more checks on the aabb follow after the step climbing it can be safely ignored and kept in the
			no longer true state */
			/*AxisAlignedBB aabb1 = aabb;*/
			aabb = aabbStartCopy;
			AxisAlignedBB aabb2 = aabb;
			AxisAlignedBB aabb3 = aabb2.a(this.velocity.x, 0, this.velocity.z);
			AxisAlignedBB aabb4 = aabb;

			mY = STEP_HEIGHT;
			List<AxisAlignedBB> stepsNearbyBlockAABBs = nmsWorld.getCubes(null, aabb.a(this.velocity.x, mY, this.velocity.z));


			double s1X = this.velocity.x;
			double s1Y = mY;
			double s1Z = this.velocity.z;

			// y-collision, method #b() means y-offset
			for(AxisAlignedBB a : stepsNearbyBlockAABBs)
				s1Y = a.b(aabb3, s1Y);
			aabb2 = aabb2.c(0, s1Y, 0);

			// x-collision, method #a() means x-offset
			for(AxisAlignedBB a : stepsNearbyBlockAABBs)
				s1X = a.a(aabb2, s1X);
			aabb2 = aabb2.c(s1X, 0, 0);

			// z-collision, method #c() means z-offset
			for(AxisAlignedBB a : stepsNearbyBlockAABBs)
				s1Z = a.c(aabb2, s1Z);
			aabb2 = aabb2.c(0, 0, s1Z);


			double s2X = this.velocity.x;
			double s2Y = mY;
			double s2Z = this.velocity.z;

			// y-collision, method #b() means y-offset
			for(AxisAlignedBB a : stepsNearbyBlockAABBs)
				s2Y = a.b(aabb4, s2Y);
			aabb4 = aabb4.c(0, s2Y, 0);

			// x-collision, method #a() means x-offset
			for(AxisAlignedBB a : stepsNearbyBlockAABBs)
				s2X = a.a(aabb4, s2X);
			aabb4 = aabb4.c(s2X, 0, 0);

			// z-collision, method #c() means z-offset
			for(AxisAlignedBB a : stepsNearbyBlockAABBs)
				s2Z = a.c(aabb4, s2Z);
			aabb4 = aabb4.c(0, 0, s2Z);

			double horizontalDistance1Squared = (s1X*s1X)+(s1Z*s1Z);
			double horizontalDistance2Squared = (s2X*s2X)+(s2Z*s2Z);
			if(horizontalDistance1Squared > horizontalDistance2Squared)
			{
				mX = s1X;
				mY = s1Y; //-s1Y;
				mZ = s1Z;
				aabb = aabb2;
			}
			else
			{
				mX = s2X;
				mY = s2Y; // -s2Y;
				mZ = s2Z;
				aabb = aabb4;
			}

			for(AxisAlignedBB a : stepsNearbyBlockAABBs)
				mY = a.b(aabb, mY);
			/*aabb = aabb.c(0, mY, 0);*/

			double oldHorizontalDistanceSquared = (mXBackup*mXBackup)+(mZBackup*mZBackup);
			double newHorizontalDistanceSquared = (mX*mX)+(mZ*mZ);
			if(oldHorizontalDistanceSquared >= newHorizontalDistanceSquared)
			{
				mX = mXBackup;
				mY = mYBackup;
				mZ = mZBackup;
				/*aabb = aabb1;*/
			}
		}

		if(mY < 0)
			mY += HOVER_HEIGHT;
		Vector3D movementVelocity = new Vector3D(mX, mY, mZ);

		moveToNearby(this.location.clone().add(movementVelocity.x, movementVelocity.y, movementVelocity.z), true);
		this.velocity = newVelocity;
	}


	// PHYSICS INTERACTION
	@API public void jump()
	{
		if(!this.onGround)
			return;

		if(this.ticksOnGround < 10)
			return;

		this.velocity = new Vector3D(this.velocity.x, 0.6, this.velocity.z);
	}

}
