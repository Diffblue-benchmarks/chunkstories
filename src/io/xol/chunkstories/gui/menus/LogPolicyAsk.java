package io.xol.chunkstories.gui.menus;

import io.xol.chunkstories.api.gui.Overlay;
import io.xol.chunkstories.client.Client;
import io.xol.chunkstories.gui.OverlayableScene;
import io.xol.engine.base.ObjectRenderer;
import io.xol.engine.base.XolioWindow;
import io.xol.engine.font.BitmapFont;
import io.xol.engine.font.FontRenderer2;
import io.xol.engine.font.TrueTypeFont;
import io.xol.engine.gui.ClickableButton;
import io.xol.engine.gui.FocusableObjectsHandler;

//(c) 2015-2016 XolioWare Interactive
//http://chunkstories.xyz
//http://xol.io

public class LogPolicyAsk extends Overlay
{
	public LogPolicyAsk(OverlayableScene scene, Overlay parent)
	{
		super(scene, parent);
		guiHandler.add(acceptButton);
		guiHandler.add(denyButton);
	}

	FocusableObjectsHandler guiHandler = new FocusableObjectsHandler();
	ClickableButton acceptButton = new ClickableButton(0, 0, 300, 32, ("I'm ok with this"), BitmapFont.SMALLFONTS, 1);
	ClickableButton denyButton = new ClickableButton(0, 0, 300, 32, ("No thanks."), BitmapFont.SMALLFONTS, 1);
	
	String message = "English: \n"
			+ "Welcome to the indev version of Chunk Stories !\n"
			+ "The whole point of having an early access title is finding and fixing bugs and crashes, and this "
			+ "often requires you sending us informations about your computer and how the game runs on it.\n"
			+ "We have an "
			+ "automatic log uploading system that uploads your .log file after you're done playing. This file can contain "
			+ "information about your game path ( reflecting likely your username ), your operation system, your CPU/RAM/GPU combo,"
			+ "your IP address (we have that one already think about it) and whatever driver/crashes related stuff it may encounter during runtime."
			+ "\nObviously the only thing we'll ever use these files for is debuging purposes and you can chose wether you are ok with that or not."
			+ "\n\n"
			+ "Fran�ais:  \n"
			+ "Bienvenue sur l'alpha de Chunk Stories !\n"
			+ "Tout l'int�r�t d'une version early access est de trouver et r�soudre les bugs et crashs, et ceci requiert souvent "
			+ "de nous envoyer des informations sur votre ordinateur et sur comment le jeu fonctionne dessus.\n"
			+ "Nous avons un syst�me d'envoi de logs automatique qui s'active une fois le jeu ferm�. Ces fichiers contiennent des "
			+ "informations sur le r�pertoire d'installation du jeu ( refl�tant votre username ), votre syst�me d'exploitation, votre "
			+ "configuration mat�rielle, votre addresse IP ( on l'a d�j� ) et quelquonque erreur/crash li� aux drivers que le jeu peut "
			+ "recontrer pendant son ex�cution.\n"
			+ "Evidement la seule utilisation que nous auront pour ces fichiers sera le d�bbugage et vous pouvez choisir de d�sactiver cette fonctionalit�."
			;
	
	@Override
	public void drawToScreen(int positionStartX, int positionStartY, int width, int height)
	{
		ObjectRenderer.renderColoredRect(XolioWindow.frameW / 2, XolioWindow.frameH / 2, XolioWindow.frameW, XolioWindow.frameH, 0, "000000", 0.5f);
		
		FontRenderer2.drawTextUsingSpecificFont(30, XolioWindow.frameH-64, 0, 64, "Chunk Stories indev log policy", BitmapFont.SMALLFONTS);
		
		int linesTaken = TrueTypeFont.arial12.getLinesHeight(message, (width-128) / 2 );
		float scaling = 2;
		if(linesTaken*32 > height)
			scaling  = 1f;
		
		TrueTypeFont.arial12.drawString(30, XolioWindow.frameH-128, message, scaling, width-128, scaling);
		
		//FontRenderer2.drawTextUsingSpecificFont(30, 100, 0, 32, message, BitmapFont.SMALLFONTS);
		//FontRenderer2.setLengthCutoff(false, width - 128);
		
		acceptButton.setPos(XolioWindow.frameW/2 - 256, XolioWindow.frameH / 4 - 32);
		acceptButton.draw();

		if (acceptButton.clicked())
		{
			mainScene.changeOverlay(this.parent);
			Client.clientConfig.setProp("log-policy", "send");
			Client.clientConfig.save();
		}
		
		denyButton.setPos(XolioWindow.frameW/2 + 256, XolioWindow.frameH / 4 - 32);
		denyButton.draw();

		if (denyButton.clicked())
		{
			mainScene.changeOverlay(this.parent);
			Client.clientConfig.setProp("log-policy", "dont");
			Client.clientConfig.save();
		}
	}
	
	public boolean onClick(int posx, int posy, int button)
	{
		if (button == 0)
			guiHandler.handleClick(posx, posy);
		return true;
	}

}
