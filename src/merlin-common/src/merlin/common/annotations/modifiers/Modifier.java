package merlin.common.annotations.modifiers;

import java.util.Map;

public abstract class Modifier implements IModifier{

	public Modifier(Map<String, IModifier> modiHandlers) {
		modiHandlers.put(this.modifier(), this);
	}	
}
