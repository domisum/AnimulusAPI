package de.domisum.lib.animulus.npc.task.tasks.movement;

import de.domisum.lib.animulus.npc.task.NPCTask;
import de.domisum.lib.animulus.npc.task.NPCTaskSlot;
import de.domisum.lib.auxilium.util.java.annotations.APIUsage;
import org.bukkit.Location;

@APIUsage
public class NPCTaskTeleport extends NPCTask
{

	// CONSTANTS
	private static NPCTaskSlot[] USED_TASK_SLOTS = new NPCTaskSlot[] {NPCTaskSlot.MOVEMENT, NPCTaskSlot.HEAD_ROTATION};

	// PROPERTIES
	private Location target;


	// -------
	// CONSTRUCTOR
	// -------
	@APIUsage public NPCTaskTeleport(Location target)
	{
		super();

		this.target = target;
	}


	// -------
	// GETTERS
	// -------
	@Override public NPCTaskSlot[] USED_TASK_SLOTS()
	{
		return USED_TASK_SLOTS;
	}


	// -------
	// EXECUTION
	// -------
	@Override protected void onStart()
	{

	}

	@Override protected boolean onUpdate()
	{
		npc.teleport(target);

		return true;
	}

	@Override protected void onCancel()
	{

	}

}
