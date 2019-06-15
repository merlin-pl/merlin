package merlin.featureide.composer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;

import de.ovgu.featureide.core.IFeatureProject;
import de.ovgu.featureide.core.builder.ComposerExtensionClass;
import de.ovgu.featureide.fm.core.ExtensionManager.NoSuchExtensionException;
import de.ovgu.featureide.fm.core.base.impl.ConfigFormatManager;
import de.ovgu.featureide.fm.core.configuration.Configuration;
import de.ovgu.featureide.fm.core.configuration.DefaultFormat;
import de.ovgu.featureide.fm.core.io.IPersistentFormat;
import de.ovgu.featureide.fm.core.io.manager.SimpleFileHandler;
import merlin.common.issues.ValidationIssue;

public class EcoreComposer extends ComposerExtensionClass {

	private IFile ecore;
	
	public EcoreComposer () { 
		System.out.println("[merlin] Initializing (constructor)");
		this.removeJavaNature(); 
	}

	private void removeJavaNature() {
		if (this.featureProject == null) return;
		IProject pr = this.featureProject.getProject();
		System.out.println("[merlin] removing Java nature of project "+pr);
		try {
			if (pr.hasNature(JAVA_NATURE)) {
				IProjectDescription desc = pr.getDescription();
			    String[] prevNatures = desc.getNatureIds();
				String[] newNatures = new String[prevNatures.length - 1];
				int idx = 0;
				for (int i = 0; i<prevNatures.length; i++) {
					if (!JAVA_NATURE.equals(prevNatures[i])) newNatures[idx++] = prevNatures[i];
				}
			    desc.setNatureIds(newNatures);
			    pr.setDescription(desc, new NullProgressMonitor());
			}			
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public EcoreComposer (IFile ecore) {
		this();
		this.removeJavaNature();
		this.ecore = ecore;
	}

	@Override
	public void performFullBuild(IFile config) {
		System.out.println("[merlin] Full build"+config.getFullPath());
		this.removeJavaNature();
	}

	public boolean initialize(IFeatureProject project) {
		boolean ret = super.initialize(project);
		System.out.println("[merlin] initialize project");
		this.removeJavaNature();
		return ret;
	}
	
	@Override
	public void addCompiler(IProject project, String sourcePath, String configPath, String buildPath) {
		// NOP! (we do not want to compile Java!).
	}
	
	private List<ValidationIssue> issues = new ArrayList<>();
	
	public List<ValidationIssue> getIssues() {
		return issues;
	}
	
	@Override
	public void buildConfiguration(IFolder folder, Configuration configuration, String cfgName) {
		if (this.isInitialized())		
			super.buildConfiguration(folder, configuration, cfgName);
		else
			this.persistConfig(folder, configuration, cfgName);
		
		IFile sourceEcore = this.ecore;          
		String ecoreName  = this.ecore.getName();
		Resource ep = this.readEcore(sourceEcore);
		try {
			EcoreProductGenerator epg = new EcoreProductGenerator(ep, ecoreName.replace(".ecore", ""));
			URI uri = URI.createFileURI(new File(folder.getLocation().toOSString()+File.separator+ecoreName).getAbsolutePath());
			epg.genProduct(configuration, cfgName, uri);	
			if (epg.getTrafoErrors().size()>0) this.issues.addAll(epg.getTrafoErrors());
		} catch (IOException e) {
//			e.printStackTrace();
		}
		
		//System.out.println("Selected features = "+configuration.getSelectedFeatureNames());
	}
	
	private void persistConfig(IFolder folder, Configuration configuration, String cfgName) {
		IPersistentFormat<Configuration> format;
		try {
			if (!folder.exists()) {
				folder.create(true, true, null);
			}
			format = ConfigFormatManager.getInstance().getFormatById(DefaultFormat.ID);
			final IFile configurationFile = folder.getFile(cfgName + "." + format.getSuffix());
			SimpleFileHandler.save(Paths.get(configurationFile.getLocationURI()), configuration, format);
		} catch (NoSuchExtensionException | CoreException e) {
			System.err.println("[merlin] error saving configuration file to "+folder);
		}		
	}

	@SuppressWarnings("unused")
	private Resource readEcore(IFile f) {
//		System.out.println("Loading ecore "+f.getLocation().toOSString());
		ResourceSet resourceSet = new ResourceSetImpl();
		resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put(
				"ecore", new EcoreResourceFactoryImpl());
		EcorePackage ecorePackage = EcorePackage.eINSTANCE;
		URI fileURI = URI.createFileURI(f.getFullPath().toOSString());
		Resource resource = resourceSet.getResource(fileURI, true);
		return resource;
	}
	
	@Override
	public boolean hasSourceFolder() {
		return false;
	}
	
	@Override
	public boolean hasFeatureFolder() {
		return false;
	}
	
	@Override
	public boolean canGeneratInParallelJobs() {
		return false;
	}
}
