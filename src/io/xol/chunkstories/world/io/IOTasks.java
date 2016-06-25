package io.xol.chunkstories.world.io;

import io.xol.chunkstories.tools.ChunkStoriesLogger;
import io.xol.chunkstories.world.WorldImplementation;
import io.xol.chunkstories.world.chunk.ChunkHolder;
import io.xol.chunkstories.world.chunk.CubicChunk;
import io.xol.chunkstories.world.summary.RegionSummary;
import io.xol.engine.concurrency.UniqueQueue;
import io.xol.engine.math.LoopingMathHelper;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

import net.jpountz.lz4.LZ4Exception;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;

//(c) 2015-2016 XolioWare Interactive
// http://chunkstories.xyz
// http://xol.io

/**
 * This thread does I/O work in queue. Extended by IOTaskMultiplayerClient and IOTaskMultiplayerServer for the client/server model.
 */
public class IOTasks extends Thread
{
	protected WorldImplementation world;

	protected UniqueQueue<IOTask> tasks = new UniqueQueue<IOTask>();
	private AtomicBoolean die = new AtomicBoolean();

	protected LZ4Factory factory = LZ4Factory.fastestInstance();
	protected LZ4FastDecompressor decompressor = factory.fastDecompressor();
	
	protected int worldSizeInChunks = 0;
	protected int worldHeightInChunks = 0;

	//Per-thread buffer
	protected static ThreadLocal<byte[]> unCompressedData = new ThreadLocal<byte[]>()
	{
		@Override
		protected byte[] initialValue()
		{
			//One-chunk buffer
			return new byte[32 * 32 * 32 * 4];
		}
	};

	public IOTasks(WorldImplementation world)
	{
		this.world = world;
		worldSizeInChunks = world.getSizeInChunks();
		worldHeightInChunks = world.getMaxHeight() / 32;
	}

	public void addTask(IOTask task)
	{
		if (die.get())
			return;

		tasks.add(task);
		synchronized (this)
		{
			notifyAll();
		}
	}

	@Override
	public String toString()
	{
		return "IOTasks : " + getSize() + " remaining.";
	}

	public void requestChunksUnload(int pCX, int pCY, int pCZ, int sizeInChunks, int chunksViewDistance)
	{
		Iterator<IOTask> iter = tasks.iterator();
		while (iter.hasNext())
		{
			IOTask task = iter.next();
			if (task instanceof IOTaskLoadChunk)
			{
				IOTaskLoadChunk loadChunkTask = (IOTaskLoadChunk) task;
				int x = loadChunkTask.x;
				int y = loadChunkTask.y;
				int z = loadChunkTask.z;

				if ((LoopingMathHelper.moduloDistance(x, pCX, sizeInChunks) > chunksViewDistance) || (LoopingMathHelper.moduloDistance(y, pCZ, sizeInChunks) > chunksViewDistance) || (Math.abs(z - pCY) > 3))
				{
					//System.out.println("Removed task "+loadChunkTask+" for being too far");
					iter.remove();
				}
			}
		}
	}

	@Override
	public void run()
	{
		System.out.println("IO Thread started.");
		Thread.currentThread().setName("IO Tasks");
		while (!die.get())
		{
			IOTask task = null;
			
			//synchronized (tasks)
			{
				task = tasks.poll();
			}
			if (task == null)
			{
				try
				{
					synchronized (this)
					{
						wait();
					}
				}
				catch (InterruptedException e)
				{
					e.printStackTrace();
				}
			}
			else
			{
				// Run it
				try
				{
					//ChunkStoriesLogger.getInstance().info("processing task : "+task);
					boolean ok = task.run();
					// If it returns false, requeue it.
					if (!ok)
					{
						tasks.add(task);
					}

				}
				catch (Exception e)
				{
					ChunkStoriesLogger.getInstance().warning("Exception occured when processing task : " + task);
					e.printStackTrace();
				}
			}
		}
		System.out.println("WorldLoader worker thread stopped");
	}

	public int getSize()
	{
		int i = 0;
		synchronized (tasks)
		{
			i = tasks.size();
		}
		return i;
	}

	public abstract class IOTask
	{
		abstract public boolean run();
	}
	
