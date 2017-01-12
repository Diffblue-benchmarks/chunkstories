package io.xol.chunkstories.content;

import io.xol.chunkstories.api.Content;
import io.xol.chunkstories.api.GameContext;
import io.xol.chunkstories.api.mods.ModsManager;
import io.xol.chunkstories.content.mods.exceptions.NotAllModsLoadedException;
import io.xol.chunkstories.entity.EntityTypesStore;
import io.xol.chunkstories.item.ItemTypes;
import io.xol.chunkstories.net.packets.PacketsProcessor;
import io.xol.chunkstories.voxel.VoxelsStore;
import io.xol.chunkstories.world.generator.WorldGenerators;

//(c) 2015-2016 XolioWare Interactive
//http://chunkstories.xyz
//http://xol.io

public class GameContent implements Content
{
	private final GameContext context;
	private final ModsManager modsManager;

	private final VoxelsStore voxels;
	private final EntityTypesStore entities;

	public GameContent(GameContext context, String enabledModsLaunchArguments)
	{
		this.context = context;
		this.modsManager = new DefaultModsManager(enabledModsLaunchArguments);

		// ! LOADS MODS

		try
		{
			modsManager.loadEnabledMods();
		}
		catch (NotAllModsLoadedException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// ! TO REFACTOR

		io.xol.chunkstories.materials.Materials.reload();
		ItemTypes.reload();

		voxels = new VoxelsStore(this);
		entities = new EntityTypesStore(this);

		PacketsProcessor.loadPacketsTypes();

		WorldGenerators.loadWorldGenerators();

		io.xol.chunkstories.particles.ParticleTypes.reload();
	}

	public void reload()
	{
		// ! LOADS MODS

		try
		{
			modsManager.loadEnabledMods();
		}
		catch (NotAllModsLoadedException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		io.xol.chunkstories.materials.Materials.reload();
		ItemTypes.reload();

		voxels.reload();
		entities.reload();

		PacketsProcessor.loadPacketsTypes();

		WorldGenerators.loadWorldGenerators();

		io.xol.chunkstories.particles.ParticleTypes.reload();
	}

	@Override
	public Materials materials()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Voxels voxels()
	{
		return voxels;
	}

	@Override
	public ItemsTypes items()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public EntityTypes entities()
	{
		return entities;
	}

	@Override
	public ParticleTypes particles()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public GameContext getContext()
	{
		return context;
	}

	@Override
	public ModsManager modsManager()
	{
		return modsManager;
	}

}