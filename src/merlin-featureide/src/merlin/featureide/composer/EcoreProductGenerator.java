package merlin.featureide.composer;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;

import de.ovgu.featureide.fm.core.configuration.Configuration;
import merlin.common.annotations.MerlinAnnotationStructure;
import merlin.common.annotations.presenceCondition.ConditionParser;
import merlin.common.concepts.ConceptDecoratorBuilder;
import merlin.common.features.FeatureIDEProvider;
import merlin.common.issues.ValidationIssue;
import merlin.common.transformation.analysis.MethodResolver;
import merlin.common.transformation.analysis.OCLHandler;
import merlin.common.utils.EMFUtils;
import merlin.common.utils.FileUtils;
import merlin.common.utils.MerlinAnnotationUtils;
import merlin.featureide.composer.transformations.TransformationProductGenerator;

public class EcoreProductGenerator {
	private EPackage root;
	private Resource resource;
	private Configuration cfg;
	private String ecoreName;
	private ArrayList<String> oclErrors = new ArrayList<>();
	private TransformationProductGenerator tpg = null;
	private OCLHandler oclHandler;
	private MethodResolver mr;
	private List<ValidationIssue> errors;
	
	public EcoreProductGenerator(Resource rs, String ecoreName) {
		this.resource = rs;
		this.root = (EPackage)rs.getContents().get(0);
		this.ecoreName = ecoreName;
	}
	
	public List<ValidationIssue> getTrafoErrors() {
		return this.tpg == null ? Collections.emptyList() : this.tpg.getTrafoErrors();
	}
				
	public void genProduct(Configuration configuration, String cfgName, URI path) throws IOException {
		this.oclErrors.clear();
		// Generate meta-model
		this.genProduct(configuration, cfgName);
		
		// Now save meta-model...
		Resource newr = this.resource.getResourceSet().createResource(path);
		newr.getContents().addAll(this.resource.getContents());
		newr.save(null);		
		
		if (errors==null) errors = new ArrayList<>();
		//errors.addAll(this.checkMissingMethodBodies());
		
		this.tpg = new TransformationProductGenerator(configuration, this.root, getAndRefreshFile(path), mr);		
		tpg.generate(path);		
		this.oclErrors.addAll(tpg.getOclErrors());
		this.oclErrors.addAll(this.oclHandler.getOCLErrors());
		errors.addAll(tpg.getTrafoErrors());
		
		this.saveOCLErrors(path);
		this.reportErrors(path, errors);
	}

	private void reportErrors(URI path, List<ValidationIssue> vis) {
		IFile file = getAndRefreshFile(path);
		FileUtils.updateMarkers(file, vis);
	}

	private IFile getAndRefreshFile(URI path) {
		String fileName = path.toFileString(); 
		IFile file = FileUtils.getIFile(fileName);
		try {
			file.refreshLocal(IResource.DEPTH_ZERO, null);
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return file;
	}	

	public Resource genProduct(Configuration configuration, String cfgName) throws IOException {
		this.cfg = configuration;
		this.root.setNsURI(this.root.getNsURI()+cfgName);
		this.resource.getResourceSet().getPackageRegistry().put(this.root.getNsURI(), this.root);
		EPackage.Registry.INSTANCE.put(this.root.getNsURI(), this.root); // global registry
		List<EClassifier> elemsToRemove = new ArrayList<>();
		for (EClassifier cl : this.root.getEClassifiers()) {
			if (!this.checkPresenceConditions(cl)) elemsToRemove.add(cl);			
		}
		this.root.getEClassifiers().removeAll(elemsToRemove);
		this.root.getEClassifiers().forEach(cl -> {if (cl instanceof EClass) ((EClass)cl).getESuperTypes().removeAll(elemsToRemove);}); 
		
		// Now process modifiers
		ModifierExecuter me = new ModifierExecuter();
		for (EClassifier cl : this.root.getEClassifiers()) {
			if (cl instanceof EClass) 
				me.exec((EClass)cl, configuration);			
		}
		
		// Now remove all merlin annotations
		MerlinAnnotationUtils.removeAllMerlinAnnotations(this.root);
		
		ConceptDecoratorBuilder cdb = new ConceptDecoratorBuilder(this.root);
		cdb.createConceptOps();
		mr = new MethodResolver(this.root);	// inefficient!!
		this.oclHandler = new OCLHandler(this.root, mr);

		// Now add concept operations...
		errors = this.oclHandler.addOCLOperations(configuration);		

		return this.resource;
	}


	private void saveOCLErrors(URI path) {
		if (this.oclErrors.size()==0) return;
		String errorPath = path.toFileString();
		errorPath += ".oclerrors";
		try {
			PrintWriter pw = new PrintWriter(errorPath);
			for (String s : this.oclErrors) {
				pw.write(s+"\n");
			}
			pw.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

/*	private EOperation cloneOperation(EOperation op) {
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
	}*/

	/*private void cloneEType(ETypedElement original, ETypedElement clone) {
		EClassifier ret = original.getEType();
		if (ret!=null) {
			EClassifier cls = this.root.getEClassifier(ret.getName());
			if (cls!=null)
				clone.setEType(cls);
			else 
				clone.setEType(this.resolveEType(original.getEType()));			
		}
		else clone.setEType(null);
	}*/
	
	/*private EClassifier resolveEType(EClassifier original) {
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
	}*/

	/*private boolean containsOperation(EClass cl, EOperation op) {
		for (EOperation oper : cl.getEOperations()) {
			if (oper.getName().equals(op.getName())) return true;
		}
		return false;
	}*/

	private boolean checkPresenceConditions(EClassifier cl) {
		EAnnotation merlin = MerlinAnnotationUtils.getClassMerlinAnnotation(cl);
		if (merlin != null) {
			String condition = merlin.getDetails().get(MerlinAnnotationStructure.PRESENCE_CONDITION);
			ConditionParser cp = new ConditionParser(condition, cl, new FeatureIDEProvider(this.cfg));
			if (!cp.eval()) return false;
		}
		if (!(cl instanceof EClass)) return true;
		EClass cls = (EClass)cl;
		
		List<EStructuralFeature> elemsToRemove = new ArrayList<>();
		for (EStructuralFeature sf : cls.getEStructuralFeatures()) {
			EAnnotation merlinf = sf.getEAnnotation(MerlinAnnotationStructure.ANNOTATION_NAME);
			if (merlinf==null) continue;
			if (!MerlinAnnotationUtils.checkPresenceConditions(sf, this.cfg)) elemsToRemove.add(sf);
		}
		cls.getEStructuralFeatures().removeAll(elemsToRemove);
		
		List<String> invsToRemove = new ArrayList<>();
		EAnnotation an = EMFUtils.getOCLAnnotation(cls);
		if (an!=null) {
			for (String invName : an.getDetails().keySet()) {
				EAnnotation cond = MerlinAnnotationUtils.getInvariantMerlinAnnotation(cls, invName);
				if (cond==null) continue;
				ConditionParser cp = new ConditionParser(cond.getDetails().get(MerlinAnnotationStructure.PRESENCE_CONDITION), 
						cls, 
						new FeatureIDEProvider(this.cfg));
				if (!cp.eval()) invsToRemove.add(invName);
			}		
		}
		
		EMFUtils.removeInvariants(invsToRemove, cls);
		return true;
	}
		
}
