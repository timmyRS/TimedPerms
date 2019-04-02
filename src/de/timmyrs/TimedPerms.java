package de.timmyrs;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class TimedPerms extends JavaPlugin implements CommandExecutor, Listener
{
	static final HashMap<String, Group> groups = new HashMap<>();
	static TimedPerms instance;
	static File playerDataDir;
	private final HashMap<Player, TimedPermsPlayer> players = new HashMap<>();

	static long getCurrentTimeSeconds()
	{
		return System.currentTimeMillis() / 1000L;
	}

	public void onEnable()
	{
		try
		{
			instance = this;
			playerDataDir = new File(getDataFolder(), "playerdata");
			final HashMap<String, HashMap> defaultGroups = new HashMap<>();
			final HashMap<String, Object> defaultGroup1 = new HashMap<>();
			defaultGroup1.put("minutes", 0);
			final ArrayList<String> defaultGroup1Permissions = new ArrayList<>();
			defaultGroup1Permissions.add("timedperms.some.permission");
			defaultGroup1.put("permissions", defaultGroup1Permissions);
			defaultGroups.put("default", defaultGroup1);
			final HashMap<String, Object> defaultGroup2 = new HashMap<>();
			defaultGroup2.put("minutes", 60);
			defaultGroup2.put("message", "Thanks for playing a total of an hour! You now not only have some permission but also some other permission! &aEnjoy!");
			defaultGroup2.put("inherit", "default");
			final ArrayList<String> defaultGroup2Permissions = new ArrayList<>();
			defaultGroup2Permissions.add("timedperms.some.other.permission");
			defaultGroup2.put("permissions", defaultGroup2Permissions);
			defaultGroups.put("60", defaultGroup2);
			getConfig().addDefault("groups", defaultGroups);
			getConfig().options().copyDefaults(true);
			saveConfig();
			synchronized(players)
			{
				for(Player p : getServer().getOnlinePlayers())
				{
					if(!p.isOp())
					{
						players.put(p, new TimedPermsPlayer(p));
					}
				}
			}
			reloadTimedPermsConfig();
			getCommand("timedperms").setExecutor(this);
			getServer().getPluginManager().registerEvents(this, this);
			getServer().getScheduler().scheduleSyncRepeatingTask(this, this::redeterminePlayerPermissions, 400L, 400L);
		}
		catch(IOException e)
		{
			e.printStackTrace();
			getServer().getPluginManager().disablePlugin(this);
		}
	}

	private void reloadTimedPermsConfig()
	{
		reloadConfig();
		synchronized(groups)
		{
			groups.clear();
			for(String groupName : getConfig().getConfigurationSection("groups").getValues(false).keySet())
			{
				groups.put(groupName, new Group(groupName));
			}
		}
	}

	private void redeterminePlayerPermissions()
	{
		synchronized(players)
		{
			for(TimedPermsPlayer p : players.values())
			{
				try
				{
					p.saveTime().determinePermissions();
				}
				catch(IOException e)
				{
					e.printStackTrace();
				}
			}
		}
	}

	@Override
	public void onDisable()
	{
		synchronized(players)
		{
			for(TimedPermsPlayer p : players.values())
			{
				try
				{
					p.saveTime();
				}
				catch(IOException e)
				{
					e.printStackTrace();
				}
			}
		}
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent e) throws IOException
	{
		if(!e.getPlayer().isOp())
		{
			synchronized(players)
			{
				players.put(e.getPlayer(), new TimedPermsPlayer(e.getPlayer()));
			}
		}
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent e) throws IOException
	{
		synchronized(players)
		{
			if(players.containsKey(e.getPlayer()))
			{
				players.get(e.getPlayer()).saveTime();
				players.remove(e.getPlayer());
			}
		}
	}

	@Override
	public boolean onCommand(CommandSender s, Command c, String l, String[] a)
	{
		if(a.length > 0 && a[0].equalsIgnoreCase("reload") && s.hasPermission("timedperms.reload"))
		{
			reloadTimedPermsConfig();
			redeterminePlayerPermissions();
			s.sendMessage("§aReloaded the configuration and player permissions.");
		}
		else if(a.length > 1 && a[0].equalsIgnoreCase("playerinfo") && s.hasPermission("timedperms.playerinfo"))
		{
			final Player p = this.getServer().getPlayer(a[1]);
			if(p != null)
			{
				synchronized(this.players)
				{
					final TimedPermsPlayer tp = this.players.get(p);
					if(tp != null)
					{
						try
						{
							tp.saveTime();
							final File playerConfigFile = tp.getConfigFile();
							final YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerConfigFile);
							s.sendMessage(a[1] + " has played a total of " + Math.round(playerConfig.getLong("t", 0) / 60L) + " minutes.");
						}
						catch(IOException e)
						{
							e.printStackTrace();
						}
					}
					else
					{
						s.sendMessage("§c" + a[1] + " is not accounted for.");
					}
				}
			}
			else
			{
				s.sendMessage("§c" + a[1] + " is not currently online.");
			}
		}
		else
		{
			s.sendMessage("https://github.com/timmyrs/TimedPerms");
		}
		return true;
	}
}

