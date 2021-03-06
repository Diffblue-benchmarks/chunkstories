//
// This file is a part of the Chunk Stories Implementation codebase
// Check out README.md for more information
// Website: http://chunkstories.xyz
//

package xyz.chunkstories.client.net.packets;

import xyz.chunkstories.api.content.OnlineContentTranslator;
import xyz.chunkstories.api.net.PacketReceptionContext;
import xyz.chunkstories.api.net.PacketSender;
import xyz.chunkstories.client.net.ClientPacketsEncoderDecoder;
import xyz.chunkstories.net.packets.PacketSendWorldInfo;
import xyz.chunkstories.world.WorldInfoUtilKt;

import java.io.DataInputStream;
import java.io.IOException;

public class PacketInitializeRemoteWorld extends PacketSendWorldInfo {

	public void process(PacketSender sender, DataInputStream in, PacketReceptionContext processor) throws IOException {
		String initializationString = in.readUTF();

		worldInfo = WorldInfoUtilKt.deserializeWorldInfo(initializationString);

		if (processor instanceof ClientPacketsEncoderDecoder) {
			processor.logger().info("Received World initialization packet");
			ClientPacketsEncoderDecoder cpp = (ClientPacketsEncoderDecoder) processor;

			OnlineContentTranslator contentTranslator = cpp.getContentTranslator();
			if (contentTranslator == null) {
				processor.logger().error("Can't initialize a world without a ContentTranslator initialized first!");
				return;
			}

			//TODO remake this mechanism but make it actually any good
			throw new RuntimeException("TODO");
			/*
			IngameClientRemoteHost client = (IngameClientRemoteHost) cpp.getContext();
			Fence fence = client.getGameWindow().queueSynchronousTask(new Runnable() {
				@Override
				public void run() {
					WorldClientRemote world;
					try {
						world = new WorldClientRemote(client, worldInfo, contentTranslator, cpp.getConnection());
						client.changeWorld(world);

						cpp.getConnection().handleSystemRequest("world/ok");
					} catch (WorldLoadingException e) {
						client.exitToMainMenu(e.getMessage());
					}
				}
			});

			fence.traverse();*/
		}
	}
}
