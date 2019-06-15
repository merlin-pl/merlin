package merlin.common.annotations.modifiers;

import java.util.Map;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.ENamedElement;

public class InterfaceModifier extends ClassModifier {
	
	public InterfaceModifier(Map<String, IModifier> modiHandlers) {
		super(modiHandlers);
	}

	@Override
	public String modifier() {
		return Modifiers.INTERFACE_MODIFIER;
	}
	
	public void exec(String val, ENamedElement ne) {
		if (!(ne instanceof EClass)) return;
		EClass c = (EClass)ne;
		if (val.equalsIgnoreCase("true"))  c.setInterface(true);
		if (val.equalsIgnoreCase("false")) c.setInterface(false);
	}
}
