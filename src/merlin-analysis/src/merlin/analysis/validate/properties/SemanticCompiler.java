package merlin.analysis.validate.properties;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.emf.common.util.EMap;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EOperation;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EParameter;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.ocl.Environment;
import org.eclipse.ocl.OCL;
import org.eclipse.ocl.ParserException;
import org.eclipse.ocl.ecore.CallOperationAction;
import org.eclipse.ocl.ecore.Constraint;
import org.eclipse.ocl.ecore.EcoreEnvironmentFactory;
import org.eclipse.ocl.ecore.SendSignalAction;
import org.eclipse.ocl.expressions.OCLExpression;
import org.eclipse.ocl.expressions.OperationCallExp;
import org.eclipse.ocl.expressions.TypeExp;
import org.eclipse.ocl.helper.OCLHelper;
import org.prop4j.Node;

import de.ovgu.featureide.fm.core.base.IFeature;
import de.ovgu.featureide.fm.core.base.IFeatureStructure;
import merlin.analysis.bindings.MetamodelMethodExtension;
import merlin.analysis.validate.annotations.AnnotationCheck;
import merlin.analysis.validate.annotations.FeatureModelAnnotationCheck;
import merlin.common.annotations.MerlinAnnotationStructure;
import merlin.common.annotations.modifiers.Modifiers;
import merlin.common.concepts.SelectedConcepts;
import merlin.common.features.DefaultFeatureProvider;
import merlin.common.utils.EMFUtils;
import merlin.common.utils.MerlinAnnotationUtils;
import merlin.common.utils.OclToStringVisitor;

public class SemanticCompiler {

	protected IFile ecore = null;
	protected IProject project = null;
	protected DefaultFeatureProvider provider = new DefaultFeatureProvider();
	protected List<EPackage> metamodel = new ArrayList<>();
	protected boolean allowPartialConfigurations = false;
	protected CompilationStrategy strategy = CompilationStrategy.min;
	protected List<EStructuralFeature> monovaluedToMultivalued = new ArrayList<>();
	
	// alternative compilation strategies for partial configurations
	public enum CompilationStrategy { min, max };  

	// name of classes added to the compiled meta-model
	public static final String FEATURE_MODEL_CLASS    = "FMC"; 
	public static final String BASE_CLASS             = "BC";  
	public static final String FEATURE_MODEL_RELATION = "fm"; 
	public static final String BASE_CLASS_RELATION    = "sp"; 
	
	public SemanticCompiler(IFile ecore) {
		this.ecore   = ecore;
		this.project = ecore==null? null: ecore.getProject();
	}	
	
	public SemanticCompiler(List<EPackage> metamodel, DefaultFeatureProvider provider, IProject project) {
		if (metamodel!=null) this.metamodel = metamodel;
		if (provider !=null) this.provider  = provider;
		if (project  !=null) this.project   = project;
	}	
	
	/**
	 * It extends a metamodel to make explicit the feature model and its constraints.
	 * @param allowPartialConfigurations false to generate total configurations (by default), true to generate partial configurations
	 * @param strategy compilation strategy to be used when allowPartialConfigurations=true
	 * @param illustrateFeatures false to generate random instances (by default), true to generate instances that exercise the selected features
	 */
	public List<EPackage> compile () { return compile(false, null, false); }
	public List<EPackage> compile (boolean allowPartialConfigurations, CompilationStrategy strategy, boolean illustrateFeatures) {
		this.allowPartialConfigurations = allowPartialConfigurations;
		this.strategy = strategy==null || this.allowPartialConfigurations==false? CompilationStrategy.min : strategy; 
		
		if (ecore!=null) {
			this.metamodel = EMFUtils.readEcore(ecore);
			AnnotationCheck check = new FeatureModelAnnotationCheck (provider, ecore.getProject());
			check.check(metamodel.get(0), false); // TODO: load feature models mentioned in all packages
		}
		
		// the subclasses of any reference type must be compiled using the min strategy 
		Set<EClassifier> forceMinStrategy = new HashSet<>();
		if (strategy == CompilationStrategy.max) 
			for (EPackage pack : metamodel)
				for (EClassifier aclass : pack.getEClassifiers())
					if (aclass instanceof EClass)
						for (EReference ref : ((EClass) aclass).getEReferences())
							if (ref.getEType() instanceof EClass && !forceMinStrategy.contains(ref.getEType()))
								forceMinStrategy.addAll(EMFUtils.subclasses(metamodel, (EClass)ref.getEType(), false));
		
		// obtain body of unimplemented operations from bindings to the structural concept 
		SelectedConcepts.setDefault(project);
		if (!SelectedConcepts.get().isEmpty())
			new MetamodelMethodExtension().extend(project, metamodel);

		// create class to represent the feature model (fm-class)
		EClass fmClass = createClass(FEATURE_MODEL_CLASS, false);
		
		// create superclass to all top meta-model classes (sp-class)
		EClass spClass = createClass(BASE_CLASS, true);
		for (EPackage pack : metamodel) 
			for (EClassifier aclass : pack.getEClassifiers())
				if (aclass instanceof EClass && ((EClass)aclass).getESuperTypes().isEmpty())
					((EClass) aclass).getESuperTypes().add(spClass);
		spClass.getEOperations().add(stroclIsKindOf(metamodel));
		
		// add reference between sp-class and fm-class
		EReference r1 = createReference(FEATURE_MODEL_RELATION, fmClass, 1, 1);
		EReference r2 = createReference(BASE_CLASS_RELATION,    spClass, 1, -1);
		r1.setEOpposite(r2);
		r2.setEOpposite(r1);
		spClass.getEStructuralFeatures().add( r1 );
		fmClass.getEStructuralFeatures().add( r2 );
		
		// handle features in the feature model
		handleFeatures(fmClass);
		
		// for each class in the meta-model...
		for (EPackage pack : metamodel) {
			for (EClassifier aclass : pack.getEClassifiers()) {
				if (aclass instanceof EClass) {
					EAnnotation clOcl = EMFUtils.getOCLAnnotation(aclass);
					if (clOcl == null) {
						clOcl = EcoreFactory.eINSTANCE.createEAnnotation();
						clOcl.setSource(EMFUtils.OCL);
						aclass.getEAnnotations().add(clOcl);
					}					
					// ... handle its presence condition and modifiers
					handleAnnotations((EClass)aclass, metamodel, fmClass, spClass, illustrateFeatures, forceMinStrategy);
					// ... handle presence conditions and modifiers of its features
					for (EStructuralFeature feature : ((EClass)aclass).getEStructuralFeatures()) 
						handleAnnotations(feature, fmClass); // presence condition, min, max				
				}
			}
			// ... handle containment modifier of references
			handleAnnotations(metamodel);
		}
		
		// ... invariants must be evaluated only when their presence condition is satisfied
		// ... and do not apply to the instances of subclasses added/removed through extend/reduces modifiers  
		//     (this must be done in a second pass)
		for (EPackage pack : metamodel) {
			for (EClassifier aclass : pack.getEClassifiers()) {
				if (aclass instanceof EClass) {
					EAnnotation clOcl = EMFUtils.getOCLAnnotation(aclass);
					EMap<String, String> invariants = EMFUtils.getInvariants(aclass);
					for (String inv : invariants.keySet()) {
						// modify invariant to forbid its application to subclasses added/removed through extend/reduces modifiers 
						applyFilter((EClass)aclass, inv); 
						// modify invariant to forbid its application when the PC is not satisfied
						String presenceCondition = rewrite(MerlinAnnotationUtils.getInvariantPresenceCondition((EClass)aclass, inv));
						if (!presenceCondition.equals("true")) {
							String oldInvariant = invariants.get(inv);
							String newInvariant = "";
							if (!allowPartialConfigurations) 
								 newInvariant = "(" + presenceCondition + ") implies (" + oldInvariant + ")";
							else if (strategy == CompilationStrategy.min)
								 newInvariant = "if " + undefined(oldInvariant) + " then false else if " + undefined(presenceCondition) + " then false else (" + presenceCondition + ") implies (" + oldInvariant + ") endif endif";
							else newInvariant = "if " + undefined(oldInvariant) + " then false else if " + undefined(presenceCondition) + " then " + oldInvariant + " else (" + presenceCondition + ") implies (" + oldInvariant + ") endif endif";							
							clOcl.getDetails().put(inv, newInvariant);
						}
					}
				}
			}
		}
		
		// ... adapt OCL expressions for features that changed from monovalued to multivalued
		handleOcl(metamodel);

		// TODO: attributes with upper bound > 1 
		
		// add created classes to metamodel
		metamodel.get(0).getEClassifiers().add(0, spClass);
		metamodel.get(0).getEClassifiers().add(0, fmClass);
		
		return metamodel;
	}
			
