package merlin.common.annotations.modifiers;

import java.util.Map;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EPackage;

public class ExtendsModifier extends ClassModifier {
	
	private EPackage context;
	
	public ExtendsModifier(Map<String, IModifier> modiHandlers) {
		super(modiHandlers);
	}
	
	@Override
	public String modifier() {
		return Modifiers.EXTENDS_MODIFIER;
	}
	
	@Override
	public void exec(String val, ENamedElement ne) {
		if (!(ne instanceof EClass)) return;
		EClass c = (EClass)ne;
		String[] supers = val.split("\\s+");
		this.context = (EPackage)c.eContainer();
		for (String cls : supers) {
			EClass sup = this.getEClass(cls);
			if (sup!=null) c.getESuperTypes().add(sup);
		}
	}

	private EClass getEClass(String cls) {		
		EClassifier cl = this.context.getEClassifier(cls);
		if (cl!=null && (cl instanceof EClass)) return (EClass)cl;
		return null;
	}
}
