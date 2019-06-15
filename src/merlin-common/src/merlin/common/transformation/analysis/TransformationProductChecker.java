package merlin.common.transformation.analysis;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EOperation;
import org.eclipse.emf.ecore.EPackage;

import de.ovgu.featureide.fm.core.configuration.Configuration;
import merlin.common.analysis.FeatureSolver;
import merlin.common.concepts.ConceptDecoratorBuilder;
import merlin.common.concepts.SelectedConcepts;
import merlin.common.features.FeatureIDEProvider;
import merlin.common.issues.IssueLevel;
import merlin.common.issues.ValidationIssue;
import merlin.common.transformation.Method;
import merlin.common.utils.EMFUtils;
import merlin.common.utils.FileUtils;

public class TransformationProductChecker {
	private ArrayList<String> oclErrors = new ArrayList<>();
	private Set<ValidationIssue> trafoErrors = new LinkedHashSet<>();
	private MethodResolver mr;
	
	public TransformationProductChecker (MethodResolver r) {
		this.mr = r;
	}
	
	public TransformationProductChecker (EPackage p) {
		// We need to add the concepts...
		ConceptDecoratorBuilder cdb = new ConceptDecoratorBuilder(p);
		cdb.createConceptOps();
		this.mr = new MethodResolver(p);
	}
	
	public MethodResolver getResolver() {
		return this.mr;
	}
	
	public Collection<ValidationIssue> getTrafoErrors() {
		return this.trafoErrors;
	}
	
	public void allChecks(Map<EPackage, IFile> concepts) {
		this.cleanMarkers();
		this.checkMethodCollisions();
		this.checkMissingBodies(concepts);
	}

	public void checkMethodCollisions() {
		for (Method m : this.mr.methods()) {
			if (!m.isOverriding()) continue;
			List<ResolvedMethod> tops = this.mr.getTopResolvedMethods(m);
			if (tops.size()>1) {
				List<ResolvedMethod> colliding = this.getCompatibleMethods(tops);
				if (colliding != null && ! this.hasMergeMethods(colliding))
					this.reportMethodError(m, tops, "(several top methods)");
			}
			else if (tops.size()==0) {
				List<ResolvedMethod> cycle = this.mr.getCycle(m);
				if (cycle.size()>0) this.reportMethodError(m, cycle, "(override cycle)");
			}
		}
	}
	
	private boolean hasMergeMethods(List<ResolvedMethod> colliding) {		
		for (ResolvedMethod rm : colliding ) {
			if (rm.isMerge()) return true;
		}
		return false;
	}

	private List<ResolvedMethod> getCompatibleMethods(List<ResolvedMethod> tops) {
		for (int i = 0; i < tops.size(); i++) {
			ResolvedMethod rm1 = tops.get(i);
			for (int j = i+1; j < tops.size(); j++) {
				ResolvedMethod rm2 = tops.get(j);
				Configuration cfg1 = rm1.getConfiguration();
				Configuration cfg2 = rm2.getConfiguration();
				FeatureSolver fs = new FeatureSolver(new FeatureIDEProvider(cfg1), cfg1.getFeatureModel());
				
				fs.addConstraints(cfg1, cfg2);
				if (fs.isSat()) return Arrays.asList(rm1, rm2);
			}
		}
		
		return null;
	}

	private void reportMethodError(Method m, List<ResolvedMethod> methods, String msg) {
		List<String> files = methods.stream().map( f -> f.getFile().getName()).collect(Collectors.toList());
		List<String> cfgs = methods.stream().map( f -> f.getConfiguration().getSelectedFeatureNames().toString()).collect(Collectors.toList());
		System.err.println("[merlin] Method collision "+msg+" for method: "+m+" in files "+files);
		System.err.println("[merlin]   in configurations "+cfgs);
		ValidationIssue vi = new ValidationIssue("Method collision "+msg+" for method: "+m+" in files "+files+" in configurations "+cfgs, IssueLevel.ERROR, null);
		this.trafoErrors.add(vi);
		for (ResolvedMethod resm : methods)  
			FileUtils.updateMarkers(FileUtils.getIFile(resm.getFile()), Collections.singletonList(vi));
	}
	
	public void checkMissingBodies(Map<EPackage, IFile> concepts) {		
		for (EPackage p : concepts.keySet()) {
			for (EClassifier ecl : p.getEClassifiers()) {
				if (! (ecl instanceof EClass)) continue;
				EClass c = (EClass)ecl;
				for (EOperation op : c.getEOperations()) {
					EAnnotation ocl = op.getEAnnotation(EMFUtils.OCLPIVOT);
					if (ocl==null) {
						// check if implemented in each variant
						List<Configuration> missing = new ArrayList<>();
						boolean misses = this.mr.missesConfiguration(new Method(c.getName(), op.getName(), null), missing);
						if (misses) {
							String errorMsg = "Operation "+op.getName()+" missing body in class "+c.getName();
							if (missing.size()>0)
								errorMsg+=", e.g., for configuration "+missing.get(0).getSelectedFeatureNames();
							ValidationIssue vi = new ValidationIssue(errorMsg,IssueLevel.ERROR, c);
							this.trafoErrors.add(vi);
							FileUtils.updateMarkers(concepts.get(p),  Collections.singletonList(vi));
						}
					}
				}
			}
		}
		
	}
	
	public void cleanMarkers() {
		if (SelectedConcepts.getConfig() != null) {
			Map<Configuration, List<File>> frags = SelectedConcepts.getConfig().getFragments();
			String MARKER_PROBLEM = "merlin.marker.problem";
		
			for (List<File> files: frags.values()) {
				for (File f : files) {
					try {
						FileUtils.getIFile(f).deleteMarkers(MARKER_PROBLEM, true, IResource.DEPTH_INFINITE);
					} catch (CoreException e) {
						System.err.println("[merlin] could not delete marker for file "+f.getName());
					}
				}
			}
		}
	}
	

	public List<String> getOclErrors() {
		return this.oclErrors;
	}

}
