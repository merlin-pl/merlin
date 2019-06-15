package merlin.analysis.validate.annotations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EModelElement;
import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;

import merlin.common.analysis.FeatureSolver;
import merlin.common.annotations.MerlinAnnotationStructure;
import merlin.common.annotations.modifiers.Modifiers;
import merlin.common.features.IFeatureProvider;
import merlin.common.issues.IssueLevel;
import merlin.common.issues.ValidationIssue;
import merlin.common.utils.MerlinAnnotationUtils;

public class ModifierCheck extends AnnotationCheck{

	public ModifierCheck(IFeatureProvider pr) {
		super(pr);
	}
	
	public Class<?> appliesAt() {
		return ENamedElement.class;
	}
	
	public List<ValidationIssue> check(EObject obj, boolean existingErrors) {
		List<ValidationIssue> issues = new ArrayList<>();
		
		if (!(obj instanceof EModelElement)) return issues;
		EModelElement me = (EModelElement)obj;
		
		List<EAnnotation> modifiers = MerlinAnnotationUtils.getModifiers(me);
		
		issues.addAll(this.checkDuplicates((ENamedElement)me, modifiers));
		issues.addAll(this.checkConflicting((ENamedElement)me, modifiers));
		
		return issues;
	}

	private Collection<ValidationIssue> checkConflicting(ENamedElement obj, List<EAnnotation> modifiers) {
		List<ValidationIssue> vi = new ArrayList<>();
		
		List<String> checked = new ArrayList<>();
		 
		for (String modifier : Modifiers.allModifiers()) {
			if (checked.contains(modifier)) continue;
			checked.add(modifier);
			
			if (MerlinAnnotationUtils.hasModifier(modifier, modifiers)) {
				List<String> conflicting = Modifiers.conflictsWith(modifier);

				for (String conflict : conflicting) {
					if (checked.contains(conflict)) continue;
					checked.add(conflict);
					
					if (MerlinAnnotationUtils.hasModifier(conflict, modifiers)) {
						vi.add(new ValidationIssue( "Conflicting modifiers "+modifier+" and "+conflict+" in "+obj.getName(), 
													IssueLevel.ERROR, obj));
					}
				}
			}
		}
		
		return vi;
	}

	private List<String> getPresenceConditionsOfModifier(String modifier, List<EAnnotation> modifiers) {
		List<String> conditions = new ArrayList<>();
		
		for (EAnnotation a : modifiers) {
			if (a.getDetails().keySet().contains(modifier)) 
				conditions.add(a.getDetails().get(MerlinAnnotationStructure.PRESENCE_CONDITION));
		}
		return conditions;
	}

	private List<ValidationIssue> checkDuplicates(ENamedElement obj, List<EAnnotation> modifiers) {
		List<ValidationIssue> vi = new ArrayList<>();		

		if (obj instanceof EPackage) return vi;	//no posible
		
		for (String modifier : Modifiers.allModifiers()) {
			List<String> conditions = this.getPresenceConditionsOfModifier(modifier, modifiers);
			if (conditions.size()>1) { 
				vi.add(new ValidationIssue("Duplicate modifiers "+modifier+" for "+obj.getName(), IssueLevel.INFORMATION, obj));
				EClassifier context = this.getContext(obj);
				if (modifiersCollide(context, conditions)) {
					vi.add(new ValidationIssue("Colliding modifiers "+modifier+" for "+obj.getName(), IssueLevel.ERROR, obj));
				}
			}
		}
		
				
		return vi;
	}

	private EClassifier getContext(ENamedElement obj) {
		EClassifier context;
		if (obj instanceof EClassifier) context = (EClassifier)obj;
		else context = (EClassifier)((EStructuralFeature)obj).eContainer();
		return context;
	}
	
	private boolean modifiersCollide(EClassifier context, List<String> conditions) {
		// Now check if they can really collide
		FeatureSolver fs = new FeatureSolver(context, this.provider.getFeatureModelFile());

		String concat = "";
		boolean first = true;
		for (String cond : conditions) {
			if (!first) concat+= "and";
			concat += "("+cond+")";
			first = false;
		}
		fs.addConstraint(concat);
		
		return fs.isSat();
		
	}
}
