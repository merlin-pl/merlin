package merlin.analysis.use;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EOperation;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EParameter;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.ETypedElement;
import org.eclipse.ocl.Environment;
import org.eclipse.ocl.OCL;
import org.eclipse.ocl.ParserException;
import org.eclipse.ocl.ecore.CallOperationAction;
import org.eclipse.ocl.ecore.Constraint;
import org.eclipse.ocl.ecore.EcoreEnvironmentFactory;
import org.eclipse.ocl.ecore.SendSignalAction;
import org.eclipse.ocl.expressions.EnumLiteralExp;
import org.eclipse.ocl.expressions.IfExp;
import org.eclipse.ocl.expressions.OCLExpression;
import org.eclipse.ocl.expressions.OperationCallExp;
import org.eclipse.ocl.expressions.PropertyCallExp;
import org.eclipse.ocl.expressions.TypeExp;
import org.eclipse.ocl.helper.OCLHelper;
import org.eclipse.ocl.utilities.ExpressionInOCL;

import merlin.common.utils.EMFUtils;
import merlin.common.utils.OclToStringVisitor;

public class UseAdapter {
	// use keywords
	private static List<String> useKeywords = Arrays.asList( "states", "transitions", "from", "attributes", "operations", "constraints", "model", "end" );
	private static String       PREFIX      = "xxx";
	
	// singleton
	public static UseAdapter INSTANCE = new UseAdapter();
	private UseAdapter() {}
	
	/**
	 * It returns the use type that corresponds to an emf type.
	 */
	public String useType (EClassifier ecoreType) {
		String useType = "String";
		if (ecoreType != null) {
			if      (EMFUtils.isInteger (ecoreType.getName())) useType = "Integer";
			else if (EMFUtils.isBoolean (ecoreType.getName())) useType = "Boolean";
			else if (EMFUtils.isFloating(ecoreType.getName())) useType = "Real";
			else if (ecoreType instanceof EEnum || ecoreType instanceof EClass) useType = ecoreType.getName();
		}
		return useType;
	}
	
	public String useType (EClassifier ecoreType, int upperBound, boolean ordered) {
		String useType = useType(ecoreType);
		if (upperBound!=1) useType = ordered? "OrderedSet(" + useType + ")" : "Set(" + useType + ")";
		return useType;
	}

	/**
	 * If the attribute name is a reserved keyword in use, it is added a prefix.
	 */
	public String useName (EAttribute attribute) { return useName (attribute.getName()); }
	
	/**
	 * The reference name is built as follows: <source-role>_<target_role>.
	 */
	public String useName (EReference reference) { return useSourceRole(reference) + "_" + useTargetRole(reference); }
	
	/**
	 * If the role name is a reserved keyword in use, it is added the prefix.
	 */
	public String useSourceRole (EReference reference) {
		String src_role = "";
		if (reference.getEOpposite()==null) {
			EClass container = reference.getEContainingClass();
			int    index     = container.getEReferences().indexOf(reference);
			src_role         = container.getName()+index;
		}
		else src_role = reference.getEOpposite().getName();
		return useName (src_role);
	}

	/**
	 * If the role name is a reserved keyword in use, it is added a prefix.
	 */
	public String useTargetRole (EReference reference) {
		return useName (reference.getName());
	}
	
	/**
	 * If the operation name is a reserved keyword in use, it is added a prefix.
	 */
	public String useName (EOperation operation) { return useName (operation.getName()); }
	
	/**
	 * If the ocl body expression contains reserved keywords in use, they are added a prefix.
	 */
	public String  useOcl (EClassifier ecoreType, String body)          { try { return useOcl(ecoreType, body, null, false); } catch (ParserException e) {} return ""; }
	public String  useOcl (EClassifier ecoreType, EOperation operation) { try { return useOcl(ecoreType, EMFUtils.getBody(operation), operation, false); } catch (ParserException e) {} return ""; }
	public String  useOcl (EClassifier ecoreType, String body, boolean throwException)          throws ParserException { return useOcl(ecoreType, body, null, throwException); }
	public String  useOcl (EClassifier ecoreType, EOperation operation, boolean throwException) throws ParserException { return useOcl(ecoreType, EMFUtils.getBody(operation), operation, throwException); }
	private String useOcl (EClassifier ecoreType, String body, EOperation operation, boolean throwErrors) throws ParserException {
		if (body!=null && !body.isEmpty()) {
			OCL        <EPackage, EClassifier, EOperation, EStructuralFeature, EEnumLiteral, EParameter, EObject, CallOperationAction, SendSignalAction, Constraint, EClass, EObject> ocl = OCL.newInstance(EcoreEnvironmentFactory.INSTANCE);
			Environment<EPackage, EClassifier, EOperation, EStructuralFeature, EEnumLiteral, EParameter, EObject, CallOperationAction, SendSignalAction, Constraint, EClass, EObject> env = ocl.getEnvironment();
			OCLHelper<EClassifier, EOperation, EStructuralFeature, Constraint> helper = ocl.createOCLHelper();

			try {
				UseOclNameAdapter visitor = new UseOclNameAdapter(env);
				String            newBody = "";
				if (operation == null) {
					helper.setContext(ecoreType);
					OCLExpression<EClassifier> query = helper.createQuery(body);
					newBody = query.accept(visitor);
				}
				else {
					helper.setOperationContext(ecoreType, operation);
					Constraint bodyCondition = helper.createBodyCondition(body);
					newBody = bodyCondition.getSpecification().accept(visitor);
				}
				if (visitor.needsAdaptation())
					body = newBody;
			}
			catch (ParserException e) {
//				System.err.println("<use> Malformed OCL expression: "+body+"\n                                "+e.getMessage());
				if (throwErrors) throw e;
			}
		}
		return body;
	}
	
