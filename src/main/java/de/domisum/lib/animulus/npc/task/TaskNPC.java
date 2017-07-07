package de.domisum.lib.animulus.npc.task;

import com.mojang.authlib.GameProfile;
import de.domisum.lib.animulus.npc.PhysicsNPC;
import de.domisum.lib.animulus.npc.ai.NPCBrain;
import de.domisum.lib.auxilium.util.java.annotations.APIUsage;
import de.domisum.lib.auxilium.util.java.annotations.DeserializationNoArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

@APIUsage
public class TaskNPC extends PhysicsNPC
{

	// REFERENCES
	@Getter @Setter private NPCBrain brain;

	// TASKS
	@Getter private Set<NPCTask> activeTasks = new HashSet<>();
	@Getter private List<NPCTask> taskQueue = new ArrayList<>();

	private Set<NPCTaskSlot> blockedTaskSlots = new HashSet<>();


	// INIT
	@DeserializationNoArgsConstructor public TaskNPC()
	{

	}

	@APIUsage public TaskNPC(GameProfile gameProfile, Location location)
	{
		super(gameProfile, location);
	}


	// QUEUE
	@APIUsage public void queueTask(NPCTask task)
	{
		task.initialize(this);
		this.taskQueue.add(task);
	}


	// UPDATING
	@Override public void update()
	{
		super.update();

		if(this.brain != null)
			this.brain.update();

		updateActiveTasks();
		tryStartNextTask();
	}

	private void updateActiveTasks()
	{
		Iterator<NPCTask> activeTasksIterator = this.activeTasks.iterator();
		while(activeTasksIterator.hasNext())
		{
			NPCTask task = activeTasksIterator.next();

			// do it in this way to only call the onUpdate method when the task isn't already canceled
			boolean remove = task.isCanceled();
			if(!remove)
				remove = task.onUpdate();

			if(remove)
			{
				activeTasksIterator.remove();
				determineBlockedTaskSlots();
			}
		}
	}

	private void tryStartNextTask()
	{
		if(this.taskQueue.size() == 0)
			return;

		for(NPCTask activeTask : this.activeTasks)
			if(activeTask.isRunSeparately())
				return;

		NPCTask task = this.taskQueue.get(0);
		if(!areTaskSlotsUnblocked(task))
			return;

		if(task.isRunSeparately() && this.activeTasks.size() > 0)
			return;

		startTask(task);
		this.taskQueue.remove(0);
	}


	private void startTask(NPCTask task)
	{
		this.activeTasks.add(task);
		task.onStart();

		determineBlockedTaskSlots();
	}


	// BLOCKING
	private void determineBlockedTaskSlots()
	{
		this.blockedTaskSlots.clear();

		for(NPCTask task : this.activeTasks)
			Collections.addAll(this.blockedTaskSlots, task.USED_TASK_SLOTS());
	}

	private boolean areTaskSlotsUnblocked(NPCTask task)
	{
		for(NPCTaskSlot slot : task.USED_TASK_SLOTS())
			if(this.blockedTaskSlots.contains(slot))
				return false;

		return true;
	}


	// TASKS
	@APIUsage public void onWalkingFail()
	{
		// to be overridden
	}

}
