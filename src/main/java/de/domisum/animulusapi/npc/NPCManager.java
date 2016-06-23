package de.domisum.animulusapi.npc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import de.domisum.animulusapi.AnimulusAPI;
import de.domisum.animulusapi.listener.NPCInteractPacketListener;
import de.domisum.auxiliumapi.util.java.ThreadUtil;

public class NPCManager implements Listener
{

	// CONSTANTS
	private static final int MS_PER_TICK = 50;
	private static final int NS_PER_TICK = MS_PER_TICK * 1000 * 1000;

	private static final int CHECK_PLAYER_DISTANCE_TICK_INTERVAL = 40;

	// STATUS
	private Thread tickingThread;
	private int tickCount;

	private Map<Integer, StateNPC> npcs = new HashMap<Integer, StateNPC>();
	private List<StateNPC> npcsToRemove = new CopyOnWriteArrayList<StateNPC>();


	// -------
	// CONSTRUCTOR
	// -------
	public NPCManager()
	{
		registerListener();

		startTickingThread();
	}

	private void registerListener()
	{
		new NPCInteractPacketListener();

		JavaPlugin instance = AnimulusAPI.getInstance().getPlugin();
		instance.getServer().getPluginManager().registerEvents(this, instance);
	}

	public void terminate()
	{
		stopTickingThread();

		terminateNPCs();
	}

	private void terminateNPCs()
	{
		for(StateNPC npc : this.npcs.values())
			npc.terminate();

		this.npcs.clear();
	}


	// -------
	// GETTERS
	// -------
	public StateNPC getNPC(int id)
	{
		return this.npcs.get(id);
	}

	public StateNPC getNPC(String id)
	{
		for(StateNPC npc : this.npcs.values())
			if(npc.getId().equals(id))
				return npc;

		return null;
	}


	// -------
	// CHANGERS
	// -------
	public void addNPC(StateNPC npc)
	{
		this.npcs.put(npc.getEntityId(), npc);
	}

	public void removeNPC(StateNPC npc)
	{
		this.npcsToRemove.add(npc);
	}


	// -------
	// TICKING
	// -------
	private void startTickingThread()
	{
		// starting tick to kick off NPCTasks
		for(StateNPC npc : this.npcs.values())
			npc.tick(0);

		Runnable run = () ->
		{
			long startNs = System.nanoTime();

			while(!Thread.currentThread().isInterrupted())
			{
				tick();
				this.tickCount++;

				// calculate it in this complex way so no tick delays accumulate
				long nsSinceLastTickStart = startNs % NS_PER_TICK;
				long sleepNs = NS_PER_TICK - nsSinceLastTickStart;
				ThreadUtil.sleepNs(sleepNs);
			}
		};

		this.tickingThread = new Thread(run);
		this.tickingThread.setName("npcTickingThread");
		this.tickingThread.start();
	}

	private void stopTickingThread()
	{
		this.tickingThread.interrupt();
		ThreadUtil.join(this.tickingThread);
	}


	private void tick()
	{
		for(StateNPC toRemove : this.npcsToRemove)
			this.npcs.values().remove(toRemove);
		this.npcsToRemove.clear();

		for(StateNPC npc : this.npcs.values())
		{
			if((this.tickCount % CHECK_PLAYER_DISTANCE_TICK_INTERVAL) == 0)
				npc.updateVisibleForPlayers();

			if(npc.isVisibleToSomebody())
				npc.tick(this.tickCount);
		}
	}


	// -------
	// EVENTS
	// -------
	@EventHandler
	public void playerJoin(PlayerQuitEvent event)
	{
		for(StateNPC npc : this.npcs.values())
			npc.updateVisibilityForPlayer(event.getPlayer());
	}

	@EventHandler
	public void playerQuit(PlayerQuitEvent event)
	{
		Player player = event.getPlayer();

		for(StateNPC npc : this.npcs.values())
			npc.becomeInvisibleFor(player, false);
	}


	@EventHandler
	public void playerRespawn(PlayerRespawnEvent event)
	{
		Player player = event.getPlayer();
		// this is needed since the world is sent anew when the player respawns
		// delay because this event is called before the respawn and the location is not right

		Runnable run = () ->
		{
			for(StateNPC npc : this.npcs.values())
			{
				npc.becomeInvisibleFor(player, true);
				npc.updateVisibilityForPlayer((player));
			}
		};

		Bukkit.getScheduler().runTaskLater(AnimulusAPI.getInstance().getPlugin(), run, 1);
	}

}
