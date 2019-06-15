package merlin.common.utils;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EOperation;
import org.eclipse.emf.ecore.EParameter;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.ocl.Environment;
import org.eclipse.ocl.ecore.CallOperationAction;
import org.eclipse.ocl.ecore.Constraint;
import org.eclipse.ocl.ecore.PrimitiveType;
import org.eclipse.ocl.ecore.SendSignalAction;
import org.eclipse.ocl.expressions.CollectionLiteralExp;
import org.eclipse.ocl.expressions.OCLExpression;
import org.eclipse.ocl.expressions.OperationCallExp;
import org.eclipse.ocl.types.CollectionType;
import org.eclipse.ocl.util.ToStringVisitor;

public class OclToStringVisitor extends ToStringVisitor<EClassifier, EOperation, EStructuralFeature, EEnumLiteral, EParameter, EObject, CallOperationAction, SendSignalAction, Constraint> {
	
	public OclToStringVisitor(Environment<?, EClassifier, EOperation, EStructuralFeature, EEnumLiteral, EParameter, EObject, CallOperationAction, SendSignalAction, Constraint, ?, ?> env) {
		super(env);
	}
	
	private static List<String> booleanUnaryOperators  = Arrays.asList( new String[] {"not"} );
	private static List<String> numericUnaryOperators  = Arrays.asList( new String[] {"-"} );
	private static List<String> booleanBinaryOperators = Arrays.asList( new String[] {"and", "or", "implies"} );
	private static List<String> otherBinaryOperators   = Arrays.asList( new String[] {"=", "<", ">", "<=", ">=", "<>", "+", "-", "*", "/"} );
	private static List<String> numericTypes           = Arrays.asList( new String[] {"Integer", "Double", "Float", "Long", "Short"} );
	
    @Override
    protected String handleOperationCallExp(OperationCallExp<EClassifier,EOperation> oc, String sourceResult, List<String> argumentResults) {
		OCLExpression<EClassifier> source = oc.getSource();
		EClassifier sourceType = source != null ? source.getType() : null;
		EOperation  oper       = oc.getReferredOperation();
		String      operName   = getName(oper); 

		StringBuilder result = new StringBuilder();

		// new ..................................................
		
		if (booleanBinaryOperators.contains(operName) && argumentResults.size() == 1 && sourceType instanceof PrimitiveType && getName(sourceType).equals("Boolean")) {
				result.append("(");
				result.append(sourceResult);					
				result.append(" "+operName+" ");
				result.append(argumentResults.get(0));
				result.append(")");
			}
		else if (otherBinaryOperators.contains(operName) && argumentResults.size() == 1) {
			result.append("(");
			result.append(sourceResult);					
			result.append(" "+operName+" ");
			result.append(argumentResults.get(0));
			result.append(")");
		}
		else if (booleanUnaryOperators.contains(operName) && argumentResults.size() == 0 && sourceType instanceof PrimitiveType && getName(sourceType).equals("Boolean")) {
			result.append(operName + " (");
			result.append(sourceResult);
			result.append(")");
		}
		else if (numericUnaryOperators.contains(operName) && argumentResults.size() == 0 && sourceType instanceof PrimitiveType && numericTypes.contains(getName(sourceType))) {
			result.append(operName + sourceResult);
		}		
		// original .............................................
		else {
//			System.out.println("<ocl> general case? "+oc);
			result.append(sourceResult);
			result.append(sourceType instanceof CollectionType<?, ?> ? "->" : "."); //$NON-NLS-1$ //$NON-NLS-2$
			result.append(getName(oper));
		
			result.append('(');
			for (Iterator<String> iter = argumentResults.iterator(); iter.hasNext();) {
				result.append(iter.next());
				if (iter.hasNext()) {
					result.append(", "); //$NON-NLS-1$
				}
			}
			result.append(')');
		}
        
		return maybeAtPre(oc, result.toString());	    	
    }
    
    @Override
    protected String handleCollectionLiteralExp(CollectionLiteralExp<EClassifier> cl, List<String> partResults) {  	
    	// fix to avoid rewriting x into Set{x}, when x is a mono-valued feature
    	return partResults.size()!=1 || cl.getStartPosition()!=-1 || cl.getEndPosition()!=-1?
    			super.handleCollectionLiteralExp(cl, partResults) : partResults.get(0);
    }
}
