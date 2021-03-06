//
// This file is a part of the Chunk Stories Implementation codebase
// Check out README.md for more information
// Website: http://chunkstories.xyz
//

package xyz.chunkstories.scrap;

import java.nio.ByteBuffer;

import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.lwjgl.BufferUtils;

import xyz.chunkstories.api.gui.GuiRenderer;
import xyz.chunkstories.api.rendering.StateMachine.BlendMode;
import xyz.chunkstories.api.rendering.StateMachine.CullingMode;
import xyz.chunkstories.api.rendering.StateMachine.DepthTestMode;
import xyz.chunkstories.api.rendering.textures.Texture2D;
import xyz.chunkstories.api.rendering.vertex.Primitive;
import xyz.chunkstories.api.rendering.vertex.VertexBuffer;
import xyz.chunkstories.api.rendering.vertex.VertexFormat;
import xyz.chunkstories.api.util.ColorsTools;
import xyz.chunkstories.renderer.OpenGLRenderingContext;
import xyz.chunkstories.renderer.opengl.texture.Texture2DGL;
import xyz.chunkstories.renderer.opengl.texture.TexturesHandler;
import xyz.chunkstories.renderer.opengl.vbo.VertexBufferGL;
import xyz.chunkstories.renderer.opengl.vbo.VertexBufferGL.UploadRegime;

public class GuiRendererImplementation implements GuiRenderer {
	private OpenGLRenderingContext renderingContext;

	public int MAX_ELEMENTS = 1024;
	public ByteBuffer buf;
	public int elementsToDraw = 0;
	public Texture2D currentTexture;
	public boolean alphaBlending = false;
	public boolean useTexture = true;
	public Vector4fc currentColor = new Vector4f(1f, 1f, 1f, 1f);

	// GL stuff
	VertexBuffer guiDrawData = new VertexBufferGL(UploadRegime.FAST);

	public GuiRendererImplementation(OpenGLRenderingContext renderingContext) {
		this.renderingContext = renderingContext;
		// Buffer contains MAX_ELEMENTS of 2 triangles, each defined by 3
		// vertices, themselves defined by 4 floats and 4 bytes : 'xy' positions, and
		// textures coords 'ts'.
		buf = BufferUtils.createByteBuffer((4 * 1 + (2 + 2) * 4) * 3 * 2 * MAX_ELEMENTS);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see io.xol.engine.graphics.util.GuiRenderer#drawBoxWindowsSpace(float,
	 * float, float, float, float, float, float, float,
	 * io.xol.engine.graphics.textures.Texture2D, boolean, boolean,
	 * xyz.chunkstories.api.math.Vector4f)
	 */
	@Override
	public void drawBoxWindowsSpace(float startX, float startY, float endX, float endY, float textureStartX,
			float textureStartY, float textureEndX, float textureEndY, Texture2D texture, boolean alpha,
			boolean textured, Vector4fc color) {
		drawBox((startX / renderingContext.getWindow().getWidth()) * 2 - 1,
				(startY / renderingContext.getWindow().getHeight()) * 2 - 1,
				(endX / renderingContext.getWindow().getWidth()) * 2 - 1,
				(endY / renderingContext.getWindow().getHeight()) * 2 - 1, textureStartX, textureStartY, textureEndX,
				textureEndY, texture, alpha, textured, color);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * io.xol.engine.graphics.util.GuiRenderer#drawBox(float,
	 * float, float, float, float, float, float, float,
	 * io.xol.engine.graphics.textures.Texture2D, boolean, boolean,
	 * xyz.chunkstories.api.math.Vector4f)
	 */
	@Override
	public void drawBox(float startX, float startY, float width, float height, float textureStartX,
			float textureStartY, float textureEndX, float textureEndY, Texture2D texture, boolean alpha,
			boolean textured, Vector4fc color) {
		float endX = startX + width;
		float endY = startY + height;
		drawBox((startX / renderingContext.getWindow().getWidth()) * 2 - 1,
				(startY / renderingContext.getWindow().getHeight()) * 2 - 1,
				(endX / renderingContext.getWindow().getWidth()) * 2 - 1,
				(endY / renderingContext.getWindow().getHeight()) * 2 - 1, textureStartX, textureStartY, textureEndX,
				textureEndY, texture, alpha, textured, color);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see io.xol.engine.graphics.util.GuiRenderer#drawBox(float, float, float,
	 * float, float, float, float, float, io.xol.engine.graphics.textures.Texture2D,
	 * boolean, boolean, xyz.chunkstories.api.math.Vector4f)
	 */
	@Override
	public void drawBox(float startX, float startY, float endX, float endY, float textureStartX, float textureStartY,
			float textureEndX, float textureEndY, Texture2D texture, boolean alpha, boolean textured, Vector4fc color) {
		// Maximum buffer size was reached, in clear the number of vertices in the
		// buffer = 6 * max elements, max elements being the max amount of drawBox calls
		// until drawBuffer()
		if (elementsToDraw >= 6 * MAX_ELEMENTS)
			drawBuffer();

		if (color != null && color.w() < 1) {
			alpha = true; // Force blending if alpha < 1
		}

		setState(texture, alpha, texture != null, color);

		addVertice(startX, startY, textureStartX, textureStartY);
		addVertice(startX, endY, textureStartX, textureEndY);
		addVertice(endX, endY, textureEndX, textureEndY);

		addVertice(startX, startY, textureStartX, textureStartY);
		addVertice(endX, startY, textureEndX, textureStartY);
		addVertice(endX, endY, textureEndX, textureEndY);

	}