	/**
	 * add to fm-class an attribute for each feature in the feature model, and the formula in the feature model
	 * @param fmClass 
	 */
	protected void handleFeatures (EClass fmClass) { 
		EAnnotation fmOcl = EMFUtils.getOCLAnnotation(fmClass);

		// a) each feature is an attribute of the fm-class
		for (IFeature feature : this.provider.getFeatures())
			fmClass.getEStructuralFeatures().add( createBooleanAttribute(feature.getName(), allowPartialConfigurations? 0:1, 1) );

		// b) the formula given by the feature model is an invariant of the fm-class
		String feature_model_formula = "";
		if (!allowPartialConfigurations) {
			// b.1) to disallow partial configurations, use the formula as computed by FeatureIDE
			Node formula = provider.getFeatureModel().getAnalyser().getCnf();
			feature_model_formula = toString(formula);
		}
		else {
			// b.2) to allow partial configurations, build formula with the minimal constraints
			List<String> terms = new ArrayList<>();
			String       term  = null;
			for (IFeature feature : this.provider.getFeatures()) {
				IFeatureStructure featureST = feature.getStructure();
				// -> parent feature: feature.undefined implies (child1.undefined and child2.undefined)
				if (featureST.hasChildren()) {
					term = feature.getName() + ".oclIsUndefined() implies (";
					for (IFeatureStructure child : featureST.getChildren())
						term += child.getFeature() + ".oclIsUndefined() and ";
					term += "true)";
					terms.add(term);
				}
				// -> alternative feature: feature.undefined or (child1.defined and child2.defined and ((child1 and not child2) or (not child1 and child2)) 
				if (featureST.isAlternative()) {
					List<String> subterms = new ArrayList<>();
					String       subterm  = null;
					String       childdef = "";
					for (IFeatureStructure child1 : featureST.getChildren()) {
						childdef += "not " + child1.getFeature().getName() + ".oclIsUndefined() and ";
						subterm = "";
						for (IFeatureStructure child2 : featureST.getChildren()) 
							if (child1!=child2) 
								subterm += "not " + child2.getFeature().getName() + " and ";						
						subterm += child1.getFeature().getName();
						subterms.add(subterm);
					}
					term = "";
					for (String st : subterms)
						term += " or (" + st + ")";
					term = feature.getName() + ".oclIsUndefined() or (" + childdef + " (" + term.substring(4) + "))";
					terms.add(term);
				}
				// -> parent feature: feature.undefined or feature
				if (featureST.hasChildren() && featureST.isMandatory())
					terms.add(feature.getName() + ".oclIsUndefined() or " + feature.getName());
//				if (featureST.hasChildren() && featureST.isMandatory()) {
//					term = feature.getName() + ".oclIsUndefined() or (" + feature.getName();
//					if (featureST.isAnd()) {
//						for (IFeatureStructure child : featureST.getChildren())
//							if (child.isMandatory())
//								term += " and " + child.getFeature().getName();
//					}
//					term += ")";
//					terms.add(term);
//				}
				// -> or feature: feature.undefined or not feature or feature.child1 or feature.child2
				if (featureST.isOr()) {
					term = feature.getName() + ".oclIsUndefined() or not " + feature.getName();
					for (IFeatureStructure child : featureST.getChildren()) 
						term += " or " + child.getFeature().getName();
					terms.add(term);
				}
			}
//			// -> constraints in feature model
//			for (IConstraint constraint : this.provider.getFeatureModel().getConstraints()) {
//				Node formula = converter.createConstraintNode(constraint);
//				terms.add(toString(formula));
//			}
			feature_model_formula = terms.stream().map(t -> "(" + t + ")").collect(Collectors.joining(" and "));
		}
		fmOcl.getDetails().put("feature_model_formula", feature_model_formula.isEmpty()? "true" : feature_model_formula);
	}
	
	private String toString (Node formula) {
		String[] symbols = new String[]{"not ", "and", "or", "implies", "=", "xor"};
		String string = "";
		if (formula.getChildren()==null || formula.getChildren().length==1)
			 string = formula.toString();
		else string = formula.toString(symbols);
		return string;
	}
	
