package merlin.concepts.handlers;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.internal.resources.File;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;

import merlin.concepts.concept.ConceptSynthesizer;

public class GenerateConceptHandler extends AbstractConceptHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		System.out.println("[merlin] Generating the concept");
		IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
		File selected = this.getSelectedFile(event);
		ConceptSynthesizer cs = new ConceptSynthesizer();
		cs.generateConcept(selected);
		
		MessageDialog.openInformation(
				window.getShell(),
				"merlin-concept",
				"Generating the concept from 150-MM "+selected+" in folder concept");
		return null;
	}
}

