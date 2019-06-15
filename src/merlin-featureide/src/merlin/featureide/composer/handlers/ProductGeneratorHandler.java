package merlin.featureide.composer.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;

import de.ovgu.featureide.core.IFeatureProject;
import merlin.featureide.composer.FeatureProjectWrapper;
import merlin.featureide.composer.wizard.MerlinProductWizard;

public class ProductGeneratorHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IFile ecore = this.getSelectedFile(event);
		IProject prj = ecore.getProject();

   		IWorkbenchWindow    window  = HandlerUtil.getActiveWorkbenchWindowChecked(event);
   		Shell               shell   = window.getShell();
   		IFeatureProject     project = new FeatureProjectWrapper(ecore);
   		MerlinProductWizard wizard  = new MerlinProductWizard(project, false, prj);
	    WizardDialog        dialog  = new WizardDialog(shell, wizard);
		wizard.setNeedsProgressMonitor(true);
		dialog.create();
		dialog.open();
		
		return Status.OK_STATUS;
	}

	// it returns the file selected in the workspace
	private IFile getSelectedFile (ExecutionEvent event) {
		IFile      file      = null;
		ISelection selection = HandlerUtil.getCurrentSelection(event);
	    if (selection instanceof IStructuredSelection) {
	    	Object first = ((IStructuredSelection)selection).getFirstElement();
	    	if (first == null) return null;
	        file         = (IFile)Platform.getAdapterManager().getAdapter(first, IFile.class);
	        if (file == null) 
	            if (first instanceof IAdaptable)
	                file = (IFile)((IAdaptable)first).getAdapter(IFile.class);
	    }
	    return file;
	}
}