	// handling of prefixes to avoid reserved keywords in use
	private String useName (String name) { return useKeywords.contains(name)? PREFIX+name : name; }
	public  String emfName (String name) { return name.startsWith(PREFIX) && useKeywords.contains(name.substring(PREFIX.length()))? name.substring(PREFIX.length()) : name; }
	
	// ------------------------------------------------------------------------------------------------

	private class UseOclNameAdapter extends OclToStringVisitor {
		
		public UseOclNameAdapter(Environment<?, EClassifier, EOperation, EStructuralFeature, EEnumLiteral, EParameter, EObject, CallOperationAction, SendSignalAction, Constraint, ?, ?> env) {
			super(env);
		}
		
		private Boolean needsAdaptation = false;
		public  Boolean needsAdaptation() { return needsAdaptation; }

		private Map<ETypedElement, String> adaptations = new HashMap<ETypedElement, String>();
		
		@Override
		public String visitPropertyCallExp (PropertyCallExp<EClassifier, EStructuralFeature> pce) {
			adaptName(pce.getReferredProperty());
			String result = super.visitPropertyCallExp(pce);
			if (pce.eContainer()==null || pce.eContainer() instanceof ExpressionInOCL) restoreOriginalNames();
			// multivalued attributes with promitive type are rewritten to consider undefined collections as empty collections
			EStructuralFeature property = pce.getReferredProperty();
			if (property instanceof EAttribute && property.isMany()) { 
				needsAdaptation = true;
				result = "(if " + result + "->oclIsUndefined() then "+(property.isOrdered()? "OrderedSet" : "Set") +"{} else " + result + " endif)";
				//result = (property.isOrdered()? "OrderedSet" : "Set") +"{}->union(" + result + ")";
			}
			// -----
			return result;
		}
		
		@Override
	    public String visitTypeExp(TypeExp<EClassifier> t) {
			// use does not qualify type names
 			String oldName = super.visitTypeExp(t);
 			String newName = getName(t.getReferredType());
 			if (!oldName.equals(newName)) 
 				needsAdaptation = true;
			return newName; 
		}
		
		@Override
	    public String visitEnumLiteralExp(EnumLiteralExp<EClassifier,EEnumLiteral> el) {
			// use does not qualify type names
 			String oldName = super.visitEnumLiteralExp(el);
 			String newName = getName(el.getType()) + "::" + getName(el.getReferredEnumLiteral());
 			if (!oldName.equals(newName)) 
 				needsAdaptation = true;
			return newName; 
		}
		
		@Override
		public String visitOperationCallExp(OperationCallExp<EClassifier, EOperation> callExp) {
			adaptName(callExp.getReferredOperation());
			String result = super.visitOperationCallExp(callExp);
			if (callExp.eContainer()==null || callExp.eContainer() instanceof ExpressionInOCL) restoreOriginalNames();
			return result;
		}
		
		@Override
		public String visitIfExp(IfExp<EClassifier> ifExp) {
			String result = super.visitIfExp(ifExp);
			if (ifExp.eContainer()==null || ifExp.eContainer() instanceof ExpressionInOCL) restoreOriginalNames();
			return result;
		}
				
		private void adaptName (ETypedElement element) {
 			String oldName = element.getName();
 			String newName = UseAdapter.INSTANCE.useName(oldName);
 			if (!oldName.equals(newName)) {
 				element.setName(newName);
 				needsAdaptation = true;
 				adaptations.put(element, oldName);
 			}
		}
		
		private void restoreOriginalNames () {
			adaptations.keySet().forEach(ref -> ref.setName(adaptations.get(ref)));
			adaptations.clear();
		}
	}
}
