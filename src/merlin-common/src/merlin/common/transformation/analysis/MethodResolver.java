package merlin.common.transformation.analysis;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.emf.ecore.EPackage;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.configuration.Configuration;
import de.ovgu.featureide.fm.core.configuration.Selection;
import merlin.common.analysis.FeatureSolver;
import merlin.common.concepts.ConfigurationFragmentPool;
import merlin.common.concepts.SelectedConcepts;
import merlin.common.exporter.AbstractExporter;
import merlin.common.exporter.PluginUtils;
import merlin.common.features.FeatureIDEProvider;
import merlin.common.transformation.Method;
import merlin.common.transformation.OverrideKind;

/**
 * Class to resolved the overrides and merge method relations
 */
public class MethodResolver {
	private Map<Method, List<ResolvedMethod>> resolvedMethods = new LinkedHashMap<>();
	private EPackage root;
	
	/**
	 * @param p: root package with all classes
	 */
	public MethodResolver(EPackage p) {
		this.root = p;
		this.resolveMethods();
	}
	
	public Set<Method> methods() {
		return this.resolvedMethods.keySet();
	}
	
	private AbstractExporter getExporter(ConfigurationFragmentPool cfp) {
		String extension = cfp.extension();
		if (extension.equals(".ocl")) return new OCLHandler(this.root);
		return PluginUtils.getExporterWithExtension(extension);
	}
	
	public void resolveMethods() {
		AbstractExporter oclexp = new OCLHandler(this.root);
		for (ConfigurationFragmentPool cfp: SelectedConcepts.getConfigs()) {
			AbstractExporter ae = this.getExporter(cfp);
			if (ae==null) {
				if (!cfp.extension().equals(".ocl")) System.err.println("[merlin] No exporter for files of type "+cfp.extension());
				continue;
			}
			Collection<File> frags = cfp.getAllFragments();
			Map<File, List<Method>> methods = new LinkedHashMap<>();
			for (File f : frags) {
				if (f.getName().endsWith(ae.extension()))
					methods.put(f, ae.getMethods(f));
				else 
					methods.put(f, oclexp.getMethods(f));
			}
			
			for (File f : methods.keySet()) {
				for (Method mth : methods.get(f)) {
					if (! this.resolvedMethods.containsKey(mth)) {
						this.resolvedMethods.put(mth, new ArrayList<>());
					}
					List<Configuration> cfgs = cfp.getConfigs(f);	// can we have more than one config?
					this.resolvedMethods.get(mth).add(new ResolvedMethod(mth, cfgs.get(0)));
				}
			}
		}
		this.doResolve();
	}
	
	private String toString(Configuration configuration) {
		String res = "";
		boolean first = true;
		for (String s : configuration.getSelectedFeatureNames()) {
			if (!first) res += " and ";
			res += s;
			first = false;
		}
		return res;
	}
	
	/**
	 * There must be a unique root in the resolvedMethods tree
	 * @param list
	 * @return
	 */
	public List<ResolvedMethod> getAllResolvedMethods(Method m) {
		List<ResolvedMethod> methods = new ArrayList<>();
		methods.addAll(this.resolvedMethods.get(m));
		return methods;
	}
	
	/**
	 * There must be a unique root in the resolvedMethods tree
	 * @param list
	 * @return
	 */
	public List<ResolvedMethod> getTopResolvedMethods(Method m) {
		List<ResolvedMethod> tops    = new ArrayList<>();
		List<ResolvedMethod> methods = this.resolvedMethods.get(m);
		tops.addAll(methods);
		for (ResolvedMethod rm : methods) 
			tops.removeAll(rm.overrides());
		
		// Now we take into account merges
		List<List<Method>> merged = this.getMergedMethods(tops.stream().map(me -> me.getFile()).collect(Collectors.toList()));
		
		//System.out.println("To merge : "+merged);
		
		return tops;
	}
	
	private boolean getCycle(ResolvedMethod current, List<ResolvedMethod> pcycle) {
		if (pcycle.contains(current)) {
			pcycle.add(current);
			return true;
		}
		pcycle.add(current);
		for (ResolvedMethod next : current.overrides()) 
			if (this.getCycle(next, pcycle)) return true;
		
		return false;
	}
	