	protected void addVertice(float vx, float vy, float t, float s) {
		// 2x4 bytes of float vertex position
		buf.putFloat(vx);
		buf.putFloat(vy);
		// 2x4 bytes of float texture coords
		buf.putFloat(t);
		buf.putFloat(s);
		// 1x4 bytes of ubyte norm color data
		buf.put((byte) (int) (currentColor.x() * 255));
		buf.put((byte) (int) (currentColor.y() * 255));
		buf.put((byte) (int) (currentColor.z() * 255));
		buf.put((byte) (int) (currentColor.w() * 255));
		elementsToDraw++;
	}

	/**
	 * Called before adding anything to the drawBuffer, if it's the same kind as
	 * before we keep filling it, if not we empty it first by drawing the current
	 * buffer.
	 */
	public void setState(Texture2D texture, boolean alpha, boolean textureEnabled, Vector4fc color) {
		if (color == null)
			color = new Vector4f(1.0F);

		// Only texture changes trigger a buffer flush now
		if (texture != currentTexture || useTexture != textureEnabled) {
			drawBuffer();
		}

		currentTexture = texture;
		alphaBlending = alpha;
		currentColor = color;
		useTexture = textureEnabled;
	}

	/**
	 * Draw the data in the buffer.
	 */
	public void drawBuffer() {
		if (elementsToDraw == 0)
			return;

		// Upload data and draw it.
		buf.flip();
		this.guiDrawData.uploadData(buf);
		buf.clear();

		renderingContext.useShader("gui");
		renderingContext.currentShader().setUniform1f("useTexture", useTexture ? 1f : 0f);

		renderingContext.bindTexture2D("sampler", currentTexture);

		renderingContext.setDepthTestMode(DepthTestMode.DISABLED);
		// TODO depreacated alpha_test mode

		renderingContext.setBlendMode(BlendMode.MIX);

		renderingContext.setCullingMode(CullingMode.DISABLED);
		renderingContext.bindAttribute("vertexIn", guiDrawData.asAttributeSource(VertexFormat.FLOAT, 2, 20, 0));
		renderingContext.bindAttribute("texCoordIn", guiDrawData.asAttributeSource(VertexFormat.FLOAT, 2, 20, 8));
		renderingContext.bindAttribute("colorIn",
				guiDrawData.asAttributeSource(VertexFormat.NORMALIZED_UBYTE, 4, 20, 16));

		renderingContext.draw(Primitive.TRIANGLE, 0, elementsToDraw);

		elementsToDraw = 0;
	}

	public void free() {
		guiDrawData.destroy();
	}

	// TODO remove completely

	public void renderTexturedRect(float xpos, float ypos, float w, float h, String tex) {
		renderTexturedRotatedRect(xpos, ypos, w, h, 0f, 0f, 0f, 1f, 1f, tex);
	}

	public void renderTexturedRectAlpha(float xpos, float ypos, float w, float h, String tex, float a) {
		renderTexturedRotatedRectAlpha(xpos, ypos, w, h, 0f, 0f, 0f, 1f, 1f, tex, a);
	}

