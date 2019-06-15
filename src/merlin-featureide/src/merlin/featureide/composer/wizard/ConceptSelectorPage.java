package merlin.featureide.composer.wizard;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import merlin.common.annotations.MerlinAnnotationStructure;
import merlin.common.exporter.AbstractExporter;
import merlin.common.exporter.PluginUtils;
import merlin.common.issues.IssueLevel;
import merlin.common.issues.ValidationIssue;
import merlin.common.utils.EMFUtils;
import merlin.common.utils.FileUtils;

public class ConceptSelectorPage extends WizardPage {

	private Composite container;
	private IProject prj; 
	private HashMap<Button, EPackage> selected = new LinkedHashMap<>();
	private HashMap<EPackage, IFolder> folders = new LinkedHashMap<>();
	private Button structureCheck;

	public ConceptSelectorPage(IProject prj) {
		super("Select product kind");
		this.prj = prj;
		setDescription("Select product kind");
	}

	@Override
	public void createControl(Composite parent) {
		container = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		container.setLayout(layout);
		layout.numColumns = 2;

		Label labelCheck = new Label(container, SWT.NONE);
		labelCheck.setText("Structure (ecore)");
		this.structureCheck = new Button(container, SWT.CHECK);
		structureCheck.setSelection(true);

		// Now create a check for each concept under concept
		this.addChecks();

		// required to avoid an error in the system
		setControl(container);
		setPageComplete(true);
	}
	
	public boolean isStructureSelected() {
		return this.structureCheck.getSelection();
	}

	private void addChecks() {
		IFolder folder = this.prj.getFolder("bindings");
		if (!folder.exists()) return;
		List<File> res = FileUtils.getAllFiles(folder.getLocation().toFile(), ".ecore");
		for (File r : res) {			
			IFile ifile = FileUtils.getIFile(r);			
			List<EPackage> packs = EMFUtils.readEcore(ifile);
			EAnnotation an = packs.get(0).getEAnnotation(MerlinAnnotationStructure.TRANSFORMATION);
			if (an!=null) {
				Label labelCheck = new Label(container, SWT.NONE);
				String trafoName = an.getDetails().get(MerlinAnnotationStructure.TRAFO_NAME);
				String technology = an.getDetails().get(MerlinAnnotationStructure.TRAFO_TECHNOLOGY);
				labelCheck.setText(	trafoName+" ["+technology+"]");
				Button check = new Button(container, SWT.CHECK);
				check.setData(Arrays.asList(trafoName,technology));
				check.setSelection(false);
				// Unmark the check if we do not know how to handle such technology
				AbstractExporter ae = PluginUtils.getExporterWithTechnology(technology);
				if (ae==null) {
					check.setEnabled(false);
					// TODO: add marker
					FileUtils.updateMarkers(ifile, Collections.singletonList(new ValidationIssue("Don't know how to handle technology "+technology, 
																								  IssueLevel.ERROR, null)));
				} else 
					FileUtils.cleanMarkers(ifile);				
					
				this.selected.put(check, packs.get(0));
			}
			else {
				if (r.getAbsolutePath().contains("bindings") && r.getAbsolutePath().contains("structure")) {
					this.structureCheck.setData(Arrays.asList("structure", "ocl"));
					this.selected.put(this.structureCheck, packs.get(0));
				}
			}
			this.folders.put(packs.get(0), (IFolder)ifile.getParent());
		}
	}

	public IFolder getFolder ( EPackage p ) {
		return this.folders.get(p);
	}
	
	public List<EPackage> getSelectedConcepts() {
		return this.selected.keySet().
					stream().
					filter( b -> b.getSelection()).
					map( b -> this.selected.get(b)).
					collect(Collectors.toList());
	}
	
	public String getTrafoNameOf(EPackage p) {
		for (Button b : this.selected.keySet()) {
			if (this.selected.get(b).equals(p)) return ((List<String>)b.getData()).get(0);
		}
		return "";
	}
	
	public String getTrafoTechnologyOf(EPackage p) {
		for (Button b : this.selected.keySet()) {
			if (this.selected.get(b).equals(p)) return ((List<String>)b.getData()).get(1);
		}
		return "";
	}

}
