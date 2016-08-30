package io.xol.chunkstories.world;

import java.io.File;
import java.util.Iterator;

import io.xol.chunkstories.api.Location;
import io.xol.chunkstories.api.entity.Entity;
import io.xol.chunkstories.api.entity.EntityLiving;
import io.xol.chunkstories.api.server.Player;
import io.xol.chunkstories.api.world.WorldMaster;
import io.xol.chunkstories.core.events.PlayerSpawnEvent;
import io.xol.chunkstories.net.packets.PacketTime;
import io.xol.chunkstories.net.packets.PacketVoxelUpdate;
import io.xol.chunkstories.net.packets.PacketsProcessor.PendingSynchPacket;
import io.xol.chunkstories.server.Server;
import io.xol.chunkstories.server.net.ServerClient;
import io.xol.chunkstories.server.propagation.VirtualServerDecalsManager;
import io.xol.chunkstories.server.propagation.VirtualServerParticlesManager;
import io.xol.chunkstories.server.propagation.VirtualServerSoundManager;
import io.xol.chunkstories.world.io.IOTasksMultiplayerServer;
import io.xol.engine.math.LoopingMathHelper;

//(c) 2015-2016 XolioWare Interactive
//http://chunkstories.xyz
//http://xol.io

public class WorldServer extends WorldImplementation implements WorldMaster, WorldNetworked
{
	private Server server;
	
	private VirtualServerSoundManager virtualServerSoundManager;
	private VirtualServerParticlesManager virtualServerParticlesManager;
	private VirtualServerDecalsManager virtualServerDecalsManager;

	public WorldServer(Server server, String worldDir)
	{
		super(new WorldInfo(new File(worldDir + "/info.txt"), new File(worldDir).getName()));

		this.server = server;
		this.virtualServerSoundManager = new VirtualServerSoundManager(this, server);
		this.virtualServerParticlesManager = new VirtualServerParticlesManager(this, server);
		this.virtualServerDecalsManager = new VirtualServerDecalsManager(this, server);

		ioHandler = new IOTasksMultiplayerServer(this);
		ioHandler.start();
	}

	@Override
	public void tick()
	{
		//Update client tracking
		Iterator<Player> pi = server.getConnectedPlayers();
		while (pi.hasNext())
		{
			Player player = pi.next();

			//System.out.println("client: "+client);
			if (player.hasSpawned())
			{
				//System.out.println(client.getProfile().hasSpawned());
				//Load 8x4x8 chunks arround player
				
				/*Location loc = player.getLocation();
				int chunkX = (int) (loc.getX() / 32f);
				int chunkY = (int) (loc.getY() / 32f);
				int chunkZ = (int) (loc.getZ() / 32f);
				*/
				
				/*for (int cx = chunkX - 4; cx < chunkX + 4; cx++)
					for (int cy = chunkY - 2; cy < chunkY + 2; cy++)
						for (int cz = chunkZ - 4; cz < chunkZ + 4; cz++)
							System.out.println("strangely this does not seem to happen huh..");*/
							//this.getChunkChunkCoordinates(chunkX, chunkY, chunkZ, true);

				//System.out.println("chunk:"+this.getChunk(chunkX, chunkY, chunkZ, true));
				//System.out.println("holder:"+client.getProfile().getControlledEntity().getChunkHolder());
				//Update whatever he controls
				player.updateTrackedEntities();
			}
			PacketTime packetTime = new PacketTime(false);
			packetTime.time = this.worldTime;
			packetTime.overcastFactor = this.getWeather();
			player.pushPacket(packetTime);
		}
		super.tick();
		
		virtualServerSoundManager.update();
	}

