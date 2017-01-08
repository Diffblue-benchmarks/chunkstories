package io.xol.chunkstories.core.voxel;

import io.xol.chunkstories.api.Content;
import io.xol.chunkstories.api.voxel.VoxelFormat;
import io.xol.chunkstories.api.voxel.VoxelSides;
import io.xol.chunkstories.physics.CollisionBox;
import io.xol.chunkstories.renderer.VoxelContext;
import io.xol.chunkstories.voxel.VoxelDefault;
import io.xol.chunkstories.voxel.VoxelsStore;
import io.xol.chunkstories.voxel.models.VoxelModel;

//(c) 2015-2016 XolioWare Interactive
// http://chunkstories.xyz
// http://xol.io

public class VoxelHalfTile extends VoxelDefault
{

	VoxelModel bot;
	VoxelModel top;

	public VoxelHalfTile(Content.Voxels store, int id, String name)
	{
		super(store, id, name);
		bot = store.models().getVoxelModelByName("halftile.bottom");
		top = store.models().getVoxelModelByName("halftile.top");
		// System.out.println("kekzer");
	}

	boolean bottomOrTop(int meta)
	{
		// int meta = VoxelFormat.meta(data);
		return meta % 2 == 0;
	}

	@Override
	public VoxelModel getVoxelRenderer(VoxelContext info)
	{
		int meta = info.getMetaData();
		if (bottomOrTop(meta))
			return bot;
		return top;
	}

	@Override
	public CollisionBox[] getCollisionBoxes(VoxelContext info)
	{
		// System.out.println("kek");
		CollisionBox box2 = new CollisionBox(1, 0.5, 1);
		if (bottomOrTop(VoxelFormat.meta(info.data)))
			box2.translate(0.5, -0, 0.5);
		else
			box2.translate(0.5, +0.5, 0.5);
		return new CollisionBox[] { box2 };
	}
	
	@Override
	public int getLightLevelModifier(int dataFrom, int dataTo, VoxelSides side2)
	{
		int side = side2.ordinal();
		
		//Special cases when half-tiles meet
		if(VoxelsStore.get().getVoxelById(dataTo) instanceof VoxelHalfTile && side < 4)
		{
			//If they are the same type, allow the light to transfer
			if(bottomOrTop(VoxelFormat.meta(dataFrom)) == bottomOrTop(VoxelFormat.meta(dataTo)))
				return 2;
			else
				return 15;
		}
		if (bottomOrTop(VoxelFormat.meta(dataFrom)) && side == 5)
			return 15;
		if (!bottomOrTop(VoxelFormat.meta(dataFrom)) && side == 4)
			return 15;
		return 2;
	}
}
