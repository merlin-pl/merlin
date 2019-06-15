package merlin.common.analysis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.emf.ecore.EClassifier;
import org.prop4j.And;
import org.prop4j.Node;
import org.prop4j.SatSolver;
import org.sat4j.specs.TimeoutException;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.configuration.Configuration;
import de.ovgu.featureide.fm.core.editing.AdvancedNodeCreator;
import de.ovgu.featureide.fm.core.editing.AdvancedNodeCreator.CNFType;
import de.ovgu.featureide.fm.core.editing.AdvancedNodeCreator.ModelType;
import de.ovgu.featureide.fm.core.filter.AbstractFeatureFilter;
import merlin.common.annotations.presenceCondition.ConditionParser;
import merlin.common.features.DefaultFeatureProvider;
import merlin.common.features.IFeatureProvider;
import merlin.common.issues.ValidationIssue;

public class FeatureSolver {
	private IFeatureProvider provider;
	private IFeatureModel fp;
	private Node rootNode;
	private EClassifier context;
	private SatSolver is;
	
	private List<Node> constraints = new ArrayList<>();
	
	public FeatureSolver(IFeatureProvider provider, IFeatureModel fm) {
		this.provider = provider;
		this.fp = fm;
		this.rootNode = AdvancedNodeCreator.createNodes(this.fp, new AbstractFeatureFilter(), CNFType.Compact, ModelType.All, true);
	}
	
	public FeatureSolver(EClassifier context, IFile f) {
		this.provider = new DefaultFeatureProvider(f);
		this.fp = ((DefaultFeatureProvider)provider).getFeatureModel();
		this.rootNode = AdvancedNodeCreator.createNodes(this.fp, new AbstractFeatureFilter(), CNFType.Compact, ModelType.All, true);
		this.context = context;		
	}
	
	public void setFeatureModel (IFeatureModel fm) {
		this.fp = fm;
	}
	
	public boolean isSat() {		
		is = new SatSolver(new And(rootNode.clone(), new And(this.constraints)), 1000);
		try {
			if (is.isSatisfiable())	return true;			
		} catch (TimeoutException e1) {
			return false;
		}
		return false;
	}
	
	public List<String> getModel(boolean pos) {
		return is.getSolution(pos);
	}

	public Collection<ValidationIssue> addConstraint(String constraint) {
		ConditionParser cp = new ConditionParser(constraint, this.context, this.provider);
		Collection<ValidationIssue> errors = cp.parse();
		if (errors.size()==0) {
			this.constraints.add(cp.getAST().toFeatureIDENode()); 
		}
		else 
			System.out.println("[merlin] parsing errors : "+errors);
		return errors;
	}
	
	public Collection<ValidationIssue> addConstraints(Configuration ...cfgs) {
		Set<String> feats = new LinkedHashSet<>();
		Set<String> negated = new LinkedHashSet<>();
		
		for (Configuration c : cfgs) { 
			feats.addAll(c.getSelectedFeatureNames());
			negated.addAll(c.getUnSelectedFeatures().stream().map(f -> f.getName()).collect(Collectors.toList()));
		}
		
		String concat = "";
		boolean first = true;
		for (String cond : feats) {
			if (!first) concat+= " and ";
			concat += cond;
			first = false;
		}
		
		for (String cond : negated) {
			if (!first) concat+= " and ";
			concat += "not "+cond;
			first = false;
		}
		
		return this.addConstraint(concat);
	}
	
	public void addNegatedConstraint(String constraint) {
		String negated = "not ("+constraint+")";
		this.addConstraint(negated);
	}
	

}
