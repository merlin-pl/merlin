package merlin.analysis.validate.properties;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EContentsEList.FeatureIterator;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.prop4j.NodeReader;

import de.ovgu.featureide.fm.core.ExtensionManager.NoSuchExtensionException;
import de.ovgu.featureide.fm.core.base.IConstraint;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.base.impl.ConfigFormatManager;
import de.ovgu.featureide.fm.core.base.impl.Constraint;
import de.ovgu.featureide.fm.core.configuration.Configuration;
import de.ovgu.featureide.fm.core.configuration.DefaultFormat;
import de.ovgu.featureide.fm.core.configuration.Selection;
import de.ovgu.featureide.fm.core.io.IPersistentFormat;
import de.ovgu.featureide.fm.core.io.Problem;
import de.ovgu.featureide.fm.core.io.ProblemList;
import de.ovgu.featureide.fm.core.io.manager.SimpleFileHandler;
import merlin.analysis.use.UseFMScrollingValidator;
import merlin.analysis.use.UseSearchScope;
import merlin.analysis.validate.annotations.AnnotationCheck;
import merlin.analysis.validate.annotations.AnnotationChecker;
import merlin.analysis.validate.annotations.FeatureModelAnnotationCheck;
import merlin.analysis.validate.properties.SemanticCompiler.CompilationStrategy;
import merlin.common.features.DefaultFeatureProvider;
import merlin.common.issues.IssueLevel;
import merlin.common.issues.ValidationIssue;
import merlin.common.utils.EMFUtils;
import merlin.common.utils.MerlinAnnotationUtils;
import merlin.featureide.composer.EcoreProductGenerator;

public class PropertyChecker {
	
	public static final String OUTPUT_FOLDER = "property-analysis";
	
	protected IFile ecore = null;
	protected DefaultFeatureProvider provider = new DefaultFeatureProvider();
	protected CompilationStrategy compilationStrategy = CompilationStrategy.min;
	
	private boolean DEBUG      = false;
	private String  DEBUG_path = null;
	
	/** enables debug mode, i.e., it persists the use and properties files */
	public void setDebug(String path) { this.DEBUG = true; this.DEBUG_path = path; }	
	
	public PropertyChecker(IFile ecore) {
		this.ecore = ecore;
	}	
	
	public enum SolutionArity {ONE, ALL_MIN, ALL_MAX};
	public enum ProblemSpace  {EXISTS, NOTEXISTS, FORALL};
	
	/**
	 * Checking of a property.
	 * @param property property to check, which can mention features prefixed by $
	 * @param configurationScope formula over features in the FM, it strengthens the search space to the given configurations 
	 * @param arity number of solutions (one / all)
	 * @param problem exists (metamodels with some instance satisfying the property), notexists (metamodels with no instance satisfying the property), forall (metamodels with all instances satisfying the property)
	 * @param generateWitness true to persist the witness model that exemplifies the property
	 * @param generateConfiguration true to persist the configuration that exemplifies the property
	 * @param exerciseFeatures true to generate models that exercise the features selected by the configuration
	 * @param checkSyntax (optional, true by default) true to validate the syntax of the 150-metamodel before checking the property (recommended)
	 * @return
	 */
	public PropertyResult check (String property, String configurationScope, SolutionArity arity, ProblemSpace problem, boolean generateWitness, boolean generateConfiguration, boolean exerciseFeatures) { return check(property, configurationScope, arity, problem, generateWitness, generateConfiguration, exerciseFeatures, true); }
	public PropertyResult check (String property, String configurationScope, SolutionArity arity, ProblemSpace problem, boolean generateWitness, boolean generateConfiguration, boolean exerciseFeatures, boolean checkSyntax) {
		List<String> properties = new ArrayList<>();
		if (property!=null && !property.trim().isEmpty()) properties.add(property);
		List<PropertyResult> result = check(properties, configurationScope, arity, problem, generateWitness, generateConfiguration, exerciseFeatures, checkSyntax); 
		return result.size()>0? result.get(0) : null;
	}
	