class TimedPermsPlayer
{
	private final Player p;
	private final PermissionAttachment attachment;
	private long joinTime;

	TimedPermsPlayer(Player p) throws IOException
	{
		this.p = p;
		joinTime = TimedPerms.getCurrentTimeSeconds();
		attachment = p.addAttachment(TimedPerms.instance);
		determinePermissions();
	}

	TimedPermsPlayer saveTime() throws IOException
	{
		final File playerConfigFile = getConfigFile();
		final YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerConfigFile);
		long playerTime = playerConfig.getLong("t", 0);
		playerTime += (TimedPerms.getCurrentTimeSeconds() - joinTime);
		joinTime = TimedPerms.getCurrentTimeSeconds();
		playerConfig.set("t", playerTime);
		playerConfig.save(playerConfigFile);
		return this;
	}

	File getConfigFile()
	{
		if(!TimedPerms.playerDataDir.exists() && !TimedPerms.playerDataDir.mkdir())
		{
			throw new RuntimeException("Failed to create " + TimedPerms.playerDataDir.getPath());
		}
		return new File(TimedPerms.playerDataDir, p.getUniqueId().toString().replace("-", "") + ".yml");
	}

	void determinePermissions() throws IOException
	{
		final File playerConfigFile = getConfigFile();
		final YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerConfigFile);
		final long playerMinutes = playerConfig.getLong("t", 0) / 60L;
		final Group playerGroup;
		Group applicableGroup = null;
		synchronized(TimedPerms.groups)
		{
			playerGroup = TimedPerms.groups.get(playerConfig.getString("g", ""));
			for(Group g : TimedPerms.groups.values())
			{
				if(playerMinutes >= g.minutes && (applicableGroup == null || applicableGroup.minutes < g.minutes))
				{
					applicableGroup = g;
				}
			}
		}
		if(applicableGroup != null)
		{
			if(applicableGroup != playerGroup)
			{
				if(playerGroup != null)
				{
					for(String perm : playerGroup.permissions)
					{
						attachment.unsetPermission(perm);
					}
				}
				playerConfig.set("g", applicableGroup.name);
				playerConfig.save(playerConfigFile);
				if(applicableGroup.message != null)
				{
					p.sendMessage(applicableGroup.message.replace("&", "§").replace("§§", "&"));
				}
			}
			for(String perm : applicableGroup.getAllPermissions())
			{
				attachment.setPermission(perm, true);
			}
		}
	}
}

class Group
{
	final String name;
	final long minutes;
	final String message;
	final List<String> permissions;
	private final String inherit;

	Group(String name)
	{
		this.name = name;
		if(TimedPerms.instance.getConfig().contains("groups." + name + ".minutes"))
		{
			minutes = TimedPerms.instance.getConfig().getLong("groups." + name + ".minutes");
		}
		else
		{
			minutes = -1;
		}
		if(TimedPerms.instance.getConfig().contains("groups." + name + ".message"))
		{
			message = TimedPerms.instance.getConfig().getString("groups." + name + ".message");
		}
		else
		{
			message = null;
		}
		if(TimedPerms.instance.getConfig().contains("groups." + name + ".inherit"))
		{
			inherit = TimedPerms.instance.getConfig().getString("groups." + name + ".inherit");
		}
		else
		{
			inherit = null;
		}
		if(TimedPerms.instance.getConfig().contains("groups." + name + ".permissions"))
		{
			//noinspection unchecked
			permissions = (List<String>) TimedPerms.instance.getConfig().getList("groups." + name + ".permissions");
		}
		else
		{
			permissions = new ArrayList<>();
		}
	}

	ArrayList<String> getAllPermissions()
	{
		final ArrayList<String> perms = new ArrayList<>(permissions);
		if(inherit != null)
		{
			final Group inheritGroup;
			synchronized(TimedPerms.groups)
			{
				inheritGroup = TimedPerms.groups.get(inherit);
			}
			if(inheritGroup != null)
			{
				perms.addAll(inheritGroup.getAllPermissions());
			}
		}
		return perms;
	}
}
