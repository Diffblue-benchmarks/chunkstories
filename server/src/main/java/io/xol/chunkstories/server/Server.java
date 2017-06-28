package io.xol.chunkstories.server;

import static org.fusesource.jansi.Ansi.ansi;
import static org.fusesource.jansi.Ansi.Color.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

import io.xol.engine.misc.ConfigFile;
import io.xol.chunkstories.VersionInfo;
import io.xol.chunkstories.api.Content;
import io.xol.chunkstories.api.entity.Entity;
import io.xol.chunkstories.api.player.Player;
import io.xol.chunkstories.api.server.PermissionsManager;
import io.xol.chunkstories.api.server.ServerInterface;
import io.xol.chunkstories.api.util.ChunkStoriesLogger;
import io.xol.chunkstories.api.util.IterableIterator;
import io.xol.chunkstories.content.GameDirectory;
import io.xol.chunkstories.plugin.DefaultPluginManager;
import io.xol.chunkstories.content.GameContentStore;
import io.xol.chunkstories.server.net.ServerAnnouncerThread;
import io.xol.chunkstories.server.net.UserConnection;
import io.xol.chunkstories.server.net.ServerConnectionsManager;
import io.xol.chunkstories.server.propagation.ServerModsProvider;
import io.xol.chunkstories.tools.ChunkStoriesLoggerImplementation;
import io.xol.chunkstories.world.WorldServer;

//(c) 2015-2017 XolioWare Interactive
// http://chunkstories.xyz
// http://xol.io

/**
 * The server class handles and make the link between all server components
 * It also takes care of the command line input as it's the main thread,
 * thought the processing of command lines is handled by ServerConsole.java
 */
public class Server implements Runnable, ServerInterface
{
	
	static Server server;
	
	public static void main(String args[])
	{
		String modsString = null;
		for (String s : args) // Debug arguments
		{
			if (s.contains("--mods"))
			{
				modsString = s.replace("--mods=", "");
			}
			else if (s.contains("--dir"))
			{
				GameDirectory.set(s.replace("--dir=", ""));
			}
			else
			{
				System.out.println("Chunk Stories server arguments : \n" + "-mods=xxx,yyy | -mods=* Tells the game to start with those mods enabled\n"
						+ "-dir=whatever Tells the game not to look for .chunkstories at it's normal location and instead use the argument" + "" + "");

				//Runtime.getRuntime().exit(0);
			}
		}

		server = new Server(modsString);

		server.run();
	}

	private ChunkStoriesLoggerImplementation log = null;
	private ConfigFile serverConfig = new ConfigFile("./config/server.cfg");

	private AtomicBoolean running = new AtomicBoolean(true);
	private long initTimestamp = System.currentTimeMillis() / 1000;

	private WorldServer world;

	private ServerConnectionsManager connectionsManager;
	private DedicatedServerConsole console = new DedicatedServerConsole(this);

	private DefaultPluginManager pluginsManager;
	private PermissionsManager permissionsManager;

	// Sleeper thread to keep servers list updated
	private ServerAnnouncerThread announcer;

	// What mods are required to join this server ?
	private ServerModsProvider modsProvider;

	private GameContentStore gameContent;
	
	public Server(String modsString)
	{
		server = this;
		// Start server services
		try
		{
			//Initialize logs to a file bearing the current date
			Calendar cal = Calendar.getInstance();
			SimpleDateFormat sdf = new SimpleDateFormat("YYYY.MM.dd HH.mm.ss");
			String time = sdf.format(cal.getTime());
			log = new ChunkStoriesLoggerImplementation(this, ChunkStoriesLogger.LogLevel.ALL, ChunkStoriesLogger.LogLevel.ALL, 
					new File(GameDirectory.getGameFolderPath() + "/serverlogs/" + time + ".log"));

			log.info("Starting Chunkstories server " + VersionInfo.version + " network protocol v" + VersionInfo.networkProtocolVersion);

			//Loads the mods/build filesystem
			gameContent = new GameContentStore(this, modsString);
			gameContent.reload();
			
			//TODO why isn't this below ?
			connectionsManager = new ServerConnectionsManager(this);

			modsProvider = new ServerModsProvider(this);
			
			// load users privs
			UsersPrivileges.load();
			pluginsManager = new DefaultServerPluginManager(this);

			// Load the world(s)
			String worldName = serverConfig.getProp("world", "world");
			String worldDir = GameDirectory.getGameFolderPath() + "/worlds/" + worldName;
			if (new File(worldDir).exists())
			{
				world = new WorldServer(this, worldDir);
			}
			else
			{
				serverConfig.save();
				System.out.println("Can't find the world \"" + worldName + "\" in " + worldDir + ". Exiting !");
				Runtime.getRuntime().exit(0);
			}
			
			// Opens socket and starts accepting clients
			connectionsManager.start();
			// Initializes the announcer ( server listings )
			announcer = new ServerAnnouncerThread(this);
			announcer.start();
			
			permissionsManager = new PermissionsManager() {

				@Override
				public boolean hasPermission(Player player, String permissionNode)
				{
					if (UsersPrivileges.isUserAdmin(player.getName()))
						return true;
					return false;
				}
				
			};
			
			// Load plugins
			pluginsManager.reloadPlugins();

			//Finally start logic
			world.startLogic();
		}
		catch (Exception e)
		{ // Exceptions stuff
			log.error("Could not initalize server. Stacktrace below");
			e.printStackTrace();
			System.exit(-1);
		}
	}

