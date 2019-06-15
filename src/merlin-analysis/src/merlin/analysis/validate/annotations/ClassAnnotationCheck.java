package merlin.analysis.validate.annotations;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;

import merlin.common.annotations.MerlinAnnotationStructure;
import merlin.common.features.IFeatureProvider;
import merlin.common.issues.IssueLevel;
import merlin.common.issues.ValidationIssue;
import merlin.common.utils.EMFUtils;

public class ClassAnnotationCheck extends AnnotationCheck {
	public ClassAnnotationCheck(IFeatureProvider prv) {
		super(prv);		
	}

	private List<ValidationIssue> check(EClass cls, EAnnotation ann) {
		List<ValidationIssue> list = new ArrayList<>();
		String condition = ann.getDetails().get(MerlinAnnotationStructure.PRESENCE_CONDITION);
		if (condition == null) {
			list.add(new ValidationIssue("Class "+cls.getName()+" lacks presence condition", 
					 IssueLevel.ERROR, 
					 cls));
			return list;
		}
		
		// Now check well-formedness of condition
		list.addAll(this.checkCondition(condition, cls));		
		
		String constraint = ann.getDetails().get(MerlinAnnotationStructure.INVARIANT);
		if (constraint != null) { // We need an invariant with name constraint
			if (!this.hasInvariant(cls, constraint)) {
				list.add(new ValidationIssue("Class "+cls.getName()+" does not have invariant '"+constraint+"'", 
						 IssueLevel.ERROR, 
						 cls));
				return list;
			}
		}
		
		return list;
	}
	
	@Override
	public List<ValidationIssue> check(EObject obj, boolean existingErrors) {
		List<ValidationIssue> list = new ArrayList<>();
		if (!(obj instanceof EClass)) return list;
		EClass cls = (EClass)obj;
		
		for (EAnnotation an : cls.getEAnnotations()) {
			if (an.getSource().contentEquals(MerlinAnnotationStructure.ANNOTATION_NAME)) {
				list.addAll(this.check(cls, an));
			}
		}
		
		return list;
	}

	private boolean hasInvariant(EClass cls, String constraint) {		
		EAnnotation ann = EMFUtils.getOCLAnnotation(cls);
		if (ann == null) return false;
		return ann.getDetails().containsKey(constraint);
	}
}
