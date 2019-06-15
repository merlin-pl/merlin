package merlin.analysis.bindings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EOperation;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EParameter;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.ocl.OCL;
import org.eclipse.ocl.ecore.CallOperationAction;
import org.eclipse.ocl.ecore.Constraint;
import org.eclipse.ocl.ecore.EcoreEnvironmentFactory;
import org.eclipse.ocl.ecore.SendSignalAction;
import org.eclipse.ocl.expressions.OCLExpression;
import org.eclipse.ocl.expressions.OperationCallExp;
import org.eclipse.ocl.helper.OCLHelper;

import de.ovgu.featureide.fm.core.configuration.Configuration;
import merlin.analysis.validate.contracts.CompositeResolvedMethod;
import merlin.common.concepts.ConceptDecoratorBuilder;
import merlin.common.features.DefaultFeatureProvider;
import merlin.common.transformation.Method;
import merlin.common.transformation.analysis.MethodResolver;
import merlin.common.transformation.analysis.OCLHandler;
import merlin.common.transformation.analysis.ResolvedMethod;
import merlin.common.utils.EMFUtils;

public class MetamodelMethodExtension {
	
//	private final String SEPARATOR = "#";
	
	/**
	 * extends metamodel with the body of methods defined by concepts
	 * @param project
	 * @param metamodel
	 * @param checkContracts
	 * @param checkConsistency
	 */
	public void extend (IProject project, List<EPackage> metamodel/*, boolean checkContracts, boolean checkConsistency*/) {
		
//		List<String> tocheck = new ArrayList<>(); 

		for (EPackage pack : metamodel) {
			
			// add signature of concept methods to package
			ConceptDecoratorBuilder cdb = new ConceptDecoratorBuilder(pack);
			cdb.createConceptOps();
								
			// resolve methods
			MethodResolver mr     = new MethodResolver(pack);
			OCLHandler oclhandler = new OCLHandler(pack, mr);
			Map<ResolvedMethod, String> bodies = oclhandler.getAllConfigBodies();
			Set<Method> methods   = mr.methods();
			Iterator<Method> it   = methods.iterator();
			while (it.hasNext()) {
				Method     concept_method = it.next();
				EOperation class_method   = eoperation(pack, concept_method);
				if (class_method != null) {
					
					// compose method bodies
					String operation_body = compose_method_body(concept_method, class_method, mr, bodies, project);
					if (!operation_body.isEmpty()) EMFUtils.setBody(class_method, operation_body);
										
//					// to check contracts, add operation for each precondition and postcondition 
//					// (we only consider postconditions of methods with a body, of queries)
//					if (checkContracts) {
//						tocheck.addAll(handle_contracts(class_method, cdb.getPreconditions().get(concept_method),  "pre"));
//						operation_body = EMFUtils.getBody(class_method);
//						if (operation_body != null && !operation_body.isEmpty())
//							tocheck.addAll(handle_contracts(class_method, cdb.getPostconditions().get(concept_method), "pos"));
//					}
				}		
			}
			
//			// add to the package the invariants defined in the concept 
//			// (as operations to check consistency, or as invariants to check contracts)
//			if (checkConsistency)
//				 tocheck.addAll(createConceptInvariants(pack, true));
//			else createConceptInvariants(pack, false);
		}
		
		removeIncorrectMethodBodies(metamodel);	
	}
	
//	/**
//	 * adds invariants in selected concepts to received package
//	 * @param asOperations 
//	 *    if true,  invariants in concept are added as operations in package; 
//	 * 	  if false, invariants in concept are added as invariants in package.
//	 *  @return The method returns the list of created invariants/operations (class-name<SEPARATOR>invariant/method-name). 
//	 */
//	private List<String> createConceptInvariants(EPackage pack, boolean asOperations) {
//		List<String> created = new ArrayList<>();
//		EClassifier mmclass;
//		for (EPackage concept : SelectedConcepts.get()) {
//			if (!concept.getName().equals(pack.getName())) continue;
//			for (EClassifier cclass : concept.getEClassifiers()) {
//				EMap<String, String> cinvariants = EMFUtils.getInvariants(cclass);
//				if (cinvariants.isEmpty()) continue;
//				if ((mmclass = pack.getEClassifier(cclass.getName())) != null) {
//					for (String inv : cinvariants.keySet()) {
//						if (!asOperations) 
//							 getOCLInvariants(mmclass).put(inv, cinvariants.get(inv));
//						else add_boolean_operation ((EClass)mmclass, inv, cinvariants.get(inv)); 
//						created.add(mmclass.getName() + SEPARATOR + inv);
//					}
//				}
//			}
//		}
//		return created;
//	}
	
//	/**
//	 * ocl invariants of a classifier; the returned emap can be manipulated to add/remove invariants to/from the classifier
//	 */
//	private EMap<String, String> getOCLInvariants (EClassifier cl) {
//		EAnnotation clOcl = EMFUtils.getOCLAnnotation(cl);
//		if (clOcl == null) {
//			clOcl = EcoreFactory.eINSTANCE.createEAnnotation();
//			clOcl.setSource(EMFUtils.OCL);
//			cl.getEAnnotations().add(clOcl);
//		}
//		return clOcl.getDetails();
//	}
	
