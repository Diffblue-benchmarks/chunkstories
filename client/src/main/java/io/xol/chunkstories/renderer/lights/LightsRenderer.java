package io.xol.chunkstories.renderer.lights;

import io.xol.chunkstories.api.math.vector.sp.Vector3fm;
import io.xol.chunkstories.api.rendering.RenderingInterface;
import io.xol.chunkstories.api.rendering.RenderingInterface.LightsAccumulator;
import io.xol.chunkstories.api.rendering.lightning.Light;
import io.xol.chunkstories.api.rendering.lightning.SpotLight;
import io.xol.engine.graphics.RenderingContext;
import io.xol.engine.graphics.shaders.ShaderProgram;
import io.xol.engine.graphics.shaders.ShadersLibrary;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

//(c) 2015-2017 XolioWare Interactive
//http://chunkstories.xyz
//http://xol.io

public class LightsRenderer implements LightsAccumulator
{
	//private final RenderingContext renderingContext;
	
	int lightsBuffer = 0;
	ShaderProgram lightShader;
	private List<Light> lights = new LinkedList<Light>();

	public LightsRenderer(RenderingContext renderingContext)
	{
		//this.renderingContext = renderingContext;
	}

	public void queueLight(Light light)
	{
		lights.add(light);
	}

	public Iterator<Light> getAllLights()
	{
		return lights.iterator();
	}
	
	private void renderDefferedLight(RenderingInterface renderingContext, Light light)
	{
		// Light culling
		if (!lightInFrustrum(renderingContext, light))
			return;

		lightShader.setUniform1f("lightDecay[" + lightsBuffer + "]", light.getDecay());
		lightShader.setUniform3f("lightPos[" + lightsBuffer + "]", light.getPosition());
		lightShader.setUniform3f("lightColor[" + lightsBuffer + "]", light.getColor());
		if (light instanceof SpotLight)
		{
			SpotLight spotlight = (SpotLight)light;
			lightShader.setUniform3f("lightDir[" + lightsBuffer + "]", spotlight.getDirection());
			lightShader.setUniform1f("lightAngle[" + lightsBuffer + "]", (float) (spotlight.getAngle() / 180 * Math.PI));
		}
		else
			lightShader.setUniform1f("lightAngle[" + lightsBuffer + "]", 0f);

		//TexturesHandler.nowrap("res/textures/flashlight.png");

		lightsBuffer++;
		if (lightsBuffer == 64)
		{
			lightShader.setUniform1i("lightsToRender", lightsBuffer);
			renderingContext.drawFSQuad();
			//drawFSQuad();
			lightsBuffer = 0;
		}
	}

	private boolean lightInFrustrum(RenderingInterface renderingContext, Light light)
	{
		return renderingContext.getCamera().isBoxInFrustrum(new Vector3fm(light.getPosition().getX() - light.getDecay(), light.getPosition().getY() - light.getDecay(), light.getPosition().getZ() - light.getDecay()), new Vector3fm(light.getDecay() * 2f, light.getDecay() * 2f, light.getDecay() * 2f));
	}
	
	public void renderPendingLights(RenderingInterface renderingContext)
	{
		lightShader = ShadersLibrary.getShaderProgram("light");
		lightsBuffer = 0;
		//Render entities lights
		Iterator<Light> lightsIterator = lights.iterator();
		while(lightsIterator.hasNext())
		{
			renderDefferedLight(renderingContext, lightsIterator.next());
			lightsIterator.remove();
		}
		//Render particles's lights
		//Client.world.particlesHolder.renderLights(this);
		// Render remaining lights
		if (lightsBuffer > 0)
		{
			lightShader.setUniform1i("lightsToRender", lightsBuffer);
			renderingContext.drawFSQuad();
		}
	}
}