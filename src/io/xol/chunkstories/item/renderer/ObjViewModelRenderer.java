package io.xol.chunkstories.item.renderer;

import io.xol.engine.graphics.textures.TexturesHandler;
import io.xol.chunkstories.api.Location;
import io.xol.chunkstories.api.item.ItemRenderer;
import io.xol.chunkstories.api.item.inventory.ItemPile;
import io.xol.chunkstories.api.math.Matrix4f;
import io.xol.chunkstories.api.rendering.RenderingInterface;
import io.xol.chunkstories.api.world.World;
import io.xol.engine.model.ModelLibrary;

//(c) 2015-2017 XolioWare Interactive
//http://chunkstories.xyz
//http://xol.io

public class ObjViewModelRenderer extends ItemRenderer
{
	final String objName;
	final String albedoTextureName;
	final String normalTextureName;
	final String materialTextureName;

	public ObjViewModelRenderer(ItemRenderer fallbackRenderer, String objName, String albedoTextureName)
	{
		this(fallbackRenderer, objName, albedoTextureName, "./textures/normalnormal.png");
	}
	
	public ObjViewModelRenderer(ItemRenderer fallbackRenderer, String objName, String albedoTextureName, String normalTextureName)
	{
		this(fallbackRenderer, objName, albedoTextureName, normalTextureName, "./textures/defaultmaterial.png");
	}
	
	public ObjViewModelRenderer(ItemRenderer fallbackRenderer, String objName, String albedoTextureName, String normalTextureName, String materialTextureName)
	{
		super(fallbackRenderer);
		
		this.objName = objName;
		this.albedoTextureName = albedoTextureName;
		this.normalTextureName = normalTextureName;
		this.materialTextureName = materialTextureName;
	}

	@Override
	public void renderItemInInventory(RenderingInterface context, ItemPile pile, int screenPositionX, int screenPositionY, int scaling)
	{
		super.renderItemInInventory(context, pile, screenPositionX, screenPositionY, scaling);
	}

	@Override
	public void renderItemInWorld(RenderingInterface renderingContext, ItemPile pile, World world, Location location, Matrix4f handTransformation)
	{
		renderingContext.setObjectMatrix(handTransformation);
		
		renderingContext.bindAlbedoTexture(TexturesHandler.getTexture(albedoTextureName));
		renderingContext.bindNormalTexture(TexturesHandler.getTexture(normalTextureName));
		renderingContext.bindMaterialTexture(TexturesHandler.getTexture(materialTextureName));
		ModelLibrary.getRenderableMesh(objName).render(renderingContext);
	}

}