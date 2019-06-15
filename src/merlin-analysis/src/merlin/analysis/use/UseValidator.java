package merlin.analysis.use;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.eclipse.emf.common.util.EMap;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAnnotation;
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
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.tzi.kodkod.KodkodModelValidatorConfiguration;
import org.tzi.kodkod.model.config.impl.PropertyConfigurationVisitor;
import org.tzi.kodkod.model.iface.IInvariant;
import org.tzi.kodkod.model.iface.IModel;
import org.tzi.use.kodkod.UseKodkodModelValidator;
import org.tzi.use.kodkod.plugin.PluginModelFactory;
import org.tzi.use.kodkod.transform.enrich.ModelEnricher;
import org.tzi.use.main.Session;
import org.tzi.use.main.shell.Shell;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MLink;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.sys.MSystemState;

import kodkod.engine.Solution;
import kodkod.engine.Solution.Outcome;
import merlin.common.utils.EMFUtils;

// TODO: if there are several packages, add a prefix (now we are assuming class names are different in each package)
// TODO: cardinality of multivalued attributes (add constraint to restrict number of elements to upper bound)
// TODO: bags, sequences, etc. as types in operations
// TODO: OrderedSet is not supported !!!

public class UseValidator {
	
	private boolean DEBUG      = false;
	private String  DEBUG_path = null;
	
	// emf metamodel (list of its packages) and global constraints
	private List<EPackage> metamodel = new ArrayList<EPackage>();
	private List<String> globalConstraints = new ArrayList<String>();
	
	// search bounds <class/reference, scope)
	private UseSearchScope scope = null;
	
	// flag to forbid/allow empty instances
	private boolean forbidEmptyInstances = true;
	public void forbidEmptyInstances() { forbidEmptyInstances = true; } 
	public void allowEmptyInstances () { forbidEmptyInstances = false; } 
	
	// global search constraints are encoded in USE as invariants of an auxiliary class
	private EClass globalSearchConstraints = EcoreFactory.eINSTANCE.createEClass();
	
	// use metamodel and search scope (generated after compiling the emf specification)
	private StringWriter useSpecification = null; 
	private StringWriter useSearchScope   = null; 
	
	// use internal objects
	protected UseInternalValidator internalValidator;
	protected IModel               internalKodkodModel;
	protected Session              internalSession;
	
	// use errors
	protected String errors; 
	
	// result of validation (models)
	protected List<Resource> result = null;
	
	// auxiliary objects
	protected static Object globalLock = new Object();
	protected UseAdapter    adapter    = UseAdapter.INSTANCE;
	
	//
	public UseValidator (List<EPackage> metamodel, String... globalConstraints) {
		if (metamodel!=null) this.metamodel = metamodel;
		this.scope = new UseSearchScope(metamodel);
	    for (String gc : globalConstraints) this.globalConstraints.add(gc);
	}
	
	/** errors raised by use */
	public String getErrors() { return errors; }
	
	/** enables debug mode, i.e., it persists the use and properties files */
	public void setDebug(String path) { this.DEBUG = true; this.DEBUG_path = path; }
	
	/**	models generated by use */
	public List<Resource> getModels() { return result; }
	
	/** number of invocations to use */
	public int getSolvings() { return internalValidator!=null? internalValidator.numSolvings() : 0; }
	
	/** It overrides the default lower bound of a class. */
	public boolean setLowerBound(String classname, int lowerBound) { return this.scope.setLowerBounds(classname, lowerBound); }
	
	/** It overrides the default upper bound of a class. */
	public boolean setUpperBound(String classname, int upperBound) { return this.scope.setUpperBounds(classname, upperBound); }
	
	/** It overrides the default lower bound of a reference. */
	public boolean setLowerBound(String classname, String refname, int lowerBound) { return this.scope.setLowerBounds(classname, refname, lowerBound); }
	
