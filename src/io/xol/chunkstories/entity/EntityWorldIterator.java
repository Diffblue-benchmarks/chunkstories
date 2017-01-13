package io.xol.chunkstories.entity;

import java.util.Iterator;

import io.xol.chunkstories.api.entity.Entity;
import io.xol.chunkstories.api.utils.IterableIterator;

//(c) 2015-2017 XolioWare Interactive
//http://chunkstories.xyz
//http://xol.io

public class EntityWorldIterator implements IterableIterator<Entity>
{
	Iterator<Entity> ie;
	Entity currentEntity;

	public EntityWorldIterator(Iterator<Entity> ie)
	{
		this.ie = ie;
	}

	@Override
	public boolean hasNext()
	{
		return ie.hasNext();
	}

	@Override
	public Entity next()
	{
		currentEntity = ie.next();
		//System.out.println(currentEntity);
		return currentEntity;
	}

	@Override
	public void remove()
	{
		//Remove it from the world set
		ie.remove();
	}
}
