//
// This file is a part of the Chunk Stories Implementation codebase
// Check out README.md for more information
// Website: http://chunkstories.xyz
//

package xyz.chunkstories.world.cell;

import xyz.chunkstories.api.voxel.Voxel;
import xyz.chunkstories.api.voxel.VoxelSide;
import xyz.chunkstories.api.world.World;
import xyz.chunkstories.api.world.cell.CellData;

/** Used to recycle results of a peek command */
public class ScratchCell implements CellData {
	final World world;

	public ScratchCell(World world) {
		this.world = world;
	}

	// Fields set to public so we can access them
	public int x, y, z;
	public Voxel voxel;
	public int sunlight, blocklight, metadata;

	@Override
	public World getWorld() {
		return world;
	}

	@Override
	public int getX() {
		return x;
	}

	@Override
	public int getY() {
		return y;
	}

	@Override
	public int getZ() {
		return z;
	}

	@Override
	public Voxel getVoxel() {
		return voxel;
	}

	@Override
	public int getMetaData() {
		return metadata;
	}

	@Override
	public int getSunlight() {
		return sunlight;
	}

	@Override
	public int getBlocklight() {
		return blocklight;
	}

	@Override
	public CellData getNeightbor(int side_int) {
		VoxelSide side = VoxelSide.values()[side_int];
		return world.peekSafely(x + side.dx, y + side.dy, z + side.dz);
	}
}