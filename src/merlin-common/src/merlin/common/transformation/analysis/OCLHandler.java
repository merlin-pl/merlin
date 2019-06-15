package merlin.common.transformation.analysis;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EOperation;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.impl.EPackageRegistryImpl;
import org.eclipse.ocl.OCL;
import org.eclipse.ocl.OCLInput;
import org.eclipse.ocl.ParserException;
import org.eclipse.ocl.ecore.Constraint;
import org.eclipse.ocl.ecore.EcoreEnvironmentFactory;

import de.ovgu.featureide.fm.core.configuration.Configuration;
import merlin.common.concepts.SelectedConcepts;
import merlin.common.exporter.AbstractExporter;
import merlin.common.issues.IssueLevel;
import merlin.common.issues.ValidationIssue;
import merlin.common.transformation.Method;
import merlin.common.transformation.OverrideKind;
import merlin.common.utils.EMFUtils;
import merlin.common.utils.FileUtils;


public class OCLHandler extends AbstractExporter{	
	private EPackage root;
	private ArrayList<String> oclErrors = new ArrayList<>();
	private EcoreEnvironmentFactory envFactory;
	/*private Map<Method, String> bodies = new TreeMap<>(new Comparator<Method>() {
		@Override
		public int compare(Method o1, Method o2) {	// take files into account		
			return o1.equals(o2) ? o1.getFile().compareTo(o2.getFile()) : o1.toString().compareTo(o2.toString());
		}
	});*/
	//private Map<ResolvedMethod, String> bodies = new LinkedHashMap<>();
	private Map<Configuration, OCLOperationsBodyCache> methodBodies = new LinkedHashMap<>();
	private MethodResolver mr;
		
	public OCLHandler() {}
	
	public OCLHandler(EPackage r) {
		this.root = r;
		EPackage.Registry registry = new EPackageRegistryImpl();
		registry.put(root.getNsURI(), root);
		this.envFactory = new EcoreEnvironmentFactory(registry);
	}
	
	public OCLHandler(EPackage r, MethodResolver mr) {
		this(r);
		this.mr = mr;
	}
	
	public List<String> getOCLErrors() {
		List<String> allErrors = new ArrayList<>(this.oclErrors);
		for (OCLOperationsBodyCache cache : this.methodBodies.values())
			allErrors.addAll(cache.oclErrors());
		return this.oclErrors;
	}
	
	public Map<ResolvedMethod, String> getAllConfigBodies() {
		Configuration cfg = SelectedConcepts.getConfig().getRootConfig();
		
		if (!this.methodBodies.containsKey(cfg))
			this.methodBodies.put(cfg, new OCLOperationsBodyCache(cfg, root, OCLOperationsBodyCache.ConfigurationDirection.UNDER));
		
		return this.methodBodies.get(cfg).getMethodBodies();		
	}

	public List<ValidationIssue> addOCLOperations(/*URI path, */Configuration cfg) {
		List<ValidationIssue> vi = new ArrayList<>();
		
		//Map<List<File>, List<Map<OCL, File>>> oclEnvs = this.readCompatibleOCLDocuments(path, cfg);
		// Now we add the implementations of the binding
		
		if (!this.methodBodies.containsKey(cfg))
			this.methodBodies.put(cfg, new OCLOperationsBodyCache(cfg, root, OCLOperationsBodyCache.ConfigurationDirection.OVER));
		
		OCLOperationsBodyCache bodies = this.methodBodies.get(cfg);
		
		List<Method> completed = new ArrayList<>();
		
		for (List<File> frgs : bodies.oclEnvs().keySet()) {
			for (Map<OCL, File> pair : bodies.oclEnvs().get(frgs)) {
				for (OCL ocl : pair.keySet()) {
					File file = pair.get(ocl);
					List<Method> overriden = this.mr.getOverridenMethods(frgs);	// methods that MUST be overriden
					List<List<Method>> merge = this.mr.getMergedMethods(frgs);	// methods that MUST be merged
					List<Constraint> constr = ocl.getConstraints();
					for (Constraint c : constr) {
						EOperation context= EMFUtils.getOperation(c);
						if (context==null) continue;
						EClass owner = (EClass)context.eContainer();
						Method thisM = new Method (owner.getName(), context.getName(), file);
						if (this.isCompleted(completed, thisM)) continue;
						if (this.isOverriden(thisM, overriden)) continue;	// The method is overriden, so do nothing
						List<Method> toMerge = this.hasToMerge(thisM, merge);
						String newBody = null;
						if (toMerge!=null && toMerge.size()>1) {
							newBody = this.mergeOps(toMerge, bodies);
							completed.addAll(toMerge);
						} 
						else completed.add(thisM);
						if (this.hasBody(context)) {	
							oclErrors.add("repeated body for : "+context.getName());
							// Unless the previous body is from default
							vi.add(new ValidationIssue("repeated body for : "+context.getName(), IssueLevel.ERROR, context));
						}
						EAnnotation cnsAnnotation = this.assignBodyToOperation(context, newBody, thisM, bodies);
						context.getEAnnotations().add(cnsAnnotation);	// TODO: should we clone the annotation?						
					}
				}
			}
		}
		return vi;
	}	
	
