package merlin.analysis.validate.ui.handlers;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EOperation;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EParameter;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.ocl.OCL;
import org.eclipse.ocl.ParserException;
import org.eclipse.ocl.ecore.CallOperationAction;
import org.eclipse.ocl.ecore.Constraint;
import org.eclipse.ocl.ecore.EcoreEnvironmentFactory;
import org.eclipse.ocl.ecore.SendSignalAction;
import org.eclipse.ocl.ecore.utilities.AbstractVisitor;
import org.eclipse.ocl.expressions.OCLExpression;
import org.eclipse.ocl.expressions.PropertyCallExp;
import org.eclipse.ocl.expressions.TypeExp;
import org.eclipse.ocl.helper.OCLHelper;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;

import merlin.analysis.validate.properties.PropertyChecker;
import merlin.analysis.validate.properties.PropertyChecker.ProblemSpace;
import merlin.analysis.validate.properties.PropertyChecker.SolutionArity;
import merlin.analysis.validate.properties.PropertyResult;
import merlin.common.utils.EMFUtils;
import merlin.common.utils.MerlinAnnotationUtils;

public class PropertyAnalysisHandler extends AbstractValidationHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		
		IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
		IFile            ecore  = this.getSelectedFile(event);
		if (ecore==null) return Status.CANCEL_STATUS;		
		try {
			AnalysisDialog dialog = new AnalysisDialog(null, ecore);
			dialog.create();
			if (dialog.open() == Window.OK) {
				
				PropertyChecker checker = new PropertyChecker(ecore);
				checker.setDebug(Path.fromOSString(ecore.getProject().getLocation().toString()).toOSString());
				PropertyResult result  = checker.check(
						dialog.input_property,       // property, 
						dialog.input_configuration,  // configuration, 
						dialog.one? SolutionArity.ONE : (dialog.all_min? SolutionArity.ALL_MIN : SolutionArity.ALL_MAX), // arity, 
						dialog.sat_0? ProblemSpace.NOTEXISTS : (dialog.sat_1? ProblemSpace.EXISTS : ProblemSpace.FORALL), // problem, 
						dialog.output_witness,       // generateWitness, 
						dialog.output_configuration, // generateConfiguration, 
						dialog.feature_exercising,   // exerciseFeatures, 
						true                         // checkSyntax
						);
				
//				updateMarkers(ecore, issues);
				if (result.hasErrors()) MessageDialog.openError      (window.getShell(), "Merlin", ecore.getName() + " has the following problems:\n" + result.getErrors());
				else                    MessageDialog.openInformation(window.getShell(), "Merlin", result.getSummary());				
			}
		}
		catch (Exception e) { e.printStackTrace(); MessageDialog.openError (window.getShell(), "Merlin", e.getMessage()); }
		
		return Status.OK_STATUS;
	}
	
	// ------------------------------------------------------------------------------------------
	
	private class AnalysisDialog extends Dialog {
		
		private boolean one, sat_0, sat_1, sat_all, feature_exercising, output_witness, output_configuration, all_min;
		private String  input_property, input_configuration;
		
		private Text iproperty_text, iconfiguration_text;
		private List<EPackage> metamodel;
		
		protected AnalysisDialog (Shell parentShell) {
			super(parentShell);
		}
		
		protected AnalysisDialog (Shell parentShell, IFile ecore) {
			this(parentShell);			
			metamodel = EMFUtils.readEcore(ecore);
		}		
		
	    @Override
	    protected Control createDialogArea(Composite parent) {
	        Composite  container = (Composite) super.createDialogArea(parent);
	        GridLayout layout    = new GridLayout(1, false);
	        layout.marginRight   = 5;
	        layout.marginLeft   = 10;
	        container.setLayout(layout);
	        
	        // property
	        Label property_label = new Label(container, SWT.LEFT); property_label.setText("Property (mentioned features must start by $):");
	        iproperty_text       = new Text (container, SWT.WRAP | SWT.MULTI | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
	        
	        Group search_group = new Group(container, SWT.NONE); 
	        search_group.setText("Search options");
	        
	        // solving problem: some / all / none
	        Label     solving_label     = new Label(search_group, SWT.LEFT); solving_label.setText("Property satisfaction");
	        Composite solving_options   = new Composite(search_group, SWT.NONE);
	        Button solving_some_radio   = new Button(solving_options, SWT.RADIO); solving_some_radio.setText("some model");
	        Button solving_all_radio    = new Button(solving_options, SWT.RADIO); solving_all_radio.setText("all models");
	        Button solving_none_radio   = new Button(solving_options, SWT.RADIO); solving_none_radio.setText("no model");
	        
	        // feature exercising
	        Label  features_label = new Label (search_group, SWT.LEFT); features_label.setText("Feature exercising");
	        Button features_check = new Button(search_group, SWT.CHECK);  
	        
	        // configuration scope
	        Label    iconfiguration_label = new Label(search_group, SWT.LEFT); iconfiguration_label.setText("Partial configuration");
	        /*Text*/ iconfiguration_text  = new Text(search_group, SWT.SINGLE | SWT.BORDER);
	        new Label(search_group, SWT.NONE);
	        Button iconfiguration_button = new Button(search_group, SWT.NONE); iconfiguration_button.setText("Extract minimal configuration for property");
	        
	        Group result_group = new Group(container, SWT.SHADOW_NONE); 
	        result_group.setText("Result");
	        
	        // result persistence: witness model / feature configuration
	        Label  omodel_label = new Label(result_group, SWT.LEFT); omodel_label.setText("Produce witness models");
	        Button omodel_check = new Button(result_group, SWT.CHECK);
	        Label  oconfiguration_label = new Label (result_group, SWT.LEFT); oconfiguration_label.setText("Produce configurations");
	        Button oconfiguration_check = new Button(result_group, SWT.CHECK);
	        
	        // result arity: 1 / all {min, max}
	        Label     arity_label   = new Label(result_group, SWT.LEFT); arity_label.setText("Number of solutions");
	        Composite arity_options = new Composite(result_group, SWT.NONE);
	        Button arity_1_radio = new Button(arity_options, SWT.RADIO); arity_1_radio.setText("one");
	        Button arity_n_radio = new Button(arity_options, SWT.RADIO); arity_n_radio.setText("all");	        
	        Label     heuristic_label   = new Label(result_group, SWT.LEFT); heuristic_label.setText("Heuristic search");
	        Composite heuristic_options = new Composite(result_group, SWT.NONE);	        
			Button heuristic_min_radio = new Button(heuristic_options, SWT.RADIO); heuristic_min_radio.setText("hard"); // hard == min
			Button heuristic_max_radio = new Button(heuristic_options, SWT.RADIO); heuristic_max_radio.setText("soft"); // soft == max	        
			
	        // layout of property
	        GridData iproperty_data = new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.VERTICAL_ALIGN_FILL);
	        iproperty_data.verticalSpan = 20;
	        /*Text*/ iproperty_text.setLayoutData(iproperty_data);	
	        
	        GridData search_data     = new GridData(SWT.FILL, SWT.FILL, true, false);
	        search_data.minimumWidth = 560; // 800;
	        search_group.setLayout (new GridLayout(4, true));
	        search_group.setLayoutData(search_data);
	        GridData one_column    = new GridData(GridData.FILL, GridData.FILL, true, true); 
	        GridData two_columns   = new GridData(GridData.FILL, GridData.FILL, true, true); two_columns.horizontalSpan   = 2;
	        GridData three_columns = new GridData(GridData.FILL, GridData.FILL, true, true); three_columns.horizontalSpan = 3;

	        // layout of solving problem
	        solving_label.setLayoutData(one_column); 
	        solving_options.setLayout(new GridLayout(3, true));
	        solving_options.setLayoutData(three_columns);
	        solving_some_radio.setLayoutData(one_column);
	        solving_all_radio .setLayoutData(one_column);
	        solving_none_radio.setLayoutData(one_column);
	        
	        // layout of feature exercising
	        features_label.setLayoutData(one_column);
	        features_check.setLayoutData(three_columns);

	        // layout of configuration scope
	        iconfiguration_label.setLayoutData (one_column);
	        iconfiguration_text.setLayoutData  (three_columns);
	        iconfiguration_button.setLayoutData(three_columns);
	        
	        result_group.setLayout    (new GridLayout(4, true));
	        result_group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
	        
	        // layout of result persistence
	        omodel_label.setLayoutData(one_column);
	        omodel_check.setLayoutData(three_columns);
	        oconfiguration_label.setLayoutData(one_column);
	        oconfiguration_check.setLayoutData(three_columns);
	        
	        // layout of result arity	 
	        arity_label.setLayoutData(one_column);
	        arity_options.setLayout(new GridLayout(3, true));
	        arity_options.setLayoutData(three_columns);
	        arity_1_radio.setLayoutData(one_column);
	        arity_n_radio.setLayoutData(one_column);	 	        
	        heuristic_label.setLayoutData(one_column);
	        heuristic_options.setLayout(new GridLayout(3, true));
	        heuristic_options.setLayoutData(three_columns);
	        heuristic_min_radio.setLayoutData(one_column);
	        heuristic_max_radio.setLayoutData(one_column);
	        
	        // listeners of solving problem
	        SelectionListener solving_listener = new SelectionListener() {
				@Override public void widgetDefaultSelected(SelectionEvent arg0) {}
				@Override public void widgetSelected(SelectionEvent arg0) { 
					sat_0   = solving_none_radio.getSelection(); 
					sat_1   = solving_some_radio.getSelection();
					sat_all = solving_all_radio.getSelection();
					if (!sat_1) omodel_check.setSelection(false);
			        omodel_label.setEnabled(sat_1);
			        omodel_check.setEnabled(sat_1);			        
				}};
			solving_some_radio.addSelectionListener(solving_listener);
			solving_all_radio .addSelectionListener(solving_listener);
			solving_none_radio.addSelectionListener(solving_listener);
			solving_some_radio.setSelection(true);	        	
			solving_some_radio.notifyListeners(SWT.Selection, new Event()); 
			
	        // listeners of feature exercising
	        SelectionListener features_listener = new SelectionListener() {
				@Override public void widgetDefaultSelected(SelectionEvent arg0) {}
				@Override public void widgetSelected(SelectionEvent arg0) { 
					feature_exercising = features_check.getSelection(); }};
			features_check.addSelectionListener(features_listener);
			features_check.setSelection(false);
			features_check.notifyListeners(SWT.Selection, new Event()); 
	        
	        // listeners of configuration scope
	        SelectionListener iconfiguration_listener = new SelectionListener() {
				@Override public void widgetDefaultSelected(SelectionEvent arg0) {}
				@Override public void widgetSelected(SelectionEvent arg0) { 
					extractConfiguration(); }};
			iconfiguration_button.addSelectionListener(iconfiguration_listener);
	        
	        // listeners of result persistence
	        SelectionListener omodel_listener = new SelectionListener() {
				@Override public void widgetDefaultSelected(SelectionEvent arg0) {}
				@Override public void widgetSelected(SelectionEvent arg0) { 
					output_witness = omodel_check.getSelection(); }};
			omodel_check.addSelectionListener(omodel_listener);
			omodel_check.setSelection(true);
			omodel_check.notifyListeners(SWT.Selection, new Event()); 
	        SelectionListener oconfiguration_listener = new SelectionListener() {
				@Override public void widgetDefaultSelected(SelectionEvent arg0) {}
				@Override public void widgetSelected(SelectionEvent arg0) { 
					output_configuration = oconfiguration_check.getSelection(); }};
			oconfiguration_check.addSelectionListener(oconfiguration_listener);
			oconfiguration_check.setSelection(true);
			oconfiguration_check.notifyListeners(SWT.Selection, new Event()); 
	        
	        // listeners of result arity
			SelectionListener arity_listener = new SelectionListener() {
				@Override public void widgetDefaultSelected(SelectionEvent arg0) {}
				@Override public void widgetSelected(SelectionEvent arg0) { 
					one = arity_1_radio.getSelection(); 
					heuristic_label.setEnabled(!one); 
					heuristic_min_radio.setEnabled(!one); 
					heuristic_max_radio.setEnabled(!one); }};
			arity_1_radio.addSelectionListener(arity_listener);
			arity_n_radio.addSelectionListener(arity_listener);
			arity_1_radio.setSelection(true);
			arity_1_radio.notifyListeners(SWT.Selection, new Event()); 
	        SelectionListener heustic_listener = new SelectionListener() {
				@Override public void widgetDefaultSelected(SelectionEvent arg0) {}
				@Override public void widgetSelected(SelectionEvent arg0) { 
					all_min = heuristic_min_radio.getSelection(); }};
			heuristic_min_radio.addSelectionListener(heustic_listener);
			heuristic_max_radio.addSelectionListener(heustic_listener);
			heuristic_min_radio.setSelection(true);
			heuristic_min_radio.notifyListeners(SWT.Selection, new Event());

	        container.pack();
	        return container;
	    }
	    
	    @Override
	    protected void configureShell(Shell newShell) {
	        super.configureShell(newShell);
	        newShell.setText("Model property analysis");
	    }
	    
	    @Override
        protected void okPressed() {
	    	input_property      = iproperty_text.getText();
	    	input_configuration = iconfiguration_text.getText();
	    	super.okPressed();
	    }
	    
	    protected void extractConfiguration() {
			String input_property = iproperty_text.getText();
			String input_configuration = "";
			if (input_property!=null && !input_property.isEmpty()) {
				OCL<EPackage, EClassifier, EOperation, EStructuralFeature, EEnumLiteral, EParameter, EObject, CallOperationAction, SendSignalAction, Constraint, EClass, EObject> ocl = OCL.newInstance(EcoreEnvironmentFactory.INSTANCE);
				OCLHelper<EClassifier, EOperation, EStructuralFeature, Constraint> helper = ocl.createOCLHelper();
				helper.setContext(metamodel.get(0).getEClassifiers().get(0));
				try {
					OCLExpression<EClassifier> query = helper.createQuery(input_property);
					OclToPresenceConditionVisitor visitor = new OclToPresenceConditionVisitor();
					Set<String> pcs = query.accept(visitor);
					if (!pcs.isEmpty())
						input_configuration = "(" + pcs.stream().map(Object::toString).collect(Collectors.joining(") and (")) + ")";					
				} catch (ParserException e) { MessageDialog.openError(null, "Merlin", "The property has errors."); }
			}
			iconfiguration_text.setText(input_configuration);
	    }
	    
		// ------------------------------------------------------------------------------------------

		private class OclToPresenceConditionVisitor extends AbstractVisitor<Set<String>> {
			public OclToPresenceConditionVisitor() {
				super();
				result = new HashSet<String>();
			}
			
			@Override
		    public Set<String> visitTypeExp(TypeExp<EClassifier> t) {
				handle(t.getReferredType());
	 			return super.visitTypeExp(t);
			}
			
			@Override
			public Set<String> visitPropertyCallExp (PropertyCallExp<EClassifier, EStructuralFeature> pce) {
	 			handle(pce.getReferredProperty());
				return super.visitPropertyCallExp(pce);
			}
			
			protected void handle (ENamedElement ne) {
	 			String pc = MerlinAnnotationUtils.getPresenceCondition(ne);
	 			if (pc!=null && !pc.isEmpty() && !pc.equals("true")) 
	 				result.add(pc);
			}
		};
	}
}