	public class IOTaskLoadChunk extends IOTask
	{
		ChunkHolder holder;
		public int x;
		public int y;
		public int z;
		boolean shouldLoadCH;
		boolean overwrite;

		public IOTaskLoadChunk(ChunkHolder holder, int x, int y, int z, boolean shouldLoadCH, boolean overwrite)
		{
			this.holder = holder;
			this.x = x;
			this.y = y;
			this.z = z;
			this.shouldLoadCH = shouldLoadCH;
			this.overwrite = overwrite;
		}

		@Override
		public boolean run()
		{
			//ChunkHolder holder = world.getChunksHolder().getChunkHolder(x, y, z, shouldLoadCH);
			
			// If for some reasons the chunks holder's are still not loaded, we
			// requeue the job for later.
			if (holder == null)
			{
				return false;
			}
			if (!holder.isLoaded())
			{
				return false;
			}
			//Already loaded
			if (holder.isChunkLoaded(x, y, z))// && !overwrite)
				return true;
			//Look for
			holder.compressedChunksLock.beginRead();
			byte[] cd = holder.getCompressedData(x, y, z);
			//holder.lock.lock();
			if (cd == null || cd.length == 0)
			{
				holder.compressedChunksLock.endRead();
				CubicChunk c = new CubicChunk(world, x, y, z);
				//System.out.println("No compressed data for this chunk.");
				//holder.lock.unlock();
				world.setChunk(c);
				return true;
			}
			else
			{
				CubicChunk c = new CubicChunk(world, x, y, z);
				try
				{
					decompressor.decompress(cd, unCompressedData.get());
				}
				catch (LZ4Exception e)
				{
					System.out.println("Failed to decompress chunk data (corrupted?) holder:" + holder + " chunk:" + c + "task: "+this);
				}

				holder.compressedChunksLock.endRead();
				for (int i = 0; i < 32 * 32 * 32; i++)
				{
					int data = ((unCompressedData.get()[i * 4] & 0xFF) << 24) | ((unCompressedData.get()[i * 4 + 1] & 0xFF) << 16) | ((unCompressedData.get()[i * 4 + 2] & 0xFF) << 8) | (unCompressedData.get()[i * 4 + 3] & 0xFF);
					c.setDataAtWithoutUpdates(i / 32 / 32, (i / 32) % 32, i % 32, data);
				}
				c.bakeVoxelLightning(false);
				world.setChunk(c);
			}
			return true;
		}

		@Override
		public String toString()
		{
			return "[IOTaskLoadChunk x=" + x + " y= " + y + " z= " + z + "]";
		}

		@Override
		public boolean equals(Object o)
		{
			if (o != null && o instanceof IOTaskLoadChunk)
			{
				IOTaskLoadChunk comp = ((IOTaskLoadChunk) o);
				if (comp.x == this.x && comp.y == this.y && comp.z == this.z)
					return true;
			}
			return false;
		}

		@Override
		public int hashCode()
		{
			return (int) ((65536 + 65536L * x + 256 * y + z) % 2147483647);
		}

	}

	public void requestChunkLoad(ChunkHolder holder, int chunkX, int chunkY, int chunkZ, boolean overwrite)
	{
		chunkX = chunkX % worldSizeInChunks;
		chunkZ = chunkZ % worldSizeInChunks;
		if (chunkX < 0)
			chunkX += worldSizeInChunks;
		if (chunkZ < 0)
			chunkZ += worldSizeInChunks;
		if (chunkY < 0)
			return;
		if (chunkY >= worldHeightInChunks)
			return;

		IOTaskLoadChunk task = new IOTaskLoadChunk(holder, chunkX, chunkY, chunkZ, true, overwrite);

		addTask(task);
	}

	public class IOTaskLoadChunkHolder extends IOTask
	{
		ChunkHolder holder;

		public IOTaskLoadChunkHolder(ChunkHolder holder)
		{
			this.holder = holder;
		}