	private EAnnotation assignBodyToOperation(EOperation context, String newBody, Method thisM, OCLOperationsBodyCache bodies) {
		EAnnotation cnsAnnotation = context.getEAnnotation(EMFUtils.OCLPIVOT);
		
		if (cnsAnnotation == null){
			cnsAnnotation = EcoreFactory.eINSTANCE.createEAnnotation();				
			cnsAnnotation.setSource(EMFUtils.OCLPIVOT);
		}
		if (newBody==null) newBody = bodies.getBody(thisM);
		cnsAnnotation.getDetails().put(EMFUtils.BODY, newBody);
		
		return cnsAnnotation;
	}
	
	private boolean isCompleted(List<Method> completed, Method thisM) {		
		for (Method m : completed) {
			if (m.identical(thisM)) return true;
		}
		return false;
	}

	private String mergeOps(List<Method> toMerge, OCLOperationsBodyCache bodies) {
		String result = "";
		boolean first = true;
		for (Method m : toMerge) {
			if (!first) { 
				if (m.getOverride().isOperator())
					result+=" "+m.getOverride().mergeOperation()+"\n     ";
				else
					result+="->\n    "+m.getOverride().mergeOperation()+"(";
			}
			result += bodies.getBody(m);
			if (!m.getOverride().isOperator() && !first) result+=")";
			first = false;
		}
		return result;
	}

	private List<Method> hasToMerge(Method thisM, List<List<Method>> merge) {
		for (List<Method> meths: merge) {
			for (Method m : meths) {
				if (m.equals(thisM) && m.getFile().equals(thisM.getFile())) return meths;
			}
		}
		return null;
	}

	private boolean isOverriden(Method thisM, List<Method> overriden) {
		for (Method m : overriden) {
			if (m.equals(thisM) && m.getFile().equals(thisM.getFile())) return true;
		}
		return false;
	}

	// TODO: Clone in OCLOperationsBodyCache
	private OCL readOCLDocument(File f, OCL ocl) {
		if (!f.getName().endsWith(".ocl")) return ocl;

		try {
			FileInputStream fis = new FileInputStream(f);
			OCLInput ocli = new OCLInput(fis);			
			ocl.parse(ocli);						
		}
		catch (FileNotFoundException e1) {
			e1.printStackTrace();			
		}
		catch (ParserException e) {
			if (this.repairOwnerOperation(e, ocl)) {	// we really have repaired
//				System.err.println("[merlin] OCL problem: " + e.getMessage());
				this.oclErrors.add(e.getMessage());
			}
		}
		return ocl;
	}
	