	public List<ResolvedMethod> getCycle(Method m) {
		List<ResolvedMethod> pcycle = new ArrayList<>();
		for (ResolvedMethod rm : this.resolvedMethods.get(m)) {
			if (getCycle(rm, pcycle)) return pcycle;
			pcycle.clear();
		}
		return pcycle;
	}
	
	public List<ResolvedMethod> getTopResolvedMethods(Method m, List<File> frags) {
		List<ResolvedMethod> tops = new ArrayList<>();
		List<ResolvedMethod> methods = this.resolvedMethods.get(m);
		tops.addAll(methods.stream().filter(met -> frags.contains(met.getFile())).collect(Collectors.toList()));
		for (ResolvedMethod rm : methods) {
			if (! frags.contains(rm.getFile())) continue;
			tops.removeAll(rm.overrides());
		}
		
		return tops;
	}
	
	private List<ResolvedMethod> getOverridenMethodsIn(List<File> frags) {
		List<ResolvedMethod> bottoms = new ArrayList<>();
		
		for (Method m : this.resolvedMethods.keySet()) {
			if (!m.isOverriding()) continue;
			List<ResolvedMethod> methods = this.resolvedMethods.get(m);
			bottoms.addAll(methods);
			bottoms.removeAll(this.getTopResolvedMethods(m, frags));
		}
		
		return bottoms;
	}
	
	public List<List<Method>> getMergedMethods(List<File> frags) {
		List<List<Method>> merged = new ArrayList<>();
		
		for (Method m : this.resolvedMethods.keySet()) {
			if (m.isOverriding()) continue;
			List<ResolvedMethod> methods = this.resolvedMethods.get(m);
			merged.add(methods.stream().map( rm -> rm.getMethod()).filter( rm -> frags.contains(rm.getFile())).collect(Collectors.toList()));
		}
		
		return merged;
	}

	// returns the list of methods that are overriden by some others within some fragment in frags
	public List<Method> getOverridenMethods (List<File> frags) {
		List<ResolvedMethod> overriden = this.getOverridenMethodsIn(frags);
		return overriden.stream().
						 filter(rm -> frags.contains(rm.getFile())).
						 map(rm -> rm.getMethod()).
						 collect(Collectors.toList());
	}
	
	private void doResolve() {
		for (Method m : this.resolvedMethods.keySet()) {
			for (ResolvedMethod rm : this.resolvedMethods.get(m)) {
				if (rm.getOverrideKind()==OverrideKind.NONE) continue;
				Configuration current = rm.getConfiguration();
				for (ResolvedMethod orm : this.resolvedMethods.get(m)) {
					if (orm==rm) continue;
					if (rm.getOverrideKind()==OverrideKind.ALL) {
						rm.overrides().add(orm);
						continue;
					}
					Configuration other = orm.getConfiguration();
					FeatureSolver fs = new FeatureSolver(new FeatureIDEProvider(current), current.getFeatureModel());
					
					if (rm.getOverrideKind()==OverrideKind.SUPER) 						
						fs.addNegatedConstraint(this.toString(current)+" implies "+this.toString(other));
					else if (rm.getOverrideKind()==OverrideKind.SUBS) 
						fs.addNegatedConstraint(this.toString(other)+" implies "+this.toString(current));
					
					if (!fs.isSat()) 
						rm.overrides().add(orm);
				}
			}		
		}
	}

	public boolean containsMethod(Method method) {
		return this.resolvedMethods.keySet().contains(method);
	}
	
	// returns if a configuration is not covered by m, and puts some examples in example
	public boolean missesConfiguration(Method m, List<Configuration> example) {
		if (!this.resolvedMethods.containsKey(m)) return true;
		if (this.resolvedMethods.get(m).size()==0) return true;
		Configuration c0 = this.resolvedMethods.get(m).get(0).getConfiguration();
		IFeatureModel fm = c0.getFeatureModel();
		FeatureSolver fs = new FeatureSolver(new FeatureIDEProvider(c0), fm);
		for (ResolvedMethod rm : this.resolvedMethods.get(m)) {
			fs.addNegatedConstraint(this.toString(rm.getConfiguration()));
		}
		if (fs.isSat()) {
			Configuration cfg = new Configuration(fm);
			for (String fname : fs.getModel(true)) {
				cfg.setManual(fname, Selection.SELECTED);
			}
			example.add(cfg);
			return true; 
		}
		return false;
	}

}
