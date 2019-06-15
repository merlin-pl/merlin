package merlin.analysis.validate.annotations;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;

import merlin.common.features.IFeatureProvider;
import merlin.common.issues.ValidationIssue;
import merlin.common.utils.MerlinAnnotationUtils;

/**
 * Checks if a presence condition is SAT
 */
public class PresenceConditionSATCheck extends ExpressionCheck {

	public PresenceConditionSATCheck(IFeatureProvider pr) {
		super(pr);
	}
	
	public Class<?> appliesAt() {
		return ENamedElement.class;
	}
	
	@Override
	public List<ValidationIssue> check(EObject obj, boolean existingErrors) {
		List<ValidationIssue> list = new ArrayList<>();
		
		if (existingErrors) return list;
		
		if (!(obj instanceof ENamedElement)) return list;
		ENamedElement cls = (ENamedElement)obj;

		EClassifier context = this.getContext(obj);		
		
		String pc = MerlinAnnotationUtils.getPresenceCondition(cls);
		
		list.addAll(this.satCheck(cls.getName(), context, pc));
		
		return list;
	}

	private EClassifier getContext(EObject obj) {
		if (obj instanceof EClassifier) return (EClassifier)obj;
		if (obj instanceof EStructuralFeature) {
			EStructuralFeature sf = (EStructuralFeature)obj;
			return (EClassifier)sf.eContainer();
		}
		return null;
	}

}
