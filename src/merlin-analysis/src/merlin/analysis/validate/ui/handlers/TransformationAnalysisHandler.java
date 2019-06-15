package merlin.analysis.validate.ui.handlers;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;

import merlin.analysis.validate.contracts.TransformationChecker;
import merlin.analysis.validate.contracts.TransformationResult;
import merlin.common.features.DefaultFeatureProvider;
import merlin.common.transformation.analysis.TransformationProductChecker;
import merlin.common.utils.EMFUtils;

public class TransformationAnalysisHandler extends AbstractValidationHandler {

	private TransformationAnalysisDialog dialog;
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IFile ecore = this.getSelectedFile(event);
		if (ecore==null) return Status.CANCEL_STATUS;
		IProject prj = ecore.getProject();
		IFile featureModelFile = prj.getFile("model.xml");
		DefaultFeatureProvider dfp = new DefaultFeatureProvider(featureModelFile);

		dialog = new TransformationAnalysisDialog(null, prj, dfp);
		dialog.create();
		IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);

		List<EPackage> packs = EMFUtils.readEcore(ecore);

		if (dialog.open() == Window.OK) {

			TransformationProductChecker tpc = new TransformationProductChecker(packs.get(0));
			tpc.allChecks(dialog.getSelectedConceptsAndFiles());

			if (tpc.getTrafoErrors().size()>0)
				MessageDialog.openError(window.getShell(), "Merlin", 
						"Transformation: "+this.TrafoNames(dialog.returnSelectedConcepts()) + " have errors.");
			else {
				if (dialog.analyseConsistency() || dialog.analyseContracts()) {
					TransformationChecker checker = new TransformationChecker(ecore);
					checker.setDebug(Path.fromOSString(ecore.getProject().getLocation().toString()).toOSString());
					String errors = "";
					if (dialog.analyseConsistency()) {
						TransformationResult result = checker.checkConsistency(true, true);
						if (result.hasErrors()) errors += result.getErrors();
					}
					if (dialog.analyseContracts()) {
						TransformationResult result = checker.checkContracts(true, true);
						if (result.hasErrors()) errors += result.getErrors();
					}
					if (!errors.isEmpty()) MessageDialog.openError      (window.getShell(), "Merlin", ecore.getName() + " has the following problems:\n" + errors);
					else                   MessageDialog.openInformation(window.getShell(), "Merlin", "Transformations: " + this.TrafoNames(dialog.returnSelectedConcepts()) + " validated successfully!");				
				}
				else MessageDialog.openInformation(window.getShell(), "Merlin", "Transformation: " + this.TrafoNames(dialog.returnSelectedConcepts()) + " validated successfully!");
			}
		}
		return null;
	}

	private List<String> TrafoNames(List<EPackage> selectedConcepts) {
		return selectedConcepts.stream().map(p -> this.dialog.getTrafoNameOf(p)).collect(Collectors.toList());
	}
}