		@Override
		public boolean run()
		{
			//Trim world first
			world.trimRemovableChunks();
			
			//Check no saving operations are occuring
			IOTaskSaveChunkHolder saveChunkHolder = new IOTaskSaveChunkHolder(holder);
			if(tasks != null && tasks.contains(saveChunkHolder))
			{
				System.out.println("A save operation is still running on "+holder+", waiting for it to complete.");
				return false;
			}
			
			if (holder.handler.exists())
			{
				try
				{
					holder.handler.load();
				}
				catch (FileNotFoundException e)
				{
					e.printStackTrace();
					return true;
				}
				catch (IOException e)
				{
					e.printStackTrace();
					return true;
				}
			}
			//Else if no file exists
			else
			{
				RegionSummary chunkSummary = world.getRegionSummaries().get(holder.regionX * 256, holder.regionZ * 256);
				//Require a chunk summary to be generated first !
				if (chunkSummary == null || !chunkSummary.isLoaded())
				{
					return false;
				}
				//Generate this crap !
				holder.generateAll();
				//Pre bake phase 1 lightning
			}
			
			//Marking the holder as loaded allows the game to remove it and unload it, so we set the timer to have a time frame until it naturally unloads.
			holder.resetUnloadCooldown();
			holder.setLoaded(true);

			world.trimRemovableChunks();
			return true;
		}

		@Override
		public boolean equals(Object o)
		{
			if (o != null && o instanceof IOTaskLoadChunkHolder)
			{
				IOTaskLoadChunkHolder comp = ((IOTaskLoadChunkHolder) o);
				if (comp.holder.regionX == holder.regionX && comp.holder.regionY == this.holder.regionY && comp.holder.regionZ == this.holder.regionZ)
					return true;
			}
			return false;
		}

		@Override
		public int hashCode()
		{
			return (874 + 64 * holder.regionX + 22 * holder.regionY + 999 * holder.regionZ) % 2147483647;
		}
	}

	public void requestChunkHolderLoad(ChunkHolder holder)
	{
		IOTask task = new IOTaskLoadChunkHolder(holder);
		addTask(task);
	}

	public class IOTaskSaveChunkHolder extends IOTask
	{
		ChunkHolder holder;

		public IOTaskSaveChunkHolder(ChunkHolder holder)
		{
			this.holder = holder;
		}

		@Override
		public boolean run()
		{
			// First compress all loaded chunks !
			holder.compressAll();
			// Then write the file.
			try
			{
				holder.handler.save();
			}
			catch (FileNotFoundException e)
			{
				e.printStackTrace();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
			return true;
		}

		@Override
		public boolean equals(Object o)
		{
			if (o != null && o instanceof IOTaskSaveChunkHolder)
			{
				IOTaskSaveChunkHolder comp = ((IOTaskSaveChunkHolder) o);
				if (comp.holder.regionX == holder.regionX && comp.holder.regionY == this.holder.regionY && comp.holder.regionZ == this.holder.regionZ)
					return true;
			}
			return false;
		}

		@Override
		public int hashCode()
		{
			return (666778 + 64 * holder.regionX + 22 * holder.regionY + 999 * holder.regionZ) % 2147483647;
		}
	}

	public void requestChunkHolderSave(ChunkHolder holder)
	{
		IOTask task = new IOTaskSaveChunkHolder(holder);
		addTask(task);
	}

	public class IOTaskLoadSummary extends IOTask
	{
		RegionSummary summary;

		public IOTaskLoadSummary(RegionSummary summary)
		{
			this.summary = summary;
		}

