package merlin.analysis.validate.annotations.ocl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EOperation;
import org.eclipse.emf.ecore.EParameter;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.ocl.ecore.CallOperationAction;
import org.eclipse.ocl.ecore.Constraint;
import org.eclipse.ocl.ecore.OppositePropertyCallExp;
import org.eclipse.ocl.ecore.SendSignalAction;
import org.eclipse.ocl.ecore.utilities.VisitorExtension;
import org.eclipse.ocl.utilities.AbstractVisitor;

import merlin.common.annotations.MerlinAnnotationStructure;
import merlin.common.annotations.modifiers.Modifiers;
import merlin.common.utils.MerlinAnnotationUtils;

public abstract class OCLMerlinVisitor<T> extends AbstractVisitor<	T, EClassifier, EOperation, EStructuralFeature,
																EEnumLiteral, EParameter, EObject,
																CallOperationAction, SendSignalAction, Constraint>
implements VisitorExtension<T> {
	
	public OCLMerlinVisitor(T val) {
		super(val);
	}

	public T getResult() {
		return this.result;
	}
	
	public List<EStructuralFeature> getWarnings() {
		return Collections.emptyList();
	}
	
	protected String getPresenceCondition(EStructuralFeature property, EClassifier type) {
		// get the presence condition of the property
		String attributeCondition = getAttributeCondition(property);

		// Now concatenate with possible requirements for inheriting such attribute
		String accessCondition = getAccessConditions(property, type);
		
		return this.simplify(attributeCondition, accessCondition);
	}

	
	private String getAccessConditions(EStructuralFeature property, EClassifier type) {
		EObject container = property.eContainer();
		if (container.equals(type)) 
			return "true";
		
		// Get all paths from type to container. At least one should exist.
		List<List<EClass>> paths = this.getAllPaths((EClass)type, (EClass)container);
		
		String aggregatedCondition = "";
		for (List<EClass> path : paths) {			
			String condition = this.pathConditions(path);
			if (!"".equals(condition)) {
				if (!"".equals(aggregatedCondition)) aggregatedCondition += " or "+condition;
				else aggregatedCondition += condition;
			}
			else return "true";		// true or X = true
		}
		return aggregatedCondition;
	}

	private List<List<EClass>> getAllPaths(EClass source, EClass target) {
		List<List<EClass>> result = new ArrayList<List<EClass>>();
		List<EClass> path = new ArrayList<EClass>();
		path.add(source);
		this.getAllPaths(source, target, path, result);
		return result;
	}
	
	private void getAllPaths(EClass source, EClass target, List<EClass> path, List<List<EClass>> allPaths) {
		if (source == target) {
			allPaths.add(path);
		}
		
		for (EClass cl : source.getESuperTypes()) {
			List<EClass> newPath = new ArrayList<>();
			newPath.addAll(path);
			newPath.add(cl);
			getAllPaths(cl, target, newPath, allPaths);
		}
		
	}
	
	private String pathConditions(List<EClass> path) {
		if (path.size()==0 || path.size()==1) return "";
		EClass pointer = path.get(0);
		String current = "";
		for (EClass cls : path.subList(1, path.size())) {
			String condition = this.getInheritanceCondition(pointer, cls);
			if (!"true".equals(condition)) {
				if (current.equals(""))
					current = "("+condition+")";
				else
					current += " and ("+condition+")";
			}			
		}
		return current;
	}

	private String getInheritanceCondition(EClass source, EClass cls) {
		List<EAnnotation> modifiers = MerlinAnnotationUtils.getModifiers(source);
		for (EAnnotation a : modifiers) {
			if (a.getDetails().keySet().contains(Modifiers.REDUCE_MODIFIER)) {
				String[] classes = a.getDetails().get(Modifiers.REDUCE_MODIFIER).split("\\s+");
				if (Arrays.asList(classes).contains(cls.getName())) {
					return "not ("+a.getDetails().get(MerlinAnnotationStructure.PRESENCE_CONDITION)+")";
				}
			}
		}
		return "true";
	}

	private String simplify(String cond1, String cond2) {
		if ("true".equals(cond1)) {
			if ("true".equals(cond2)) {
				return "true";
			}
			return cond2;
		}
		if ("true".equals(cond2)) return cond1;
		return "("+cond1+") and ("+cond2+")";
	}

	private String getAttributeCondition(EStructuralFeature property) {
		EAnnotation an = property.getEAnnotation(MerlinAnnotationStructure.ANNOTATION_NAME);
		if (an==null) return "true";
		return an.getDetails().get(MerlinAnnotationStructure.PRESENCE_CONDITION);
	}
	
	/**
	 * Visits the opposite property-call source. No qualifiers are visited as
	 * these calls are not expected for UML but only for Ecore. Returns the
	 * result of
	 * {@link #handleOppositePropertyCallExp(OppositePropertyCallExp, Object)}.
	 * 
	 * @since 3.1
	 */
	public T visitOppositePropertyCallExp(OppositePropertyCallExp callExp) {
		T sourceResult = safeVisit(callExp.getSource());
        return handleOppositePropertyCallExp(callExp, sourceResult);
	}
    
    /**
     * Visits the specified opposite property call with the results of visiting
     * its source and qualifiers (if any).  Note that in the case of a opposite property
     * call expression as a qualifier of an association class call, the
     * opposite property call does not have a source and, therefore, the
     * <code>sourceResult</code> will be <code>null</code> in that case.
     * 
     * @param callExp the opposite property call expression
     * @param sourceResult the result of visiting the expression's source
     * @return the accumulated {@link #result}, by default
     * 
     * @see #visitOppositePropertyCallExp(OppositePropertyCallExp)
     * @since 3.1
     */
    protected T handleOppositePropertyCallExp(OppositePropertyCallExp callExp, T sourceResult) {
        return result;
    }
	
}