	public void renderTexturedRect(float xpos, float ypos, float w, float h, float tcsx, float tcsy, float tcex,
			float tcey, float texSize, String tex) {
		renderTexturedRotatedRect(xpos, ypos, w, h, 0f, tcsx / texSize, tcsy / texSize, tcex / texSize, tcey / texSize,
				tex);
	}

	public void renderTexturedRotatedRect(float xpos, float ypos, float w, float h, float rot, float tcsx, float tcsy,
			float tcex, float tcey, String tex) {
		renderTexturedRotatedRectAlpha(xpos, ypos, w, h, rot, tcsx, tcsy, tcex, tcey, tex, 1f);
	}

	public void renderTexturedRotatedRectAlpha(float xpos, float ypos, float w, float h, float rot, float tcsx,
			float tcsy, float tcex, float tcey, String tex, float a) {
		renderTexturedRotatedRectRVBA(xpos, ypos, w, h, rot, tcsx, tcsy, tcex, tcey, tex, 1f, 1f, 1f, a);
	}

	public void renderTexturedRotatedRectRVBA(float xpos, float ypos, float w, float h, float rot, float tcsx,
			float tcsy, float tcex, float tcey, String textureName, float r, float v, float b, float a) {
		if (textureName.startsWith("internal://"))
			textureName = textureName.substring("internal://".length());
		else if (textureName.startsWith("gameDir://"))
			textureName = textureName.substring("gameDir://".length());// GameDirectory.getGameFolderPath() + "/" +
																		// tex.substring("gameDir://".length());
		else if (textureName.contains("../"))
			textureName = ("./" + textureName.replace("../", "") + ".png");
		else
			textureName = ("./textures/" + textureName + ".png");

		Texture2DGL texture = TexturesHandler.getTexture(textureName);

		texture.setLinearFiltering(false);
		// TexturesHandler.mipmapLevel(texture, -1);

		drawBoxWindowsSpace(xpos - w / 2, ypos + h / 2, xpos + w / 2, ypos - h / 2, tcsx, tcsy, tcex, tcey, texture,
				false, true, new Vector4f(r, v, b, a));
	}

	public void renderColoredRect(float xpos, float ypos, float w, float h, float rot, String hex, float a) {
		int rgb[] = ColorsTools.hexToRGB(hex);
		renderColoredRect(xpos, ypos, w, h, rot, rgb[0] / 255f, rgb[1] / 255f, rgb[2] / 255f, a);
	}

	public void renderColoredRect(float xpos, float ypos, float w, float h, float rot, float r, float v, float b,
			float a) {
		drawBoxWindowsSpace(xpos - w / 2, ypos + h / 2, xpos + w / 2, ypos - h / 2, 0, 0, 0, 0, null, false, true,
				new Vector4f(r, v, b, a));
	}

