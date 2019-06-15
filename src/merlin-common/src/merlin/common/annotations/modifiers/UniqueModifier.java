package merlin.common.annotations.modifiers;

import java.util.Map;

import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EStructuralFeature;

public class UniqueModifier extends FieldModifier {
	
	public UniqueModifier(Map<String, IModifier> modiHandlers) {
		super(modiHandlers);
	}

	@Override
	public String modifier() {
		return Modifiers.UNIQUE_MODIFIER;
	}
	
	public void exec(String val, ENamedElement ne) {
		if (!(ne instanceof EStructuralFeature)) return;
		EStructuralFeature c = (EStructuralFeature)ne;
		if (val.equalsIgnoreCase("true")) c.setUnique(true);
		else c.setUnique(false);
	}
}
