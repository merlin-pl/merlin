package merlin.compare.handlers;

import java.util.List;
import java.util.Set;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.internal.resources.File;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;

import de.ovgu.featureide.fm.core.configuration.Configuration;
import merlin.common.utils.MerlinAnnotationUtils;
import merlin.compare.comparison.EcoreComparator;
import merlin.compare.comparison.ProductCalculator;
import merlin.featureide.composer.EcoreComposer;

/**
 * Our sample handler extends AbstractHandler, an IHandler base class.
 * @see org.eclipse.core.commands.IHandler
 * @see org.eclipse.core.commands.AbstractHandler
 */
public class ObtainClosestProductHandler extends AbstractHandler {

	@SuppressWarnings("restriction")
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
		File selected = this.getSelectedFile(event);
		
		FileDialog dialog = new FileDialog(window.getShell(), SWT.OPEN);
		dialog.setText("Select meta-model product line");
		dialog.setFilterExtensions(new String [] {"*.ecore"});
		dialog.setFilterPath(selected.getParent().getLocation().toOSString());
		String mmpl = dialog.open();
		
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IPath location = Path.fromOSString(mmpl);
		IFile mmplfile = workspace.getRoot().getFileForLocation(location);
		
		EcoreComparator     ecc  = new EcoreComparator(selected.getLocation().toOSString(), mmpl, getFeatureModel(mmplfile.getProject()));
		ProductCalculator   pc   = new ProductCalculator(ecc);
		List<Configuration> conf = pc.getClosestConfigs();
		Set<String>   conditions = pc.collectRequiredPresenceConditions();
		this.persistConfigs(conf, this.makeFolderName(selected.getName()), mmplfile);
		
		MessageDialog.openInformation(
				window.getShell(),
				"merlin-compare",
				"Getting the closest product to "+selected+" from PL "+mmpl+"\n\n"+
				"The needed presence conditions are: "+conditions);
		return null;
	}
	
	private String makeFolderName(String name) {
		return name.replaceFirst(".ecore", "");		
	}

	private void persistConfigs(List<Configuration> conf, String folderName, IFile sourceEcore) {
		IProject      prj = sourceEcore.getProject();
		IFolder       fld = this.createFolders(prj, folderName);
		EcoreComposer cmp = new EcoreComposer(sourceEcore);

		for (Configuration cfg : conf) {
			cmp.buildConfiguration(fld, cfg, folderName);
		}
	}

	private IFolder createFolders (IProject prj, String folderName) {
		IFolder rfld = null, fld = null;
		try {
			rfld = this.createFolder(prj, MerlinAnnotationUtils.PRODUCTS_FOLDER);
			fld  = this.createFolder(prj, MerlinAnnotationUtils.PRODUCTS_FOLDER+IPath.SEPARATOR+folderName);
		} catch (CoreException e) {
			System.err.println("[merlin] could not create folder in: "+rfld != null ? rfld.getLocation() : fld.getLocation());
			return null;
		}				
		return fld;
	}
	
	private IFolder createFolder(IProject prj, String folder) throws CoreException {
		IFolder rootFolder = prj.getFolder(folder);
		if (!rootFolder.exists())
		rootFolder.create(IResource.NONE, true, null);		
		return rootFolder;
	}
	
	@SuppressWarnings("restriction")
	private File getSelectedFile(ExecutionEvent event) {
		ISelection selection = HandlerUtil.getActiveWorkbenchWindow(event).getActivePage().getSelection();
		if (selection != null & selection instanceof IStructuredSelection) {
            IStructuredSelection strucSelection = (IStructuredSelection) selection;
            return (File)strucSelection.getFirstElement();
        }
		return null;
	}
	
	
	private IFile getFeatureModel (IProject prj) {
		IFile fm = null;
		if (prj != null) {	// take the model.xml file by default, if exists
			fm = prj.getFile("model.xml");
			if (!fm.exists()) fm = null;
		}
		return fm;
	}
}