	/**
	 * Checking of a list of properties. The method returns a list of results, each one corresponding 
	 * to the analysis of a property in the list (properties and results maintain the same order). 
	 * If the meta-model contains syntactic errors, only an erroneous result is returned.
	 * @param properties properties to check, which can mention features prefixed by $
	 * @param configurationScope formula over features in the FM, it strengthens the search space to the given configurations 
	 * @param arity number of solutions (one / all)
	 * @param problem exists (metamodels with some instance satisfying the property), notexists (metamodels with no instance satisfying the property), forall (metamodels with all instances satisfying the property)
	 * @param generateWitness true to persist the witness model that exemplifies the property
	 * @param generateConfiguration true to persist the configuration that exemplifies the property
	 * @param exerciseFeatures true to generate models that exercise the features selected by the configuration
	 * @param checkSyntax (optional, true by default) true to validate the syntax of the 150-metamodel before checking the property (recommended)
	 * @return
	 */
	public List<PropertyResult> check (List<String> properties, String configurationScope, SolutionArity arity, ProblemSpace problem, boolean generateWitness, boolean generateConfiguration, boolean exerciseFeatures, boolean checkSyntax) {
		boolean  allSolutions = arity != SolutionArity.ONE || problem != ProblemSpace.EXISTS;
		if      (arity == SolutionArity.ALL_MIN) compilationStrategy = CompilationStrategy.min;
		else if (arity == SolutionArity.ALL_MAX) compilationStrategy = CompilationStrategy.max;
		List<PropertyResult>  result       = new ArrayList<>();
		if (properties==null) properties   = new ArrayList<>();
		if (properties.isEmpty()) properties.add("true");
		properties.replaceAll(property -> property.trim());
		configurationScope = configurationScope.trim();
		
		// perform syntactic validation, and return if there are errors
		if (checkSyntax) {
			AnnotationChecker ac = new AnnotationChecker(this.ecore);
			PropertyResult error = new PropertyResult(arity, problem);
			error.addError( ac.check() );
			if (error.hasErrors()) {
				result.add(error);
				return result;
			}
		}
		
		// extend meta-model with the feature model information
		List<EPackage>  metamodel = readEcore();
		AnnotationCheck check     = new FeatureModelAnnotationCheck (provider, ecore.getProject());
		check.check(metamodel.get(0), false); // TODO: load all packages
		SemanticCompiler compiler = new SemanticCompiler(metamodel, provider, ecore.getProject());
		compile (compiler, allSolutions, compilationStrategy, exerciseFeatures);
		
		// if given, add properties to feature-model class
		EClassifier fmClass = metamodel.get(0).getEClassifier(SemanticCompiler.FEATURE_MODEL_CLASS);
		EAnnotation fmOcl   = EMFUtils.getOCLAnnotation(fmClass);
		String      propertyName = "property2check";
		for (int i=0; i<properties.size(); i++) {
			if (problem == ProblemSpace.FORALL) 
				 fmOcl.getDetails().put(propertyName+i, "not (" + properties.get(i).replace("$", "self.") + ")");
			else fmOcl.getDetails().put(propertyName+i,           properties.get(i).replace("$", "self."));
		}
		
		// if given, add partial configuration to feature-model class
		if (!configurationScope.isEmpty()) fmOcl.getDetails().put("configuration2check", configurationScope.replace("$", "self."));	
		
		// check properties
		UseFMScrollingValidator validator = getValidator(metamodel, allSolutions, generateWitness, generateConfiguration, provider.getFeatureModel());
		validator.setLowerBound(SemanticCompiler.FEATURE_MODEL_CLASS, 1);
		validator.setUpperBound(SemanticCompiler.FEATURE_MODEL_CLASS, 1);
		validator.setUpperBound(SemanticCompiler.FEATURE_MODEL_CLASS, SemanticCompiler.BASE_CLASS_RELATION, Math.min(127, UseSearchScope.DEFAULT_CLASS_UPPER_BOUND*(numclasses(metamodel)-1)));
		for (EAttribute feature : ((EClass)fmClass).getEAllAttributes()) { 
			validator.setLowerBound(SemanticCompiler.FEATURE_MODEL_CLASS, feature.getName(), feature.getLowerBound());
			validator.setUpperBound(SemanticCompiler.FEATURE_MODEL_CLASS, feature.getName(), feature.getUpperBound());
		}
		validator.allowEmptyInstances();
		if (this.DEBUG) validator.setDebug(this.DEBUG_path);
		if (properties.size() <= 1) {
			validator.validate();
			result.add( process_validation_result(metamodel, validator, configurationScope, arity, problem, generateWitness, generateConfiguration) );
		}
		else {
			validator.load(true);
			// deactivate all properties
			for (int i=0; i<properties.size(); i++) validator.deactivateInvariant(SemanticCompiler.FEATURE_MODEL_CLASS, propertyName+i);
			// activate one property at a time, and validate
			for (int i=0; i<properties.size(); i++) {
				validator.activateInvariant(SemanticCompiler.FEATURE_MODEL_CLASS, propertyName+i);
				validator.validate(false);
				result.add( process_validation_result(metamodel, validator, configurationScope, arity, problem, generateWitness, generateConfiguration) );
				validator.deactivateInvariant(SemanticCompiler.FEATURE_MODEL_CLASS, propertyName+i);
			}
		}
		return result;
	}
	