	@Override
	public void drawCorneredBoxTiled(float posx, float posy, float width, float height, int cornerSize,
			Texture2D texture, int textureSize, int scale) {
		GuiRenderer guiRenderer = this;

		float topLeftCornerX = posx;// - width / 2;
		float topLeftCornerY = posy;// - height / 2;

		float botRightCornerX = posx + width;// / 2;
		float botRightCornerY = posy + height;// / 2;

		// Debug helper
		// guiRenderer.drawBoxWindowsSpace(topLeftCornerX, topLeftCornerY,
		// botRightCornerX, botRightCornerY, 0, 0, 0, 0, null, true, false, new
		// Vector4f(1.0, 1.0, 0.0, 1.0));

		int cornerSizeScaled = scale * cornerSize;

		float textureSizeInternal = textureSize - cornerSize * 2;

		float insideWidth = width - cornerSizeScaled * 2;
		float insideHeight = height - cornerSizeScaled * 2;

		float texCoordInsideTopLeft = ((float) cornerSize) / textureSize;
		float texCoordInsideBottomRight = ((float) (textureSize - cornerSize)) / textureSize;

		// Fill the inside of the box
		for (int fillerX = 0; fillerX < insideWidth; fillerX += textureSizeInternal * scale) {
			for (int fillerY = 0; fillerY < insideHeight; fillerY += textureSizeInternal * scale) {
				float toFillX = Math.min(textureSizeInternal * scale, insideWidth - fillerX);
				float toFillY = Math.min(textureSizeInternal * scale, insideHeight - fillerY);

				float startX = topLeftCornerX + cornerSizeScaled + fillerX;
				float startY = topLeftCornerY + cornerSizeScaled + fillerY;

				guiRenderer.drawBoxWindowsSpace(startX, startY, startX + toFillX, startY + toFillY,
						texCoordInsideTopLeft, texCoordInsideTopLeft + toFillY / textureSize / scale,
						texCoordInsideTopLeft + toFillX / textureSize / scale, texCoordInsideTopLeft, texture, true,
						false, new Vector4f(1.0f, 1.0f, 1.0f, 1.0f));
			}
		}

		// Fill the horizontal sides
		for (int fillerX = 0; fillerX < insideWidth; fillerX += textureSizeInternal * scale) {
			float toFillX = Math.min(textureSizeInternal * scale, insideWidth - fillerX);

			float startX = topLeftCornerX + cornerSizeScaled + fillerX;
			float startY = topLeftCornerY;

			guiRenderer.drawBoxWindowsSpace(startX, startY + height - cornerSizeScaled, startX + toFillX,
					startY + height, texCoordInsideTopLeft, texCoordInsideTopLeft,
					texCoordInsideTopLeft + toFillX / textureSize / scale, 0, texture, true, false,
					new Vector4f(1.0f, 1.0f, 1.0f, 1.0f));

			guiRenderer.drawBoxWindowsSpace(startX, startY, startX + toFillX, startY + cornerSizeScaled,
					texCoordInsideTopLeft, 1.0f, texCoordInsideTopLeft + toFillX / textureSize / scale,
					texCoordInsideBottomRight, texture, true, false, new Vector4f(1.0f, 1.0f, 1.0f, 1.0f));
		}

		// Fill the vertical sides
		for (int fillerY = 0; fillerY < insideHeight; fillerY += textureSizeInternal * scale) {
			float toFillY = Math.min(textureSizeInternal * scale, insideHeight - fillerY);

			float startY = topLeftCornerY + cornerSizeScaled + fillerY;
			float startX = topLeftCornerX;

			guiRenderer.drawBoxWindowsSpace(startX, startY, startX + cornerSizeScaled, startY + toFillY, 0,
					texCoordInsideBottomRight - (textureSizeInternal * scale - toFillY) / textureSize / scale,
					texCoordInsideTopLeft, texCoordInsideTopLeft, texture, true, false,
					new Vector4f(1.0f, 1.0f, 1.0f, 1.0f));

			guiRenderer.drawBoxWindowsSpace(startX + width - cornerSizeScaled, startY, startX + width, startY + toFillY,
					texCoordInsideBottomRight,
					texCoordInsideBottomRight - (textureSizeInternal * scale - toFillY) / textureSize / scale, 1.0f,
					texCoordInsideTopLeft, texture, true, false, new Vector4f(1.0f, 1.0f, 1.0f, 1.0f));
		}

		// Fill the 4 corners
		guiRenderer.drawBoxWindowsSpace(topLeftCornerX, botRightCornerY - cornerSizeScaled,
				topLeftCornerX + cornerSizeScaled, botRightCornerY, 0, texCoordInsideTopLeft, texCoordInsideTopLeft, 0,
				texture, true, false, new Vector4f(1.0f, 1.0f, 1.0f, 1.0f));

		guiRenderer.drawBoxWindowsSpace(topLeftCornerX, topLeftCornerY, topLeftCornerX + cornerSizeScaled,
				topLeftCornerY + cornerSizeScaled, 0, 1.0f, texCoordInsideTopLeft, texCoordInsideBottomRight, texture,
				true, false, new Vector4f(1.0f, 1.0f, 1.0f, 1.0f));

		guiRenderer.drawBoxWindowsSpace(botRightCornerX - cornerSizeScaled, botRightCornerY - cornerSizeScaled,
				botRightCornerX, botRightCornerY, texCoordInsideBottomRight, texCoordInsideTopLeft, 1.0f, 0, texture,
				true, false, new Vector4f(1.0f, 1.0f, 1.0f, 1.0f));

		guiRenderer.drawBoxWindowsSpace(botRightCornerX - cornerSizeScaled, topLeftCornerY, botRightCornerX,
				topLeftCornerY + cornerSizeScaled, texCoordInsideBottomRight, 1.0f, 1.0f, texCoordInsideBottomRight,
				texture, true, false, new Vector4f(1.0f, 1.0f, 1.0f, 1.0f));

	}
}
