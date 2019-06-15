package merlin.common.annotations.modifiers;

import java.util.Map;

import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EStructuralFeature;

public class MinModifier extends FieldModifier {
	
	public MinModifier(Map<String, IModifier> modiHandlers) {
		super(modiHandlers);
	}

	@Override
	public String modifier() {
		return Modifiers.MIN_MODIFIER;
	}
	
	public void exec(String val, ENamedElement ne) {
		if (!(ne instanceof EStructuralFeature)) return;
		EStructuralFeature c = (EStructuralFeature)ne;
		try {
			int n = Integer.parseInt(val);
			c.setLowerBound(n);
		} catch (NumberFormatException nfe) {
			System.err.println("[merlin] Error: "+nfe+" is not a valid min cardinality!");
		}
	}
}
