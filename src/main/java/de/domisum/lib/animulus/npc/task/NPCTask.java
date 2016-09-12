package de.domisum.lib.animulus.npc.task;

import de.domisum.lib.auxilium.util.java.annotations.APIUsage;
import de.domisum.lib.auxilium.util.java.annotations.DeserializationNoArgsConstructor;

public abstract class NPCTask
{

	// PROPERTIES
	private boolean runSeparately = false;

	// REFERENCES
	protected TaskNPC npc;

	// STATUS
	private boolean canceled;


	// -------
	// CONSTRUCTOR
	// -------
	@DeserializationNoArgsConstructor
	public NPCTask()
	{

	}

	public void initialize(TaskNPC npc)
	{
		this.npc = npc;
	}


	// -------
	// GETTERS
	// -------
	public abstract NPCTaskSlot[] USED_TASK_SLOTS();

	protected boolean isRunSeparately()
	{
		return this.runSeparately;
	}

	boolean isCanceled()
	{
		return this.canceled;
	}


	// -------
	// SETTERS
	// -------
	@APIUsage
	public NPCTask setRunSeparately(boolean runSeparately)
	{
		this.runSeparately = runSeparately;
		return this;
	}


	// -------
	// EXECUTION
	// -------
	protected abstract void onStart();

	/**
	 * @return true if the task is finished, false if not
	 */
	protected abstract boolean onUpdate();

	protected abstract void onCancel();

	@APIUsage
	public void cancel()
	{
		onCancel();
		this.canceled = true;
	}

}