	// compile metamodel using compiler
	protected List<EPackage> compile (SemanticCompiler compiler, boolean allSolutions, CompilationStrategy strategy, boolean exerciseFeatures) { 
		return compiler.compile(allSolutions, strategy, exerciseFeatures);
	}
	
	// number of concrete classes in metamodel
	private int numclasses (List<EPackage> metamodel) {
		int nclasses = 0;
		for (EPackage pack : metamodel) 
			nclasses += pack.getEClassifiers().stream().filter(cl -> cl instanceof EClass && !((EClass)cl).isAbstract()).count();
		return nclasses;
	}
	
	// instance of USE validator
	protected UseFMScrollingValidator getValidator(List<EPackage> metamodel, boolean allSolutions, boolean generateWitness, boolean generateConfiguration, IFeatureModel featureModel) {
		return new UseFMScrollingValidator(metamodel, allSolutions, true/*strategy*/, true/*only nonpartial configs*/, featureModel);
	}

	/**
	 * Hook for subclasses
	 */
	protected List<EPackage> readEcore () { return EMFUtils.readEcore(ecore); }
	
	/**
	 * It processes the result that is stored in the received validator.
	 * The method should only be called after executing validator.validate.
	 */
	private PropertyResult process_validation_result(List<EPackage> metamodel, UseFMScrollingValidator validator, String configurationScope, SolutionArity arity, ProblemSpace problem, boolean generateWitness, boolean generateConfiguration) {
		PropertyResult result = new PropertyResult(arity, problem);
		if (validator.getErrors().isEmpty()) {
			result.setSolvings(validator.getSolvings());
			
			// arity = 1,   problem = exists => return 1 configuration found
			// arity = all, problem = exists => return all configurations found
			if (problem == ProblemSpace.EXISTS) {
				result.setSolutions(validator.getModels().size());
				if (result.getSolutions() > 0) {
					if (generateWitness || generateConfiguration) {
						String basefolder = ecore.getProject().getLocation().toString();
						String time       = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
						int    file_index = 1;
						int solutions = validator.getModels().size();
						for (int i=0; i<solutions; i++) {
							Resource      model         = validator.getModels().get(i);
							Configuration configuration = validator.getConfigurations().get(i);
							String path = output_folder(basefolder, time, file_index++);
							if (generateConfiguration) result.addError( save(configuration, path) ) ;
							if (generateWitness)       result.addError( save(model, metamodel, configuration, path) );
						}
						result.setOutputfolder(output_subfolder(time));
					}
				}
			}
			
			// arity = 1,   problem = forall => return 1 configuration not found
			// arity = 1,   problem = none   => return 1 configuration not found
			// arity = all, problem = forall => return all configurations not found
			// arity = all, problem = none   => return all configurations not found	
			else {
				List<Configuration> not_found_configurations = valid_configurations(provider.getFeatureModel(), configurationScope, validator.getConfigurations(), arity!=SolutionArity.ONE);
				if (generateConfiguration) {				
					String basefolder = ecore.getProject().getLocation().toString();
					String time       = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
					int    file_index = 1;					
					for (Configuration cfg : not_found_configurations) {
						String path = output_folder(basefolder, time, file_index++);
						result.addError( save(cfg, path) );
					}
					result.setOutputfolder(output_subfolder(time));
				}
				result.setSolutions(not_found_configurations.size());
			}
		}
		else result.addError(validator.getErrors());
		
		return result;
	}
	
//	/**
//	 * It creates the configuration object that corresponds to the received model.
//	 * @param model
//	 * @return configuration
//	 */
//	protected Configuration extract_configuration (Resource model) {
//		Configuration configuration = new Configuration(provider.getFeatureModel());
//		for (EObject object : model.getContents()) {
//			if (object.eClass().getName().equals(SemanticCompiler.FEATURE_MODEL_CLASS)) {
//				for (SelectableFeature feature : configuration.getFeatures()) {
//					if (!feature.hasChildren() && feature.getSelection() == Selection.UNDEFINED) {							 
//						EStructuralFeature sf = object.eClass().getEStructuralFeature(feature.getName());
//						boolean  featureValue = (Boolean)object.eGet(sf);
//						configuration.setManual(feature.getName(), featureValue==true? Selection.SELECTED : Selection.UNSELECTED);
//					}
//				}
//				break;
//			}
//		}
//		return configuration;
//	}
	
