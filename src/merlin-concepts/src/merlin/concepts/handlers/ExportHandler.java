package merlin.concepts.handlers;

import java.util.List;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.internal.resources.File;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;

import merlin.common.exporter.AbstractExporter;
import merlin.common.utils.EMFUtils;
import merlin.common.utils.FileUtils;

public class ExportHandler extends AbstractConceptHandler {
	private static final String EXPORTER_ID = "merlin-concepts.exporter";

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		System.out.println("[merlin] Exporting the product to...");
		IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);

		File selected = this.getSelectedFile(event);
		
		List<EPackage> packs = EMFUtils.readEcore(FileUtils.getIFile(selected));
		
		IConfigurationElement[] config = getExtensionImplementations();

		try {
			for (IConfigurationElement e : config) {
				final Object o = e.createExecutableExtension("logic");
				if (o instanceof AbstractExporter) {
					executeExtension(o, packs);
				}
			}
		} catch (CoreException ex) {
			System.out.println(ex.getMessage());
		}
		MessageDialog.openInformation(
				window.getShell(),
				"merlin-concept",
				"Exporting the product to...");
		return null;
	}

	private IConfigurationElement[] getExtensionImplementations() {
		for (IExtensionPoint p : Platform.getExtensionRegistry().getExtensionPoints()) {
			if (p.getUniqueIdentifier().equals(EXPORTER_ID)) 
				return p.getConfigurationElements();			
		}
		return null;
	}

	private void executeExtension(final Object o, List<EPackage> packs) {
		ISafeRunnable runnable = new ISafeRunnable() {
			@Override
			public void handleException(Throwable e) {
				System.out.println("Exception in client");
			}

			@Override
			public void run() throws Exception {
				((AbstractExporter) o).export(packs.size()>0 ? packs.get(0) : null, "foo");
			}
		};
		SafeRunner.run(runnable);
	}

}