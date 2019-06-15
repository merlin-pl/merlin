package merlin.compare.comparison;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.emf.compare.Comparison;
import org.eclipse.emf.compare.Match;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;

import de.ovgu.featureide.fm.core.configuration.Configuration;
import de.ovgu.featureide.fm.core.configuration.Selection;
import merlin.common.analysis.FeatureSolver;
import merlin.common.features.DefaultFeatureProvider;
import merlin.common.utils.MerlinAnnotationUtils;

public class ProductCalculator {
	private Comparison cmp;
	private Resource ecoreResource, pLineResource;
	private Set<String> presenceConditions;
	private IFile featureModel;
	
	public ProductCalculator(EcoreComparator comp) {
		this.cmp = comp.getComparison();
		this.ecoreResource = comp.getEcoreResource();
		this.pLineResource = comp.getPLineResource();
		this.featureModel  = comp.getFeatureModel();
	}

	public List<Configuration> getClosestConfigs() {
		this.presenceConditions = this.collectRequiredPresenceConditions();
		System.out.println("Required presence conditions: "+this.presenceConditions);
		// Now check if SAT
		
		FeatureSolver fs = new FeatureSolver(this.getDefaultContext(), this.featureModel);
		for (String pc : this.presenceConditions) {
			fs.addConstraint(pc);
		}
		if (fs.isSat()) {
			System.out.println("SAT: only one config is needed");
			Configuration cfg = this.getConfig(fs, this.featureModel);	
			return Collections.singletonList(cfg);
		}
		else {
			System.out.println("UNSAT: more than one config is needed"); 
			// And now try MaxSAT
			return this.getMaximalSat(fs);
		}				
	}
	
	private List<Configuration> getMaximalSat( FeatureSolver fs ) {
		// TODO
		return Collections.emptyList();
	}

	private Configuration getConfig(FeatureSolver fs, IFile fm) {
		List<String> model = fs.getModel(false);	// this selection strategy leads to smaller solutions
		Configuration cfg = new Configuration(new DefaultFeatureProvider(fm).getFeatureModel());
		cfg.makeManual(true);
		for (String feat : model) {	
			cfg.setManual(feat, Selection.SELECTED);
		}
		System.out.println(cfg);
		System.out.println(cfg.getSelectedFeatureNames());
		return cfg;
		/*List<ClauseFeature> model = fs.getModel();
		Configuration cfg = new Configuration(new DefaultFeatureProvider(EcoreTracker.iTRACKER.getActiveFeatureModel()).getFeatureModel());
		cfg.makeManual(true);
		for (ClauseFeature feat : model) {	
			cfg.setManual(feat.getName(), feat.isNegated() ? Selection.UNSELECTED : Selection.SELECTED);
		}
		System.out.println(cfg);
		System.out.println(cfg.getSelectedFeatureNames());
		return cfg;*/
	}
	
	private EClassifier getDefaultContext() {
		for (EObject elem : ecoreResource.getContents() ) {
			if (elem instanceof EClassifier) return (EClassifier)elem;
		}
		return null;
	}

	public Set<String> collectRequiredPresenceConditions() {
		Set<String> presenceConditions = new LinkedHashSet<>();
		for (Match m : cmp.getMatches()) 
			presenceConditions.addAll(this.collectRequiredPresenceConditions(m));
		
		return presenceConditions;
	}
	
	private void addPresenceCondition(Set<String> pool, Match m) {
		EObject plElement = m.getRight();
		if (m.getLeft()!=null && plElement != null && plElement instanceof ENamedElement ) {
			String pc = MerlinAnnotationUtils.getPresenceCondition((ENamedElement)plElement);
			if (pc!=null & !"true".equals(pc)) pool.add(pc);
		}
	}
	
	private Set<String> collectRequiredPresenceConditions(Match root) {
		Set<String> presenceConditions = new LinkedHashSet<>();
		this.addPresenceCondition(presenceConditions, root);
		for (Match m : root.getSubmatches()) {
			this.addPresenceCondition(presenceConditions, m);
			presenceConditions.addAll(this.collectRequiredPresenceConditions(m));
		}
		return presenceConditions;
	}
}