	/**
	 * a) Add invariant "if not (presence-condition) then class.all.size() = 0 else (b) endif" to the fm-class.
	 * 
	 * b) If the class is concrete, and there are modifiers abstract=true or interface=true, add condition "(pc-1 or pc-2 or...) implies aclass.size() = 0". 
	 *    If the class is abstract, and there are modifiers abstract=false or interface=false, make the class concrete, and add condition "not (pc-1 or pc-2 or...) implies aclass.size() = 0".
	 *    
	 * c) For each modifier extends="supertype":
	 *    c.1) add supertype to class
	 *    c.2) add invariant to class, constraining the value of the inherited features when the modifier condition is not met
	 *    c.3) for each reference with type=supertype (or higher), add invariant to reference.src forbidding objects of the child class when the modifier condition is not met
	 *    c.4) rewrite OCL expressions "supertype(or higher).allInstances..." to exclude objects of the child class when the modifier condition is not met
	 *    c.5) rewrite OCL expressions "oclIsKindOf(supertype(or higher))..." to make it false for the child class when the modifier condition is not met
	 *    c.6) for each inherited invariant, add duplicate with PC = "invariant-pc and condition" to class, and do not apply original invariant to the instances of the class 
	 * 
	 * d) For each modifier reduce="supertype", do steps c.2 to c.5 but checking if the modifier condition is met (instead of not met)
	 * 
	 * @param aclass
	 * @param metamodel
	 * @param fmClass
	 * @param spClass
	 * @param illustrateFeatures false to generate random instances, true to generate instances that illustrate each feature configuration
	 * @param forceMinStrategy (only used when strategy=max) classes to be compiled using strategy=min  
	 */
	protected void handleAnnotations (EClass aclass, List<EPackage> metamodel, EClass fmClass, EClass spClass, boolean illustrateFeatures, Set<EClassifier> forceMinStrategy) {
		EAnnotation clOcl = EMFUtils.getOCLAnnotation(aclass);
		EAnnotation fmOcl = EMFUtils.getOCLAnnotation(fmClass);
		OCL        <EPackage, EClassifier, EOperation, EStructuralFeature, EEnumLiteral, EParameter, EObject, CallOperationAction, SendSignalAction, Constraint, EClass, EObject> ocl = OCL.newInstance(EcoreEnvironmentFactory.INSTANCE);
		Environment<EPackage, EClassifier, EOperation, EStructuralFeature, EEnumLiteral, EParameter, EObject, CallOperationAction, SendSignalAction, Constraint, EClass, EObject> env = ocl.getEnvironment();
		OCLHelper<EClassifier, EOperation, EStructuralFeature, Constraint> helper = ocl.createOCLHelper();

		// a) presence condition
		String presenceCondition = MerlinAnnotationUtils.getPresenceCondition(aclass);

		// b) abstract and interface modifiers
		List<String> conditions        = new ArrayList<String>();
		String       modifiers         = "true";
		String       emptyCondition    = "true";
		for (EAnnotation an : MerlinAnnotationUtils.getModifiers(aclass)) {
			String abstractModifier  = an.getDetails().get( Modifiers.ABSTRACT_MODIFIER );
			String interfaceModifier = an.getDetails().get( Modifiers.INTERFACE_MODIFIER );
			String condition = an.getDetails().get(MerlinAnnotationStructure.MODIFIER_CONDITION);
			if ( (!aclass.isAbstract() && ("true".equals(abstractModifier)  || "true".equals(interfaceModifier))) ||
			     ( aclass.isAbstract() && ("false".equals(abstractModifier) || "false".equals(interfaceModifier))) ) 
				conditions.add(condition);
		}
		if (!conditions.isEmpty()) {
			modifiers = "(" + join(conditions, "or") + ")";
			if (aclass.isAbstract()) {
				aclass.setAbstract(false);
				modifiers = "not " + modifiers;
			}
			emptyCondition = rewrite(modifiers) + " implies " + empty(aclass);
		}

		// create invariant taking into account presence condition and modifiers
		if (!presenceCondition.equals("true") || !modifiers.equals("true")) {			
			String invariant = emptyCondition;
			if (!presenceCondition.equals("true")) {
				if (!allowPartialConfigurations)
					 invariant = "if " + not(presenceCondition) + " then " + empty(aclass) + " else " + emptyCondition + " endif";
				else if (strategy == CompilationStrategy.max && !forceMinStrategy.contains(aclass))
					 invariant = "if " + undefined(presenceCondition) + " then true else if " + not(presenceCondition) + " then " + empty(aclass, true) + " else true endif endif";
				else invariant = "if " + undefined(presenceCondition) + " then " + empty(aclass, true) + " else if " + not(presenceCondition) + " then " + empty(aclass, true) + " else " + emptyCondition + " endif endif";
			}
			fmOcl.getDetails().put(aclass.getName() + "_presence_condition", invariant);			
		}
		
		// f) if required, enforce the creation of instances when the presence condition is met
		if (illustrateFeatures) handleIllustrateFeatures(aclass, presenceCondition, modifiers, fmClass, metamodel);
		
		// c,d) extends/reduce modifiers
		Map<EClass,List<String>> supertypes = new HashMap<EClass,List<String>>();
		for (EAnnotation an : MerlinAnnotationUtils.getModifiers(aclass)) {
			String extendModifier = an.getDetails().get(Modifiers.EXTENDS_MODIFIER); // <condition>
			String reduceModifier = an.getDetails().get(Modifiers.REDUCE_MODIFIER);  // not <condition>
			String condition      = rewrite(an.getDetails().get(MerlinAnnotationStructure.MODIFIER_CONDITION));
			String[] extendSupertypes = extendModifier!=null? extendModifier.split("\\s+") : new String[]{};
			String[] reduceSupertypes = reduceModifier!=null? reduceModifier.split("\\s+") : new String[]{};
			EClass supertype;
			for (String extendSupertype : extendSupertypes) {
				if ((supertype = EMFUtils.getEClass(metamodel, extendSupertype)) != null) {
					if (!supertypes.containsKey(supertype)) 
						supertypes.put(supertype, new ArrayList<String>());
					supertypes.get(supertype).add(condition);
				}
			}
			for (String reduceSupertype : reduceSupertypes) {
				if ((supertype = EMFUtils.getEClass(metamodel, reduceSupertype)) != null) {
					if (!supertypes.containsKey(supertype)) 
						supertypes.put(supertype, new ArrayList<String>());
					supertypes.get(supertype).add(not(condition));
				}
			}
		}
		if (!supertypes.isEmpty()) {
			for (EClass supertype : supertypes.keySet()) {
				
				// c.1: add supertype to class
				if (!aclass.getESuperTypes().contains((EClass)supertype)) 
					aclass.getESuperTypes().add((EClass)supertype);

				// remove from class and subclasses any attribute and reference that get duplicated after adding the supertype
				List<EClass> children = EMFUtils.subclasses(metamodel, aclass, true);					
				((EClass)supertype).getEAllStructuralFeatures().forEach(inheritedSTFeat -> 
					children.forEach(child -> 
						child.getEStructuralFeatures().removeIf(declaredSTFeat -> 
							declaredSTFeat.getName().equals(inheritedSTFeat.getName()))));				
				
				// c.2: add invariant constraining the value of inherited features when condition=false
				List<String> emptyfeatures = new ArrayList<String>();
				for (EStructuralFeature feature : supertype.getEAllStructuralFeatures()) {
					if (feature.getEContainingClass() != spClass) { // to exclude features of the base superclass
						emptyfeatures.add(empty(feature));
					}
				}
				String premise      = not(join(supertypes.get(supertype), "or"));
				String consequences = join(emptyfeatures, "and");				
				if (!consequences.equals("()")) {
					String invariant = "";
					if (!allowPartialConfigurations)
						 invariant = "(" + premise + ") implies " + consequences;
					else if (this.strategy == CompilationStrategy.max)
						 invariant = "if " + undefined(premise) + " then true else if " + premise + " then " + consequences + " else true endif endif";
					else invariant = "if " + undefined(premise) + " then " + consequences  + " else if " + premise + " then " + consequences + " else true endif endif";
					clOcl.getDetails().put("extends_" + supertype.getName(), invariant);
				}
				
				// c.3: add invariant constraining the value of any ref:supertype when condition=false 
				for (EPackage pack : metamodel) {
					for (EClassifier source : pack.getEClassifiers()) {
						if (source instanceof EClass) {
							EAnnotation srcOcl = EMFUtils.getOCLAnnotation(source);	
							if (srcOcl == null) {
								srcOcl = EcoreFactory.eINSTANCE.createEAnnotation();
								srcOcl.setSource(EMFUtils.OCL);
								source.getEAnnotations().add(srcOcl);
							}
							for (EReference reference : ((EClass)source).getEReferences()) {
								if (reference.getEType() instanceof EClass && ((EClass)reference.getEType()).isSuperTypeOf(supertype)) {
									String consequence = 
											reference.getUpperBound() == 1? 
											"not " + reference.getName()+".oclIsKindOf(" + aclass.getName() + ")" :
											"not " + reference.getName()+"->exists(o | o.oclIsKindOf(" + aclass.getName() + "))";
									String invariant = "";
									if (!allowPartialConfigurations)
										 invariant = "(" + premise + ") implies " + consequence;
									else if (this.strategy == CompilationStrategy.max)
										 invariant = "if " + undefined(premise) + " then true else if " + premise + " then (" + consequence + ") else true endif endif";
									else invariant = "if " + undefined(premise) + " then (" + consequence + ") else if " + premise + " then (" + consequence + ") else true endif endif"; 
									srcOcl.getDetails().put(reference.getName() + "_in_extension_" + aclass.getName(), invariant);
								}
							}							
						}
					}
				}
				
				// c.6a: add copy of each inherited invariant to the class; set its PC to "invariant-pc and condition"
				List<EClass> ancestors = new ArrayList<>();
				ancestors.add(supertype);
				ancestors.addAll(supertype.getEAllSuperTypes());
				for (EClass anc : ancestors) {
					EMap<String, String> ancInvariants = EMFUtils.getInvariants(anc);
					for (String ancInvariantName : ancInvariants.keySet()) {
						String ancInvariantPC   = MerlinAnnotationUtils.getInvariantPresenceCondition(anc, ancInvariantName);
						String ancInvariant     = ancInvariants.get(ancInvariantName);
						String newInvariantName = ancInvariantName + "_inherited_from_" + anc.getName();
						for (int i=0; clOcl.getDetails().containsKey(newInvariantName); i++,newInvariantName=ancInvariantName+i);						
						clOcl.getDetails().put(newInvariantName, ancInvariant);
						MerlinAnnotationUtils.setInvariantPresenceCondition(aclass, newInvariantName, and(ancInvariantPC, join(supertypes.get(supertype), "or")));
				// c.6b: forbid applying the original invariant to the objects of the class
						setFilter(anc, ancInvariantName, aclass);
					}
				}				
			}

			// c.4:  rewrite "supertype.allInstances..." to exclude the instances of the class when condition=false
			// c.5a: rewrite "oclIsKindOf(supertype)" as "stroclIsKindOf(supertype)"
			for (EPackage pack : metamodel) {
				for (EClassifier source : pack.getEClassifiers()) {
					if (source instanceof EClass) {
						// ... in invariants
						EAnnotation srcOcl = EMFUtils.getOCLAnnotation(source);						
						EMap<String,String> invariants =  EMFUtils.getInvariants(source);
						for (String name : invariants.keySet()) {
							String body = invariants.get(name);								
							if (body.contains("oclIsKindOf") || body.contains("allInstances")) {
								helper.setContext(source);
								try {
									OCLExpression<EClassifier> query   = helper.createQuery(body);
									OclTransformer             visitor = new OclTransformer(env, aclass, supertypes);
									String                     newBody = query.accept(visitor);
									if (visitor.needsAdaptation())
										srcOcl.getDetails().put(name, newBody);
								}
								catch (ParserException e) { System.err.println("<merlin> Malformed OCL expression: "+body); }
							}
						}
						// ... in operations
						for (EOperation operation : ((EClass) source).getEOperations()) {
							String body = EMFUtils.getBody(operation);								
							if (body.contains("oclIsKindOf") || body.contains("allInstances")) {
								helper.setOperationContext(source, operation);
								try {
									Constraint bodyCondition = helper.createBodyCondition(body);									
									OclTransformer             visitor = new OclTransformer(env, aclass, supertypes);
									String                     newBody = bodyCondition.getSpecification().accept(visitor);
									if (visitor.needsAdaptation())
										EMFUtils.setBody(operation, newBody);
								}
								catch (ParserException e) { System.err.println("<merlin> Malformed OCL expression: "+body); }
							}
						}
					}
				}
			}
			
			// c.5b: define operation "stroclIsKindOf()" to exclude the instances of the class when condition=false
			aclass.getEOperations().add( stroclIsKindOf(metamodel, supertypes) );
		}
	}
	
