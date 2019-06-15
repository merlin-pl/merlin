package merlin.analysis.validate.annotations;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;

import merlin.common.annotations.MerlinAnnotationStructure;
import merlin.common.features.IFeatureProvider;
import merlin.common.issues.IssueLevel;
import merlin.common.issues.ValidationIssue;

public class StructuralFeatureCheck extends AnnotationCheck{

	public StructuralFeatureCheck(IFeatureProvider prv) {
		super(prv);		
	}

	public Class<?> appliesAt() {
		return EStructuralFeature.class;
	}
	
	@Override
	public List<ValidationIssue> check(EObject obj, boolean existingErrors) {
		List<ValidationIssue> list = new ArrayList<>();
		if (!(obj instanceof EStructuralFeature)) return list;
		EStructuralFeature cls = (EStructuralFeature)obj;
		
		EAnnotation ann = cls.getEAnnotation(MerlinAnnotationStructure.ANNOTATION_NAME);
		
		if (ann==null) return list;
		
		String condition = ann.getDetails().get("condition");
		if (condition == null) {
			list.add(new ValidationIssue("Field "+cls.getName()+" lacks presence condition", 
					 IssueLevel.ERROR, 
					 cls));
			return list;
		}
		
		// Now check well-formedness of condition
		list.addAll(this.checkCondition(condition, cls));		
				
		return list;
	}
	
	public boolean equals(Object o) {
		if (!(o instanceof StructuralFeatureCheck)) return false;		
		return true;
	}
	
	@Override
	public int hashCode() {
		return StructuralFeatureCheck.class.hashCode();
	}
}
