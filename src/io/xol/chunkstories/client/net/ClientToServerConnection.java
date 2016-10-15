package io.xol.chunkstories.client.net;

import io.xol.chunkstories.api.entity.Entity;
import io.xol.chunkstories.api.net.Packet;
import io.xol.chunkstories.api.net.RemoteServer;
import io.xol.chunkstories.net.SendQueue;
import io.xol.chunkstories.net.packets.PacketText;
import io.xol.chunkstories.net.packets.PacketsProcessor;
import io.xol.chunkstories.tools.ChunkStoriesLogger;
import io.xol.engine.concurrency.Fence;
import io.xol.engine.concurrency.SimpleFence;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

//(c) 2015-2016 XolioWare Interactive
// http://chunkstories.xyz
// http://xol.io

public class ClientToServerConnection extends Thread implements RemoteServer
{
	//This objects connects to a server
	private String ip = "";
	private int port = 30410;

	//Network stuff
	private Socket socket;
	private PacketsProcessor packetsProcessor;
	private DataInputStream in;
	private SendQueue sendQueue;

	// Status check
	private SimpleFence authFence = new SimpleFence();
	boolean failed = false;
	private String latestErrorMessage = "";
	private String connectionStatus = "Establishing connection...";

	private ClientSideConnectionSequence connectionSequence;
	
	// Receiving buffers
	public List<String> chatReceived = new ArrayList<String>();

	// Code magic here
	boolean die = false;
	boolean closeMethodAlreadyCalled = false;
	
	public ClientToServerConnection(ClientSideConnectionSequence connectionSequence, String ip, int port)
	{
		this.connectionSequence = connectionSequence;
		this.ip = ip;
		this.port = port;
		
		this.packetsProcessor = new PacketsProcessor(this);
		this.setName("Server Connection thread - " + ip);
		
		//Start the connection
		this.start();
		
	}

	// Connect on/off
	public boolean openSocket()
	{
		ChunkStoriesLogger.getInstance().info("Connecting to " + ip + ":" + port + ".");
		try
		{
			//Opens the socket
			socket = new Socket(ip, port);
			in = new DataInputStream(socket.getInputStream());
			//Create the sendQueue thread
			DataOutputStream out = new DataOutputStream(socket.getOutputStream());
			sendQueue = new SendQueue(this, out, packetsProcessor);
			sendQueue.start();
			
			/*
			connectionStatus = "Established, waiting for login token...";
			
			sendTextMessage("info");
			login();*/
			return true;
		}
		catch (Exception e)
		{
			failed = true;
			latestErrorMessage = "Failed to connect to " + ip + ":" + port + ". (" + e.getClass().getName() + ")";
			System.out.println(latestErrorMessage);
			// e.printStackTrace();
			return false;
		}
	}

	// run
	@Override
	public void run()
	{
		openSocket();
		
		while (!die)
		{
			// Just wait for the goddamn packets to come !
			try
			{
				Packet packet = packetsProcessor.getPacket(in, true);
				packet.process(this, in, packetsProcessor);
			}
			catch (Exception e)
			{
				if (!die) // If the thread was killed then there is no point
							// handling the error.
				{
					// close();
					failed = true;
					latestErrorMessage = "Fatal error while handling connection to " + ip + ":" + port + ". (" + e.getClass().getName() + ")";
					System.out.println(latestErrorMessage);
					close();
					e.printStackTrace();
				}
			}
		}
		System.out.println("Letting thread die as it finished it's job.");
	}

	// @SuppressWarnings("deprecation")
	public void close()
	{
		if (closeMethodAlreadyCalled)
			return;
		closeMethodAlreadyCalled = true;
		try
		{
			in.close();
			sendQueue.kill();
			socket.close();
			die = true;
		}
		catch (Exception e)
		{
			System.out.println("Couldn't close connection to " + ip + ":" + port + ". (" + e.getClass().getName() + ")");
			// e.printStackTrace();
		}
	}

	// I/O

	public void handleTextPacket(String msg)
	{
		// System.out.println("m:"+msg); //debug
		if (msg.startsWith("chat/"))
		{
			// System.out.println(msg.substring(5, msg.length()));
			chatReceived.add(msg.substring(5, msg.length()));
		}
		else if (msg.startsWith("world/"))
		{
			System.out.println("Received a message about the world, but no remote world exists as of now...\nFaulty message : \n" + msg.substring(6, msg.length()));
			// Client.word.handleWorldMessage(msg.substring(6, msg.length()));
		}
		else if (msg.startsWith("disconnect/"))
		{
			latestErrorMessage = msg.replace("disconnect/", "");
			failed = true;
			System.out.println("Disconnected by server : "+msg.replace("disconnect/", ""));
			close();
		}
		else if (msg.equals("login/ok"))
		{
			authFence.signal();
		}
	}

	public void sendTextMessage(String msg)
	{
		try
		{
			PacketText packet = new PacketText(true);
			packet.text = msg;
			sendQueue.queue(packet);
		}
		catch (Exception e)
		{
			// close();
			System.out.println("Fatal error while handling connection to " + ip + ":" + port + ". (" + e.getClass().getName() + ")");
			e.printStackTrace();
		}
	}

	public void sendPacket(Packet packet)
	{
		try
		{
			sendQueue.queue(packet);
		}
		catch (Exception e)
		{
			// close();
			System.out.println("Fatal error while handling connection to " + ip + ":" + port + ". (" + e.getClass().getName() + ")");
			e.printStackTrace();
		}
	}
	
	// accessor

	public synchronized String getLastChatMessage()
	{
		if (chatReceived.size() > 0)
		{
			String m = chatReceived.get(0);
			chatReceived.remove(0);
			return m;
		}
		return null;
	}
	
	public boolean hasFailed()
	{
		return failed;
	}

	public String getLatestErrorMessage()
	{
		return this.latestErrorMessage;
	}
	
	@Override
	public long getUUID()
	{
		return -1;
	}

	Set<Entity> controlledEntity = new HashSet<Entity>(1);
	
	@Override
	public Iterator<Entity> getSubscribedToList()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean subscribe(Entity entity)
	{
		assert controlledEntity.size() == 0;
		entity.subscribe(this);
		return controlledEntity.add(entity);
	}

	@Override
	public boolean unsubscribe(Entity entity)
	{
		assert controlledEntity.size() == 1;
		entity.unsubscribe(this);
		return controlledEntity.remove(entity);
	}

	@Override
	public void unsubscribeAll()
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public void pushPacket(Packet packet)
	{
		this.sendQueue.queue(packet);
	}

	@Override
	public boolean isSubscribedTo(Entity entity)
	{
		return controlledEntity.contains(entity);
	}

	public Fence getAuthentificationFence()
	{
		return authFence;
	}

	public PacketsProcessor getPacketsProcessor()
	{
		return packetsProcessor;
	}
}
