/**
 * Iterator over the product metamodels of a 150mm. 
 * It relies on FeatureIDE to improve performance.
 * It persists the products metamodels.
 */
package merlin.analysis.validate.properties;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.emf.ecore.EPackage;

import de.ovgu.featureide.core.IFeatureProject;
import de.ovgu.featureide.fm.core.localization.StringTable;
import de.ovgu.featureide.ui.actions.generator.ConfigurationBuilder;
import de.ovgu.featureide.ui.actions.generator.IConfigurationBuilderBasics.BuildOrder;
import de.ovgu.featureide.ui.actions.generator.IConfigurationBuilderBasics.BuildType;
import merlin.common.utils.EMFUtils;
import merlin.featureide.composer.FeatureProjectWrapper;

public class MetamodelIterator {
	protected IFile ecore;
	protected ConfigurationBuilder builder;
	protected long current = -1;
	
	// constructor for 150mm
	public MetamodelIterator (IFile ecore) {
		IFeatureProject project = new FeatureProjectWrapper(ecore) ;		
		this.builder = new ConfigurationBuilder(project, BuildType.ALL_VALID, false/*toggleState*/, StringTable.ICPL, 2/*T*/, BuildOrder.DEFAULT, false/*test*/, 10000000/*max*/, 2/*TInteraction*/);
		this.current = 0;
		this.ecore   = ecore;
	}
	
	public boolean hasNext () { 
		return this.current < this.builder.configurationNumber; 
	}
	
	public List<EPackage> next() {
		return hasNext()? readMetamodel(++current) : null;
	}	
	
	public List<EPackage> same() {
		return this.current<=this.builder.configurationNumber? readMetamodel(current) : null;
	}	

	public String path() {
		return this.current<=this.builder.configurationNumber? ecore.getProject().getFile(folder(current)).getFullPath().toOSString() : null;
	}	
	
	private List<EPackage> readMetamodel (long index) {
		List<EPackage> metamodel = new ArrayList<EPackage>();
		try {
			IFile ifile = ecore.getProject().getFile(folder(index) + File.separator + ecore.getName());
			while (!ifile.exists() || // wait until the file has been created
				   !ifile.isSynchronized(IResource.DEPTH_ZERO));
			ifile.refreshLocal(IResource.DEPTH_ZERO, null);
			metamodel = EMFUtils.readEcore(ifile);
		} 
		catch (CoreException e) { e.printStackTrace(); }
		return metamodel;
	}	

	private String folder (long index) {
		return "products" + File.separator + String.format("%05d", index);
	}
}