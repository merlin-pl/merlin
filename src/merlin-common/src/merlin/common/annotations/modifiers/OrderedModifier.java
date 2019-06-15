package merlin.common.annotations.modifiers;

import java.util.Map;

import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EStructuralFeature;

public class OrderedModifier extends FieldModifier {
	
	public OrderedModifier(Map<String, IModifier> modiHandlers) {
		super(modiHandlers);
	}

	@Override
	public String modifier() {
		return Modifiers.ORDERED_MODIFIER;
	}
	
	public void exec(String val, ENamedElement ne) {
		if (!(ne instanceof EStructuralFeature)) return;
		EStructuralFeature c = (EStructuralFeature)ne;
		if (val.equalsIgnoreCase("true")) c.setOrdered(true);
		else c.setOrdered(false);
	}
}