	/** It overrides the default upper bound of a reference. */
	public boolean setUpperBound(String classname, String refname, int upperBound) { return this.scope.setUpperBounds(classname, refname, upperBound); }
	
	/** It overrides the default lower bound of all non-abstract classes. */
	public boolean setLowerBound (int lowerBound) {
		boolean result = true;
		for (EPackage pack : metamodel) 
			for (EClassifier cl : pack.getEClassifiers())
				if (cl instanceof EClass && !((EClass)cl).isAbstract()) 
					result = result || setLowerBound(cl.getName(), lowerBound);
		return result;
	}
	
	/** It overrides the default upper bound of all non-abstract classes. */
	public boolean setUpperBound (int upperBound) {
		boolean result = true;
		for (EPackage pack : metamodel) 
			for (EClassifier cl : pack.getEClassifiers())
				if (cl instanceof EClass && !((EClass)cl).isAbstract()) 
					result = result || setUpperBound(cl.getName(), upperBound);
		return result;
	}
	
	/**
	 * It looks for an instance of the metamodel. Any possible error is stored in attribute "errors".
	 * @param loadMetamodel (optional) pass "true" to load the metamodel received in the constructor;
	 * 			pass "false" to perform successive validations on the same metamodel without having
	 * 			to reload it (more efficient); the parameter is ignored the first time the method is
	 * 			invoked, as loading the metamodel is mandatory in that case; if no value is specified,
	 * 			the metamodel is loaded.
	 * @return true if an instance is found, false if the metamodel has no instances.
	 */
	public boolean validate (boolean loadMetamodel) {
		OutputStream logStream = new ByteArrayOutputStream();
		PrintWriter  logWriter = new PrintWriter(logStream);
		this.result = new ArrayList<Resource>();
		this.errors = "";

		synchronized (globalLock) {
			// load metamodel and search bounds
			if (!this.load(loadMetamodel)) return false;

			// find instance
			internalValidator.validate(internalKodkodModel);
			
			// process and store result
			internalSession.system().registerPPCHandlerOverride(Shell.getInstance());
			MSystemState omodel  = internalValidator.getSolution(0); 
			boolean      isValid = this.isValid(omodel, logWriter); 
			Outcome      outcome = internalValidator.getSolution().outcome();
			if (!isValid || outcome == null || outcome == Outcome.TRIVIALLY_UNSATISFIABLE || outcome == Outcome.UNSATISFIABLE) {
				errors = "no instance was found"; 
				return false;
			}
			else {
				Resource resource = translate2emf(omodel);
				if (resource!=null) this.result.add(resource);
			}
		}

		return true;
	}
	public boolean validate () { return validate(true); }
	
	/**
	 * It saves the result of a validation (one or more instance models). To be called after method validation.
	 */
	public boolean saveResult (String path) {
		boolean  success  = true;
		Resource resource = null;
		for (int i=0; i<this.result.size(); i++) {
			try {
				resource = this.result.get(i);
				resource.setURI(URI.createFileURI(path + File.separator + "instance"+(i+1)+".xmi"));
				resource.save(null);
			} 
			catch (IOException e) {
				e.printStackTrace();
				errors += (errors.isEmpty()? "" : "; ") + "Error saving model "+resource.getURI().toString();
				success = false;
			}
		}
		return success;
	}
	
	/**
	 * It activates the specified invariant.
	 * @param className
	 * @param invariantName
	 * @return false if the invariant does not exist or the metamodel has not been loaded, true otherwise
	 */
	public boolean activateInvariant(String className, String invariantName) { return setInvariantActivation(className, invariantName, true); }
	
	/**
	 * It deactivates the specified invariant.
	 * @param className
	 * @param invariantName
	 * @return false if the invariant does not exist or the metamodel has not been loaded, true otherwise
	 */
	public boolean deactivateInvariant(String className, String invariantName) { return setInvariantActivation(className, invariantName, false); }
	
