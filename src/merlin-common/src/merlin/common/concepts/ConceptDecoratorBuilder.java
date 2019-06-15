package merlin.common.concepts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EOperation;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EParameter;
import org.eclipse.emf.ecore.ETypedElement;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.util.EcoreUtil;

import merlin.common.transformation.Method;
import merlin.common.utils.EMFUtils;

public class ConceptDecoratorBuilder {

	private EPackage root;
	private List<String> oclErrors = new ArrayList<>();
	private Map<Method, Map<String,String>> preconditions = new LinkedHashMap<>();
	private Map<Method, Map<String,String>> postconditions = new LinkedHashMap<>();
	
	public Map<Method, Map<String,String>> getPreconditions() {
		return this.preconditions;
	}
	
	public Map<Method, Map<String,String>> getPostconditions() {
		return this.postconditions;
	}
	
	public ConceptDecoratorBuilder(EPackage root) {
		this.root = root;
	}
	
	public List<String> getOCLErrors() {
		return this.oclErrors;
	}

	public void createConceptOps() {
		for (EPackage p : SelectedConcepts.get())
			this.addConceptOperations(p);
	}
	
	private void addConceptOperations(EPackage ePackage) {
		for (EClassifier cl : this.root.getEClassifiers()) {
			if (!(cl instanceof EClass)) continue;
			EClass pclass = (EClass)cl;
			EClassifier cclassifier = ePackage.getEClassifier(pclass.getName());
			if (cclassifier == null || !(cclassifier instanceof EClass)) continue;
			EClass cclass = (EClass)cclassifier;
			for (EOperation op : cclass.getEOperations()) {
				this.addContract(cclass, op);
				if (this.containsOperation(pclass, op)) continue;
				pclass.getEOperations().add(this.cloneOperation(op));	
			}
		}
	}
	
	private void addContract(EClass cl, EOperation op) {
		Map<String,String> preconds = EMFUtils.getPreconditions(op);
		Map<String,String> postconds = EMFUtils.getPostconditions(op);
		Method m = new Method(cl.getName(), op.getName(), null);
		addConditions(preconds, m, this.preconditions);
		addConditions(postconds, m, this.postconditions);
	}

	private void addConditions(Map<String,String> preconds, Method m, Map<Method, Map<String,String>> cond) {
		if (preconds.size()>0) {
			if (!cond.containsKey(m)) cond.put(m, new HashMap<>());
			cond.get(m).putAll(preconds);
		}
	}
	
	private boolean containsOperation(EClass cl, EOperation op) {
		for (EOperation oper : cl.getEOperations()) {
			if (oper.getName().equals(op.getName())) return true;
		}
		return false;
	}
	
	private EOperation cloneOperation(EOperation op) {
		EOperation oper = EcoreFactory.eINSTANCE.createEOperation();
		oper.setName(op.getName());
		oper.setLowerBound(op.getLowerBound());
		oper.setOrdered(op.isOrdered());
		oper.setUnique(op.isUnique());
		oper.setUpperBound(op.getUpperBound());
		cloneEType(op, oper);

		for (EParameter par : op.getEParameters()) {
			EParameter clone = EcoreFactory.eINSTANCE.createEParameter();
			clone.setName(par.getName());
			clone.setLowerBound(par.getLowerBound());
			clone.setUpperBound(par.getUpperBound());
			clone.setOrdered(par.isOrdered());
			this.cloneEType(par, clone);
			oper.getEParameters().add(clone);
		}
		
		// Now clone the possible OCL (body, pre, post)
		this.cloneAnnotations(op, oper);
		return oper;
	}
	
	private EClassifier resolveEType(EClassifier original) {
		if (original.eIsProxy()) {
			EObject resolved = EcoreUtil.resolve(original, original.eContainer());
			System.out.println("resolved = "+resolved);
			if (!resolved.eIsProxy()) {
				return this.root.getEClassifier(((ETypedElement)resolved).getName());
			}
						
			URI uri = EcoreUtil.getURI(resolved);
			String [] parts = uri.toString().split("#//");
			if (parts.length==2) {
				return this.root.getEClassifier(parts[1]);
			}
			this.oclErrors.add("Could not resolve type "+original);
		}
		return (original instanceof EClassifier) ? (EClassifier)original : null;
	}
	
	private void cloneAnnotations(EOperation op, EOperation oper) {
		EAnnotation ocl = op.getEAnnotation(EMFUtils.OCLPIVOT);
		if (ocl!=null) {
			EAnnotation another = EcoreFactory.eINSTANCE.createEAnnotation();
			another.setSource(ocl.getSource());
			for (String s : ocl.getDetails().keySet()) {
				another.getDetails().put(s, ocl.getDetails().get(s));
			}
			oper.getEAnnotations().add(another);
		}
	}
	
	private void cloneEType(ETypedElement original, ETypedElement clone) {
		EClassifier ret = original.getEType();
		if (ret!=null) {
			EClassifier cls = this.root.getEClassifier(ret.getName());
			if (cls!=null)
				clone.setEType(cls);
			else 
				clone.setEType(this.resolveEType(original.getEType()));			
		}
		else clone.setEType(null);
	}

}
