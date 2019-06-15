package merlin.common.annotations.modifiers;

import java.util.Map;

import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EReference;

public class ContainmentModifier extends FieldModifier {
	
	public ContainmentModifier(Map<String, IModifier> modiHandlers) {
		super(modiHandlers);
	}

	@Override
	public String modifier() {
		return Modifiers.CONTAINMENT_MODIFIER;
	}
	
	public void exec(String val, ENamedElement ne) {
		if (!(ne instanceof EReference)) return;
		EReference c = (EReference)ne;
		if (val.equalsIgnoreCase("true")) c.setContainment(true);
		else c.setContainment(false);
	}
}
