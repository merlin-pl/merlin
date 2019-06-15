package merlin.common.annotations.modifiers;

import java.util.Map;

import org.eclipse.emf.ecore.EClass;

public abstract class ClassModifier extends Modifier{

	public ClassModifier(Map<String, IModifier> modiHandlers) {
		super(modiHandlers);
	}
	
	@Override
	public Class<?> appliesOn() {
		return EClass.class;
	}

}
