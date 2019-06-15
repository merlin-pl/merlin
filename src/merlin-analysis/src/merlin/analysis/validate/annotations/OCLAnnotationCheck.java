package merlin.analysis.validate.annotations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.EMap;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;

import merlin.analysis.validate.annotations.ocl.OCLValidator;
import merlin.common.analysis.FeatureSolver;
import merlin.common.annotations.MerlinAnnotationStructure;
import merlin.common.annotations.modifiers.Modifiers;
import merlin.common.features.IFeatureProvider;
import merlin.common.issues.IssueLevel;
import merlin.common.issues.ValidationIssue;
import merlin.common.utils.EMFUtils;
import merlin.common.utils.MerlinAnnotationUtils;

public class OCLAnnotationCheck extends ExpressionCheck {

	public OCLAnnotationCheck(IFeatureProvider pr) {
		super(pr);
	}
	
	public Class<?> appliesAt() {
		return EClass.class;
	}

	@Override
	public List<ValidationIssue> check(EObject obj, boolean existingErrors) {
		List<ValidationIssue> list = new ArrayList<>();
		if (!(obj instanceof EClass)) return list;
		EClass cls = (EClass)obj;
		if (existingErrors) return list;	// We do not continue
		
		EMap<String, String> invs = EMFUtils.getInvariants(cls);
		
		OCLValidator val = new OCLValidator(this.provider);
		
		// First check satisfiability
		this.checkSatisfiability(list, cls, invs);
		
		// No check compatibility with implicit presence condition of expression
		this.checkImplicitPresenceConditions(list, cls, invs, val);
		
		// No check whether a collection can be safely converted into a monovalued feature
		this.checkCollectionModifierOper(list, invs, val, cls);
		
		// No check whether a monovalued field can be safely converted into a multivalued feature
		this.checkMonovaluedModifierOper(list, invs, val, cls);		
		
		return list;
	}

	private void checkSatisfiability(List<ValidationIssue> list, EClass cls, EMap<String, String> invs) {
		for (String inv : invs.keySet()) {
			String presenceCondition = MerlinAnnotationUtils.getInvariantPresenceCondition(cls, inv);
			list.addAll(this.satCheck(cls.getName(), cls, presenceCondition));
		}
	}
	
	private void checkImplicitPresenceConditions(List<ValidationIssue> list, EClass cls, EMap<String, String> invs, OCLValidator val) {
		for (String inv : invs.keySet()) {
			String presenceCondition = MerlinAnnotationUtils.getInvariantPresenceCondition(cls, inv);
			
			if (!presenceCondition.equals("true"))
				list.addAll(val.checkPresenceConditions(cls, presenceCondition, invs.get(inv), inv));
			else {
				Map<String, String> implicitConditions = val.parse(cls, invs.get(inv));
				if (implicitConditions!=null)
					for (String value : implicitConditions.values()) {
						if (!"true".equals(value)) {
							list.add(new ValidationIssue("The implicit presence condition ["+ value +"]"+
														 " of invariant "+inv+
														 " may not be selected in class "+cls.getName(),
														 IssueLevel.ERROR, cls));
						}
					}
				
			}
		}
	}
	
	protected void checkCollectionModifierOper(List<ValidationIssue> list, EMap<String, String> invs, OCLValidator val, EClass cls) {

		for (String inv : invs.keySet()) {
			Map<EStructuralFeature, String> collectionConditions = val.parseCollections(cls, invs.get(inv));
			
			//System.out.println("[merlin] collection conditions: "+collectionConditions);
			
			if (collectionConditions == null) continue;
			// Now check if there is a modifier turning them into a non-collection
			for (EStructuralFeature sf : collectionConditions.keySet()) {
				List<EAnnotation> modifiers = MerlinAnnotationUtils.getModifiers(sf);
				for (EAnnotation an : modifiers) {
					if (an.getDetails().containsKey(Modifiers.MAX_MODIFIER)) {
						String max_val = an.getDetails().get(Modifiers.MAX_MODIFIER);
						if ("1".equals(max_val)) {
							// Now get the presence condition!
							//System.out.println("[merlin] Found modifier for property with collection operation");
							String modifier_condition = an.getDetails().get(MerlinAnnotationStructure.PRESENCE_CONDITION);
							// Check satisfiability of presence condition of invariant and presence condition of modifier
							String invariantCondition = MerlinAnnotationUtils.getInvariantPresenceCondition(cls, inv);
							String overallCondition = "("+invariantCondition+") and ("+modifier_condition+")";
							
							FeatureSolver fs = new FeatureSolver(cls, this.provider.getFeatureModelFile());
							list.addAll(fs.addConstraint(overallCondition));
							
							if (this.hasErrors(list)) return; 	// parsing errors!
							
							if (fs.isSat()) {
								list.add(new ValidationIssue(	"Multivalued feature '"+sf.getName()+"' recieves a collection operation in invariant '"+
																inv+"', but may become monovalued field "
																+ "when max modifier equals to 1 [condition : "+overallCondition+"]"
																, val.getWarnings().contains(sf) ? IssueLevel.WARNING : IssueLevel.ERROR, cls));
							}
							
						}
					}
				}
			}
		}		
	}
	
	private boolean hasErrors (Collection<ValidationIssue> errors) {
		for (ValidationIssue vi : errors) {
			if (vi.getLevel().equals(IssueLevel.ERROR)) return true;
		}
		return false;
	}
	
	private void checkMonovaluedModifierOper(List<ValidationIssue> list, EMap<String, String> invs, OCLValidator val, EClass cls) {
		for (String inv : invs.keySet()) {
			Map<EStructuralFeature, String> collectionConditions = val.parseMonoValued(cls, invs.get(inv));
			
			//System.out.println("[merlin] collection conditions: "+collectionConditions);
			if (collectionConditions==null)	//something went wrong when analysing the condition
				continue;
			
			for (EStructuralFeature sf : collectionConditions.keySet()) {
				List<EAnnotation> modifiers = MerlinAnnotationUtils.getModifiers(sf);
				for (EAnnotation an : modifiers) {
					if (an.getDetails().containsKey(Modifiers.MAX_MODIFIER)) {
						String max_val = an.getDetails().get(Modifiers.MAX_MODIFIER);
						if (!"1".equals(max_val)) {
							// Now get the presence condition!
							//System.out.println("[merlin] Found modifier for property with monovalued operation");
							String modifier_condition = an.getDetails().get(MerlinAnnotationStructure.PRESENCE_CONDITION);
							// Check satisfiability of presence condition of invariant and presence condition of modifier
							String invariantCondition = MerlinAnnotationUtils.getInvariantPresenceCondition(cls, inv);
							String overallCondition = "("+invariantCondition+") and ("+modifier_condition+")";
							
							FeatureSolver fs = new FeatureSolver(cls, this.provider.getFeatureModelFile());
							list.addAll(fs.addConstraint(overallCondition));
							
							if (list.size()>0) return; 	// parsing errors!
							
							if (fs.isSat()) 
								list.add(new ValidationIssue(	"Monovalued feature '"+sf.getName()+"' recieves a monovalued operation in invariant '"+
																inv+"', but may become multivalued field "
																+ "when max modifier differs from 1 [condition : "+overallCondition+"]"
																, IssueLevel.ERROR, cls));
							
						}
					}
				}
			}
		}
		
	}

}
