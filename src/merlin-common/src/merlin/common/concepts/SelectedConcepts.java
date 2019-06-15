package merlin.common.concepts;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.emf.ecore.EPackage;

import merlin.common.exporter.PluginUtils;
import merlin.common.features.DefaultFeatureProvider;
import merlin.common.utils.EMFUtils;
import merlin.common.utils.FileUtils;

/**
 * Means to let the composer know about the concepts that need to be composed
 */
public class SelectedConcepts {
	private static final String DEFAULT = "structure";
	private static List<EPackage> selected = new ArrayList<>();
	private static Map<String, ConfigurationFragmentPool> cfp = new LinkedHashMap<>();
	
	public static List<EPackage> get() {
		return selected;
	}

	public static void set(List<EPackage> pcks) {
		selected.clear();
		selected.addAll(pcks);
	}
	
	// loads structural concept in received project
	public static void setDefault (IProject project) {
		clear();
		if (project!=null) {
			DefaultFeatureProvider fp       = new DefaultFeatureProvider(project.getFile("model.xml"));
			IFolder                folder   = project.getFolder("bindings" + File.separator + DEFAULT);
			List<File>             concepts = FileUtils.getAllFiles(folder.getLocation().toFile(), ".ecore");
			if (concepts.size() > 0) {
				IFile          ifile = FileUtils.getIFile(concepts.get(0));
				List<EPackage> packs = EMFUtils.readEcore(ifile);
				set(packs);
				setConfigs(DEFAULT, new ConfigurationFragmentPool(project, PluginUtils.getExtension("OCL"), folder, fp.getFeatureModel()));
			}
		}
	}
	
	public static void clear() {
		selected.clear();
		cfp.clear();
	}

	public static void setConfigs(String str, ConfigurationFragmentPool configurationFragmentPool) {
		cfp.put(str, configurationFragmentPool);
	}
	
	public static ConfigurationFragmentPool getConfig() {
		return cfp.get(DEFAULT);
	}
	
	public static Collection<ConfigurationFragmentPool> getConfigs() {
		return cfp.values();
	}
	
	public static ConfigurationFragmentPool getConfig(String s) {
		return cfp.get(s);
	}
}