	/**
	 * Enforces the generation of instances of the class if this declares a presence condition or modifier.
	 * Enforces the instantiation of the features of the class that declare a presence condition or modifier, not necessarily in the same object.
	 * Enforces the instantiation of inherited features, when the inheritance depends on modifiers.
	 * In all cases, instantiation is enforced by adding invariants with the form "presence-condition/modifier-condition implies element.size() > 0".
	 */
	protected void handleIllustrateFeatures (EClass aclass, String presenceCondition, String abstractModifiers, EClass fmClass, List<EPackage> metamodel) {
		EAnnotation fmOcl = EMFUtils.getOCLAnnotation(fmClass);
		
		// enforce instance of class if it has presence condition or abstract modifier ......................................
		if (!presenceCondition.equals("true") || !abstractModifiers.equals("true")) {
			// (build invariant)
			String invariant = "";
			if (!presenceCondition.equals("true")) invariant = presenceCondition;
			// (handling of abstract and interface modifiers)
			if (!abstractModifiers.equals("true")) invariant = "(" + invariant + (invariant.isEmpty()? "":" and not ") + abstractModifiers + ")";
			invariant += " implies " + nonempty(aclass); 
			fmOcl.getDetails().put(aclass.getName() + "_illustrate", invariant);
		}
		
		// enforce instance of features that have presence-condition or modifier ............................................
		for (EStructuralFeature feature : aclass.getEStructuralFeatures()) {
			String       featurePC = MerlinAnnotationUtils.getPresenceCondition(feature);
			List<String> featureMC = new ArrayList<String>(); 
			for (EAnnotation an : MerlinAnnotationUtils.getModifiers(feature)) {
				featureMC.add( an.getDetails().get(MerlinAnnotationStructure.MODIFIER_CONDITION) );
			}
			String illustrateCondition = "true";
			if (!featurePC.equals("true")) illustrateCondition = featurePC;                         // presence condition of feature
			else if (!featureMC.isEmpty()) illustrateCondition = "(" + join(featureMC, "or") + ")"; // if there is none, use modifier conditions of feature
			if (!illustrateCondition.equals("true")) {
				fmOcl.getDetails().put(
						aclass.getName() + "_" + feature.getName() + "_illustrate", 
						illustrateCondition + " implies " + aclass.getName() + ".allInstances()->exists(o | " + bigger("o", feature, 1) + ")"); // feature.size()>0
			}
		}
				
		// enforce instance of inherited features if the inheritance is based on modifiers ..................................
		for (EAnnotation an : MerlinAnnotationUtils.getModifiers(aclass)) {
			String extendsModifier  = an.getDetails().get(Modifiers.EXTENDS_MODIFIER); // <condition>
			String reduceModifier   = an.getDetails().get(Modifiers.REDUCE_MODIFIER);  // not <condition>
			String condition        = an.getDetails().get(MerlinAnnotationStructure.MODIFIER_CONDITION);
			EClass extendsSupertype = EMFUtils.getEClass(metamodel, extendsModifier);
			EClass reduceSupertype  = EMFUtils.getEClass(metamodel, reduceModifier );
			if (extendsSupertype != null) {
				for (EStructuralFeature feature : extendsSupertype.getEStructuralFeatures())
					fmOcl.getDetails().put(
						aclass.getName() + "_" + feature.getName() + "_illustrate", 
						"(" + condition + ") implies " + aclass.getName() + ".allInstances()->exists(o | " + bigger("o", feature, 1) + ")"); // feature.size()>0
			}
			if (reduceSupertype != null) {
				for (EStructuralFeature feature : reduceSupertype.getEStructuralFeatures())
					fmOcl.getDetails().put(
						aclass.getName() + "_" + feature.getName() + "_illustrate", 
						"(" + not(condition) + ") implies " + aclass.getName() + ".allInstances()->exists(o | " + bigger("o", feature, 1) + ")"); // feature.size()>0
			}
		}
	}
		