		@Override
		public boolean run()
		{
			if (summary.isLoaded())
				return true;
			if (summary.handler.exists())
			{
				try
				{
					FileInputStream in = new FileInputStream(summary.handler);

					byte[] size = new byte[4];
					in.read(size);
					int s = ByteBuffer.wrap(size).asIntBuffer().get(0);
					byte[] compressed = new byte[s];
					in.read(compressed);

					byte[] decompressed = decompressor.decompress(compressed, 256 * 256 * 4);
					IntBuffer ib = ByteBuffer.wrap(decompressed).asIntBuffer();
					for (int i = 0; i < 256 * 256; i++)
						summary.heights[i] = ib.get();

					in.read(size);
					s = ByteBuffer.wrap(size).asIntBuffer().get(0);
					compressed = new byte[s];
					in.read(compressed);

					decompressed = decompressor.decompress(compressed, 256 * 256 * 4);
					ib = ByteBuffer.wrap(decompressed).asIntBuffer();
					for (int i = 0; i < 256 * 256; i++)
						summary.ids[i] = ib.get();

					in.close();

					summary.uploadUpToDate.set(false);
					summary.loaded.set(true);

					summary.computeHeightMetadata();
				}
				catch (FileNotFoundException e)
				{
					e.printStackTrace();
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}
			}
			else
			{
				// System.out.println("Couldn't find file : " + summary.handler.getAbsolutePath());
				// Generate summary according to generator heightmap
				int h, t;
				for (int x = 0; x < 256; x++)
					for (int z = 0; z < 256; z++)
					{
						h = world.getGenerator().getHeightAt(x + summary.rx * 256, z + summary.rz * 256);
						t = world.getGenerator().getTopDataAt(x + summary.rx * 256, z + summary.rz * 256);
						summary.heights[x * 256 + z] = h;
						summary.ids[x * 256 + z] = t;
					}

				summary.uploadUpToDate.set(false);
				summary.loaded.set(true);
				// then save

				//summary.save(summary.handler);

				// Thread.dumpStack();
			}
			return true;
		}

		@Override
		public boolean equals(Object o)
		{
			if (o instanceof IOTaskLoadSummary)
			{
				IOTaskLoadSummary comp = ((IOTaskLoadSummary) o);
				if (comp.summary.rx == this.summary.rx && comp.summary.rz == this.summary.rz)
					return true;
			}
			return false;
		}

		@Override
		public int hashCode()
		{
			return 1111 + summary.rx + summary.rz * 256;
		}
	}

	public void requestChunkSummaryLoad(RegionSummary summary)
	{
		IOTask task = new IOTaskLoadSummary(summary);
		addTask(task);
	}

	public class IOTaskSaveSummary extends IOTask
	{

		RegionSummary summary;

		public IOTaskSaveSummary(RegionSummary summary)
		{
			this.summary = summary;
		}

		@Override
		public boolean run()
		{
			try
			{
				summary.handler.getParentFile().mkdirs();
				if (!summary.handler.exists())
					summary.handler.createNewFile();
				FileOutputStream out = new FileOutputStream(summary.handler);

				ByteBuffer writeMe = ByteBuffer.allocate(256 * 256 * 4);

				for(int i = 0; i < 256 * 256; i++)
					writeMe.putInt(summary.heights[i]);

				byte[] compressed = RegionSummary.compressor.compress(writeMe.array());

				int compressedSize = compressed.length;

				byte[] size = ByteBuffer.allocate(4).putInt(compressedSize).array();
				out.write(size);
				out.write(compressed);

				writeMe.clear();
				for (int i : summary.ids)
					writeMe.putInt(i);

				compressed = RegionSummary.compressor.compress(writeMe.array());
				compressedSize = compressed.length;

				size = ByteBuffer.allocate(4).putInt(compressedSize).array();
				out.write(size);
				out.write(compressed);

				out.close();
				// System.out.println("saved");
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
			return true;
		}

		@Override
		public boolean equals(Object o)
		{
			/*if(o instanceof IOTaskLoadSummary)
			{
				IOTaskLoadSummary comp = ((IOTaskLoadSummary)o);
				if(comp.summary.rx == this.summary.rx && comp.summary.rz == this.summary.rz)
					return true;
			}*/
			//All saves request are unique
			return false;
		}

		@Override
		public int hashCode()
		{
			return 7777 + summary.rx + summary.rz * 256;
		}
	}

	public void requestChunkSummarySave(RegionSummary summary)
	{
		IOTask task = new IOTaskSaveSummary(summary);
		addTask(task);
	}

	public void notifyChunkUnload(int chunkX, int chunkY, int chunkZ)
	{

	}

	public void kill()
	{
		die.set(true);
		synchronized (this)
		{
			notifyAll();
		}
	}

	public void shutdown()
	{
		synchronized (this)
		{
			notifyAll();
		}
		while (this.tasks.size() > 0)
		{
			try
			{
				sleep(150L);
			}
			catch (InterruptedException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		die.set(true);
	}
}