	/**
	 * metamodel eoperation that corresponds to the described method given in the 2nd parameter
	 */
	private EOperation eoperation (EPackage pack, Method method) {
		EClassifier ownerclass = pack.getEClassifier(method.getClassName());
		if (ownerclass!=null && ownerclass instanceof EClass) {
			Optional<EOperation> operation = ((EClass)ownerclass).getEOperations().stream().filter(op -> op.getName().equals(method.getMethodName())).findFirst();
			return operation.isPresent()? operation.get() : null;
		}
		else return null;
	}

	/**
	 * it composes the method body taking into account the presence condition of the its different implementations
	 * @param concept_method
	 * @param class_method
	 * @param mr
	 * @param bodies
	 * @param project
	 * @return
	 */
	private String compose_method_body (Method concept_method, EOperation class_method, MethodResolver mr, Map<ResolvedMethod, String> bodies, IProject project) {
		// default feature configuration
		IFile          fm        = project.getFile("model.xml");
		Configuration defaultCfg = new Configuration(new DefaultFeatureProvider(fm).getFeatureModel());

		List<ResolvedMethod> all_methods       = mr.getAllResolvedMethods(concept_method);	// body of all methods
		List<CompositeResolvedMethod> branches = new ArrayList<>(); // branches to be added in the if-then-else
		
		// methods with @merge are converted into 2^merges-1 branches of the if-then-else expression
		// e.g., if      (pc1-and-pc2)     then m1 and m2 
		//       else if (pc1-and-not-pc2) then m1 
		//       else if (not-pc1-and-pc2) then m2 
		//       else null endif endif endif
		List<ResolvedMethod> merged_methods = all_methods.stream().filter(rm -> rm.isMerge() && body(rm, bodies)!=null).collect(Collectors.toList());
		for (int i=1; i<Math.pow(2, merged_methods.size()); i++) {
			CompositeResolvedMethod crm = new CompositeResolvedMethod();
			char[] combination = new char[merged_methods.size()];
			char[] binary      = Integer.toBinaryString(i).toCharArray();
			for (int j=0; j<combination.length-binary.length; j++) combination[j] = '0';
			for (int j=0; j<binary.length; j++) combination[j+combination.length-binary.length] = binary[j];
			for (int j=0; j<combination.length; j++) {
				if (combination[j]=='1') 
				     crm.addYes(merged_methods.get(j));
				else crm.addNo (merged_methods.get(j));
			}
			// simplification: if default configuration is negated, do not add branch
			if (crm.no().stream().anyMatch(rm -> equals(rm.getConfiguration(), defaultCfg))) continue;
			// add branch
			branches.add(crm);
		}
		
		// methods with no @merge are added with no further processing
		for (ResolvedMethod rm : all_methods) {
			if (rm.isMerge() == false && body(rm, bodies) != null) {
				CompositeResolvedMethod crm = new CompositeResolvedMethod();
				crm.addYes(rm);
				branches.add(crm);
			}
		}
				
		// order branches according to override relationships (overriden method go later)
		Collections.sort(branches, new Comparator<CompositeResolvedMethod>() {
			@Override
			public int compare(CompositeResolvedMethod crm1, CompositeResolvedMethod crm2) {
				List<ResolvedMethod> rm2 = crm2.yes(); if (crm1.overrides().stream().anyMatch(rm -> rm2.contains(rm))) return 1;
				List<ResolvedMethod> rm1 = crm1.yes(); if (crm2.overrides().stream().anyMatch(rm -> rm1.contains(rm))) return -1;
				return crm1.formula().compareTo(crm2.formula());
			}
		});
		
		// compose if-then-else
		String operation_default = EMFUtils.getBody(class_method);
		String operation_body    = "";
		// a) no body for the method was provided
		if (branches.isEmpty() && operation_default != null) {
			operation_body = operation_default;
		}
		// b) there is only one possible body which corresponds to the default configuration 
		else if (branches.size() == 1 && equals(defaultCfg, branches.get(0).yes(0)!=null? branches.get(0).yes(0).getConfiguration():branches.get(0).no(0).getConfiguration())) { 
			operation_body = branches.get(0).body(bodies);
		}
		// c) there are several possible bodies, or only one which corresponds to a configuration different from the default one
		else if (!branches.isEmpty() && operation_body.isEmpty()) { 
			operation_body = "\n\t\t" + (operation_default!=null && operation_default.isEmpty()? "null" : operation_default);
			for (CompositeResolvedMethod crm : branches)
				operation_body = "\n\t\tif (" + crm.formula() + ") then " + crm.body(bodies) + " else" + operation_body + " endif";
		}
		
		return operation_body;
	}
	
