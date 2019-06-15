package merlin.common.utils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.emf.common.util.BasicEMap;
import org.eclipse.emf.common.util.EMap;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EOperation;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreEList;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.ocl.ecore.Constraint;

/**
 * Auxiliary methods for EMF models/metamodels. 
 */
public class EMFUtils {
	
	// not exact match, but extended differently depending on the OCL version used	 
	public static final String OCL   = "http://www.eclipse.org/emf/2002/Ecore/OCL/";
	public static final String OCLPIVOT = "http://www.eclipse.org/emf/2002/Ecore/OCL/Pivot";
	public static final String ECORE = "http://www.eclipse.org/emf/2002/Ecore";
	public static final String BODY = "body";
	public static final String PRECONDITION = "pre";
	public static final String POSTCONDITION = "post";
		
	/**
	 * It returns the invariants defined by an EClass
	 * @param cls class
	 * @return a map with pairs (invariant name, invariant body)
	 */
	public static EMap<String, String> getInvariants(EClassifier cls) {
		EAnnotation ann = getOCLAnnotation(cls);
		if (ann != null) return ann.getDetails();
		return new BasicEMap<String, String>();
	}
	
	public static void removeInvariants(List<String> invsToRemove, EClass cls) {
		for (EAnnotation an : cls.getEAnnotations()) {
			if (an.getSource()!=null && 
				an.getSource().contains(EMFUtils.OCL)) {
				for (String s : invsToRemove) {
					an.getDetails().removeKey(s);
				}
			}
			else if (EMFUtils.ECORE.equals(an.getSource())) {
				String val = an.getDetails().get("constraints");
				for (String s : invsToRemove) {
					val = val.replace(s, "");
				}
				val = val.replaceAll("\\s+", " ");
				val = val.trim();
				an.getDetails().put("constraints", val);
			}
		}
		// Now check if empty, and remove whole annotation
		EAnnotation an = cls.getEAnnotation(EMFUtils.OCLPIVOT);
		if (an!=null) {			
			if (an.getDetails().keySet().isEmpty()) {
				cls.getEAnnotations().remove(an);
				an = cls.getEAnnotation(EMFUtils.ECORE);
				cls.getEAnnotations().remove(an);
			}
		}
	}
	
	/**
	 * It returns the body of an EOperation
	 * @param operation
	 * @return operation body
	 */
	public static String getBody(EOperation operation) {
		EAnnotation ann = getOCLAnnotation(operation);
		if (ann != null) return ann.getDetails().get(EMFUtils.BODY);
		return "";
	}
	
	/**
	 * It sets the body of an EOperation
	 * @param operation
	 * @param operation body
	 */
	public static void setBody(EOperation operation, String body) {
		EAnnotation ann = getOCLAnnotation(operation);
		if (ann == null) {
			ann = EcoreFactory.eINSTANCE.createEAnnotation();
			ann.setSource(EMFUtils.OCL);
			operation.getEAnnotations().add(ann);
		}
		ann.getDetails().put(EMFUtils.BODY, body);
	}
	
	/**
	 * It returns the invariants defined by a named element
	 * @param ne named element
	 * @return annotation that contains the invariants
	 */
	public static EAnnotation getOCLAnnotation(ENamedElement ne) {
		for (EAnnotation an : ne.getEAnnotations()) 
			if (an.getSource().contains(EMFUtils.OCL)) 
				return an;
		return null;
	}
	
	/**
	 * It returns the eclass with the given name, null if it does not exist.
	 */
	public static EClass getEClass(List<EPackage> metamodel, String classname) {
		EClassifier classifier = null;
		EClass      aclass     = null;
		if (classname != null) {
			for (EPackage pack : metamodel) {
				if ((classifier = pack.getEClassifier(classname)) != null && classifier instanceof EClass) {
					aclass = (EClass)classifier;
					break;
				}
			}
		}
		return aclass;
	}
	
