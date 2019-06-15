package merlin.analysis.validate.ui.handlers;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;

import merlin.common.annotations.MerlinAnnotationStructure;
import merlin.common.concepts.ConfigurationFragmentPool;
import merlin.common.concepts.SelectedConcepts;
import merlin.common.exporter.AbstractExporter;
import merlin.common.exporter.PluginUtils;
import merlin.common.features.DefaultFeatureProvider;
import merlin.common.issues.IssueLevel;
import merlin.common.issues.ValidationIssue;
import merlin.common.utils.EMFUtils;
import merlin.common.utils.FileUtils;

public class TransformationAnalysisDialog extends Dialog {
	private Composite container;
	private IProject prj; 
	private HashMap<Button, EPackage> 	selected = new LinkedHashMap<>();
	private HashMap<EPackage, IFolder> 	folders = new LinkedHashMap<>();
	private HashMap<EPackage, IFile> 	files = new LinkedHashMap<>();
	private HashMap<EPackage, String> 	trafoNames = new LinkedHashMap<>();
	private DefaultFeatureProvider fp;
	private Button structure;
	private boolean analyseConsistency = false, analyseContracts = false;

	protected TransformationAnalysisDialog(Shell parentShell, IProject p, DefaultFeatureProvider fp) {
		super(parentShell);
		this.prj = p;
		this.fp = fp;
	}
	
	@Override
    protected Control createDialogArea(Composite parent) {
        this.container = (Composite) super.createDialogArea(parent);
        setGridLayout(this.container, 1);

		Group search_group = new Group(container, SWT.NONE); 
        search_group.setText("Transformations:");
        setGridLayout(search_group, 2);
		
        Composite structural = new Composite(container, SWT.NONE);
        setGridLayout(structural, 2);
        
		// Now create a check for each concept under concept
		this.addChecks(search_group, structural);
		
		Label labelCheck = new Label(structural, SWT.NONE);
		labelCheck.setText(	"Analyse consistency?");
		Button analyseConsistencyOp = new Button(structural, SWT.CHECK);
		analyseConsistencyOp.addListener(SWT.Selection, new Listener() {
			@Override 
			public void handleEvent(Event event) {
				analyseConsistency = analyseConsistencyOp.getSelection();
			}
		});
		
		labelCheck = new Label(structural, SWT.NONE);
		labelCheck.setText(	"Analyse contracts?");
		Button analyseContractsOp = new Button(structural, SWT.CHECK);
		analyseContractsOp.addListener(SWT.Selection, new Listener() {
			@Override 
			public void handleEvent(Event event) {
				analyseContracts = analyseContractsOp.getSelection();
			}
		});
		
		search_group.pack();
		structural.pack();

        container.pack();
        return container;
	}

	private void setGridLayout(Composite structural, int cols) {
		GridLayout structuralLayout = new GridLayout();
        structural.setLayout(structuralLayout);
        structuralLayout.numColumns = cols;
	}
	
