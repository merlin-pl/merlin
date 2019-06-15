package merlin.common.annotations.modifiers;

import java.util.Map;

import org.eclipse.emf.ecore.EStructuralFeature;

public abstract class FieldModifier extends Modifier{

	public FieldModifier(Map<String, IModifier> modiHandlers) {
		super(modiHandlers);
	}
	
	@Override
	public Class<?> appliesOn() {
		return EStructuralFeature.class;
	}

}
