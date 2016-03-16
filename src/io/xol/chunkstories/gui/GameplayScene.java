package io.xol.chunkstories.gui;

import org.lwjgl.util.vector.Vector3f;

import java.util.Iterator;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

import static org.lwjgl.opengl.GL11.*;
import io.xol.engine.base.ObjectRenderer;
import io.xol.engine.base.XolioWindow;
import io.xol.engine.font.BitmapFont;
import io.xol.engine.font.FontRenderer2;
import io.xol.chunkstories.GameData;
import io.xol.chunkstories.api.Location;
import io.xol.chunkstories.api.entity.Entity;
import io.xol.chunkstories.api.events.actions.ClientActionMouseClick;
import io.xol.chunkstories.api.events.actions.ClientActionMouseClick.MouseButton;
import io.xol.chunkstories.api.voxel.VoxelFormat;
import io.xol.chunkstories.api.world.ChunksIterator;
import io.xol.chunkstories.client.Client;
import io.xol.chunkstories.client.FastConfig;
import io.xol.chunkstories.entity.EntityControllable;
import io.xol.chunkstories.entity.EntityImplementation;
import io.xol.chunkstories.entity.core.EntityPlayer;
import io.xol.chunkstories.gui.menus.InventoryOverlay;
import io.xol.chunkstories.gui.menus.PauseOverlay;
import io.xol.chunkstories.item.ItemPile;
import io.xol.chunkstories.item.inventory.Inventory;
import io.xol.chunkstories.item.inventory.InventoryAllVoxels;
import io.xol.chunkstories.item.renderer.InventoryDrawer;
import io.xol.chunkstories.physics.CollisionBox;
import io.xol.chunkstories.physics.particules.ParticleLight;
import io.xol.chunkstories.physics.particules.ParticleSetupLight;
import io.xol.chunkstories.renderer.Camera;
import io.xol.chunkstories.renderer.DefferedLight;
import io.xol.chunkstories.renderer.SelectionRenderer;
import io.xol.chunkstories.renderer.WorldRenderer;
import io.xol.chunkstories.renderer.chunks.ChunkRenderData;
import io.xol.chunkstories.renderer.chunks.ChunksRenderer;
import io.xol.chunkstories.voxel.VoxelTypes;
import io.xol.chunkstories.world.chunk.CubicChunk;


//(c) 2015-2016 XolioWare Interactive
// http://chunkstories.xyz
// http://xol.io

public class GameplayScene extends OverlayableScene
{
	// Renderer
	public WorldRenderer worldRenderer;
	SelectionRenderer selectionRenderer;
	InventoryDrawer inventoryDrawer;

	Camera camera = new Camera();
	ChatPanel chat = new ChatPanel();
	boolean focus = true;

	Entity player;

	public GameplayScene(XolioWindow w, boolean multiPlayer)
	{
		super(w);
		w.renderingContext.setCamera(camera);

		//We need a world to work on
		if (Client.world == null)
			w.changeScene(new MainMenu(w, false));

		//Spawn manually the player if we're in SP debug
		if (!multiPlayer)
		{
			Client.controlledEntity = new EntityPlayer(Client.world, 0, 100, 0, Client.username);
			((EntityControllable) Client.controlledEntity).setController(Client.getInstance());
			Client.world.addEntity(Client.controlledEntity);
		}

		//Creates the rendering stuff
		worldRenderer = new WorldRenderer(Client.world);
		worldRenderer.setupRenderSize(XolioWindow.frameW, XolioWindow.frameH);
		//TODO wtf is this
		selectionRenderer = new SelectionRenderer(Client.world, worldRenderer);

		//Give focus
		focus(true);
	}
	
	public boolean hasFocus()
	{
		if(this.currentOverlay != null)
			return false;
		return focus;
	}
	
	//int selectedInventorySlot = 0;