	/**
	 * It persists a configuration.
	 * @param configuration
	 * @param path
	 * @return
	 */
	private List<ValidationIssue> save (Configuration configuration, String folder) {
		List<ValidationIssue> issues = new ArrayList<>();
		try {
			IPersistentFormat<Configuration> format = ConfigFormatManager.getInstance().getFormatById(DefaultFormat.ID);
			ProblemList problems = SimpleFileHandler.save(Paths.get(folder + File.separator + "cfg" + "." + format.getSuffix()), configuration, format);
			if (!problems.isEmpty())
				for (Problem problem : problems.getErrors())
					issues.add(new ValidationIssue(problem.getMessage(), IssueLevel.ERROR, null));
		} 
		catch (NoSuchExtensionException e) { issues.add(new ValidationIssue(e.getMessage(), IssueLevel.ERROR, null)); }
		return issues;
	}
	
	/**
	 * It persists a model and its metamodel (this latter given by a configuration over a 150 metamodel).
	 * @param model
	 * @param metamodel150
	 * @param configuration configurations that corresponds to the model
	 * @param folder
	 * @return
	 */
	private List<ValidationIssue> save (Resource model, List<EPackage> metamodel150, Configuration configuration, String folder) {
		List<ValidationIssue> issues    = new ArrayList<>();
		List<EObject>         modelCopy = compilationStrategy==CompilationStrategy.max? clone(metamodel150, model) : null;
		Resource mm = null;
		
		try {
			// save metamodel
			EcoreProductGenerator epg = new EcoreProductGenerator(readEcore(ecore), ecore.getName());
			Resource r  = epg.genProduct(configuration, Path.fromOSString(folder).lastSegment());
			mm = r.getResourceSet().createResource(URI.createURI("file://" + folder + ecore.getName()));
			mm.getContents().addAll(r.getContents());
			mm.save(null);
		}
		catch (IOException e) { issues.add( new ValidationIssue(e.getMessage(), IssueLevel.ERROR, null) ); }	

		try {
			if (mm!=null) {
				// remove auxiliary elements from model
				filter(model, metamodel150, mm);  
				
				// ... if 150mm uses modifier 'containment', there may be non-containment references which get 
				// compiled into compositions for some configurations. In such cases, we postprocess the model 
				// to convert links into compositions (i.e., remove the contained objects from the model root, 
				// and convert link into composition in the 150mm before saving the model).
				List<EReference> compositions = MerlinAnnotationUtils.getCompositions(metamodel150, configuration);
				Iterator<EObject> objects     = model.getAllContents();
				List<EObject>     roots       = model.getContents();
				List<EObject>     remove      = new ArrayList<EObject>();
				Set<EReference>   update      = new HashSet<EReference>();
				while (objects.hasNext()) {
					EObject object = objects.next();
					for (FeatureIterator<EObject> featureIterator = (FeatureIterator<EObject>)object.eCrossReferences().iterator(); featureIterator.hasNext(); ) {
						EObject    target    = (EObject)featureIterator.next();
						EReference reference = (EReference)featureIterator.feature();
						// candidate objects: root objects participating in non-containment references which are containment in the product
						if (roots.contains(target) && 
							!reference.isContainment() && 
							compositions.stream().anyMatch(	comp -> 
								comp.getName().equals(reference.getName()) && 
								comp.getEContainingClass().getName().equals(reference.getEContainingClass().getName()) &&
								comp.getEType().getName().equals(reference.getEType().getName())) ) {
							remove.add(target);
							update.add(reference);									
						}
					}
				}
				update.forEach(ref -> ref.setContainment(true));
				roots.removeAll(remove);
							
				// retype model by metamodel associated to configuration						
				String suffix   = Path.fromOSString(folder).lastSegment();
				String filepath = folder + File.separator + "instance.xmi";
				ByteArrayOutputStream os = new ByteArrayOutputStream();
				model.save(os, null);
				String content = new String(os.toByteArray(), "UTF-8");
				os.close();
				for (EPackage pack : metamodel150) 
					content = content.replace("\""+pack.getNsURI()+"\"", "\""+pack.getNsURI()+suffix+"\"");

				// save model	
				if (update.isEmpty()) register(metamodel150, model.getResourceSet());
				PrintWriter printer = new PrintWriter(filepath);
				printer.write(content);
				printer.close();
				update.forEach(ref -> ref.setContainment(false));
				if (update.isEmpty()) register(metamodel150, model.getResourceSet());
			}
		}
		catch (IOException e) { issues.add( new ValidationIssue(e.getMessage(), IssueLevel.ERROR, null) ); }

		// restore unfiltered instance of 150 metamodel 
		if (compilationStrategy==CompilationStrategy.max) { 
			model.getContents().clear(); 
			model.getContents().addAll(modelCopy); 
		}
		
		return issues;
	}
	
