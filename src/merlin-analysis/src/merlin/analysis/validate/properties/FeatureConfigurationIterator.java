/**
 * Iterator over the valid feature configurations of a feature model.
 * It does not persist the configurations.
 */
package merlin.analysis.validate.properties;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import org.eclipse.core.resources.IFile;
import org.eclipse.emf.ecore.EPackage;

import de.ovgu.featureide.fm.core.base.IFeature;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.base.IFeatureStructure;
import de.ovgu.featureide.fm.core.configuration.Configuration;
import de.ovgu.featureide.fm.core.configuration.SelectableFeature;
import de.ovgu.featureide.fm.core.configuration.Selection;
import merlin.analysis.validate.annotations.FeatureModelAnnotationCheck;
import merlin.common.features.DefaultFeatureProvider;
import merlin.common.utils.EMFUtils;

public class FeatureConfigurationIterator {
	private   IFeatureModel featureModel;
	private   List<Configuration> previous = null;
	protected List<Configuration> toProcess = null; 
	protected List<String> orderedFeatures = null;
	
	
	// constructor for feature model
	public FeatureConfigurationIterator (IFeatureModel featureModel) { this.init (featureModel); }
	
	// constructor for feature model referred by a 150mm
	public FeatureConfigurationIterator (IFile ecore) {
		List<EPackage>              packages = EMFUtils.readEcore(ecore);
		DefaultFeatureProvider      provider = new DefaultFeatureProvider();
		FeatureModelAnnotationCheck check    = new FeatureModelAnnotationCheck (provider, ecore.getProject());
		check.check(packages.get(0), false);		
		this.init (provider.getFeatureModel());
	}
	
	private void init (IFeatureModel featureModel) {
		this.featureModel = featureModel;
	}
	
	public Configuration next () {				
		if (toProcess==null)  {
			previous  = new ArrayList<>();
			toProcess = new ArrayList<>();
			toProcess.add(new Configuration(featureModel, false/*do not propagate values*/));
			
			// order features by descending number of children
			orderedFeatures = new ArrayList<>();
			TreeMap<Integer, List<String>> features = new TreeMap<>();
			for (IFeature feature : this.featureModel.getFeatures()) {
				int children = children(feature);
				if (!features.containsKey(children)) 
					features.put(children, new ArrayList<String>());
				features.get(children).add(feature.getName());
			}
			for (Integer children : features.descendingKeySet()) 
				orderedFeatures.addAll(features.get(children));
		}
		
		for (int i=0, max_i=toProcess.size(); i<max_i; i++) {
			Configuration cfg = toProcess.get(i);
		
			// -> valid total configuration: return solution 
			if (cfg.getUndefinedSelectedFeatures().isEmpty() && cfg.isValid()/* && isNew(cfg, previous)*/) {				
				previous.add(cfg);
				toProcess.remove(i);
				return cfg;
			}
		
			// -> partial configuration that may be valid: concretize undefined feature with more children  
			else if (cfg.canBeValid()) {
				SelectableFeature undefinedFeature = null;
				for (SelectableFeature feature : cfg.getFeatures()) {
					if (feature.getManual() == Selection.UNDEFINED) {
						if (undefinedFeature == null ||
						    (orderedFeatures.contains(feature.getName()) &&	orderedFeatures.indexOf(feature.getName()) < orderedFeatures.indexOf(undefinedFeature.getName())))
							undefinedFeature = feature;
					}
				}
				if (undefinedFeature!=null) {
					Configuration cfg2 = clone(cfg);
					cfg.setManual (undefinedFeature, Selection.SELECTED);
					cfg2.setManual(undefinedFeature.getName(), Selection.UNSELECTED);
					i--;                 // process this configuration again
					toProcess.add(cfg2); // process the new one
					max_i++;
				}
			}
		}
		
		return null;
	}
	
	// create clone of configuration
	private Configuration clone(Configuration configuration) {
		Configuration clone = new Configuration(featureModel, false/*do not propagate values*/);
		configuration.getFeatures().forEach(f -> clone.setManual(f.getName(), f.getManual()));
		return clone;
	}
	
	// number of direct and indirect children of a feature
	private int children (IFeature feature) { int children = 0; for (IFeatureStructure child : feature.getStructure().getChildren()) children += 1 + children(child.getFeature()); return children; }
}
