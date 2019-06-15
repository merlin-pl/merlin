package merlin.analysis.validate.annotations;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;

import merlin.common.analysis.FeatureSolver;
import merlin.common.features.IFeatureProvider;
import merlin.common.issues.IssueLevel;
import merlin.common.issues.ValidationIssue;
import merlin.common.utils.MerlinAnnotationUtils;

public class ReferencePresenceConditionCheck extends ExpressionCheck {

	public ReferencePresenceConditionCheck(IFeatureProvider pr) {
		super(pr);
	}
	
	public Class<?> appliesAt() {
		return EReference.class;
	}

	@Override
	public List<ValidationIssue> check(EObject obj, boolean existingErrors) {
		List<ValidationIssue> list = new ArrayList<>();
		if (!(obj instanceof EReference)) return list;
		EReference ref = (EReference)obj;
		if (existingErrors) return list;	// We do not continue
						
		String presenceCondition = MerlinAnnotationUtils.getPresenceCondition(ref);
		// Check compatibility with presence condition of target class
				
		if (!presenceCondition.equals("true")) {
			
			String targetCondition = MerlinAnnotationUtils.getPresenceCondition(ref.getEReferenceType());
			if (targetCondition == null || targetCondition.equals("true")) return list;
			
			String strengthenedCondition = "("+presenceCondition + ") and ("+targetCondition+")";
			FeatureSolver fs = new FeatureSolver(ref.getEContainingClass(), this.provider.getFeatureModelFile());
			fs.addConstraint(strengthenedCondition);
			if (!fs.isSat()) {
				list.add(new ValidationIssue(	"Presence condition ["+presenceCondition+"] for reference "+ref.getName()+
						" is not compatible with the presence condition "+
						"["+targetCondition+"]"+
						" of the target class \""+targetCondition+"\""
						, IssueLevel.ERROR, ref));
			}
				
			fs = new FeatureSolver(ref.getEContainingClass(), this.provider.getFeatureModelFile());
			String negated = "not ( "+presenceCondition+" implies ("+targetCondition+"))";
			fs.addConstraint(negated);
			if (fs.isSat()) {
				list.add(new ValidationIssue(	"Presence condition ["+presenceCondition+"] for reference "+ref.getName()+
						" does not imply the presence condition "+
						"["+targetCondition+"]"+
						" of the target class \""+targetCondition+"\""
						, IssueLevel.ERROR, ref));
			}	
		}
		
		return list;
	}
	
}