	/**
	 * It creates deep clone of received model.
	 * @param metamodel
	 * @param model
	 * @return root objects in model clone
	 */
	protected List<EObject> clone (List<EPackage> metamodel, Resource model) {
		Map<EObject, EObject> objectclones = new HashMap<>();
		List<EObject> rootobjects = new ArrayList<>();
		Object value;
		
		// object clones
		Iterator<EObject> it = model.getAllContents();
		while (it.hasNext()) { 
			EObject object = it.next();
			EObject clone  = EMFUtils.createEObject(metamodel, object.eClass().getName());
			objectclones.put(object, clone);
			if (model.getContents().contains(object))
				rootobjects.add(clone);
		}		
		
		for (EObject object : objectclones.keySet()) {
			// attribute clones
			for (EAttribute att : object.eClass().getEAllAttributes()) {
				if ((value = object.eGet(att)) != null) {
					if (att.isMany()) {
						for (Object elem : (EList<?>)value) { 
							EMFUtils.setAttribute(metamodel, objectclones.get(object), att.getName(), elem.toString());
					}}
					else EMFUtils.setAttribute(metamodel, objectclones.get(object), att.getName(), value.toString());
			}}
			
			// reference clones
			for (EReference ref : object.eClass().getEAllReferences()) {
				if ((value = object.eGet(ref)) != null) {
					if (ref.isMany()) {
						for (EObject elem : (EList<EObject>)value) {
							EMFUtils.setReference(metamodel, objectclones.get(object), ref.getName(), objectclones.get(elem));
					}}
					else EMFUtils.setReference(metamodel, objectclones.get(object), ref.getName(), objectclones.get(value));
		}}}
		
		return rootobjects;
	}
	
