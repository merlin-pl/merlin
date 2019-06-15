package merlin.analysis.validate.annotations;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.emf.ecore.EClassifier;

import merlin.common.analysis.FeatureSolver;
import merlin.common.features.IFeatureProvider;
import merlin.common.issues.IssueLevel;
import merlin.common.issues.ValidationIssue;

public abstract class ExpressionCheck extends AnnotationCheck {

	public ExpressionCheck(IFeatureProvider pr) {
		super(pr);
	}

	protected Collection<ValidationIssue> satCheck(String contexts, EClassifier context, String pc) {
		Set<ValidationIssue> list = new LinkedHashSet<>();
		
		if ("true".contentEquals(pc)) return list;
				
		FeatureSolver fs = new FeatureSolver(context, this.provider.getFeatureModelFile());
		list.addAll(fs.addConstraint(pc));
		
		if (list.size()>0) return list; 	// parsing errors!
		
		if (!fs.isSat()) {
			list.add(new ValidationIssue(	"Presence condition ["+pc+"] for "+contexts+
											" is unsatisfiable"
											, IssueLevel.ERROR, context));
		}
		
		return list;
	}
}
