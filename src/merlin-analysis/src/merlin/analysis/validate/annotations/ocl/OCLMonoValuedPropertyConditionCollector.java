package merlin.analysis.validate.annotations.ocl;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EOperation;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.ocl.expressions.OperationCallExp;
import org.eclipse.ocl.expressions.PropertyCallExp;

/**
 * A visitor that collects the presence conditions for multivalued properties (i.e., those for which a
 * collection operator is called) in a map, with keys the feature names used in the expression...
 */
public class OCLMonoValuedPropertyConditionCollector extends OCLMerlinVisitor<Map<EStructuralFeature, String>> {
	
	public OCLMonoValuedPropertyConditionCollector() {
		super(new HashMap<EStructuralFeature, String>());
	}

	private static final List<String> monoValuedOperations = Arrays.asList("<", ">", "=", "<=", ">=", "+", "-", "*", "/");
	
	
	
	/**
	 * We are only interested in multivalued properties, to which a collection operator is applieds
	 */
	@Override
	public Map<EStructuralFeature,String> visitPropertyCallExp( PropertyCallExp<EClassifier, EStructuralFeature> pce) {
		if (!pce.getReferredProperty().isMany()) {
//			System.out.println("[merlin] Processing a monovalued operation "+pce.getReferredProperty().getName());
			if (isMonovaluedOperator(pce)) {
//				System.out.println("[merlin] found a monovalued operator: "+pce.eContainer());
				this.result.put(pce.getReferredProperty(), this.getPresenceCondition(pce.getReferredProperty(), pce.getSource().getType()));									
			}
		}
		return super.visitPropertyCallExp(pce);			
	}

	private boolean isMonovaluedOperator(PropertyCallExp<EClassifier, EStructuralFeature> pce) {
		if (pce.eContainer() instanceof OperationCallExp) {
			OperationCallExp<EClassifier, EOperation> oce = (OperationCallExp<EClassifier, EOperation>)pce.eContainer();
			String operName = oce.getReferredOperation().getName();
			return monoValuedOperations.contains(operName);
		}
		return false;
	}
}