	public void update()
	{
		// Update client entity
		if (player == null || player != Client.controlledEntity && Client.controlledEntity != null)
		{
			player = (EntityImplementation) Client.controlledEntity;
			inventoryDrawer = player.getInventory() == null ? null : new InventoryDrawer(player.getInventory());
		}
		inventoryDrawer.inventory = player.getInventory();
		
		//Get the player location
		Location loc = player.getLocation();
		
		// Update the player
		if (player instanceof EntityControllable)
			((EntityControllable) player).moveCamera(Client.clientController);

		int[] selectedBlock = null;
		if (player instanceof EntityPlayer)
		{
			selectedBlock = ((EntityPlayer) player).rayTraceSelectedBlock(true);
		}
		if (player != null)
			player.setupCamera(camera);
		
		//Temp
		if (flashLight)
		{
			
			float transformedViewH = (float) ((camera.view_rotx) / 180 * Math.PI);
			// System.out.println(Math.sin(transformedViewV)+"f");
			Vector3f viewerCamDirVector = new Vector3f((float) (Math.sin((-camera.view_roty) / 180 * Math.PI) * Math.cos(transformedViewH)), (float) (Math.sin(transformedViewH)),
					(float) (Math.cos((-camera.view_roty) / 180 * Math.PI) * Math.cos(transformedViewH)));
			Vector3f lightPosition = new Vector3f((float) loc.x, (float) loc.y + (float)((EntityPlayer)this.player).eyePosition, (float) loc.z);
			viewerCamDirVector.scale(-0.5f);
			Vector3f.add(viewerCamDirVector, lightPosition, lightPosition);
			viewerCamDirVector.scale(-1f);
			viewerCamDirVector.normalise();
			worldRenderer.lights.add(new DefferedLight(new Vector3f(1f, 1f, 0.9f), lightPosition, 75f, 40f, viewerCamDirVector));
			if (Keyboard.isKeyDown(Keyboard.KEY_F5))
				Client.world.particlesHolder.addParticle(new ParticleSetupLight(Client.world, loc.x, loc.y + 1.0f, loc.z, new DefferedLight(new Vector3f(1f, 1f, 1f), new Vector3f((float) loc.x, (float) loc.y + 1.5f,
						(float) loc.z), 75f, 40f, viewerCamDirVector)));
		}
		//Main render call
		worldRenderer.renderWorldAtCamera(camera);
		
		if (selectedBlock != null)
			selectionRenderer.drawSelectionBox(selectedBlock[0], selectedBlock[1], selectedBlock[2]);
		
		//Debug draws
		if (FastConfig.physicsVisualization && player != null)
		{
			int id, data;
			int drawDebugDist = 6;
			for (int i = ((int) loc.x) - drawDebugDist; i <= ((int) loc.x) + drawDebugDist; i++)
				for (int j = ((int) loc.y) - drawDebugDist; j <= ((int) loc.y) + drawDebugDist; j++)
					for (int k = ((int) loc.z) - drawDebugDist; k <= ((int) loc.z) + drawDebugDist; k++)
					{
						data = Client.world.getDataAt(i, j, k);
						id = VoxelFormat.id(data);
						VoxelTypes.get(id).debugRenderCollision(Client.world, i, j, k);
					}

			for (CollisionBox b : player.getTranslatedCollisionBoxes())
				b.debugDraw(0, 1, 1, 1);
			glDisable(GL_DEPTH_TEST);
			Iterator<Entity> ie = Client.world.getAllLoadedEntities();
			while(ie.hasNext())
				ie.next().debugDraw();
			glEnable(GL_DEPTH_TEST);
		}
		//Cubemap rendering trigger (can't run it while main render is occuring)
		if (shouldCM)
		{
			shouldCM = false;
			worldRenderer.screenCubeMap(512, null);
		}
		//Blit the final 3d image
		worldRenderer.postProcess();
		
		if (FastConfig.showDebugInfo)
			debug();
		else
			Client.profiler.reset("gui");
		 
		chat.update();
		chat.draw();

		if (player != null && player.getInventory() != null)
				inventoryDrawer.drawPlayerInventorySummary(eng.renderingContext, XolioWindow.frameW / 2, 64 + 64);

		if (Keyboard.isKeyDown(78))
			Client.world.worldTime += 10;
		if (Keyboard.isKeyDown(74))
		{
			if (Client.world.worldTime > 10)
				Client.world.worldTime -= 10;
		}
		
		if (currentOverlay == null && !chat.chatting)
			focus(true);
		// Draw overlay
		if (currentOverlay != null)
			currentOverlay.drawToScreen(0, 0, XolioWindow.frameW, XolioWindow.frameH);
		else
			ObjectRenderer.renderTexturedRect(XolioWindow.frameW/2, XolioWindow.frameH/2, 16, 16, 0, 0, 16, 16, 16, "internal://./res/textures/gui/cursor.png");
			
		super.update();
		// Check connection didn't died and change scene if it has
		if (Client.connection != null)
		{
			if (!Client.connection.isAlive() || Client.connection.hasFailed())
				eng.changeScene(new MainMenu(eng, "Connection failed : " + Client.connection.getLatestErrorMessage()));
		}
	}