	/**
	 * body of method
	 */
	private String body (ResolvedMethod method, Map<ResolvedMethod, String> bodies) {
		return bodies.get(method);
	}

//	/**
//	 * adds to the class a new boolean operation with the given name and body  
//	 */
//	private void add_boolean_operation (EClass cl, String name, String body) {
//		EDataType bool = EcoreFactory.eINSTANCE.createEDataType(); 
//		bool.setName("EBooleanObject"); 
//		bool.setInstanceClassName("java.lang.Boolean");
//		EOperation operation = EcoreFactory.eINSTANCE.createEOperation();
//		operation.setName(name);
//		operation.setEType(bool);
//		EMFUtils.setBody(operation, body);
//		cl.getEOperations().add(operation);;		
//	}
	
//	/**
//	 * Each contract associated to the method is converted into an operation in the method's owner class.
//	 * The contract is not added if it contains @pre, because USE does not support @pre in operations.
//	 * The contract is not added if it makes use of arguments of the method.
//	 * @return The method returns the list of created operations (class-name<SEPARATOR>method-name). 
//	 */
//	private List<String> handle_contracts (EOperation method, Map<String,String> contracts, String type) {
//		List<String> operations = new ArrayList<>();
//		if (contracts!=null) {
//			for (String name : contracts.keySet()) {
//				if (!contracts.get(name).contains("@pre")) { // do not add if it includes @pre
//					EClass ownerclass = (EClass)method.eContainer();
//					String methodname = method.getName() + "_" + name;
//					String methodbody = contracts.get(name);
//					try {
//						UseAdapter.INSTANCE.useOcl(ownerclass, methodbody, true); // do not add if it refers to method parameters
//						add_boolean_operation(ownerclass, methodname, methodbody);
//						operations.add(ownerclass.getName() + SEPARATOR + methodname);
//					}
//					catch (ParserException e) {}
//				}
//			}
//		}
//		return operations;
//	}
	
	/**
	 * It returns whether two given configurations have selected/unselected the same features.
	 * @param cfg1
	 * @param cfg2
	 * @return
	 */
	private boolean equals(Configuration cfg1, Configuration cfg2) {
		return cfg1.getSelectedFeatureNames().stream().allMatch(f -> cfg2.getSelectedFeatureNames().contains(f)) &&
			   cfg2.getSelectedFeatureNames().stream().allMatch(f -> cfg1.getSelectedFeatureNames().contains(f));
	}
	
	/**
	 * deletes the body of the methods that refer to other methods with an an empty body (they do not load in USE)
	 */
	private void removeIncorrectMethodBodies (List<EPackage> metamodel) {
		OCL        <EPackage, EClassifier, EOperation, EStructuralFeature, EEnumLiteral, EParameter, EObject, CallOperationAction, SendSignalAction, Constraint, EClass, EObject> ocl = OCL.newInstance(EcoreEnvironmentFactory.INSTANCE);
		OCLHelper<EClassifier, EOperation, EStructuralFeature, Constraint> helper = ocl.createOCLHelper();
		for (EPackage pack : metamodel) {
			for (EClassifier cl : pack.getEClassifiers()) {
				if (cl instanceof EClass) {
					for (EOperation operation : ((EClass)cl).getEOperations()) {
						if (calls_empty_operation(metamodel, operation, helper)) 
							EMFUtils.setBody(operation, "");
					}
				}
			}		
		}
	}	
	
	/**
	 * It returns whether the received operation invokes a method without body.
	 * @param metamodel
	 * @param sourceOperation
	 * @param helper
	 * @return
	 */
	private boolean calls_empty_operation (List<EPackage> metamodel, EOperation sourceOperation, OCLHelper<EClassifier, EOperation, EStructuralFeature, Constraint> helper) {
		String body = EMFUtils.getBody(sourceOperation);
		if (body==null || body.isEmpty()) return false;
		
		boolean call_empty_operation = false;
		try {
			helper.setOperationContext(sourceOperation.getEContainingClass(), sourceOperation);
			Constraint                 bodyCondition  = helper.createBodyCondition(body);
			OCLExpression<EClassifier> bodyExpression = bodyCondition.getSpecification().getBodyExpression();
			TreeIterator<EObject> it = bodyExpression.eAllContents();
			while (it.hasNext()) {
				EObject obj = it.next();
				if (obj instanceof OperationCallExp && ((OperationCallExp)obj).getReferredOperation() instanceof EOperation) {
					EOperation referredOperation = ((EOperation)((OperationCallExp)obj).getReferredOperation());
					if (metamodel.stream().anyMatch(p -> p.getEClassifier(referredOperation.getEContainingClass().getName())!=null)) { // operation in metamodel
						String referredBody = EMFUtils.getBody(referredOperation);
						if (referredBody == null || referredBody.isEmpty()) {
							call_empty_operation = true;
							break;
						}
					}
				}
			}
		}
		catch(Exception e) {}

		return call_empty_operation;
	}	
}