	/**
	 * a) Add invariant "if not (presence-condition) then feature.size() = 0 else (b,c) endif" to the class.
	 * b) If the class has modifiers min=i, add condition "(pc-i implies feature.size() >= i) and ... and (not (pc-i or pc-j or ...) implies feature.size() >= feature.min)".
	 * c) If the class has modifiers max=i, add condition "(pc-i implies feature.size() <= i) and ... and (not (pc-i or pc-j or ...) implies feature.size() <= feature.max)".
	 * d) Adjust lower bound of the feature to MIN(feature.min, i..j, 0 if presence-condition!=true).
	 * e) Adjust upper bound of the feature to MAX(feature.max, i..j).
	 * @param feature
	 * @param fmClass
	 */
	protected void handleAnnotations (EStructuralFeature feature, EClass fmClass) {
		EClass       aclass = feature.getEContainingClass();
		EAnnotation  clOcl  = EMFUtils.getOCLAnnotation(aclass);
		int          lowerBound = feature.getLowerBound();
		int          upperBound = feature.getUpperBound();
		
		// a) presence condition ............................................................................
		String presenceCondition = rewrite(MerlinAnnotationUtils.getPresenceCondition(feature));
		if (!presenceCondition.equals("true")) {
			lowerBound = 0;
		}
		
		// b,c) min and max modifiers .......................................................................
		Map<String, String> minModifiers = new HashMap<String,String>(); // <pc, constraint> 
		Map<String, String> maxModifiers = new HashMap<String,String>(); // <pc, constraint> 
		for (EAnnotation an : MerlinAnnotationUtils.getModifiers(feature)) {
			String minModifier = an.getDetails().get(Modifiers.MIN_MODIFIER);
			String maxModifier = an.getDetails().get(Modifiers.MAX_MODIFIER);
			String condition   = rewrite(an.getDetails().get(MerlinAnnotationStructure.MODIFIER_CONDITION));
			if (minModifier != null) {
				try {
					int value = Integer.parseInt(minModifier);
					if (value < lowerBound) lowerBound = value;
					minModifiers.put(condition, "(" + undefined_or(condition) + condition +") implies " + bigger(feature, value)); 
				}   catch (Exception e) { System.err.println("[merlin] exception when converting min='" + minModifier + "' into an integer value"); }
			}
			if (maxModifier != null) {
				try {
					int value = maxModifier.equals("*")? -1 : Integer.parseInt(maxModifier);
					if (upperBound!=-1 && (value > upperBound || value == -1)) upperBound = value;
					maxModifiers.put(condition, value != -1? "(" + undefined_or(condition) + condition +") implies " + smaller(feature, value) : "true");
				}   catch (Exception e) { System.err.println("[merlin] exception when converting max='" + maxModifier + "' into an integer value"); }
			}
		}		
		Set<String> terms = new HashSet<String>();
		terms.addAll(minModifiers.values());
		terms.addAll(maxModifiers.values());
		// min
		if (!minModifiers.isEmpty())          terms.add(undefined_or(join(minModifiers.keySet(), "or")) + not(join(minModifiers.keySet(), "or")) + " implies " + bigger(feature, feature.getLowerBound()));
		else if (feature.getLowerBound() > 0) terms.add(bigger(feature, feature.getLowerBound()));
		// max
		if (feature.getUpperBound() != -1) {
			if (!maxModifiers.isEmpty())
				terms.add(undefined_or(join(maxModifiers.keySet(), "or")) + not(join(maxModifiers.keySet(), "or")) + " implies " + smaller(feature, feature.getUpperBound()));
			else terms.add(smaller(feature, feature.getUpperBound())); 
		}		
		String modifiers = terms.isEmpty()? "true" : join(terms, "and");
		
		// create invariant taking into account presence condition and modifiers ............................
		if (!presenceCondition.equals("true") || /*!modifiers.equals("true") ||*/ !minModifiers.isEmpty() || !maxModifiers.isEmpty()) {
			String invariant = modifiers;
			if (!presenceCondition.equals("true")) {
				if (!allowPartialConfigurations)
					 invariant = "if " + not(presenceCondition) + " then " + empty(feature) + " else " + modifiers + " endif";
				else if (this.strategy == CompilationStrategy.max) 
					 invariant = "if " + undefined(presenceCondition) + " then " + modifiers + " else if " + not(presenceCondition) + " then " + modifiers + " else true endif endif";
				else invariant = "if " + undefined(presenceCondition) + " then " + and(empty(feature), modifiers) + " else if " + not(presenceCondition) + " then " + empty(feature) + " else " + modifiers + " endif endif" ;
			}
			clOcl.getDetails().put(feature.getName() + "_presence_condition", invariant);
		}
		
		// store features that change from monovalued to multivalued (used later)
		if (feature.getUpperBound()==1 && (upperBound==-1 || upperBound>1)) monovaluedToMultivalued.add(feature);
		
		// d,e) adjust lower and upper bounds of feature ....................................................
		feature.setLowerBound(lowerBound);
		feature.setUpperBound(upperBound);		
	}
	
