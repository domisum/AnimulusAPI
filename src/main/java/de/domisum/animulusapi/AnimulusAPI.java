package de.domisum.animulusapi;

import de.domisum.animulusapi.npc.NPCManager;
import de.domisum.auxiliumapi.AuxiliumAPI;
import de.domisum.auxiliumapi.util.java.annotations.APIUsage;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

public class AnimulusAPI
{

	// REFERENCES
	private static AnimulusAPI instance;
	private JavaPlugin plugin;

	private static NPCManager npcManager;


	// -------
	// CONSTRUCTOR
	// -------
	private AnimulusAPI(JavaPlugin plugin)
	{
		instance = this;
		this.plugin = plugin;

		onEnable();
	}

	@APIUsage
	public static void enable(JavaPlugin plugin)
	{
		if(instance != null)
			return;

		new AnimulusAPI(plugin);
	}

	@APIUsage
	public static void disable()
	{
		if(instance == null)
			return;

		getInstance().onDisable();
		instance = null;
	}

	private void onEnable()
	{
		AuxiliumAPI.enable(this.plugin);

		npcManager = new NPCManager();

		getLogger().info(this.getClass().getSimpleName()+" has been enabled");
	}

	private void onDisable()
	{
		if(npcManager != null)
			npcManager.terminate();

		getLogger().info(this.getClass().getSimpleName()+" has been disabled");
	}


	// -------
	// GETTERS
	// -------
	public static AnimulusAPI getInstance()
	{
		return instance;
	}

	public JavaPlugin getPlugin()
	{
		return this.plugin;
	}

	public Logger getLogger()
	{
		return getInstance().plugin.getLogger();
	}


	public static NPCManager getNPCManager()
	{
		return npcManager;
	}

}
