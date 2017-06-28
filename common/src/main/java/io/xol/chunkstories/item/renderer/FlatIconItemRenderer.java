package io.xol.chunkstories.item.renderer;

import io.xol.chunkstories.api.Location;
import io.xol.chunkstories.api.client.ClientContent;
import io.xol.chunkstories.api.client.ClientContent.TexturesLibrary;
import io.xol.chunkstories.api.item.Item;
import io.xol.chunkstories.api.item.ItemType;
import io.xol.chunkstories.api.item.inventory.ItemPile;
import io.xol.chunkstories.api.item.renderer.ItemRenderer;
import io.xol.chunkstories.api.math.Matrix4f;
import io.xol.chunkstories.api.math.vector.sp.Vector3fm;
import io.xol.chunkstories.api.rendering.RenderingInterface;
import io.xol.chunkstories.api.rendering.textures.Texture2D;
import io.xol.chunkstories.api.world.World;

//(c) 2015-2017 XolioWare Interactive
//http://chunkstories.xyz
//http://xol.io

public class FlatIconItemRenderer extends DefaultItemRenderer
{
	private final TexturesLibrary textures;
	
	public FlatIconItemRenderer(Item item, ItemRenderer fallbackRenderer, ItemType itemType)
	{
		super(item);
		this.textures = ((ClientContent)item.getType().store().parent()).textures();
	}
	
	@Override
	public void renderItemInWorld(RenderingInterface renderingInterface, ItemPile pile, World world, Location location, Matrix4f handTransformation)
	{
		handTransformation.rotate((float) (Math.PI / 4f), new Vector3fm(0.0, 0.0, 1.0));
		handTransformation.rotate((float) (Math.PI / 2f), new Vector3fm(0.0, 1.0, 0.0));
		handTransformation.translate(new Vector3fm(-0.05, -0.05, 0.05));
		
		int max = pile.getItem().getType().getSlotsWidth() - 1;
		handTransformation.scale(new Vector3fm(0.25 + 0.20 * max));
		
		renderingInterface.setObjectMatrix(handTransformation);

		textures.getTexture(pile.getTextureName()).setLinearFiltering(false);
		Texture2D texture = textures.getTexture(pile.getTextureName());
		if(texture == null)
			texture = textures.getTexture("res/items/icons/notex.png");
		
		//texture = TexturesHandler.getTexture("res/textures/notex.png");
		texture.setLinearFiltering(false);
		renderingInterface.bindAlbedoTexture(texture);
		
		draw3DPlane(renderingInterface);
	}
}