	/**
	 * It removes from the model any object/attribute representing feature model information.
	 * If the metamodel was compiled using the max strategy, then it also removes from the model
	 * any object and feature not present in the received metamodel.
	 * @param model
	 * @param metamodel150
	 * @param metamodel model's metamodel
	 */
	protected void filter (Resource model, List<EPackage> metamodel150, Resource metamodel) {
		// remove information representing the feature model
		EClass             spClass = (EClass)metamodel150.get(0).getEClassifier(SemanticCompiler.BASE_CLASS);
		EStructuralFeature fmRef   = spClass.getEStructuralFeature(SemanticCompiler.FEATURE_MODEL_RELATION);
		model.getContents().removeIf(object -> object.eClass().getName().equals(SemanticCompiler.FEATURE_MODEL_CLASS));
		Iterator<EObject> it = model.getAllContents();
		while (it.hasNext()) {
			EObject object = it.next(); 
			spClass.getEAttributes().forEach(att -> object.eUnset(att));
			object.eSet(fmRef, null);	
		}
		
		// if compilation strategy is max...
		if (this.compilationStrategy == CompilationStrategy.max) {
			List<EObject> objectstodelete  = new ArrayList<>(), objectstopreserve = new ArrayList<>();
			List<EClass>  metamodelclasses = new ArrayList<>();
			
			// ... obtain list of classes in metamodel			
			it = metamodel.getAllContents();
			while (it.hasNext()) {
				EObject element = it.next();
				if (element instanceof EClass) 
					metamodelclasses.add((EClass)element);
			}
			
			// ... obtain lists of model objects to preserve and to delete
			it = model.getAllContents();
			while (it.hasNext()) {
				EObject object         = it.next();
			    EClass  object150class = object.eClass(); 			    
				EClass  objectclass    = null; for (EClass aclass : metamodelclasses) if (aclass.getName().equals(object150class.getName())) { objectclass = aclass; break; }
				if (objectclass == null) 
					 objectstodelete.add(object);
				else objectstopreserve.add(object);
			}
			
			// ... remove objects that do not belong to the metamodel, and move their children to the root of the resource
			for (EObject object : objectstodelete) 
				for (EReference ref : object.eClass().getEAllReferences()) 
					if (ref.isContainment()) 
						move2root(model.getContents(), ref, object.eGet(ref));
			EcoreUtil.deleteAll(objectstodelete, false);
			
			// ... remove features that do not belong to the metamodel
			for (EObject object : objectstopreserve) {
			    EClass  object150class = object.eClass(); 			    
				EClass  objectclass    = null; for (EClass aclass : metamodelclasses) if (aclass.getName().equals(object150class.getName())) { objectclass = aclass; break; }
				for (EAttribute att : object150class.getEAllAttributes())
					if (objectclass.getEAllAttributes().stream().noneMatch(att2 -> att.getName().equals(att2.getName()))) 
						object.eUnset(att);
				for (EReference ref : object150class.getEAllReferences())
					if (objectclass.getEAllReferences().stream().noneMatch(ref2 -> ref.getName().equals(ref2.getName()))) {
						if (ref.isContainment()) 
							move2root(model.getContents(), ref, object.eGet(ref));
						object.eSet(ref, ref.isMany()? new ArrayList<>() : null);
					}
			}
		}
	}
	
