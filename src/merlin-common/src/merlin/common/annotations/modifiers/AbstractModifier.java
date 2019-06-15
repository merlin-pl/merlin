package merlin.common.annotations.modifiers;

import java.util.Map;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.ENamedElement;

public class AbstractModifier extends ClassModifier {
	
	public AbstractModifier(Map<String, IModifier> modiHandlers) {
		super(modiHandlers);
	}

	@Override
	public String modifier() {
		return "abstract";
	}
	
	public void exec(String val, ENamedElement ne) {
		if (!(ne instanceof EClass)) return;
		EClass c = (EClass)ne;
		if (val.equalsIgnoreCase("true"))  c.setAbstract(true);
		if (val.equalsIgnoreCase("false")) c.setAbstract(false);
	}
}
