package io.xol.chunkstories.world.io;

import io.xol.chunkstories.world.WorldImplementation;
import io.xol.chunkstories.world.chunk.ChunkHolder;
import io.xol.chunkstories.world.summary.RegionSummary;

//(c) 2015-2016 XolioWare Interactive
//http://chunkstories.xyz
//http://xol.io

public class IOTasksImmediate extends IOTasks {

	public IOTasksImmediate(WorldImplementation world) {
		super(world);
		this.tasks = null;
	}

	@Override
	public void requestChunkLoad(ChunkHolder holder, int chunkX, int chunkY, int chunkZ, boolean overwrite)
	{
		IOTaskLoadChunk task = new IOTaskLoadChunk(holder, chunkX, chunkY, chunkZ, true, overwrite);
		task.run();
	}

	@Override
	public void requestChunkHolderLoad(ChunkHolder holder)
	{
		IOTask task = new IOTaskLoadChunkHolder(holder);
		task.run();
	}

	@Override
	public void requestChunkHolderSave(ChunkHolder holder)
	{
		IOTask task = new IOTaskSaveChunkHolder(holder);
		task.run();
	}

	@Override
	public void requestChunkSummaryLoad(RegionSummary summary)
	{
		IOTask task = new IOTaskLoadSummary(summary);
		task.run();
	}

	@Override
	public void requestChunkSummarySave(RegionSummary summary)
	{
		IOTask task = new IOTaskSaveSummary(summary);
		task.run();
	}
}