	public void handleWorldMessage(ServerClient sender, String message)
	{
		if (message.equals("info"))
		{
			//Sends the construction info for the world, and then the player entity
			worldInfo.sendInfo(sender);

			PlayerSpawnEvent playerSpawnEvent = new PlayerSpawnEvent(sender.getProfile(), this);
			Server.getInstance().getPluginsManager().fireEvent(playerSpawnEvent);

		}
		else if (message.equals("respawn"))
		{
			Player player = sender.getProfile();
			if(player == null)
			{
				sender.sendChat("Fuck off ?");
				return;
			}
			else
			{
				if(player.getControlledEntity() == null || (player.getControlledEntity() instanceof EntityLiving && ((EntityLiving)player.getControlledEntity()).isDead()))
				{
					PlayerSpawnEvent playerSpawnEvent = new PlayerSpawnEvent(sender.getProfile(), this);
					Server.getInstance().getPluginsManager().fireEvent(playerSpawnEvent);
					sender.sendChat("Respawning ...");
				}
				else
					sender.sendChat("You're not dead, or you are controlling a non-living entity.");
			}
		}
		if (message.startsWith("getChunkCompressed"))
		{
			//System.out.println(message);
			String[] split = message.split(":");
			int x = Integer.parseInt(split[1]);
			int y = Integer.parseInt(split[2]);
			int z = Integer.parseInt(split[3]);
			((IOTasksMultiplayerServer) ioHandler).requestCompressedChunkSend(x, y, z, sender);
		}
		if (message.startsWith("getChunkSummary") || message.startsWith("getRegionSummary"))
		{
			String[] split = message.split(":");
			int x = Integer.parseInt(split[1]);
			int z = Integer.parseInt(split[2]);
			((IOTasksMultiplayerServer) ioHandler).requestRegionSummary(x, z, sender);
		}
	}
	
	@Override
	protected int actuallySetsDataAt(int x, int y, int z, int newData, Entity entity)
	{
		newData = super.actuallySetsDataAt(x, y, z, newData, entity);
		if (newData != -1)
		{
			int blocksViewDistance = 256;
			int sizeInBlocks = getWorldInfo().getSize().sizeInChunks * 32;
			PacketVoxelUpdate packet = new PacketVoxelUpdate(false);
			packet.x = x;
			packet.y = y;
			packet.z = z;
			packet.data = newData;
			Iterator<Player> pi = server.getConnectedPlayers();
			while (pi.hasNext())
			{
				Player player = pi.next();

				Entity clientEntity = player.getControlledEntity();
				if (clientEntity == null)
					continue;
				Location loc = clientEntity.getLocation();
				int plocx = (int) loc.getX();
				int plocy = (int) loc.getY();
				int plocz = (int) loc.getZ();
				//TODO use proper configurable values for this
				if (!((LoopingMathHelper.moduloDistance(x, plocx, sizeInBlocks) > blocksViewDistance + 2) || (LoopingMathHelper.moduloDistance(z, plocz, sizeInBlocks) > blocksViewDistance + 2) || (y - plocy) > 4 * 32))
				{
					player.pushPacket(packet);
				}

			}
		}
		return newData;
	}

	@Override
	public void processIncommingPackets()
	{
		Iterator<ServerClient> clientsIterator = server.getHandler().getAuthentificatedClients();
		while (clientsIterator.hasNext())
		{
			ServerClient client = clientsIterator.next();

			//Get buffered packets from this player
			PendingSynchPacket packet = client.getPacketsProcessor().getPendingSynchPacket();
			while (packet != null)
			{
				packet.process(client, client.getPacketsProcessor());
				packet = client.getPacketsProcessor().getPendingSynchPacket();
			}

		}
	}

	@Override
	public VirtualServerSoundManager getSoundManager()
	{
		return virtualServerSoundManager;
	}

	@Override
	public VirtualServerParticlesManager getParticlesManager()
	{
		return virtualServerParticlesManager;
	}

	@Override
	public VirtualServerDecalsManager getDecalsManager()
	{
		return virtualServerDecalsManager;
	}

	public Iterator<Player> getPlayers()
	{
		return Server.getInstance().getConnectedPlayers();
	}
}
