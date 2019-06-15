package merlin.featureide.composer;

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EStructuralFeature;

import de.ovgu.featureide.fm.core.configuration.Configuration;
import merlin.common.annotations.MerlinAnnotationStructure;
import merlin.common.annotations.modifiers.AbstractModifier;
import merlin.common.annotations.modifiers.ContainmentModifier;
import merlin.common.annotations.modifiers.ExtendsModifier;
import merlin.common.annotations.modifiers.IModifier;
import merlin.common.annotations.modifiers.InterfaceModifier;
import merlin.common.annotations.modifiers.MaxModifier;
import merlin.common.annotations.modifiers.MinModifier;
import merlin.common.annotations.modifiers.OrderedModifier;
import merlin.common.annotations.modifiers.ReduceModifier;
import merlin.common.annotations.modifiers.UniqueModifier;
import merlin.common.annotations.presenceCondition.ConditionParser;
import merlin.common.features.FeatureIDEProvider;

public class ModifierExecuter {

	private Map<String, IModifier> modiHandlers = new LinkedHashMap<>();
	
	/**
	 * Creates all handlers for modifiers
	 */
	public ModifierExecuter() {
		new AbstractModifier(this.modiHandlers);
		new ExtendsModifier(this.modiHandlers);
		new ReduceModifier(this.modiHandlers);
		new InterfaceModifier(this.modiHandlers);
		new MinModifier(this.modiHandlers);
		new MaxModifier(this.modiHandlers);
		new ContainmentModifier(this.modiHandlers);
		new OrderedModifier(this.modiHandlers);
		new UniqueModifier(this.modiHandlers);
	}
	
	/**
	 * Modifies class c according to its possible modifier annotations
	 * @param c
	 */
	public void exec(EClass c, Configuration cfg) {
		this.execAllModifiers(c, cfg);
		
		// Now execute modifiers on features
		for (EStructuralFeature sf : c.getEStructuralFeatures()) {
			this.execAllModifiers(sf, cfg);
		}
	}

	private void execAllModifiers(ENamedElement c, Configuration cfg) {
		EAnnotation an = c.getEAnnotation(MerlinAnnotationStructure.MODIFIER_ANNOTATION);
		if (an==null) return;	// nothing to do
		String condition = an.getDetails().get(MerlinAnnotationStructure.MODIFIER_CONDITION);
		if (condition != null && !condition.equals("true")) {
			ConditionParser cp = new ConditionParser(condition, c, new FeatureIDEProvider(cfg));
			if (!cp.eval()) return;		
		}
		//List<EReference> refs = MMUtils.getCompositions((EPackage)EcoreUtil.getRootContainer(c), cfg);
		//System.out.println("[merlin] Cfg "+cfg.getSelectedFeatureNames()+" comps = "+refs);
		// Now execute all modifiers
		for (String mod : an.getDetails().keySet()) {
			this.handleModifier(mod, an.getDetails().get(mod), c);
		}
	}

	private void handleModifier(String mod, String val, ENamedElement c) {
		if (this.modiHandlers.containsKey(mod)) {
			IModifier m = this.modiHandlers.get(mod);
			if (compatible(c, m)) {
				m.exec(val, c);
			}
		}
	}

	private boolean compatible(ENamedElement c, IModifier m) {
		Class<?> cls = c.getClass();
		return m.appliesOn().isAssignableFrom(cls);
	}
}
