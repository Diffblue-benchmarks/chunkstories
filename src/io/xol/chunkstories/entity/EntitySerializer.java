package io.xol.chunkstories.entity;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import io.xol.chunkstories.api.csf.OfflineSerializedData;
import io.xol.chunkstories.api.entity.Entity;
import io.xol.chunkstories.api.exceptions.UnknownComponentException;
import io.xol.chunkstories.api.world.World;

//(c) 2015-2016 XolioWare Interactive
//http://chunkstories.xyz
//http://xol.io

/**
 * The one place in the code where we serialize entities
 */
public class EntitySerializer
{
	public static void writeEntityToStream(DataOutputStream out, OfflineSerializedData destination, Entity entity)
	{
		try
		{
			out.writeLong(entity.getUUID());
			out.writeShort(entity.getEID());
			
			//Write all components we wanna update
			entity.getComponents().pushAllComponentsInStream(destination, out);
			
			//Then write 0 to mark end of components
			out.writeInt((int)0);
			
			//System.out.println("Wrote serialized entity to : "+destination);
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}
	
	public static Entity readEntityFromStream(DataInputStream in, OfflineSerializedData source, World world)
	{
		try
		{
			long entityUUID = in.readLong();
			
			//When we reach -1 in a stream of entities, it means we reached the end.
			if(entityUUID == -1)
				return null;
			
			short entityTypeID = in.readShort();
			
			Entity entity = EntitiesList.newEntity(world, entityTypeID);
			entity.setUUID(entityUUID);
			
			int componentId = in.readInt();
			//Loop throught all components
			while(componentId != 0)
			{
				if(!entity.getComponents().tryPullComponentInStream(componentId, source, in))
					throw new UnknownComponentException(componentId, entity.getClass());
				componentId = in.readInt();
			}
			
			//System.out.println("Read serialized entity from : "+source);
			//System.out.println(entity.exists()+" - "+entity);
			
			return entity;
		}
		catch (IOException | UnknownComponentException e)
		{
			e.printStackTrace();
		}
		return null;
	}
}