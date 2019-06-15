package merlin.common.annotations.modifiers;

import java.util.Map;

import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EStructuralFeature;

public class MaxModifier extends FieldModifier {
	
	public MaxModifier(Map<String, IModifier> modiHandlers) {
		super(modiHandlers);
	}

	@Override
	public String modifier() {
		return Modifiers.MAX_MODIFIER;
	}
	
	public void exec(String val, ENamedElement ne) {
		if (!(ne instanceof EStructuralFeature)) return;
		EStructuralFeature c = (EStructuralFeature)ne;
		try {
			if (val.equals("*")) val = "-1";
			int n = Integer.parseInt(val);
			c.setUpperBound(n);
		} catch (NumberFormatException nfe) {
			System.err.println("[merlin] Error: "+nfe+" is not a valid min cardinality!");
		}
	}
}