	@Override
	//Just a command prompt, the actual server threads run in the background !
	public void run()
	{
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		System.out.print("> ");
		while (running.get())
		{
			try
			{
				// wait until we have data to complete a readLine()
				while (!br.ready() && running.get())
				{
					printTopScreenDebug();

					Thread.sleep(1000L);
				}
				if (!running.get())
					break;
				String unparsedCommandText = br.readLine();
				if (unparsedCommandText == null)
					continue;
				try
				{
					//Parse and fire
					String cmdName = unparsedCommandText.toLowerCase();
					String[] args = {};
					if (unparsedCommandText.contains(" "))
					{
						cmdName = unparsedCommandText.substring(0, unparsedCommandText.indexOf(" "));
						args = unparsedCommandText.substring(unparsedCommandText.indexOf(" ") + 1, unparsedCommandText.length()).split(" ");
					}

					console.dispatchCommand(console, cmdName, args);

					System.out.print("> ");
					System.out.flush();
				}
				catch (Exception e)
				{
					System.out.println("error while handling command :");
					e.printStackTrace();
				}
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
			catch (InterruptedException e)
			{
				System.out.println("ConsoleInputReadTask() cancelled");
				break;
			}

		}
		try
		{
			br.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		closeServer();
		log.save();
	}

	private void printTopScreenDebug()
	{
		String txt = "" + ansi().fg(BLACK).bg(CYAN);

		int ec = 0;
		IterableIterator<Entity> i = world.getAllLoadedEntities();
		while (i.hasNext())
		{
			i.next();
			ec++;
		}

		txt += "Chunk Stories Server " + VersionInfo.version;
		txt += " | world running at " + world.getGameLogic().getSimulationFps() + " Fps";
		txt += " | " + ec + " Entities";
		txt += " | " + this.connectionsManager.getNumberOfAuthentificatedClients() + "/" + this.connectionsManager.getMaxClients() + " players";
		txt += " | " + this.world.getRegionsHolder().getStats() + " + " + this.world.getRegionsSummariesHolder().countSummaries() + " summaries ";
		txt += " | " + this.world.ioHandler.toString();

		txt += ansi().bg(BLACK).fg(WHITE);

		System.out.print(ansi().saveCursorPosition().cursor(0, 0).eraseLine().fg(RED) + txt + ansi().restoreCursorPosition());
		System.out.flush();
	}

	public WorldServer getWorld()
	{
		return world;
	}

	public ConfigFile getServerConfig()
	{
		return serverConfig;
	}

	@Override
	public DefaultPluginManager getPluginManager()
	{
		return pluginsManager;
	}

	private void closeServer()
	{
		// When stopped, close sockets and save config.
		log.info("Killing all connections");
		connectionsManager.closeAll();
		connectionsManager.closeConnectionsManager();

		log.info("Saving map ...");
		world.saveEverything();
		log.info("Shutting down plugins ...");
		pluginsManager.disablePlugins();
		log.info("Done, closing worlds");
		world.ioHandler.shutdown();
		world.destroy();
		log.info("IO done");

		log.info("Saving configuration");
		serverConfig.save();
		UsersPrivileges.save();
		log.info("Good night sweet prince");
		Runtime.getRuntime().exit(0);
	}

	public void stop()
	{
		// When stopped, close sockets and save config.
		announcer.stopAnnouncer();
		running.set(false);
	}

	public boolean isRunning()
	{
		return running.get();
	}

	public void reloadConfig()
	{
		UsersPrivileges.load();
		serverConfig.load();
	}

	@Override
	public IterableIterator<Player> getConnectedPlayers()
	{
		return new IterableIterator<Player>()
		{
			Iterator<UserConnection> authClients = connectionsManager.getAuthentificatedClients();

			@Override
			public boolean hasNext()
			{
				return authClients.hasNext();
			}

			@Override
			public Player next()
			{
				return authClients.next().getProfile();
			}

		};
	}

	@Override
	public String toString()
	{
		return "[ChunkStoriesServer " + VersionInfo.version + "]";
	}

	@Override
	public Player getPlayerByName(String playerName)
	{
		UserConnection clientByThatName = connectionsManager.getAuthentificatedClientByName(playerName);
		if (clientByThatName != null)
			return clientByThatName.getProfile();
		return null;
	}

	@Override
	public Player getPlayerByUUID(long UUID)
	{
		Iterator<Player> i = getConnectedPlayers();
		while (i.hasNext())
		{
			Player p = i.next();
			if (p.getUUID() == UUID)
				return p;
		}
		return null;
	}

	public DedicatedServerConsole getConsole()
	{
		return console;
	}

	public ServerConnectionsManager getHandler()
	{
		return connectionsManager;
	}

	public ChunkStoriesLogger getLogger()
	{
		return log;
	}

	public long getUptime()
	{
		return (System.currentTimeMillis() / 1000 - initTimestamp);
	}

	@Override
	public void broadcastMessage(String message)
	{
		this.connectionsManager.broadcastChatMessage(message);
	}

	/**
	 * Returns the mods provider
	 */
	public ServerModsProvider getModsProvider()
	{
		return modsProvider;
	}

	@Override
	public void print(String message)
	{
		log.info(message);
	}

	@Override
	public Content getContent()
	{
		return gameContent;
	}

	@Override
	public PermissionsManager getPermissionsManager()
	{
		return permissionsManager;
	}

	@Override
	public void installPermissionsManager(PermissionsManager permissionsManager)
	{
		this.permissionsManager = permissionsManager;
	}

	@Override
	public ChunkStoriesLogger logger() {
		return log;
	}
}