	private void focus(boolean f)
	{
		Mouse.setGrabbed(f);
		if (f && !focus)
		{
			Mouse.setCursorPosition(XolioWindow.frameW / 2, XolioWindow.frameH / 2);
			this.changeOverlay(null);
		}
		focus = f;
	}

	boolean flashLight = false;
	boolean shouldCM = false;

	byte[] inventorySerialized;
	
	public boolean onKeyPress(int k)
	{
		Location loc = player.getLocation();
		if (currentOverlay != null && currentOverlay.handleKeypress(k))
			return true;
		if (!chat.chatting)
		{
			if (k == FastConfig.CHAT_KEY)
			{
				this.changeOverlay(chat.new ChatPanelOverlay(this, null));
				focus(false);
				return true;
			}
		}
		if (k == 19)
		{
			Client.world.particlesHolder.cleanAllParticles();
			Client.world.reRender();
			worldRenderer.chunksRenderer.clear();
			ChunksRenderer.renderStart = System.currentTimeMillis();
			worldRenderer.modified();
		}
		//Temp, to rework
		else if (k == FastConfig.GRABUSE_KEY)
		{
			Client.getSoundManager().playSoundEffect("sfx/flashlight.ogg", (float)loc.x, (float)loc.y, (float)loc.z, 1.0f, 1.0f);
			flashLight = !flashLight;
		}
		else if (k == FastConfig.INVENTORY_KEY)
		{
			if (player != null)
			{
				focus(false);
				this.changeOverlay(new InventoryOverlay(this, null, new Inventory[]{player.getInventory(), new InventoryAllVoxels()}));
			}
		}
		else if (k == Keyboard.KEY_F1)
		{
			if (player instanceof EntityPlayer)
				((EntityPlayer) player).toogleFly();
		}
		else if (k == Keyboard.KEY_F2)
			chat.insert(worldRenderer.screenShot());
		else if (k == Keyboard.KEY_F3)
		{
			//Client.getSoundManager().playSoundEffect("music/menu3.ogg", (float)loc.x, (float)loc.y, (float)loc.z, 1.0f, 1.0f);
			Client.getSoundManager().stopAnySound();
			Client.getSoundManager().playMusic("music/radio/horse.ogg", (float)loc.x, (float)loc.y, (float)loc.z, 1.0f, 1.0f, false).setAttenuationEnd(50f);
		}
		else if (k == Keyboard.KEY_F4)
			Client.world.particlesHolder.addParticle(new ParticleLight(Client.world, loc.x + (Math.random() - 0.5) * 30, loc.y + (Math.random()) * 10, loc.z + (Math.random() - 0.5) * 30));
		
		else if (k == Keyboard.KEY_F6)
		{
			if (player instanceof EntityPlayer)
				((EntityPlayer) player).toggleNoclip();
		}
		else if (k == Keyboard.KEY_F8)
			shouldCM = true;
		else if (k == Keyboard.KEY_F12)
		{
			GameData.reload();
			GameData.reloadClientContent();
			worldRenderer.terrain.redoBlockTexturesSummary();
		}
		else if (k == FastConfig.EXIT_KEY)
		{
			focus(false);
			this.changeOverlay(new PauseOverlay(this, null));
		}
		return false;
	}

