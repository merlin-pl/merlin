package merlin.featureide.composer.wizard;

import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import de.ovgu.featureide.core.IFeatureProject;
import de.ovgu.featureide.ui.actions.generator.BuildProductsWizard;
import merlin.common.concepts.ConfigurationFragmentPool;
import merlin.common.concepts.SelectedConcepts;
import merlin.common.exporter.PluginUtils;
import merlin.common.issues.IssueLevel;
import merlin.featureide.composer.EcoreComposer;

public class MerlinProductWizard extends BuildProductsWizard{
	private ConceptSelectorPage csp;
	private IFeatureProject fp;
	private IProject prj;
	
	public MerlinProductWizard(IFeatureProject featureProject, boolean toggleState, IProject prj) {
		super(featureProject, toggleState);
		this.csp = new ConceptSelectorPage(prj);
		this.fp = featureProject;
		this.prj = prj;
	}

	public void addPages() {
		this.addPage(csp);
		super.addPages();				
	}
	
	public boolean isStructureSelected() {
		return this.csp.isStructureSelected();
	}
	
	public List<EPackage> getSelectedConcepts() {
		return this.csp.getSelectedConcepts();
	}
	
	@Override
	public IWizardPage getNextPage(IWizardPage page) {
		if (csp.equals(page)) return super.getPage(this.fp.getProjectName());
		else return null;
	}
	
	@Override
	public boolean performFinish() {
		SelectedConcepts.clear();
		SelectedConcepts.set(this.csp.getSelectedConcepts());
		for (EPackage pck : SelectedConcepts.get()) {
			String techn = this.csp.getTrafoTechnologyOf(pck);		
			String trafo = this.csp.getTrafoNameOf(pck);
			SelectedConcepts.setConfigs(trafo, new ConfigurationFragmentPool(prj, 
																			 PluginUtils.getExtension(techn), 
																			 this.csp.getFolder(pck), 
																			 this.fp.getFeatureModel()));			
		}
		boolean res = super.performFinish();
		
		//this.showIssues();
		// No point in trying to show errors here, as the composer runs in a separate thread
		
		return res;
	}
	
	private void showIssues() {
		EcoreComposer comp = (EcoreComposer)this.fp.getComposer();
		IWorkbench wb = PlatformUI.getWorkbench();
		IWorkbenchWindow window = wb.getActiveWorkbenchWindow();
		if (comp.getIssues().stream().anyMatch(issue -> issue.getLevel() == IssueLevel.ERROR)) 
			MessageDialog.openError       (window.getShell(), "Merlin", " Products generated with errors");
		else MessageDialog.openInformation(window.getShell(), "Merlin", " Products generated successfully!");
	}
}
