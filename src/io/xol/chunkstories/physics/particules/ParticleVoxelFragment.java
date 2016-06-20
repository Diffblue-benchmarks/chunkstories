package io.xol.chunkstories.physics.particules;

import io.xol.chunkstories.world.WorldImplementation;
import static io.xol.chunkstories.physics.particules.Particle.Type.*;

//(c) 2015-2016 XolioWare Interactive
// http://chunkstories.xyz
// http://xol.io

public class ParticleVoxelFragment extends Particle
{

	@Override
	public Type getType()
	{
		return VOXEL_FRAGMENT;
	}

	@Override
	public void update()
	{
		/*
		 * if(!world.checkCollisionPoint(posX, posY, posZ)) {
		 * 
		 * }
		 */
		posY += (Math.random() - 0.1) * 0.005;
		posX += (Math.random() - 0.5) * 0.005;
		posZ += (Math.random() - 0.5) * 0.005;
	}

	public ParticleVoxelFragment(WorldImplementation world, double posX, double posY,
			double posZ)
	{
		super(world, posX, posY, posZ);
	}

	@Override
	public String getTextureName()
	{
		return "./res/textures/tiles_merged_diffuse.png";
	}

	@Override
	public Float getSize()
	{
		return 0.5f;
	}
}