	/**
	 * For each reference that can be containment or not depending on the selected features:
	 * a) add invariant to avoid cycles of containment when features are selected
	 * b) add invariant to avoid an object to be contained in two compositions when features are selected
	 * c) make reference.containment = false
	 * @param feature
	 */
	protected void handleAnnotations (List<EPackage> metamodel) {
		// build allconditions : map of references and their containment conditions <ereference, string>
		// build allcontainers : map of classes and their container references <eclass, list<ereference>>
		Map<EReference,String>       allconditions = new HashMap<EReference, String>();
		Map<EClass, Set<EReference>> allcontainers = new HashMap<EClass, Set<EReference>>();
		for (EPackage pack : metamodel) {
			for (EClassifier aclass : pack.getEClassifiers()) {
				if (aclass instanceof EClass) {
					for (EReference reference : ((EClass) aclass).getEReferences()) {
						List<String> refconditions = new ArrayList<String>(); 
						for (EAnnotation an : MerlinAnnotationUtils.getModifiers(reference)) {
							String containmentModifier = an.getDetails().get( Modifiers.CONTAINMENT_MODIFIER );
							String condition = rewrite(an.getDetails().get(MerlinAnnotationStructure.MODIFIER_CONDITION));
							if ( (!reference.isContainment() && "true".equals(containmentModifier)) ||
								  (reference.isContainment() && "false".equals(containmentModifier))) 
								refconditions.add(condition);
						}
						if (!refconditions.isEmpty()) {
							boolean realContainment = reference.isContainment();
							reference.setContainment(true); // convert into containment to generate the appropriate constraint
							allconditions.put(reference, (realContainment? "not " : "") + "(" + join(refconditions, "or") + ")");
						}
						if (reference.isContainment()) {
							EClass       target  = reference.getEReferenceType();
							List<EClass> targets = EMFUtils.subclasses(metamodel, target, true); // consider subclasses
							for (EClass atarget : targets) {
								if (!allcontainers.containsKey(atarget)) 
									allcontainers.put(atarget, new HashSet<EReference>());
								allcontainers.get(atarget).add(reference);
							}							
						}
					}
				}
			}
		}
		
		// a) generate constraints to avoid cycles of containment
		for (EReference reference : allconditions.keySet()) {
			EClass      source = reference.getEContainingClass();
			EAnnotation srcOcl = EMFUtils.getOCLAnnotation(source);
			String implication = compositionAcyclicConstraint(reference);
			if (!implication.equals("true")) 
				srcOcl.getDetails().put(reference.getName() + "_acyclic_containment", allconditions.get(reference) + " implies " + implication);
			
		// c) convert reference into non-containment
			reference.setContainment(false); 
		}
		
		// b) generate constraints to avoid multiple containers for an object
		for (EClass target : allcontainers.keySet()) {
			if (allcontainers.get(target).stream().anyMatch(ref -> allconditions.containsKey(ref))) { // only if some reference had the containment modifier
				String constraint  = "";
				for (EReference reference : allcontainers.get(target)) {
					constraint += "\tif " + (allconditions.containsKey(reference)? allconditions.get(reference) : "true") + 
									" then " + reference.getEContainingClass().getName() + ".allInstances()->collect(o | o." + reference.getName() + ")->flatten()->count(self) " +
									"else 0 endif +\n";
				}
				constraint = constraint.substring(0, constraint.lastIndexOf("+")) + "<= 1";
				EAnnotation tarOcl = EMFUtils.getOCLAnnotation(target);
				tarOcl.getDetails().put(target.getName() + "_single_container", constraint);
			}
		}
	}
	
	/**
	 * For each monovalued feature in the metamodel, which was changed to multivalued in the 150MM,
	 * adapt invariants and operations accordingly: "feat=" is changed to "feat->any(true)".  
	 * @param metamodel
	 */
	protected void handleOcl(List<EPackage> metamodel) {
		if (monovaluedToMultivalued.isEmpty()) return;
		for (EPackage pack : metamodel) { 
			for (EClassifier aclass : pack.getEClassifiers()) {
				if (aclass instanceof EClass) {
					EMap<String,String> invariants =  EMFUtils.getInvariants((EClass)aclass);
					for (Entry<String, String> invariant : invariants.entrySet()) // ocl invariants
						invariants.put(invariant.getKey(), mono2multi(invariant.getValue(), monovaluedToMultivalued));
					for (EOperation operation : ((EClass)aclass).getEOperations()) // ocl operations
						EMFUtils.setBody(operation, mono2multi(EMFUtils.getBody(operation), monovaluedToMultivalued));
				}
			}			
		}
	}
	
