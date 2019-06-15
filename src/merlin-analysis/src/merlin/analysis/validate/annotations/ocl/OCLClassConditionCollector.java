package merlin.analysis.validate.annotations.ocl;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EOperation;
import org.eclipse.ocl.expressions.OperationCallExp;
import org.eclipse.ocl.expressions.TypeExp;

import merlin.common.utils.MerlinAnnotationUtils;

public class OCLClassConditionCollector extends OCLMerlinVisitor<Map<String, String>>{

	private static final List<String> typeOperations = Arrays.asList("oclIsTypeOf", "oclIsKindOf", "oclAsType");
	
	public OCLClassConditionCollector(Map<String, String> val) {
		super(val);
	}

	@Override
	public Map<String,String> visitOperationCallExp( OperationCallExp<EClassifier, EOperation> pce) {		
		String operName = pce.getReferredOperation().getName();
		if ("allInstances".equals(operName)) {
			TypeExp<EClassifier> srcType = (TypeExp<EClassifier>)pce.getSource();
			EClass cls = (EClass)srcType.getReferredType();
			
			this.result.put(cls.getName(), MerlinAnnotationUtils.getPresenceCondition(cls));
		}
		else if (typeOperations.contains(operName)) {
			TypeExp<EClassifier> srcType = (TypeExp<EClassifier>)pce.getArgument().get(0);
			EClass cls = (EClass)srcType.getReferredType();
			this.result.put(cls.getName(), MerlinAnnotationUtils.getPresenceCondition(cls));
		}
		return super.visitOperationCallExp(pce);
	}
}
