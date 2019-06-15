package merlin.analysis.validate.annotations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;

import merlin.common.features.DefaultFeatureProvider;
import merlin.common.features.IFeatureProvider;
import merlin.common.issues.IssueLevel;
import merlin.common.issues.ValidationIssue;
import merlin.common.utils.EMFUtils;

public class AnnotationChecker {
	private IFile ecore = null;
	private IFeatureProvider provider = new DefaultFeatureProvider();
	private boolean existingErrors = false;
	
	public AnnotationChecker(IFile ecore) {
		this.ecore = ecore;
		AnnotationCheck.reset(); // reset list of checks
		new FeatureModelAnnotationCheck(this.provider, (ecore!=null? ecore.getProject() : null));
		new ClassAnnotationCheck(this.provider);
		new StructuralFeatureCheck(this.provider);
		new OCLAnnotationCheck(this.provider);
		new PresenceConditionSATCheck(this.provider);
		new UnusedFeaturesCheck(this.provider);
		new ReferencePresenceConditionCheck(this.provider);
		new ModifierCheck(this.provider);
		new InheritanceCyclesCheck(this.provider);
	}	
	
	public List<ValidationIssue> check() {
		List<ValidationIssue> ret = new ArrayList<ValidationIssue>();
		List<EPackage>  metamodel = EMFUtils.readEcore(ecore);
		if (!metamodel.isEmpty()) {
			for (EPackage epackage : metamodel) 
				ret.addAll(this.check(epackage));
		}
		return ret;
	}

	private boolean hasStoppingErrors( Collection<ValidationIssue> issues ) {
		return issues.stream().anyMatch( p -> p.getLevel().equals(IssueLevel.ERROR));
	}
	
	private void checkStoppingErrors( Collection<ValidationIssue> issues ) {
		this.existingErrors = this.existingErrors || this.hasStoppingErrors(issues);
		//this.existingErrors = this.existingErrors || issues.size()>0;
	}
	
	private List<ValidationIssue> check(EPackage p) {
		this.existingErrors = false;
		List<ValidationIssue> issues = new ArrayList<>();
		issues.addAll(this.doCheck(p, EPackage.class));
		// now traverse the classes
		for (EClassifier cl : p.getEClassifiers()) {
			issues.addAll(this.doCheck(cl, EClass.class));
			this.checkStoppingErrors(issues);
			// Traverse the Features
			if (cl instanceof EClass) {
				EClass clss = (EClass)cl;
				for (EStructuralFeature sf : clss.getEStructuralFeatures()) {
					issues.addAll(this.doCheck(sf, sf.getClass()));
				}
			}
		}
		return issues;
	}
	
	private Collection<ValidationIssue> doCheck(EObject el, Class<? extends EObject> cls) {
		Set<ValidationIssue> issues = new LinkedHashSet<>();
		for (AnnotationCheck ac : AnnotationCheck.getChecksFor(cls)) {
			issues.addAll(ac.check(el, this.existingErrors));
			this.checkStoppingErrors(issues);
		}
		return issues;
	}
}
