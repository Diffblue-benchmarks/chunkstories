//
// This file is a part of the Chunk Stories API codebase
// Check out README.md for more information
// Website: http://chunkstories.xyz
//

package io.xol.engine.concurrency;

import io.xol.chunkstories.api.util.concurrency.Fence;

public class TrivialFence implements Fence {

	@Override
	public void traverse() {
		//Do absolutely nothing
	}

}