	public boolean onClick(int x, int y, int button)
	{
		if (currentOverlay != null)
			return currentOverlay.onClick(x, y, button);
		if (!(player instanceof EntityPlayer))
			return false;
		
		ItemPile itemSelected = this.player.getInventory().getSelectedItem();
		if(itemSelected != null)
		{
			MouseButton mButton = null;
			switch(button)
			{
			case 0:
				mButton = MouseButton.MOUSE_LEFT;
				break;
			case 1:
				mButton = MouseButton.MOUSE_RIGHT;
				break;
			case 2:
				mButton = MouseButton.MOUSE_MIDDLE;
				break;
			}
			if(mButton != null)
				itemSelected.getItem().onUse(player, itemSelected, new ClientActionMouseClick(mButton));
		}
		return false;
	}

	public boolean onScroll(int a)
	{
		if (currentOverlay != null && currentOverlay.onScroll(a))
			return true;
		//Scroll trought the items
		if(player != null && player.getInventory() != null)
		{
			ItemPile selected = null;
			int selectedInventorySlot = player.getInventory().getSelectedSlot();
			int originalSlot = selectedInventorySlot;
			if(a < 0)
			{
				selectedInventorySlot %= player.getInventory().width;
				selected = player.getInventory().getItem(selectedInventorySlot, 0);
				if(selected != null)
					selectedInventorySlot+= selected.item.getSlotsWidth();
				else
					selectedInventorySlot++;
			}
			else
			{
				selectedInventorySlot--;
				if(selectedInventorySlot < 0)
					selectedInventorySlot += player.getInventory().width;
				selected = player.getInventory().getItem(selectedInventorySlot, 0);
				if(selected != null)
					selectedInventorySlot = selected.x;
			}
			//Switch slot
			if(originalSlot != selectedInventorySlot)
				player.getInventory().setSelectedSlot(selectedInventorySlot);
		}
		return true;
	}

	public void onResize()
	{
		worldRenderer.setupRenderSize(XolioWindow.frameW, XolioWindow.frameH);
	}

	// CLEANING - do it properly or mum will smash the shit out of you

	public void destroy()
	{
		Client.world.destroy();
		this.worldRenderer.destroy();
		if (Client.connection != null)
		{
			Client.connection.close();
			Client.connection = null;
		}
	}

