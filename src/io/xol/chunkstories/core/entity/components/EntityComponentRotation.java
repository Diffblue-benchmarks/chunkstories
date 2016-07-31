package io.xol.chunkstories.core.entity.components;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import io.xol.chunkstories.api.csf.StreamSource;
import io.xol.chunkstories.api.csf.StreamTarget;
import io.xol.chunkstories.api.entity.Entity;
import io.xol.chunkstories.api.entity.components.EntityComponent;
import io.xol.chunkstories.api.world.WorldMaster;
import io.xol.engine.math.lalgb.Vector2f;
import io.xol.engine.math.lalgb.Vector3d;

//(c) 2015-2016 XolioWare Interactive
//http://chunkstories.xyz
//http://xol.io

public class EntityComponentRotation extends EntityComponent
{
	private float rotationHorizontal = 0f;
	private float rotationVertical = 0f;
	
	private Vector2f rotationImpulse = new Vector2f();
	
	public EntityComponentRotation(Entity entity, EntityComponent previous)
	{
		super(entity, previous);
	}

	public float getHorizontalRotation()
	{
		return rotationHorizontal;
	}
	
	public float getVerticalRotation()
	{
		return rotationVertical;
	}

	/**
	 * @return A vector3d for the direction
	 */
	public Vector3d getDirectionLookingAt()
	{
		Vector3d direction = new Vector3d();

		float a = (float) ((-getHorizontalRotation()) / 360f * 2 * Math.PI);
		float b = (float) ((getVerticalRotation()) / 360f * 2 * Math.PI);
		direction.setX(-(float) Math.sin(a) * (float) Math.cos(b));
		direction.setY(-(float) Math.sin(b));
		direction.setZ(-(float) Math.cos(a) * (float) Math.cos(b));

		return direction.normalize();
	}
	
	public void setRotation(double horizontalAngle, double verticalAngle)
	{
		this.rotationHorizontal = (float)(360 + horizontalAngle) % 360;
		this.rotationVertical = (float)verticalAngle;
		
		if (rotationVertical > 90)
			rotationVertical = 90;
		if (rotationVertical < -90)
			rotationVertical = -90;
		
		this.pushComponentEveryone();
	}

	public void addRotation(float x, float y)
	{
		setRotation(rotationHorizontal + x, rotationVertical + y);
	}
	
	@Override
	protected void push(StreamTarget destinator, DataOutputStream dos) throws IOException
	{
		dos.writeFloat(rotationHorizontal);
		dos.writeFloat(rotationVertical);
	}

	@Override
	protected void pull(StreamSource from, DataInputStream dis) throws IOException
	{
		rotationHorizontal = dis.readFloat();
		rotationVertical = dis.readFloat();

		//Position updates received by the server should be told to everyone but the controller
		if(entity.getWorld() instanceof WorldMaster)
			this.pushComponentEveryoneButController();
	}

	/**
	 * Sends the view flying about
	 */
	public void applyInpulse(double inpulseHorizontal, double inpulseVertical)
	{
		rotationImpulse.add(new Vector2f((float)inpulseHorizontal, (float)inpulseVertical));
	}
	
	/**
	 * Reduces the acceleration and returns it
	 */
	public Vector2f tickInpulse()
	{
		rotationImpulse.scale(0.50f);
		if(rotationImpulse.length() < 0.05)
			rotationImpulse.set(0.0f, 0.0f);
		return rotationImpulse;
	}

}
