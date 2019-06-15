package merlin.analysis.validate.annotations.ocl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EOperation;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.ocl.ecore.IteratorExp;
import org.eclipse.ocl.expressions.OperationCallExp;
import org.eclipse.ocl.expressions.PropertyCallExp;

/**
 * A visitor that collects the presence conditions for multivalued properties (i.e., those for which a
 * collection operator is called) in a map, with keys the feature names used in the expression...
 */
public class OCLMultiValuedPropertyConditionCollector extends OCLMerlinVisitor<Map<EStructuralFeature, String>> {
	
	private List<EStructuralFeature> warnings = new ArrayList<>();
	
	public OCLMultiValuedPropertyConditionCollector() {
		super(new HashMap<EStructuralFeature, String>());
	}

	private static final List<String> collectionOperations = Arrays.asList("size", "isEmpty", "notEmpty");
	
	/**
	 * We are only interested in multivalued properties, to which a collection operator is applieds
	 */
	@Override
	public Map<EStructuralFeature,String> visitPropertyCallExp( PropertyCallExp<EClassifier, EStructuralFeature> pce) {
		if (pce.getReferredProperty().isMany()) {
//			System.out.println("[merlin] Processing an isMany operation "+pce.getReferredProperty().getName());
			if (isCollectionOperator(pce)) {
//				System.out.println("[merlin] found a collection operator: "+pce.eContainer());
				this.result.put(pce.getReferredProperty(), this.getPresenceCondition(pce.getReferredProperty(), pce.getSource().getType()));									
			}
			if (! this.isIterator(pce)) {
				this.warnings.add(pce.getReferredProperty());
			}
		}
		return super.visitPropertyCallExp(pce);			
	}
	
	private boolean isIterator(PropertyCallExp<EClassifier, EStructuralFeature> pce) {
		return pce.eContainer() instanceof IteratorExp;
	}

	private boolean isCollectionOperator(PropertyCallExp<EClassifier, EStructuralFeature> pce) {
		if (this.isIterator(pce)) return true;
		if (pce.eContainer() instanceof OperationCallExp) {
			OperationCallExp<EClassifier, EOperation> oce = (OperationCallExp<EClassifier, EOperation>)pce.eContainer();
			String operName = oce.getReferredOperation().getName();
			return collectionOperations.contains(operName);
		}
		return false;
	}
	
	public List<EStructuralFeature> getWarnings() {
		return this.warnings;
	}
	
	/*@Override
	public Map<String, String> visitOperationCallExp( OperationCallExp<EClassifier, EOperation> op) {
		System.out.println("[merlin] op name: "+op.getReferredOperation().getName());
		return null;
	}*/
}