	private boolean repairOwnerOperation(ParserException e, OCL ocl) {
		if (!(e.getMessage().contains("already defined in type"))) return true;
		String operName = e.getMessage().substring("Operation (".length());
		operName = operName.substring(0, operName.indexOf("("));
		//System.out.println("[merlin] operName = "+operName);
		
		Constraint c = (Constraint) ocl.getConstraints().get(ocl.getConstraints().size()-1);
		if (c.eContainer() == null) {
			EAnnotation ann = EcoreFactory.eINSTANCE.createEAnnotation();
			ann.setSource("http://www.eclipse.org/ocl/1.1.0/OCL");
			ann.getContents().add(c);
			// Now get the operation
			String typeName = this.getTypeName(e.getMessage());
			EClass owner = (EClass) this.root.getEClassifier(typeName);
			EOperation oper = EMFUtils.getOperation(owner, operName);
			oper.getEAnnotations().add(ann);
			return true;
		}
		return false;
	}
	
	private String getTypeName(String message) {		
		int occur = message.indexOf("already defined in type (");
		String typeName = message.substring(occur+"already defined in type (".length());
		typeName = typeName.substring(0, typeName.length()-1);
		return typeName;
	}
	
	private EOperation getOperationWithNoBody(EClass cl) {
		for (EOperation op : cl.getEOperations()) {
			if (!this.hasBody(op)) return op;
		}
		// We return just the first one
		if (!cl.getEOperations().isEmpty()) return cl.getEOperations().get(0);
		return null;
	}

	private boolean hasBody(EOperation context) {
		EAnnotation ann = context.getEAnnotation(EMFUtils.OCLPIVOT);
		if (ann==null) return false;
		return ann.getDetails().get(EMFUtils.BODY)!=null;
	}
	
	@Override
	public List<Method> getMethods(File f) {
		List<Method> methods = new ArrayList<>();
		
		OCL ocl = OCL.newInstance(envFactory);
		ocl = this.readOCLDocument(f, ocl);
		
		String oclFile = FileUtils.readFile(f);
		
		List<Constraint> constraints = ocl.getConstraints();
		for (Constraint c : constraints) {
			EOperation context = null;
			context = EMFUtils.getOperation(c);
			if (context!=null) {
				EClass cl = (EClass)context.eContainer();
				Method m = new Method(cl.getName(), context.getName(), f);
				this.setOverride(m, context, oclFile);
				methods.add(m);
			}
		}
		
		return methods;
	}

	private void setOverride(Method m, EOperation context, String oclFile) {
		// We need to find the comment previous to the context operation
		int idx = oclFile.indexOf(context.getName());
		Pattern signre = Pattern.compile("\\s*def:\\s*"+context.getName()+"\\s*[(]");
		Matcher matcher = signre.matcher(oclFile);
		if (!matcher.find()) return;
		int indx = matcher.start();
		m.setOverride(this.getOverride(indx, oclFile));
		//System.out.println(idx);
	}

	private OverrideKind getOverride(int indx, String oclFile) {
		String substr = oclFile.substring(0, indx);
		int idx = substr.lastIndexOf("--");
		substr = substr.substring(idx);
		substr = substr.toLowerCase();
		if (substr.contains("def:")) return OverrideKind.NONE;  // This hopefully discards annotations to previous operations
		if (substr.contains("@override")) {	// TODO: change by a StreamTokenizer
			if (substr.contains("all")) return OverrideKind.ALL;
			if (substr.contains("super")) return OverrideKind.SUPER;
			if (substr.contains("subs")) return OverrideKind.SUBS;
		}
		else if (substr.contains("@merge")) {
			if (substr.contains("and")) return OverrideKind.MERGE_AND;
			if (substr.contains("or")) return OverrideKind.MERGE_OR;
			if (substr.contains("add")) return OverrideKind.MERGE_ADD;
			if (substr.contains("intersection")) return OverrideKind.MERGE_INTERSECTION;
			if (substr.contains("multiply")) return OverrideKind.MERGE_MULTIPLY;
			if (substr.contains("union")) return OverrideKind.MERGE_UNION;
		}
		return OverrideKind.NONE;
	}

	@Override
	public void export(EPackage p, String fname) {
		// Just do nothing
	}

	@Override
	public String name() {
		return "ocl";
	}

	@Override
	public String extension() {
		return ".ocl";
	}

	@Override
	public String getImport(String importFile) {
		return "";
	}

	public void setResolver(MethodResolver mr) {
		this.mr = mr;
	}
}