	// constraints on the size of classes and features
	protected String bigger (EStructuralFeature feature, int value) { return bigger("", feature, value); }
	protected String bigger (String object, EStructuralFeature feature, int value) { 
		if (feature instanceof EReference)             return featureName(object, feature) + "->size() >= " + value;
		if (feature instanceof EAttribute && value>0)  return "not " + featureName(object, feature) + ".oclIsUndefined()";
		return "true"; 
	}
	protected String smaller (EStructuralFeature feature, int value) { return smaller("", feature, value); }
	protected String smaller (String object, EStructuralFeature feature, int value) { 
		if (feature instanceof EReference)             return featureName(object, feature) + "->size() <= " + value;
		if (feature instanceof EAttribute && value==0) return featureName(object, feature) + ".oclIsUndefined()";
		return "true"; 
	}
	protected String empty    (EStructuralFeature f)        { return f.getName() + (f instanceof EAttribute? ((f.isMany()?"->":".") + "oclIsUndefined()") : "->size() = 0");  }
	protected String empty    (EClass c)                    { return empty   (c, false); }
	protected String nonempty (EClass c)                    { return nonempty(c, false); }
	protected String empty    (EClass c, boolean exactType) { return c.getName() + ".allInstances()" + (exactType&&EMFUtils.hasSubclasses(metamodel, c)? "->select(oclIsTypeOf("+c.getName()+"))" : "") + "->size() = 0";  }
	protected String nonempty (EClass c, boolean exactType) { return c.getName() + ".allInstances()" + (exactType&&EMFUtils.hasSubclasses(metamodel, c)? "->select(oclIsTypeOf("+c.getName()+"))" : "") + "->size() > 0";  }
	protected String featureName (String object, EStructuralFeature feature) { return object!=null && !object.isEmpty()? object + "." + feature.getName() : feature.getName(); }
	
	 // additional condition if the features are optional in FMC
	protected String undefined_or (String condition) { return allowPartialConfigurations? " " + undefined(condition) + " or "  : ""; }
	protected String undefined    (String condition) { return "(" + condition.replaceAll("\\snot\\s"," ").replaceAll("not\\s", " ").replaceAll("\\(not\\s", "(") + ").oclIsUndefined()"; }
	
	// replaces every access to a monovalued feature (.feature) by an equivalent multivalued expression (.feature->any(true))	
	protected String mono2multi (String ocl, List<EStructuralFeature> features) { for (EStructuralFeature f : features) ocl = mono2multi(ocl, f.getName()); return ocl; }
	protected String mono2multi (String ocl, String feature) { return ocl.replaceAll("\\." + feature + "[\\s+^]", "." + feature + "->any(true) "); }
	
	// constraint: an object cannot be contained itself through a composition relation, directly or indirectly
	protected String compositionAcyclicConstraint (EReference ref) {
		if (ref!=null && ref.isContainment() && (ref.getEContainingClass()==ref.getEReferenceType() || ref.getEContainingClass().getESuperTypes().contains(ref.getEReferenceType()))) {
			List<String> terms = new ArrayList<String>();
			int      num_terms = 5;
			String   type      = ref.getEContainingClass().getName();
			String   setOpen   = ref.getUpperBound()==1? "Set{":""; // add monovalued features to a set
			String   setClose  = ref.getUpperBound()==1? "}"   :""; 
			for (int term=1; term<=num_terms; term++)	{			
				String expression = "";
				for (int index=1; index<=term; index++) {
					if (index==1) {
						expression = setOpen + (index<term? ref.getName() + index + ".oclAsType(" + type + ")." : "self.") + ref.getName() + setClose + "->includes(self)";
					}
					else {
						String select1 = "", select2 = "";
						if (index>1) {
							select1 = setOpen + (index<term? ref.getName() + index + ".oclAsType(" + type + ")." : "self.") + ref.getName() + setClose + "->exists(" + ref.getName() + (index-1) + " |\n";
							select2 = ")";
						}
						expression = select1 +
								"if " + ref.getName() + (index-1) + ".oclIsKindOf(" + type + ") then\n" +
								"\t" + expression + "\n" +
								"\t else false endif" + 
								select2;
					}
				}
				terms.add("not " + expression);				
			}
			return join(terms, "and");
		}
		else return "true";
	}
	
	// operation: implementation of oclIsKindOf, but receiving a string parameter
	protected EOperation stroclIsKindOf (List<EPackage> metamodel) { return stroclIsKindOf(metamodel, new HashMap<EClass,List<String>>()); }
	protected EOperation stroclIsKindOf (List<EPackage> metamodel, Map<EClass, List<String>> modifiers) {
		EDataType  string    = EcoreFactory.eINSTANCE.createEDataType(); string.setName("EString");        string.setInstanceClassName("java.lang.String");
		EDataType  bool      = EcoreFactory.eINSTANCE.createEDataType(); bool.setName  ("EBooleanObject"); bool.setInstanceClassName  ("java.lang.Boolean");
		EOperation operation = EcoreFactory.eINSTANCE.createEOperation();
		operation.setName("stroclIsKindOf");
		operation.setEType(bool);

		EParameter param = EcoreFactory.eINSTANCE.createEParameter();
		param.setName("type");
		param.setEType(string);
		param.setLowerBound(1);
		param.setUpperBound(1);
		operation.getEParameters().add(param);
		
		String body = "false";
		String then = "";
		for (EPackage pack : metamodel) {
			for (EClassifier aclass : pack.getEClassifiers()) {
				if (aclass instanceof EClass) {
					// - regular inheritance is checked with method oclIsKindOf
					// - modifier-based inheritance requires checking the modifier condition
					then = "";
					for (EClass supertype : modifiers.keySet()) {
						if (((EClass)aclass).isSuperTypeOf(supertype))
							then = (then.isEmpty()? "": then + " or ") + join(modifiers.get(supertype), "or");
					}
					if (then.isEmpty()) then = "self.oclIsKindOf(" + aclass.getName() + ")";                  
					body = "if (type = '" + aclass.getName() + "') then " + then + " else\n\t\t" + body + " endif";
				}
			}
		}
		EMFUtils.setBody(operation, "\n\t\t" + body);
		
		return operation;
	}
			