	// move content of reference to list root
	private void move2root (List<EObject> root, EReference reference, Object value) {
		List<EObject> children = new ArrayList<>();
		if (reference.isMany()) 
			 children.addAll((EList<EObject>)value);
		else children.add((EObject)value);
		for (EObject child : children)
			if (child != null)
				root.add(child);
	}
	
	/**
	 * It creates an index with all valid configurations of a feature model
	 * (i.e., satisfying the feature model and the given formula over features).
	 * @param featureModel
	 * @param formulaOverFeatures
	 */
	protected List<Configuration> valid_configurations (IFeatureModel featureModel, String formulaOverFeatures) {		
		return valid_configurations(featureModel, formulaOverFeatures, new ArrayList<>(), true);
	}
	
	/**
	 * It returns one/all valid configurations of a feature model (i.e., satisfying the 
	 * feature model and the given formula over features), excluding those in parameter 
	 * "exclude".
	 * @param featureModel feature model
	 * @param formulaOverFeatures formula over the features
	 * @param exclude configurations to be excluded from the result
	 * @param exhaustive true to return all-subset, false to return 1 in all-subset
	 */
	protected List<Configuration> valid_configurations (IFeatureModel featureModel, String formulaOverFeatures, List<Configuration> exclude, boolean exhaustive) {
		
		// add formula over features to feature model
		IConstraint newConstraint = null;
		if (!formulaOverFeatures.isEmpty()) {
			NodeReader nodereader = new NodeReader();
			newConstraint         = new Constraint(featureModel, nodereader.stringToNode(formulaOverFeatures));
			featureModel.addConstraint(newConstraint); 
		}

		// calculate difference (only 1 element if not exhaustive)
		List<Configuration> difference = new ArrayList<>();
		FeatureConfigurationIterator it = new FeatureConfigurationIterator(featureModel);
		Configuration cfg = null;
		while ((cfg = it.next()) != null) {
			boolean found = false;
			for (Configuration selection : exclude) {
				if (cfg.getSelectedFeatures().stream().allMatch         (feature -> selection.getSelectablefeature(feature.getName()).getSelection() == Selection.SELECTED) && 
				    cfg.getUnSelectedFeatures().stream().allMatch       (feature -> selection.getSelectablefeature(feature.getName()).getSelection() != Selection.SELECTED) && 
				    cfg.getUndefinedSelectedFeatures().stream().allMatch(feature -> selection.getSelectablefeature(feature.getName()).getSelection() != Selection.SELECTED)) { 
					found = true;
					break;
				}
			}		
			if (!found) {
				difference.add(cfg);
				if (!exhaustive) break;
			}			
		}
		
		// remove formula over features from feature model
		featureModel.removeConstraint(newConstraint);

		return exhaustive || difference.isEmpty()? difference : difference.subList(0, 1);
	}
		
	private Resource readEcore (IFile f) {
		ResourceSet resourceSet = new ResourceSetImpl();
		resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new EcoreResourceFactoryImpl());
		URI fileURI = URI.createFileURI(f.getFullPath().toOSString());
		return resourceSet.getResource(fileURI, true);
	}
	
	private void register (List<EPackage> packages, ResourceSet rs) {
		for (EPackage pack : packages) {
			EPackage.Registry.INSTANCE.put(pack.getNsURI(), pack.getEFactoryInstance().getEPackage());
			rs.getPackageRegistry().put   (pack.getNsURI(), pack.getEFactoryInstance().getEPackage());
		}
	}
	
	private String output_folder (String parent_folder, String time, int index) {
		String subfolder = output_subfolder (time);
		String folder    = Path.fromOSString(parent_folder + File.separator + subfolder + index + File.separator).toOSString();
		File   file      = new File(folder); 
		file.mkdirs();
		try { ecore.getProject().getFolder(subfolder).refreshLocal(IResource.DEPTH_ONE, null); } catch (CoreException e1) {}
		return folder;
	}
	
	private String output_subfolder (String time) { return OUTPUT_FOLDER + "-" + time + File.separator; }
}