	private void addChecks(Composite gcontainer, Composite container) {
		IFolder folder = this.prj.getFolder("bindings");
		if (!folder.exists()) return;
		List<File> res = FileUtils.getAllFiles(folder.getLocation().toFile(), ".ecore");
		
		Composite cont = gcontainer;
		for (File r : res) {			
			String trafoName, technology;
			IFile ifile = FileUtils.getIFile(r);			
			List<EPackage> packs = EMFUtils.readEcore(ifile);
			EAnnotation an = packs.get(0).getEAnnotation(MerlinAnnotationStructure.TRANSFORMATION);
			if (an!=null) {
				trafoName = an.getDetails().get(MerlinAnnotationStructure.TRAFO_NAME);
				technology = an.getDetails().get(MerlinAnnotationStructure.TRAFO_TECHNOLOGY);
				cont = gcontainer;
			} else if ( this.isStructuralConcept(r)) {	// structural concept
				trafoName = "Structure";
				technology = "OCL";
				cont = gcontainer;
			} else continue;
			
			this.files.put(packs.get(0), ifile);
			Label labelCheck = new Label(cont, SWT.NONE);
			labelCheck.setText(	trafoName+" ["+technology+"]");
			Button check = new Button(cont, SWT.RADIO);
			if (this.isStructuralConcept(r)) this.structure = check;
//			if (cont==container) {
//				check.addListener(SWT.Selection, new Listener() {
//					@Override
//					public void handleEvent(Event event) {
//						Button self = (Button)event.widget;
//						if (event.type==SWT.Selection) {
//							for (Button b : selected.keySet()) {
//								if (b != self) {
//									b.setSelection(false);
//									b.setEnabled(!self.getSelection());
//								}
//							}
//						}
//					}
//					
//				});
//			}
			check.setData(Arrays.asList(trafoName,technology));
			this.trafoNames.put(packs.get(0), trafoName);
			check.setSelection(false);
			// Unmark the check if we do not know how to handle such technology
			AbstractExporter ae = PluginUtils.getExporterWithTechnology(technology);
			if (ae==null) {
				check.setEnabled(false);
				FileUtils.updateMarkers(ifile, Collections.singletonList(new ValidationIssue("Don't know how to handle technology "+technology, 
						IssueLevel.ERROR, null)));
			} else 
				FileUtils.cleanMarkers(ifile);				

			this.selected.put(check, packs.get(0));
			this.folders.put(packs.get(0), (IFolder)ifile.getParent());
		}
	}
	
	private boolean isStructuralConcept(File r) {		
		return r.getAbsolutePath().contains("bindings") && r.getAbsolutePath().contains("structure");
	}

	public List<EPackage> getSelectedConcepts() {
		List<EPackage> result = 
				this.selected.keySet().
					stream().
					filter( b -> b.getSelection() || b.equals(this.structure)). // we add the structure anyways
					map( b -> this.selected.get(b)).
					collect(Collectors.toList());
		return result;
	}
	
	public Map<EPackage, IFile> getSelectedConceptsAndFiles() {
		List<EPackage> selected = this.returnSelectedConcepts();
		return selected.stream().
						filter(this.files::containsKey).
						collect(Collectors.toMap(Function.identity(), this.files::get));
	}
	
	 @Override
	 protected void configureShell(Shell newShell) {
	    super.configureShell(newShell);
	    newShell.setText("Select elements to analyse");
	 }
	 
	 public IFolder getFolder ( EPackage p ) {
		 return this.folders.get(p);
	 }
	 
	 public String getTrafoNameOf(EPackage p) {
		 return this.trafoNames.get(p);
	 }

	 public String getTrafoTechnologyOf(EPackage p) {
		 for (Button b : this.selected.keySet()) {
			 if (this.selected.get(b).equals(p)) return ((List<String>)b.getData()).get(1);
		 }
		 return "";
	 }
	 
	 public List<EPackage> returnSelectedConcepts() {
		 return this.selConcepts;
	 }
	 
	 private List<EPackage> selConcepts = new ArrayList<>();
	 
	 public boolean analyseConsistency() {
		 return this.analyseConsistency;
	 }
	 
	 public boolean analyseContracts() {
		 return this.analyseContracts;
	 }
	 
	 @Override
	 protected void okPressed() {
		 SelectedConcepts.clear();
		 SelectedConcepts.set(this.getSelectedConcepts());
		 this.selConcepts.addAll(this.getSelectedConcepts());
		 for (EPackage pck : SelectedConcepts.get()) {
			 String techn = this.getTrafoTechnologyOf(pck);		
			 String trafo = this.getTrafoNameOf(pck);
			 SelectedConcepts.setConfigs(trafo.toLowerCase(), new ConfigurationFragmentPool(prj, 
					 PluginUtils.getExtension(techn), 
					 this.getFolder(pck), 
					 fp.getFeatureModel()));			
		 }
		 
		 
		 
		 super.okPressed();
	 }
}
