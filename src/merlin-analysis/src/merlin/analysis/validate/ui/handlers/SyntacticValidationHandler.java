package merlin.analysis.validate.ui.handlers;

import java.util.List;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;

import merlin.analysis.validate.annotations.AnnotationChecker;
import merlin.common.issues.IssueLevel;
import merlin.common.issues.ValidationIssue;
import merlin.common.utils.FileUtils;

public class SyntacticValidationHandler extends AbstractValidationHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		//long millis = System.currentTimeMillis();
		IFile ecore = this.getSelectedFile(event);
		if (ecore==null) return Status.CANCEL_STATUS;
   		AnnotationChecker     ac  = new AnnotationChecker( this.getSelectedFile(event) );
   		List<ValidationIssue> ret = ac.check();
   		FileUtils.updateMarkers(ecore, ret);
   		//long millis2 = System.currentTimeMillis();
   		//System.out.println("[merlin - eval] time spent: " + (millis2-millis) + "ms" );
   		
   		IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
		if (ret.stream().anyMatch(issue -> issue.getLevel() == IssueLevel.ERROR)) 
			 MessageDialog.openError      (window.getShell(), "Merlin", ecore.getName() + " has errors.");
		else MessageDialog.openInformation(window.getShell(), "Merlin", ecore.getName() + " validated successfully!");
		
		return Status.OK_STATUS;
	}
}