	// methods to create metamodel elements .............................................
	protected EClass createClass (String name, boolean isAbstract) {
		EClass cl = EcoreFactory.eINSTANCE.createEClass();
		cl.setName(name);
		cl.setAbstract(isAbstract);
		// add annotation required to define ocl invariants
		EAnnotation spInvariants = EcoreFactory.eINSTANCE.createEAnnotation();
		spInvariants.setSource(EMFUtils.OCL);
		cl.getEAnnotations().add(spInvariants);
		return cl;
	}
	protected EAttribute createBooleanAttribute (String name, int lowerBound, int upperBound) {
		EAttribute att = EcoreFactory.eINSTANCE.createEAttribute();
		att.setName(name);
		att.setEType(EcorePackage.eINSTANCE.getEBoolean());
		att.setLowerBound(lowerBound);
		att.setUpperBound(upperBound);
		return att;
	}
	protected EReference createReference (String name, EClassifier type, int lowerBound, int upperBound) {
		EReference ref = EcoreFactory.eINSTANCE.createEReference();
		ref.setName(name);
		ref.setEType(type);
		ref.setLowerBound(lowerBound);
		ref.setUpperBound(upperBound);
		return ref;
	}
	
	// auxiliary methods for strings ....................................................
	protected String join (Collection<String> terms, String particle) { 
		return "(" + terms.stream().map(Object::toString).collect(Collectors.joining(") " + particle + " (")) + ")"; 
	}
	protected String rewrite (String formula) { // rewrites a formula to convert feature into fm.feature 
		for (IFeature f : this.provider.getFeatures()) { 
			int i = formula.indexOf(f.getName());
			while (i>=0) {
				int j = i + f.getName().length();
				if ((i==0                || formula.charAt(i-1)==' ' || formula.charAt(i-1)=='(') &&
				    (j==formula.length() || formula.charAt(j)  ==' ' || formula.charAt(j)  ==')')) {
					formula = formula.replaceAll(f.getName(), FEATURE_MODEL_RELATION + "." + f.getName());
				}
				i = formula.indexOf(f.getName(), j);
			}
		}
		return formula; 
	}
	protected String not (String condition) {
		return condition.startsWith("not ")? condition.substring(4) : "not (" + condition + ")";
	}
	protected String and (String condition1, String condition2) {
		return condition1.equals("true")? condition2 : (condition2.equals("true")? condition1 : "(" + condition1 + " and " + condition2 + ")");
	}
	
	// methods to define/apply class-filters in invariants, i.e., classes where the invariants do not apply
	protected void setFilter(EClass invariantOwner, String invariantName, EClass filter) {
		EAnnotation an = EcoreFactory.eINSTANCE.createEAnnotation();
		an.setSource("ExcludedClasses");
		an.getDetails().put(MerlinAnnotationStructure.INVARIANT, invariantName);
		an.getDetails().put(MerlinAnnotationStructure.PRESENCE_CONDITION, "not oclIsKindOf("+filter.getName()+")");
		invariantOwner.getEAnnotations().add(an);
	}
	protected void applyFilter(EClass invariantOwner, String invariantName) {
		List<String> filter = new ArrayList<>();
		for (EAnnotation an : invariantOwner.getEAnnotations()) { 
			if (an.getSource().equals("ExcludedClasses")) 		
				if (invariantName.equals(an.getDetails().get(MerlinAnnotationStructure.INVARIANT))) 
					filter.add(an.getDetails().get(MerlinAnnotationStructure.PRESENCE_CONDITION));
		}
		if (!filter.isEmpty()) {
			String filterExpression = join(filter, "and");
			EAnnotation invariants = EMFUtils.getOCLAnnotation(invariantOwner);
			invariants.getDetails().put(invariantName, filterExpression + " implies " + invariants.getDetails().get(invariantName));
		}
	}
	
	// ----------------------------------------------------------------------------------

	class OclTransformer extends OclToStringVisitor {
		
		public OclTransformer(Environment<?, EClassifier, EOperation, EStructuralFeature, EEnumLiteral, EParameter, EObject, CallOperationAction, SendSignalAction, Constraint, ?, ?> env, EClass aclass, Map<EClass, List<String>> modifiers) {
			super(env);
			this.aclass    = aclass;
			this.modifiers = modifiers!=null? modifiers : new HashMap<EClass, List<String>>();
		}
		
		private Boolean needsAdaptation = false;
		public  Boolean needsAdaptation() { return needsAdaptation; }

		private EClass aclass;                       // class
		private Map<EClass, List<String>> modifiers; // class' superclasses defined with extends modifier
		
		@Override
	    public String visitTypeExp(TypeExp<EClassifier> t) {
			// do not use qualified type names
			return getName(t.getReferredType());
		}
		
		@Override
		public String visitOperationCallExp(OperationCallExp<EClassifier, EOperation> callExp) {			
			String result          = super.visitOperationCallExp(callExp);
			String operation       = callExp.getReferredOperation() != null? callExp.getReferredOperation().getName() : "";
			OCLExpression<?> param = callExp.getArgument().isEmpty()? null : callExp.getArgument().get(0);
			OCLExpression<?> src   = callExp.getSource();
			
			// replace oclIsKindOf(supertype) by stroclIsKindOf('supertype')
			if ("oclIsKindOf".equals(operation) && param instanceof TypeExp && ((TypeExp<?>)param).getReferredType() instanceof EClass) {
				EClass argclass = (EClass)((TypeExp<?>)param).getReferredType();
				if (modifiers.keySet().stream().anyMatch(supertype -> argclass.isSuperTypeOf(supertype))) {
					result          = result.replace("oclIsKindOf", "stroclIsKindOf");
					result          = result.replace("("+argclass.getName()+")", "('"+argclass.getName()+"')");
					needsAdaptation = true;
				}
			}
			
			// replace supertype.allInstances by supertype.allInstances.filter(...)
			else if ("allInstances".equals(operation) && src instanceof TypeExp && ((TypeExp<?>)src).getReferredType() instanceof EClass) {
				EClass srcclass = (EClass)((TypeExp<?>)src).getReferredType();
				if (modifiers.keySet().stream().anyMatch(supertype -> srcclass.isSuperTypeOf(supertype))) {
					String condition = "";
					for (EClass supertype : modifiers.keySet()) {
						if (((EClass)srcclass).isSuperTypeOf(supertype)) {
							condition = (condition.isEmpty()? "": condition + " or ") + join(modifiers.get(supertype), "or");
						}
					}					
					result          = result + "->select(o | " + condition + " or not o.oclIsKindOf(" + aclass.getName() + "))";
					needsAdaptation = true;
				}
			}
			
			return result;
		}
	}	
}
