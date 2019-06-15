package merlin.common.concepts;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.base.impl.ConfigFormatManager;
import de.ovgu.featureide.fm.core.configuration.Configuration;
import de.ovgu.featureide.fm.core.configuration.SelectableFeature;
import de.ovgu.featureide.fm.core.configuration.Selection;
import de.ovgu.featureide.fm.core.io.ProblemList;
import de.ovgu.featureide.fm.core.io.manager.SimpleFileHandler;
import merlin.common.utils.FileUtils;

public class ConfigurationFragmentPool {
	private HashMap<Configuration, List<File>> fragments = new LinkedHashMap<>();
	private List<File> rootFiles = new ArrayList<>();
	private String extension;
	private Configuration rootConfig;
			
	public String extension() {
		return this.extension;
	}
	
	public HashMap<Configuration, List<File>> getFragments() {
		return this.fragments;
	}
	
	/**
	 * returns the list of configurations that select f
	 * @param f
	 * @return
	 */
	public List<Configuration> getConfigs(File f) {
		List<Configuration> configs = new ArrayList<>();
		for (Configuration cfg : this.fragments.keySet()) {
			if (this.fragments.get(cfg).contains(f)) configs.add(cfg);
		}
		return configs;
	}
	
	public ConfigurationFragmentPool(IProject prj, String ext, IFolder fld, IFeatureModel model) {
		List<File> configFiles = FileUtils.getAllFiles(fld.getLocation().toFile(), ".xml");
		this.extension = ext;
		for (File f : configFiles) {
			Configuration c = new Configuration(model);
			ProblemList pl = SimpleFileHandler.load(f.toPath(), c, ConfigFormatManager.getInstance());
			if (pl.size()>0) {
				System.err.println("[merlin] "+pl);
			}
			this.fragments.put(c, this.getAssociatedFiles(f));
		}
		// Add the root as well
		this.rootConfig = new Configuration(model); // This gives all mandatory features only
		List<File> rootFiles = FileUtils.getFiles(fld.getLocation().toFile(), ext);	
		if (!ext.equals(".ocl")) rootFiles.addAll(FileUtils.getFiles(fld.getLocation().toFile(), ".ocl"));
		this.fragments.put(this.rootConfig, rootFiles);		
		this.rootFiles.addAll(rootFiles);
	}
	
	public Configuration getRootConfig() {
		return this.rootConfig;
	}
	
	public boolean isRootFile(File f) {
		return this.rootFiles.contains(f);
	}
	
	private List<File> getAssociatedFiles(File f) {
		File folder = f.getParentFile();
		File[] contents = folder.listFiles();
		List<File> files = new ArrayList<>();
		if (contents!=null) {	// TODO: Consider ORs : several config files...
			for (File fc : contents) {
				if (fc.isFile() && ( fc.getName().endsWith(this.extension) || fc.getName().endsWith(".ocl")) ) {	// OCL files are always indexed	
					files.add(fc);
				}
			}
		}	
		return files;
	}
	
	public List<File> getFragmentsCompatibleWith(Configuration general) {
		return this.getFragments(general, true);
	}
	
	public List<File> getFragmentsUnder(Configuration general) {
		return this.getFragments(general, false);
	}
	
	private List<File> getFragments(Configuration general, boolean over) {
		Set<File> frgs = new LinkedHashSet<>();
		// Add all roots first
		frgs.addAll(this.rootFiles);
		for (Configuration c : this.fragments.keySet()) {
			if (over) {
				if (this.subsumesConfig(general, c)) {
					frgs.addAll(this.fragments.get(c));
				}
			} else {
				if (this.subsumesConfig(c, general)) {
					frgs.addAll(this.fragments.get(c));
				}
			}
		}
		// Order from upper folders to deeper folders
		List<File> fragments = new ArrayList<>(frgs);

		Collections.sort(fragments, new Comparator<File>() {

			@Override
			public int compare(File f0, File f1) {
				int depth0 = StringUtils.countMatches(f0.getAbsolutePath(), "\\");	// TODO: Handle properly!!
				int depth1 = StringUtils.countMatches(f1.getAbsolutePath(), "\\");
				
				//System.out.println(depth0+"  "+depth1);
				
				return depth0-depth1;
			}
			
		});
		return fragments;
	}

	public boolean subsumesConfig(Configuration general, Configuration partial) {	
		for (SelectableFeature f : partial.getFeatures()) {
			if (f.getParent()==null) continue;	// we do not get the root (because they have a bug!)
			SelectableFeature gsf = general.getSelectablefeature(f.getName());
			if (gsf==null) {
				System.err.println("[merlin] Error in subsumesConfig, feature "+f.getName()+" not found!");
				return false;
			}
			switch(f.getSelection()) {
			case SELECTED: 
				if ( gsf.getSelection() != Selection.SELECTED ) return false;
				break;
			case UNSELECTED:				
				if ( gsf.getSelection() != Selection.UNDEFINED && gsf.getSelection() != Selection.UNSELECTED) return false;
				break;
			default: break;				
			}
		}
		return true;
	}

	/**
	 * returns all fragments
	 * @return
	 */
	public Collection<File> getAllFragments() {
		Set<File> all = new LinkedHashSet<>();
		all.addAll(this.rootFiles);
		for (List<File> frs : this.fragments.values())
			all.addAll(frs);
		return all;
	}
}