	private void debug()
	{
		int timeTook = Client.profiler.timeTook();
		String debugInfo = Client.profiler.reset("gui");
		if (timeTook > 400)
			System.out.println(debugInfo);

		long total = Runtime.getRuntime().totalMemory();
		long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
		int bx = (-(int) camera.camPosX);
		int by = (-(int) camera.camPosY);
		int bz = (-(int) camera.camPosZ);
		int data = Client.world.getDataAt(bx, by, bz);
		int bl = (data & 0x0F000000) >> 0x18;
		int sl = (data & 0x00F00000) >> 0x14;
		int cx = bx / 32;
		int cy = by / 32;
		int cz = bz / 32;
		int csh = Client.world.chunkSummaries.getHeightAt(bx, bz);
		CubicChunk current = Client.world.getChunk(cx, cy, cz, false);
		// FontRenderer2.drawTextUsingSpecificFont(20, XolioWindow.frameH - 20,
		// 0, 16, "X:" + loc.x + " Y:" + loc.y + " Z:" + loc.z
		// + "rotH" + camera.view_roty + " worldtime:" + Client.world.worldTime
		// % 1000, BitmapFont.SMALLFONTS);
		FontRenderer2.drawTextUsingSpecificFont(20, XolioWindow.frameH - 36, 0, 16, "Position : x:" + bx + " y:" + by + " z:" + bz + " bl:" + bl + " sl:" + sl + " cx:" + cx + " cy:" + cy + " cz:" + cz + " csh:" + csh, BitmapFont.SMALLFONTS);
		FontRenderer2.drawTextUsingSpecificFont(20, XolioWindow.frameH - 52, 0, 16, "CR : T : " + worldRenderer.chunksRenderer.todo.size() + " D: " + worldRenderer.chunksRenderer.done.size() + "WL : " + Client.world.ioHandler.toString()
				+ " ChunksData" + Client.world.chunksData.size() + " WR list:" + worldRenderer.getQueueSize(), BitmapFont.SMALLFONTS);
		if (current == null)
			FontRenderer2.drawTextUsingSpecificFont(20, XolioWindow.frameH - 68, 0, 16, "Current chunk null", BitmapFont.SMALLFONTS);
		else
		{
			ChunkRenderData chunkRenderData = current.chunkRenderData;
			if(chunkRenderData != null)
			{
				FontRenderer2.drawTextUsingSpecificFont(20, XolioWindow.frameH - 68, 0, 16, "Current chunk : "+current + " - "+chunkRenderData.toString(), BitmapFont.SMALLFONTS);
			}
			else
				FontRenderer2.drawTextUsingSpecificFont(20, XolioWindow.frameH - 68, 0, 16, "Current chunk : "+current + " - No rendering data", BitmapFont.SMALLFONTS);
		}
		//	FontRenderer2.drawTextUsingSpecificFont(20, XolioWindow.frameH - 68, 0, 16, "Current chunk : vbo=" + current.vbo_id + " vboSize=" + (current.vbo_size_normal + current.vbo_size_water) + " needRender=" + current.need_render + " requestable=" + current.requestable
		//			+ " dataPointer=" + current.dataPointer + " etc "+current+" etc2"+current.holder, BitmapFont.SMALLFONTS);
		FontRenderer2.drawTextUsingSpecificFont(20, XolioWindow.frameH - 84, 0, 16, debugInfo, BitmapFont.SMALLFONTS);
		FontRenderer2.drawTextUsingSpecificFont(20, XolioWindow.frameH - 100, 0, 16, "View distance : " + FastConfig.viewDistance + " Vertices(N):" + formatBigAssNumber(worldRenderer.renderedVertices + "") + " Chunks in view : "
				+ formatBigAssNumber("" + worldRenderer.renderedChunks) + " Particles :" + Client.world.particlesHolder.count() + " #FF0000FPS : " + XolioWindow.getFPS(), BitmapFont.SMALLFONTS);
		FontRenderer2.drawTextUsingSpecificFont(20, XolioWindow.frameH - 114, 0, 16, used / 1024 / 1024 + " / " + total / 1024 / 1024 + " mb used", BitmapFont.SMALLFONTS);
		
		FontRenderer2.drawTextUsingSpecificFont(20, XolioWindow.frameH - 130, 0, 16, "VRAM usage : " + getLoadedChunksVramFootprint() + ", " + getLoadedTerrainVramFootprint(), BitmapFont.SMALLFONTS);
		FontRenderer2.drawTextUsingSpecificFont(20, XolioWindow.frameH - 130 - 16, 0, 16, "Player model : " + this.player, BitmapFont.SMALLFONTS);
		 
		if (!Display.isActive() && this.currentOverlay == null)
		{
			focus(false);
			this.changeOverlay(new PauseOverlay(this, null));
		}
	}

	private String getLoadedChunksVramFootprint()
	{
		int nbChunks = 0;
		long octelsTotal = 0;

		ChunksIterator i = Client.world.iterator();
		CubicChunk c;
		while(i.hasNext())
		{
			c = i.next();
			if(c == null)
				continue;
			ChunkRenderData chunkRenderData = c.chunkRenderData;
			nbChunks++;
			if(chunkRenderData != null)
				octelsTotal += chunkRenderData.getVramSize();
		}
		return nbChunks + " chunks, storing " + octelsTotal / 1024 / 1024 + "Mb of vertex data.";
	}

	private String getLoadedTerrainVramFootprint()
	{
		int nbChunks = Client.world.chunkSummaries.all().size();
		long octelsTotal = nbChunks * 256 * 256 * (1 + 1) * 4;

		return nbChunks + " regions, storing " + octelsTotal / 1024 / 1024 + "Mb of data";
	}

	public String formatBigAssNumber(String in)
	{
		String formatted = "";
		for (int i = 0; i < in.length(); i++)
		{
			if (i > 0 && i % 3 == 0)
				formatted = "." + formatted;
			formatted = in.charAt(in.length() - i - 1) + formatted;
		}
		return formatted;
	}
}
