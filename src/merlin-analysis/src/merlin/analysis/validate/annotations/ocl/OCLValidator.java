package merlin.analysis.validate.annotations.ocl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EOperation;
import org.eclipse.emf.ecore.EParameter;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.ocl.OCL;
import org.eclipse.ocl.ParserException;
import org.eclipse.ocl.ecore.Constraint;
import org.eclipse.ocl.ecore.EcoreEnvironmentFactory;
import org.eclipse.ocl.helper.OCLHelper;
import org.eclipse.ocl.utilities.ExpressionInOCL;

import merlin.common.analysis.FeatureSolver;
import merlin.common.features.IFeatureProvider;
import merlin.common.issues.IssueLevel;
import merlin.common.issues.ValidationIssue;

/**
 * Validates the presence conditions in OCL expressions
 */
public class OCLValidator {
	private OCL environment;
	private IFeatureProvider provider;
	
	public OCLValidator(IFeatureProvider provider) {
		this.provider = provider;
		this.environment = OCL.newInstance(EcoreEnvironmentFactory.INSTANCE);
	}
	
	public List<ValidationIssue> checkPresenceConditions(EClassifier context, String invCondition, String constraint, String constName) {
		List<ValidationIssue> issues = new ArrayList<>();
		Map<String, String> presenceConditions = this.parse(context, constraint);
		if (presenceConditions == null) {
			issues.add(new ValidationIssue("Malformed constraint "+constraint, IssueLevel.ERROR, context));
			return issues;
		}
		String condition = this.concatConditions(presenceConditions);
		if (condition.equals("")) return issues;	// The induced condition is true
		
		String strengthenedCondition = invCondition + " and ("+condition+")";
		FeatureSolver fs = new FeatureSolver(context, this.provider.getFeatureModelFile());
		fs.addConstraint(strengthenedCondition);
		if (!fs.isSat()) {
			issues.add(new ValidationIssue(	"Presence condition ["+invCondition+"] for invariant "+constName+
					" is not compatible with the implicit presence condition "+
					"["+condition+"]"+
					" of the OCL expression \""+constraint+"\""
					, IssueLevel.ERROR, context));
		}		
		
		fs = new FeatureSolver(context, this.provider.getFeatureModelFile());
		String negated = "not ( "+invCondition+" implies ("+condition+"))";
		fs.addConstraint(negated);
		if (fs.isSat()) {
			issues.add(new ValidationIssue(	"Presence condition ["+invCondition+"] for invariant "+constName+
					" does not imply the implicit presence condition "+
					"["+condition+"]"+
					" of the OCL expression \""+constraint+"\""
					, IssueLevel.ERROR, context));
		}	
		
		return issues;
	}
	
	/**
	 * Concats all presence conditions, or return "" if they are true
	 * @param presenceConditions
	 * @return
	 */
	private String concatConditions(Map<String, String> presenceConditions) {
		String result = "";
		
		Set<String> pcs = new HashSet<>(presenceConditions.values());	// we remove equal terms
		
		boolean first = true;
				
		for (String invn : pcs) {
			if (invn.equals("true")) continue;
			if (!first) result += " and ";
			result += invn;
			first = false;
		}
		
		return result;
	}
	
	private OCLMerlinVisitor<?> visitorUsed;
	
	public Map<String, String> parse(EClassifier context, String constraint) {
		OCLMerlinVisitor<Map<String, String>> visitor = new OCLPresenceConditionCollector();
		this.visitorUsed = visitor;
		Map<String, String> result = this.parse(context, constraint, visitor);
		
		OCLMerlinVisitor<Map<String, String>> visitorc = new OCLClassConditionCollector(result);
		this.visitorUsed = visitor;
		result = this.parse(context, constraint, visitorc);
		
		return result;
	}
	
	public Map<EStructuralFeature, String> parseCollections(EClassifier context, String constraint) {
		OCLMerlinVisitor<Map<EStructuralFeature, String>> visitor = new OCLMultiValuedPropertyConditionCollector();
		this.visitorUsed = visitor;
		return this.parse(context, constraint, visitor);
	}
	
	public Map<EStructuralFeature, String> parseMonoValued(EClassifier context, String constraint) {
		OCLMerlinVisitor<Map<EStructuralFeature, String>> visitor = new OCLMonoValuedPropertyConditionCollector();
		this.visitorUsed = visitor;
		return this.parse(context, constraint, visitor);
	}
	
	public List<EStructuralFeature> getWarnings() {
		return this.visitorUsed==null ? Collections.emptyList() : this.visitorUsed.getWarnings();
	}
	
	@SuppressWarnings("unchecked")
	private <T> T parse(EClassifier context, String constraint, OCLMerlinVisitor<T> visitor) {
		OCLHelper<EClassifier, EOperation, EStructuralFeature, Constraint> helper = this.environment.createOCLHelper();
		helper.setContext(context);
		try {
			Constraint invariant = helper.createInvariant(constraint);
			ExpressionInOCL<EClassifier, EParameter> specification = invariant.getSpecification();
			specification.accept(visitor);
			return visitor.getResult();
		} catch (ParserException e) {	// TODO: Handle properly			
			System.err.println("[merlin] Malformed constraint "+constraint);
			System.err.println("[merlin] Reason "+e.getMessage());
			return null;
		}
	}
}
