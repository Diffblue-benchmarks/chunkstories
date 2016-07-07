package io.xol.chunkstories.server;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import io.xol.chunkstories.api.input.Input;
import io.xol.chunkstories.api.input.InputsManager;
import io.xol.chunkstories.input.InputVirtual;
import io.xol.chunkstories.input.Inputs;

//(c) 2015-2016 XolioWare Interactive
//http://chunkstories.xyz
//http://xol.io

public class ServerInputsManager implements InputsManager
{
	Set<Input> inputs = new HashSet<Input>();
	Map<Long, Input> inputsMap = new HashMap<Long, Input>();

	public ServerInputsManager(ServerPlayer serverPlayer)
	{
		reload();
	}

	public Input getInputByName(String bindName)
	{
		for (Input keyBind : inputs)
		{
			if (keyBind.getName().equals(bindName))
				return keyBind;
		}
		return null;
	}

	@Override
	public Iterator<Input> getAllInputs()
	{
		return inputs.iterator();
	}

	public Input getInputFromHash(long hash)
	{
		return inputsMap.get(hash);
	}

	public void reload()
	{
		inputs.clear();
		inputsMap.clear();
		
		//Load all keys as virtual ones
		Iterator<Input> i = Inputs.loadKeyBindsIntoManager(this);
		while (i.hasNext())
		{
			Input input = i.next();
			inputs.add(input);
			inputsMap.put(input.getHash(), input);
		}
		
		//Add virtual mouse buttons
		InputVirtual mouseLeft = new InputVirtual("mouse.left", 0);
		inputs.add(mouseLeft);
		inputsMap.put(mouseLeft.getHash(), mouseLeft);
		InputVirtual mouseRight = new InputVirtual("mouse.right", 1);
		inputs.add(mouseRight);
		inputsMap.put(mouseRight.getHash(), mouseRight);
		InputVirtual mouseMiddle = new InputVirtual("mouse.middle", 2);
		inputs.add(mouseMiddle);
		inputsMap.put(mouseMiddle.getHash(), mouseMiddle);
	}

}