	/**
	 * It reads an ecore meta-model, and registers its packages.
	 * @param ecore file
	 * @return list of packages in meta-model
	 */
	public static List<EPackage> readEcore (IFile ecore) {
		List<EPackage> packages = new ArrayList<EPackage>();
		if (ecore!=null && ecore.exists() && ecore.getFileExtension().equals("ecore")) {
			ResourceSet resourceSet = new ResourceSetImpl();
			resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new EcoreResourceFactoryImpl());
			URI fileURI = URI.createFileURI(ecore.getFullPath().toOSString());
			Resource resource = resourceSet.getResource(fileURI, true);	
			for (EObject obj : resource.getContents()) {
				if (obj instanceof EPackage) {						
					EPackage.Registry.INSTANCE.put		(((EPackage)obj).getNsURI(), ((EPackage)obj).getEFactoryInstance().getEPackage());
					resourceSet.getPackageRegistry().put(((EPackage)obj).getNsURI(), ((EPackage)obj).getEFactoryInstance().getEPackage());
					packages.add((EPackage)obj);
				}
			}
		}
		return packages;
	}
	
	/**
	 * It reads an ecore meta-model, and registers its packages. 
	 * The ecore meta-model must be located in the workspace. 
	 * @param ecore file
	 * @return list of packages in meta-model
	 */
	public static List<EPackage> readEcore (String ecore) {
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IWorkspaceRoot root  = workspace.getRoot();
		for (IProject p : root.getProjects()) {
			IFile file = null;
			if ((file = p.getFile(ecore)) != null) {
				if (!file.exists()) continue;
				return readEcore(file);
			}
		}
		return new ArrayList<EPackage>();
	}
	
	/**
	 * It obtains the upper bound of an association end.
	 * @param className name of the class
	 * @param propertyName name of the association end
	 * @param metamodel meta-model
	 */
	public static int upperBound (String className, String propertyName, EPackage metamodel) {
		int maxCardinality = 0;
		EClassifier classifier = metamodel.getEClassifier(className); 
		if (classifier!=null) {
			for (EObject o : classifier.eCrossReferences()) 
				if (o instanceof EReference && ((EReference)o).getName().equals(propertyName))
					maxCardinality = ((EReference)o).getUpperBound(); 
		}
		return maxCardinality;
	}
	
	/**
	 * It returns whether a classifier is abstract or not.
	 * @param classifier classifier
	 * @return
	 */
	public static boolean isAbstract (EClassifier classifier) {
		EStructuralFeature isAbstract = classifier.eClass().getEStructuralFeature("abstract"); 
		return isAbstract!=null? 
			    classifier.eGet(isAbstract).toString().toString().equals("true") :
				true;
	}
	
	/**
	 * It returns whether a class has subclasses.
	 */
	public static boolean hasSubclasses (List<EPackage> metamodel, EClass aclass) {
		for (EPackage pack : metamodel) 
			for (EClassifier aclassif : pack.getEClassifiers()) {
				if (aclassif instanceof EClass) {
					if (aclass!=aclassif && aclass.isSuperTypeOf((EClass)aclassif))
						return true;
				}
			}
		return false;
	}
	
	/**
	 * It returns the subclasses of a given class, optionally including the class (last parameter).
	 */
	public static List<EClass> subclasses (List<EPackage> metamodel, EClass aclass, boolean includeSelf) {
		List<EClass> subclasses = new ArrayList<EClass>();
		if (includeSelf) subclasses.add(aclass);
		for (EPackage pack : metamodel) 
			for (EClassifier aclassif : pack.getEClassifiers()) {
				if (aclassif instanceof EClass) {
					if (aclass!=aclassif && aclass.isSuperTypeOf((EClass)aclassif))
						subclasses.add((EClass)aclassif);
				}
			}
		return subclasses;
	}
	
	/**
	 * It creates an EObject of the given type
	 */
	public static EObject createEObject (List<EPackage> metamodel, String type) {
		EObject eobject = null;
		for (EPackage pack : metamodel)
			if ((eobject = EMFUtils.createEObject(pack, type)) != null)
				break;
		return eobject;
	}
	
	/**
	 * It creates an EObject of the given type
	 */
	public static EObject createEObject (EPackage metamodel, String type) {
		EClassifier classif = metamodel.getEClassifier(type);
		return classif==null? null : metamodel.getEFactoryInstance().create((EClass)classif);
	}
	
	/**
	 * It assigns a certain value to an attribute of an EObject
	 */
	public static boolean setAttribute (List<EPackage> metamodel, EObject object, String attname, String attvalue) {
		boolean ok = false;
		for (EPackage pack : metamodel) 
			if ((ok = EMFUtils.setAttribute(pack, object, attname, attvalue)) == true)
				break;
		return ok;
	}
	
	/**
	 * It assigns a certain value to an attribute of an EObject
	 */
	public static boolean setAttribute (EPackage metamodel, EObject object, String attname, String attvalue) {
		EStructuralFeature feature = object.eClass().getEStructuralFeature(attname);

		if (feature!=null) {
			String featureTypeName = feature.getEType().getName();
			// ecore data-types
			if (metamodel.getEClassifier(featureTypeName)==null) {
				if      (isBigInteger(featureTypeName)) setAttribute(metamodel, object, feature, "java.math.BigInteger", new BigInteger(attvalue)); 
				else if (isInteger(featureTypeName))    setAttribute(metamodel, object, feature, "java.lang.Integer", new Integer(attvalue)); // object.eSet(feature, new Integer(attvalue));
				else if (isBoolean(featureTypeName))    setAttribute(metamodel, object, feature, "java.lang.Boolean", new Boolean(attvalue)); // object.eSet(feature, new Boolean(attvalue));
				else if (isString (featureTypeName))    setAttribute(metamodel, object, feature, "java.lang.String", 
						(attvalue.startsWith("\"") && attvalue.endsWith("\"")) || (attvalue.startsWith("'")  && attvalue.endsWith("'")) ? 
						 attvalue.substring(1,attvalue.length()-1) : attvalue);
				//else   object.eSet(feature, attvalue);
				else return false;
				return true;
			}
			// enumerates 
			else if (feature.getEType() instanceof EEnum) {
				EEnum        enumerate = (EEnum)feature.getEType();
				EEnumLiteral literal   = enumerate.getEEnumLiteral(attvalue.substring(attvalue.indexOf("::")+2));
				object.eSet(feature, literal);
				return true;
			}
			// user-defined data-types
			else {
				if      (isInteger(featureTypeName)) setAttribute(metamodel, object, feature, "java.lang.Integer", new Integer(attvalue));
				else if (isBoolean(featureTypeName)) setAttribute(metamodel, object, feature, "java.lang.Boolean", new Boolean(attvalue));
				else if (isString (featureTypeName)) setAttribute(metamodel, object, feature, "java.lang.String", 
						(attvalue.startsWith("\"") && attvalue.endsWith("\"")) || (attvalue.startsWith("'")  && attvalue.endsWith("'")) ? 
						 attvalue.substring(1,attvalue.length()-1) : attvalue);
				else object.eSet(feature, metamodel.getEFactoryInstance().createFromString(((EAttribute)feature).getEAttributeType(), attvalue));
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * Method used by public method EMFUtils.setAttribute (EPackage, EObject, String, String). It takes into account that the
	 * instance class name of the data type can be null, in which case, it uses the instance class name received as parameter. 
	 */
	private static void setAttribute (EPackage metamodel, EObject object, EStructuralFeature feature, String instanceClassName, Object value) {
		if (feature.getEType().getInstanceClassName()==null) {
			feature.getEType().setInstanceClassName(instanceClassName);
			object.eSet(feature, value);
		}
		else {
			int upperbound = feature.getUpperBound();
			if      (upperbound== 1)                 object.eSet(feature, value);
			else if (upperbound>0 || upperbound==-1) ((EcoreEList)object.eGet(feature)).add(value);
		}
	}
	
	/**
	 * It assigns a certain value to a reference of an EObject
	 */
	public static boolean setReference (List<EPackage> metamodel, EObject object1, String reference, EObject object2) {
		boolean ok = false;
		for (EPackage pack : metamodel) 
			if ((ok = EMFUtils.setReference(pack, object1, reference, object2)) == true)
				break;
		return ok;
	}
	
	/**
	 * It assigns a certain value to a reference of an EObject
	 */
	public static boolean setReference (EPackage metamodel, EObject object1, String reference, EObject object2) {
		EStructuralFeature feature = object1.eClass().getEStructuralFeature(reference);
		if (feature!=null && feature.isChangeable()) {
			int upperbound = EMFUtils.upperBound(object1.eClass().getName(), reference, metamodel);
			if      (upperbound== 1)                 object1.eSet(feature, object2);
			else if (upperbound>0 || upperbound==-1) ((EcoreEList)object1.eGet(feature)).add(object2);
			return true;
		}
		return false;
	}
	
	/**
	 * It checks whether an object has an attribute with the given name 
	 * @param object
	 * @param attname
	 * @return true / false
	 */
	public static boolean hasAttribute (EObject object, String attname) {
		EStructuralFeature feature = object.eClass().getEStructuralFeature(attname);
		return feature==null? false : feature instanceof EAttribute;
	}

	public static EOperation getOperation(Constraint c) {
		EOperation context=null;
		if (c.getStereotype()!=null &&  c.getStereotype().equals("body")) {
			context = (EOperation)c.getConstrainedElements().get(0);
		} 
		else if (c.getStereotype()!=null && c.getStereotype().equals("definition") ) {   
			EClass cl = (EClass)c.getConstrainedElements().get(0);
			EAnnotation ann = (EAnnotation)c.eContainer();
			if (ann!=null) {
				if ( ! (ann.eContainer() instanceof EOperation)) {						
					context = getOperation(cl, ((ENamedElement)ann.eContainer()).getName());
				}
				else { 
					EOperation fakeOp = (EOperation)ann.eContainer();				
					context = getOperation(cl, fakeOp.getName());
				}
			}
		}
		return context;
	}
	
	public static EOperation getOperation(EClass owner, String operName) {
		for (EOperation oper : owner.getEOperations()) {
			if (oper.getName().equals(operName)) return oper;
		}
		return null;
	}
	
	/**
	 * It checks whether an object defines a reference with the given name 
	 * @param object
	 * @param refname
	 * @return true / false
	 */
	public static boolean hasReference (EObject object, String refname) {
		EStructuralFeature feature = object.eClass().getEStructuralFeature(refname);
		return feature==null? false : feature instanceof EReference;
	}

	// (some methods to check ecore types)
	public static boolean isBigInteger (String type) { return type.equals("EBigInteger"); }	
	public static boolean isInteger    (String type) { return type.equals("EInt") || type.equals("Integer") || type.equals("IntegerObject") || type.endsWith("Integer"); }	
	public static boolean isString     (String type) { return type.equals("EString") || type.equals("String") || type.endsWith("String"); }	
	public static boolean isBoolean    (String type) { return type.equals("EBoolean") || type.equals("boolean") || type.equals("EBooleanObject") || type.equals("Boolean") || type.endsWith("Boolean"); }
	public static boolean isFloating   (String type) { return type.equals("EFloat")  || type.equals("float")  || type.equals("EFloatObject")  || type.equals("Float")  || type.endsWith("Float") ||
	                                                          type.equals("EDouble") || type.equals("double") || type.equals("EDoubleObject") || type.equals("Double") || type.endsWith("Double"); }

	public static Map<String,String> getPreconditions(EOperation operation) {
		return getConditions(operation, EMFUtils.PRECONDITION);
	}
	
	public static Map<String,String> getPostconditions(EOperation operation) {
		return getConditions(operation, EMFUtils.POSTCONDITION);
	}
	
	public static Map<String,String> getConditions(EOperation operation, String which) {
		EAnnotation ann = getOCLAnnotation(operation);
		Map<String,String> conds = new HashMap<>();
		if (ann==null) return conds;
		for (String nm : ann.getDetails().keySet()) 
			if (nm.startsWith(which))
				conds.put(nm.isEmpty()? which : nm, ann.getDetails().get(nm));
		return conds;
	}
}