	// -------------------------------------------------------------------------------------------
	// PRIVATE / PROTECTED METHODS
	// -------------------------------------------------------------------------------------------
	
	/**
	 * It loads the metamodel into USE. Any possible error is stored in attribute "errors".
	 */	
	public/*protected*/ boolean load (boolean loadMetamodel) {
		if (!loadMetamodel && this.internalKodkodModel!=null) {
			this.internalValidator = newUseInternalValidator(this.internalSession); 
			return true;
		}
		
		OutputStream logStream = new ByteArrayOutputStream();
		PrintWriter  logWriter = new PrintWriter(logStream);

		// translate specification into use (metamodel and search scope)
		this.translate2use();

		// compile specification
		InputStream   iStream = new ByteArrayInputStream(useSpecification.toString().getBytes());
		MModel  specification = USECompiler.compileSpecification(iStream, "<solver>", logWriter, new ModelFactory());
		if (specification == null) {
			errors = logStream.toString(); // specification has errors
			return false;			
		}

		// load specification in use
		MSystem system = new MSystem(specification); 
		this.internalSession = new Session();
		this.internalSession.setSystem(system);
		PluginModelFactory.INSTANCE.registerForSession(this.internalSession);
		PluginModelFactory.INSTANCE.onClassInvariantLoaded(null); // enforce model reload
		this.internalValidator   = newUseInternalValidator(this.internalSession);
		this.internalKodkodModel = PluginModelFactory.INSTANCE.getModel(system.model());
		ModelEnricher enricher   = KodkodModelValidatorConfiguration.INSTANCE.getModelEnricher();
		enricher.enrichModel(system, internalKodkodModel);

		// load search bounds (file.properties)
		try {
			Configuration config = extractConfigFromString(useSearchScope.toString());
			internalKodkodModel.reset(); 
			PropertyConfigurationVisitor newConfigurationVisitor = new PropertyConfigurationVisitor(config, logWriter);
			internalKodkodModel.accept(newConfigurationVisitor);
			if (newConfigurationVisitor.containErrors()) {
				errors = newConfigurationVisitor.toString();
				return false;
			}
		} 
		catch (ConfigurationException e) {
			e.printStackTrace();
			errors = logStream.toString(); // search bounds have errors
			return false;
		}

		return true;
	}	
		
	/**
	 * Global search constraints are encoded as invariants in an auxiliary class that is mandatorily instantiated.
	 * If forbidEmptyInstances=true, we add an invariant ensuring that the generated instance has at least 1 object.
	 */
	protected void encodeGlobalSearchOptions() {
		this.globalSearchConstraints.setName("AuxiliaryClass");
		this.globalSearchConstraints.getEAnnotations().clear(); 
		EAnnotation auxiliaryAnn = EcoreFactory.eINSTANCE.createEAnnotation();
		auxiliaryAnn.setSource(EMFUtils.OCL);
		
		// invariant = T1.allInstances()->size() + ... + Tn.allInstances()->size() > 0 
		if (forbidEmptyInstances) {
			String invariant = "";
			for (EPackage epackage : metamodel) {
				for (EClassifier eclass : epackage.getEClassifiers())
					if ((eclass instanceof EClass) && !((EClass)eclass).isAbstract())
						invariant += " + " + eclass.getName() + ".allInstances()->size()";
			}
			if (!invariant.isEmpty())  
				auxiliaryAnn.getDetails().put("non_empty_instance", invariant.substring(3) + " > 0");
		}
		
		// global constraints (received in UseValidator constructor)
		for (int i=0; i<globalConstraints.size(); i++) {
			auxiliaryAnn.getDetails().put("global_constraint_"+(i+1), globalConstraints.get(i));
		}

		if (!auxiliaryAnn.getDetails().isEmpty()) 
			this.globalSearchConstraints.getEAnnotations().add(auxiliaryAnn);				
	}	
	
