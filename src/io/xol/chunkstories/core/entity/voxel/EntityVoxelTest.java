package io.xol.chunkstories.core.entity.voxel;

import io.xol.chunkstories.api.entity.EntityVoxel;
import io.xol.chunkstories.entity.EntityImplementation;
import io.xol.chunkstories.physics.CollisionBox;
import io.xol.chunkstories.world.WorldImplementation;

//(c) 2015-2017 XolioWare Interactive
//http://chunkstories.xyz
//http://xol.io

public class EntityVoxelTest extends EntityImplementation implements EntityVoxel
{
	public EntityVoxelTest(WorldImplementation w, double x, double y, double z)
	{
		super(w, x, y, z);
	}
	
	@Override
	public CollisionBox[] getCollisionBoxes()
	{
		return new CollisionBox[] { new CollisionBox(1.0, 1.0, 1.0).translate(-0.5, 0, -0.5) };
	}

	/*@Override
	public void render(RenderingContext context)
	{
		//System.out.println("k man" + getLocation());
		
		Vector3f pos = getLocation().castToSP();
		pos.x += 0.5f;
		pos.z += 0.5f;
		
		pos.y += 1.5f;
		context.addLight(new DefferedLight(new Vector3f(1.0f, 1.0f, 0.0f), pos, 15f));
	}*/
}
