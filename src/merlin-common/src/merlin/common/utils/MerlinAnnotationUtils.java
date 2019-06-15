package merlin.common.utils;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EModelElement;
import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;

import de.ovgu.featureide.fm.core.configuration.Configuration;
import merlin.common.annotations.MerlinAnnotationStructure;
import merlin.common.annotations.presenceCondition.ConditionParser;
import merlin.common.features.FeatureIDEProvider;

/**
 * A collection of utility methods to query ecore models for MERLIN annotations
 */
public class MerlinAnnotationUtils {
	
	public static final String PRODUCTS_FOLDER = "products";
	
	// not exact match, but extended differently depending on the OCL version used	 
	public static final String OCL   = "http://www.eclipse.org/emf/2002/Ecore/OCL/";
	public static final String OCLPIVOT = "http://www.eclipse.org/emf/2002/Ecore/OCL/Pivot";
	public static final String ECORE = "http://www.eclipse.org/emf/2002/Ecore";
	public static final String BODY = "body";
	
	public static EAnnotation getClassMerlinAnnotation(EClassifier cl) {
		for (EAnnotation a : cl.getEAnnotations()) {
			if (a.getSource().equals(MerlinAnnotationStructure.ANNOTATION_NAME)) {
				if (!a.getDetails().containsKey(MerlinAnnotationStructure.INVARIANT)) return a;
			}
		}
		return null;
	}
	
	public static EAnnotation getInvariantMerlinAnnotation(EClassifier cl, String inv) {
		for (EAnnotation a : cl.getEAnnotations()) {
			if (a.getSource().equals(MerlinAnnotationStructure.ANNOTATION_NAME)) {
				if (inv.equals(a.getDetails().get(MerlinAnnotationStructure.INVARIANT))) return a;	
			}
		}
		return null;
	}
	
	public static List<EAnnotation> getModifiers (EModelElement obj) {
		List<EAnnotation> annot = new ArrayList<>();
		
		for (EAnnotation a : obj.getEAnnotations()) {
			if (a.getSource().equals(MerlinAnnotationStructure.MODIFIER_ANNOTATION)) annot.add(a);
		}
		
		return annot;
	}
	
	public static boolean hasModifier(String modifier, List<EAnnotation> modifiers) {
		for (EAnnotation an : modifiers) 
			if (an.getDetails().get(modifier)!=null) return true;
		
		return false;
	}
	
	public static void removeAllMerlinAnnotations(EPackage root) {
		removeMerlinAnnotations(root);
		
		for (EClassifier cl : root.getEClassifiers()) {
			removeMerlinAnnotations(cl);
			if (!(cl instanceof EClass)) continue;
			EClass cls = (EClass)cl;
			for (EStructuralFeature sf : cls.getEStructuralFeatures()) 
				removeMerlinAnnotations(sf);			
		}
	}

	public static void removeMerlinAnnotations(EModelElement element) {
		removeAnnotation(element, MerlinAnnotationStructure.ANNOTATION_NAME);
		removeAnnotation(element, MerlinAnnotationStructure.FEATURE_ANNOTATION );
		removeAnnotation(element, MerlinAnnotationStructure.MODIFIER_ANNOTATION);
	}
	
	private static void removeAnnotation(EModelElement element, String name) {
		EAnnotation merlin = element.getEAnnotation(name);
		while (merlin!=null) {
			element.getEAnnotations().remove(merlin);
			merlin = element.getEAnnotation(name);
		}
	}
	
	public static String getInvariantPresenceCondition(EClass cls, String inv) {
		for (EAnnotation an : cls.getEAnnotations()) {
			if (an.getSource().equals(MerlinAnnotationStructure.ANNOTATION_NAME) && 
				an.getDetails()!=null) {
				
				String invName = an.getDetails().get(MerlinAnnotationStructure.INVARIANT);
				if (invName != null && invName.equals(inv)) {
					String pc = an.getDetails().get(MerlinAnnotationStructure.PRESENCE_CONDITION); 
					return pc!=null ? pc : "true";	// TODO: Should be cls.presenceCondition and pc
				}
			}		
		}
		return "true";	// Should be cls.presenceCondition
	}
	
	public static List<EAnnotation> getModifiers (ENamedElement ne) {			
		List<EAnnotation> modifiers = new ArrayList<EAnnotation>();
		for (EAnnotation an : ne.getEAnnotations()) {
			if (an.getSource().equals(MerlinAnnotationStructure.MODIFIER_ANNOTATION) && an.getDetails()!=null) {
				String   pc = an.getDetails().get(MerlinAnnotationStructure.MODIFIER_CONDITION); 
				if (pc==null) an.getDetails().put(MerlinAnnotationStructure.MODIFIER_CONDITION, "true");
				modifiers.add(an);
			}
		}
		return modifiers;
	}
	
	public static String getPresenceCondition(ENamedElement cls) {		
		for (EAnnotation an : cls.getEAnnotations()) {
			if (an.getSource().equals(MerlinAnnotationStructure.ANNOTATION_NAME) && an.getDetails()!=null) {
				String pc = an.getDetails().get(MerlinAnnotationStructure.PRESENCE_CONDITION); 
				if ( an.getDetails().get(MerlinAnnotationStructure.INVARIANT) == null ) {	// Otherwise it is the presence condition of an invariant
					return pc!=null ? pc : "true";	// TODO: Should be container.presenceCondition and pc
				}
			}
		}
		return "true";
	}
	
	public static boolean checkPresenceConditions(ENamedElement sf, Configuration cfg) {
		EAnnotation merlin = null;
		if (sf instanceof EStructuralFeature) merlin = sf.getEAnnotation(MerlinAnnotationStructure.ANNOTATION_NAME);
		else if (sf instanceof EClass) merlin = MerlinAnnotationUtils.getClassMerlinAnnotation((EClass)sf);
		if (merlin == null) return true;	
		String condition = merlin.getDetails().get(MerlinAnnotationStructure.PRESENCE_CONDITION);
		ConditionParser cp = new ConditionParser(condition, sf, new FeatureIDEProvider(cfg));
		return cp.eval();
	}
	
	public static List<EReference> getCompositions(List<EPackage> packages, Configuration c) {
		List<EReference> comps = new ArrayList<>();		
		for (EPackage p : packages) {
			for (EClassifier cls : p.getEClassifiers()) {
				if (!(cls instanceof EClass)) continue;
				EClass cl = (EClass)cls;
				// Check presence condition
				if (!MerlinAnnotationUtils.checkPresenceConditions(cls, c)) continue;
				for (EReference r : cl.getEReferences()) {
					if (!MerlinAnnotationUtils.checkPresenceConditions(r, c)) continue;
					EAnnotation an = r.getEAnnotation(MerlinAnnotationStructure.MODIFIER_ANNOTATION);
					if (an==null) {
						if (r.isContainment()) comps.add(r);
						continue;	// nothing to do
					}
					String comp = an.getDetails().get("containment");
					String condition = an.getDetails().get(MerlinAnnotationStructure.MODIFIER_CONDITION);
					if (condition != null && !condition.equals("true")) {
						ConditionParser cp = new ConditionParser(condition, cl, new FeatureIDEProvider(c));
						if (!cp.eval()) {	// the modifier has no effect: take what it was
							if (r.isContainment()) comps.add(r);
						} else {
							if (comp!=null && comp.toLowerCase().equals("true")) comps.add(r);	// should put the modifier containment
						}
					} else if (condition.equals("true")) {
						if (comp!=null & comp.toLowerCase().equals("true")) comps.add(r);
					}
				}
			}
		}		
		return comps;
	}
}