	/**
	 * It translates the metamodel into USE.
	 */
	protected boolean translate2use () {
		useSpecification = new StringWriter();
		useSearchScope   = new StringWriter();
		useSpecification.append("model ecore\n\n");
		useSearchScope  .append("[ecore]\n\n");
		
		// global search options (e.g., non-empty instances)
		encodeGlobalSearchOptions();
		if (!this.globalSearchConstraints.getEAnnotations().isEmpty()) {
			EPackage auxiliaryPackage = EcoreFactory.eINSTANCE.createEPackage();
			auxiliaryPackage.getEClassifiers().add(this.globalSearchConstraints);
			scope.addPackage(auxiliaryPackage);
			scope.setLowerBounds(this.globalSearchConstraints.getName(), 1);
			scope.setUpperBounds(this.globalSearchConstraints.getName(), 1);		
			translateMetamodel (useSpecification, auxiliaryPackage);
			translateProperties(useSearchScope,   auxiliaryPackage);
		}
		
		// meta-model
		for (EPackage epackage : metamodel) {
			translateMetamodel (useSpecification, epackage);
			translateProperties(useSearchScope,   epackage);
		}
		
		// data types
		useSearchScope.append("aggregationcyclefreeness = on\n"); // avoid cycles of composition
		useSearchScope.append("forbiddensharing = on\n\n");       // avoid objects to be in two containers
		useSearchScope.append("String_min = 0\n");               
		useSearchScope.append("String_max = 10\n");               
		useSearchScope.append("Integer_min = -10\n");               
		useSearchScope.append("Integer_max = 10\n");               
		useSearchScope.append("Real_min = -10\n");               
		useSearchScope.append("Real_max = 10\n");  
		useSearchScope.append("Real_step = 0.1\n");  

		if (DEBUG && DEBUG_path != null) {
			try {
				FileWriter fw1 = new FileWriter(DEBUG_path + File.separator + "metamodel.use");
				FileWriter fw2 = new FileWriter(DEBUG_path + File.separator + "metamodel.properties");
				fw1.write(useSpecification.toString());
				fw2.write(useSearchScope.toString());
				fw1.close();
				fw2.close();
			} 
			catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		return true;
	}
	
	/**
	 * It translates an ecore metamodel into the USE format.
	 * @param out
	 * @param metamodel 
	 */
	protected void translateMetamodel (StringWriter out, EPackage metamodel) {
		List<EReference> references = new ArrayList<EReference>();
		// enums
		for (EClassifier cf : metamodel.getEClassifiers()) {
			if (cf instanceof EEnum) {
				EEnum e = (EEnum)cf;
				out.append("enum " + e.getName() + " {");
				String literals = "";
				for (EEnumLiteral literal : e.getELiterals()) 
					literals += literal.getLiteral()+", ";
				out.append(literals.substring(0,literals.lastIndexOf(',')) + "}\n\n");
			}
		}
		// classes
		for (EClassifier cf : metamodel.getEClassifiers()) {
			if (cf instanceof EClass) {
				EClass c = (EClass) cf;
				if (c.isAbstract()) out.append("abstract ");
				out.append("class " + c.getName());
				// superclasses
				if (!c.getESuperTypes().isEmpty()) {
					out.append(" <");
					String separator = " ";
					for (EClass st : c.getESuperTypes()) {
						out.append(separator + st.getName());
						separator = ", ";
					}						
				}
				out.append("\n");
				// attributes
				if (!c.getEAttributes().isEmpty()) {
					out.append("  attributes\n");
					for (EAttribute att : c.getEAttributes())
						 out.append("    " + adapter.useName(att) + " : " + adapter.useType(att.getEType(), att.getUpperBound(), att.isOrdered()) + "\n");
				}
				// operations
				if (!c.getEOperations().isEmpty()) {
					out.append("  operations\n");
					for (EOperation operation : c.getEOperations()) {
						out.append("    " + adapter.useName(operation) + "(");
						for (EParameter parameter : operation.getEParameters()) {
							if (parameter != operation.getEParameters().get(0)) 
								out.append(", ");
							out.append(parameter.getName() + ":" + adapter.useType(parameter.getEType(), parameter.getUpperBound(), parameter.isOrdered()));
						}
						String body = adapter.useOcl(c, operation);
						String type = adapter.useType(operation.getEType(), operation.getUpperBound(), operation.isOrdered());
						out.append(") : " + type + (body == null || body.isEmpty()? ";\n" : " = " + body + "\n"));
					}
				}
				// invariants
				EMap<String, String> invariants = EMFUtils.getInvariants(c);
				if (!invariants.isEmpty() || c.getEAllAttributes().stream().anyMatch(att -> att.getLowerBound()>0)) {
					out.append("  constraints\n");
					for (String name : invariants.keySet()) 
						out.append("    inv " + name + " : " + adapter.useOcl(c, invariants.get(name)) + "\n");
					// enforce the generation of values for mandatory attributes
					for (EAttribute att : c.getEAttributes())
						if (att.getLowerBound()>0)
							out.append("    inv mandatory_att_" + adapter.useName(att) + " : not " + adapter.useName(att) + ".oclIsUndefined()\n");
				}
				// references
				for (EReference ref : c.getEReferences()) 
					if (!references.contains(ref.getEOpposite()))
						references.add(ref);
				out.append("end\n\n");
			}
		}

		for (EReference ref : references) {
			String src_role = adapter.useSourceRole(ref);
			String tar_role = adapter.useTargetRole(ref);
			String ref_name = adapter.useName(ref);
			String src_card = ref.getEOpposite()==null? (ref.isContainment()? "[0..1]" : "[*]") : cardinality(ref.getEOpposite()); 
			out.append((ref.isContainment()?"composition ":"association ") + ref_name + " between\n");
			out.append("  " + ref.getEContainingClass().getName() + src_card         + " role " + src_role + "\n");
			out.append("  " + ref.getEReferenceType().getName()   + cardinality(ref) + " role " + tar_role + "\n");
			out.append("end\n\n");
		}
	}
	
	// [min..max]
	private String cardinality (EReference ref) {
		return ref==null? 
				"[*]":
				"[" + 
	           /* lower */ (ref.getLowerBound()==ref.getUpperBound() || (ref.getLowerBound()==0 && ref.getUpperBound()==-1)? "" : ref.getLowerBound() + "..") +
		       /* upper */ (ref.getUpperBound()==-1? "*" : ref.getUpperBound()) +
		       "]";
	}
	
	/**
	 * It translates the search scope into the USE format.
	 * @param out
	 * @param metamodel 
	 */
	protected void translateProperties(StringWriter out, EPackage metamodel) {
		List<EReference> references = new ArrayList<EReference>();	
		// classes
		for (EClassifier classifier : metamodel.getEClassifiers()) {
			out.append("# class " + classifier.getName() + "\n");
			if (!EMFUtils.isAbstract(classifier)) {
				out.append(classifier.getName() + "_min = " + scope.getLowerBounds(classifier.getName()) + "\n");
				out.append(classifier.getName() + "_max = " + scope.getUpperBounds(classifier.getName()) + "\n");
			}
		// attributes
			if (classifier instanceof EClass) {
				for (EAttribute att : ((EClass)classifier).getEAttributes()) {
					out.append(classifier.getName() + "_" + adapter.useName(att) + "_min = " + scope.getLowerBounds(classifier.getName(), adapter.useName(att)) + "\n");
					out.append(classifier.getName() + "_" + adapter.useName(att) + "_max = " + scope.getUpperBounds(classifier.getName(), adapter.useName(att)) + "\n");
				}
				for (EReference ref : ((EClass)classifier).getEReferences()) 
					if (!references.contains(ref.getEOpposite()))
						references.add(ref);				
			}	
			out.append("\n");
		}	
		// references	
		for (EReference ref : references) {
			String ref_name = adapter.useName(ref);
			out.append("# reference " + ref_name + "\n");
			out.append(ref_name + "_min = " + scope.getLowerBounds(ref.getEContainingClass().getName(), ref.getName()) + "\n");
			out.append(ref_name + "_max = " + scope.getUpperBounds(ref.getEContainingClass().getName(), ref.getName()) + "\n");
			out.append("\n");
		}	
	}	
	
	protected Configuration extractConfigFromString(String string) throws ConfigurationException {
		// ConfigurablePlugin#extractConfigFromFile
		HierarchicalINIConfiguration hierarchicalINIConfiguration = new HierarchicalINIConfiguration();
		hierarchicalINIConfiguration.load( new StringReader(string) );
		if (hierarchicalINIConfiguration.getSections().isEmpty()) {
			return hierarchicalINIConfiguration.getSection(null);
		} 
		else {
			String section = hierarchicalINIConfiguration.getSections().iterator().next();
			return hierarchicalINIConfiguration.getSection(section);
		}
	}
	
	/**
	 * It validates a model.
	 */
	protected boolean isValid (MSystemState omodel, PrintWriter logWriter) {
		// do not validate deactivated invariants
		Collection<IInvariant> allInvariants = internalKodkodModel.classInvariants();
		List<String> activeInvariants = new ArrayList<>();
		for (IInvariant invariant : allInvariants) 
			if (invariant.isActivated()) activeInvariants.add(invariant.name());
		return allInvariants.size() == activeInvariants.size()?
				omodel.check(logWriter, true, true, true,  Collections.<String>emptyList()) :
				omodel.check(logWriter, true, true, false, activeInvariants);
	}
	
	/**
	 * It translates a use model into EMF.
	 */
	protected Resource translate2emf (MSystemState result) {
		if (result==null) return null; 

		// create emf model
		ResourceSet       resourceSet      = new ResourceSetImpl();
		EPackage.Registry ePackageRegistry = resourceSet.getPackageRegistry();
		metamodel.forEach(pack -> ePackageRegistry.put(pack.getNsURI(), pack));
		Resource model = resourceSet.createResource(URI.createFileURI("./instance.xmi"));

		Hashtable<String,EObject> eobjects  = new Hashtable<String,EObject>();

		// parse objects (except instance of auxiliary class holding global search constraints)
		for (MObject useObject : result.allObjects()) {
			if (!useObject.cls().name().equals(this.globalSearchConstraints.getName())) { 
				EObject object = EMFUtils.createEObject(metamodel, useObject.cls().name());
				eobjects.put(useObject.name(), object);
				model.getContents().add(object);
	
				// parse attributes
				Map<MAttribute, Value> attributes = useObject.state(result).attributeValueMap();
				for (MAttribute attribute : attributes.keySet()) {
					String field = adapter.emfName( attribute.name() ); 
					String value = trim( attributes.get(attribute).toString() );
					if (!value.equals("Undefined")) {
						String values[] = {value};
						if (value.startsWith("Set{")) values = value.substring(4,value.length()-1).split(",");
						if  (EMFUtils.hasAttribute(object, field))
							for (String v : values) { 
								if (!v.equals("Undefined"))
									EMFUtils.setAttribute(metamodel, object, field, v);
							}
						else for (String v : values) {
							if (!v.isEmpty()) {
								EObject object2 = eobjects.get(v.substring(1));
								EMFUtils.setReference(metamodel, object, field, object2);
								if (isContainment(object, field)) model.getContents().remove(object2);
							}
						}
					}
				}
			}
		}

		// parse links
		for (MLink useLink : result.allLinks()) {
			EObject object0 = eobjects.get(useLink.linkedObjects().get(0).name());
			EObject object1 = eobjects.get(useLink.linkedObjects().get(1).name());
			String linkend0 = adapter.emfName( useLink.association().associationEnds().get(0).name() ); 
			String linkend1 = adapter.emfName( useLink.association().associationEnds().get(1).name() );	
			if (EMFUtils.hasReference(object1, linkend0)) {
				EMFUtils.setReference(metamodel, object1, linkend0, object0);
				if (isContainment(object1, linkend0)) model.getContents().remove(object0);
				if (isContainment(object0, linkend1)) model.getContents().remove(object1);
			}
//			else if (EMFUtils.hasReference(object0, linkend0)) {
//				EMFUtils.setReference(metamodel, object0, linkend0, object1);
//				if (isContainment(object0, linkend0)) model.getContents().remove(object1);
//				if (isContainment(object1, linkend1)) model.getContents().remove(object0);
			else if (EMFUtils.hasReference(object0, linkend1)) {
				EMFUtils.setReference(metamodel, object0, linkend1, object1);
				if (isContainment(object0, linkend1)) model.getContents().remove(object1);
				if (isContainment(object1, linkend0)) model.getContents().remove(object0);
			}
//			else if (linkend0.startsWith(object0.eClass().getName())) {
//				EMFUtils.setReference(metamodel, object0, linkend1, object1);
//				if (isContainment(object0, linkend1)) model.getContents().remove(object1);
//			}
//			else if (linkend1.startsWith(object1.eClass().getName())) {
//				EMFUtils.setReference(metamodel, object1, linkend0, object0);
//				if (isContainment(object1, linkend0)) model.getContents().remove(object0);
//			}
			else { System.err.println("<use> reference "+linkend0+"-"+linkend1+" not found"); }
		}	        

		return model;		
	}
	
	// used by method translate2emf: it checks whether an object defines a containment reference with the given name
	private static boolean isContainment (EObject object, String refname) {
		return EMFUtils.hasReference(object, refname)? ((EReference) object.eClass().getEStructuralFeature(refname)).isContainment() : false;
	}
	
	// used by method translate2emf: it removes the initial an final quotes of a phrase 
	private String trim (String phrase) {
		while (phrase.startsWith("'")) phrase = phrase.substring(1);
		while (phrase.endsWith("'"))   phrase = phrase.substring(0, phrase.length()-1);
		return phrase;
	}
	
	/**
	 * It activates/deactivates the specified invariant.
	 * @param className
	 * @param invariantName
	 * @param active true to activate the invariant, false to deactivate it
	 * @return false if the invariant does not exist or the metamodel has not been loaded, true otherwise
	 */
	private boolean setInvariantActivation(String className, String invariantName, boolean active) {
		if (this.internalKodkodModel == null) return false;
		IInvariant invariant = this.internalKodkodModel.getInvariant(className + "::" + invariantName);
		if (invariant == null) return false;
		if (active) invariant.activate(); 
		else        invariant.deactivate();
		return true;
	}
	
	protected interface UseInternalValidator {
		public         int      numSolvings(); 
		public         int      numSolutions();
		public default Solution getSolution() { return null; }
		public     MSystemState getSolution (int i);
		public         void     validate(IModel model);
	}
	
	protected class InternalValidator extends UseKodkodModelValidator implements UseInternalValidator {
		protected int solvings = 0;
		public InternalValidator(Session session) { super(session); solvings = 0; }
		public int      numSolvings()             { return solvings; }
		public int      numSolutions()            { return solution==null? 0 : 1; }
		public Solution getSolution()             { return solution; }
		public MSystemState getSolution(int i)    { return i>=0 && i<numSolutions()? internalSession.system().state() : null; }
		@Override protected void handleSolution() { super.handleSolution(); solvings++; }
	}
	
	protected UseInternalValidator newUseInternalValidator(Session session) {
		return new InternalValidator(session);
	}
}