package merlin.analysis.validate.annotations.ocl;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.ocl.expressions.PropertyCallExp;

/**
 * A visitor that collects the presence conditions in a map, with keys the feature names used in the expression...
 */
public class OCLPresenceConditionCollector extends OCLMerlinVisitor<Map<String, String>>{
	
	public OCLPresenceConditionCollector() {
		super (new HashMap<String, String>());
	}
	
	@Override
	public Map<String,String> visitPropertyCallExp( PropertyCallExp<EClassifier, EStructuralFeature> pce) {
		this.result.put(pce.getReferredProperty().getName(), this.getPresenceCondition(pce.getReferredProperty(), pce.getSource().getType()));
		return super.visitPropertyCallExp(pce);			
	}

}
