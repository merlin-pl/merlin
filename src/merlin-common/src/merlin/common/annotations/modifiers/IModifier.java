package merlin.common.annotations.modifiers;

import org.eclipse.emf.ecore.ENamedElement;

public interface IModifier {

	public abstract Class<?> appliesOn();
	
	public abstract String modifier();
	
	public abstract void exec(String val, ENamedElement c);
}
