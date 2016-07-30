package io.xol.chunkstories.core.particles;

import io.xol.engine.math.lalgb.Vector3f;

import static io.xol.chunkstories.particles.Particle.Type.*;

import io.xol.chunkstories.api.rendering.Light;
import io.xol.chunkstories.particles.Particle;
import io.xol.chunkstories.particles.Particle.Type;
import io.xol.chunkstories.renderer.lights.DefferedLight;
import io.xol.chunkstories.world.WorldImplementation;

//(c) 2015-2016 XolioWare Interactive
// http://chunkstories.xyz
// http://xol.io

public class ParticleLight extends Particle
{

	int timer = 2400;// for 40sec

	DefferedLight dl;

	@Override
	public Type getType()
	{
		return LIGHT;
	}

	@Override
	public void update()
	{
		if (!((WorldImplementation) world).checkCollisionPoint(posX, posY, posZ))
			posY += -0.25;
		// posX+=Math.sin(timer/30f)*0.5;
		// posZ+=Math.cos(timer/30f)*0.5;
		// posY+=Math.cos(timer/15f)*0.1;
		timer--;
		/*
		 * if(timer < 0) kill();
		 */
		dl.position = new Vector3f((float) posX, (float) posY, (float) posZ);
		// dl.decay = timer/360f;
		// dl.decay = 8f+(float)Math.sin(timer/20f)*4f;
		// dl.decay = 8f+1f;
	}

	public ParticleLight(WorldImplementation world, double posX, double posY, double posZ)
	{
		super(world, posX, posY, posZ);
		dl = new DefferedLight(new Vector3f(0.5f + (float) Math.random(),
				0.5f + (float) Math.random(), 0.5f + (float) Math.random()),
				new Vector3f((float) posX, (float) posY, (float) posZ),
				05f + (float) Math.random() * 15f);
		/*
		 * if(Math.random() > 0.5) { dl.angle = 22; float rotx = (float)
		 * (Math.random()*45f); float roty = (float) (Math.random()*360f); float
		 * transformedViewH = (float) ((rotx)/180*Math.PI);
		 * //System.out.println(Math.sin(transformedViewV)+"f"); dl.direction =
		 * new Vector3f((float)
		 * (Math.sin((-roty)/180*Math.PI)*Math.cos(transformedViewH)), (float)
		 * (Math.sin(transformedViewH)), (float)
		 * (Math.cos((-roty)/180*Math.PI)*Math.cos(transformedViewH))); dl.decay
		 * = 50f; }
		 */
	}

	@Override
	public String getTextureName()
	{
		return "./res/textures/particle.png";
	}

	@Override
	public boolean emitsLights()
	{
		return true;
	}

	@Override
	public Light getLightEmited()
	{
		return dl;
	}

	@Override
	public Float getSize()
	{
		return 0.0f;
	}
}