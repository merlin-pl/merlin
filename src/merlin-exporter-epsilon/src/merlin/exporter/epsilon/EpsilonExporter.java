package merlin.exporter.epsilon;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EOperation;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EParameter;
import org.eclipse.emf.ecore.ETypedElement;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.epsilon.eol.EolModule;
import org.eclipse.epsilon.eol.dom.Annotation;
import org.eclipse.epsilon.eol.dom.Operation;
import org.eclipse.epsilon.eol.dom.TypeExpression;
import org.eclipse.epsilon.eol.exceptions.EolRuntimeException;

import merlin.common.exporter.AbstractExporter;
import merlin.common.transformation.Method;
import merlin.common.transformation.OverrideKind;
import merlin.common.utils.EMFUtils;

public abstract class EpsilonExporter extends AbstractExporter {

	@Override
	public void export(EPackage p, String path) {
		System.out.println("[merlin] Exporter to Epsilon, received package "+p+" ... exporting to file "+path);
		try {
			PrintWriter pw = new PrintWriter(path);
			pw.println(this.preExport());
			pw.println("-- generated by [merlin]");
			pw.println("-- Library for ecore operations in "+p.getName());
			this.genOCLCompatibilityOperations(pw);
			for (EClassifier c : p.getEClassifiers()) {
				if (!(c instanceof EClass)) continue;
				EClass cls = (EClass)c;
				for (EOperation op : cls.getEOperations()) {
					EAnnotation oclBody = op.getEAnnotation(EMFUtils.OCLPIVOT);
					if (oclBody==null) continue;
					String body = oclBody.getDetails().get(EMFUtils.BODY);
					if (body==null) continue;
					pw.println("operation "+cls.getName()+" "+op.getName()+"("+this.writeParams(op)+") :"+this.getType(op)+"{");
					pw.println("   return "+this.toEpsilon(body)+";");	// TODO: Translate to Epsilon properly
					pw.println("}");   
				}
			}
			pw.println(this.postExport());
			pw.close();
		} catch (FileNotFoundException e) {
			System.err.println("[merlin-epsilon] Could not create file "+path);
		}
	}
	
	protected String preExport() {
		return "";
	}
	
	protected String postExport() {
		return "";
	}
	
	protected String toEpsilon(String body) {
		body = body.replace("->", ".");
		if (body.matches("[A-Za-z][A-Za-z0-9_]*::")) {
			System.out.println("[merlin] package name found!");
		}
		body = body.replaceAll("[A-Za-z][A-Za-z0-9_]*::", "");	// We eliminate "package" names
		return body;
	}
	
	protected void genOCLCompatibilityOperations(PrintWriter pw) {
		pw.println("operation Collection any ( b : Boolean ) : Any {");
		pw.println("	return self.first();");
		pw.println("}\n");
		pw.println("operation Any oclAsType(a : Any) : Any {");
		pw.println("    return self;");
		pw.println("}");
		pw.println("operation Collection intersection(a : Collection) : Collection {");
		pw.println("	var res : Collection := new Collection;");
		pw.println("	for (c in self) ");
		pw.println("		if (a.includes(c)) res.add(c);");
		pw.println("	return res;");
		pw.println("}\n");
	}

	/*private String toEpsilon(EClassifier context, EOperation oper, String body) {
		// We parse the OCL body, and fix details. For example, we change:
		// - "let variable = exp in etc" by "var variable = exp; return etc; "
		// - "if c then y else z endif" by "if c return y; else return z;"
		OCL environment = OCL.newInstance(EcoreEnvironmentFactory.INSTANCE);
		OCLHelper<EClassifier, EOperation, EStructuralFeature, Constraint> helper = environment.createOCLHelper();
		//helper.setContext(oper);
		helper.setOperationContext(context, oper);
		try {
			//Constraint invariant = helper.createInvariant(constraint);
			Constraint invariant = helper.createBodyCondition(body);
			System.out.println(invariant.getSpecification());
			System.out.println(invariant.getConstrainedElements());
		} catch (ParserException e) {	// TODO: Handle properly			
			System.err.println("[maple] Malformed constraint "+body);
			System.err.println("[maple] Reason "+e.getMessage());
			return "return "+body;
		}
		return "return "+body;
	}*/

	protected String getType(ETypedElement element) {
		if (!element.isMany()) return this.getTypeName(element);
		String container = this.getContainerType(element);
		return container+"("+this.getTypeName(element)+")";
	}
	
	protected String getContainerType(ETypedElement element) {
		return "Set";
		// Problems with other data types!
		/*if (element.isUnique() && !element.isOrdered()) return "Set";
		if (element.isOrdered()) return "Sequence";
		return "Bag";*/
	}
	
	protected String getTypeName(ETypedElement element) {
		EClassifier cls = element.getEType();
		if (cls instanceof EDataType) {
			if (cls.equals(EcorePackage.Literals.EBOOLEAN) ||
				cls.equals(EcorePackage.Literals.EBOOLEAN_OBJECT)) return "Boolean";
			if (cls.equals(EcorePackage.Literals.EINT) ||
				cls.equals(EcorePackage.Literals.EINTEGER_OBJECT) ||
				cls.equals(EcorePackage.Literals.EBIG_INTEGER) ||
				cls.equals(EcorePackage.Literals.ESHORT) ||
				cls.equals(EcorePackage.Literals.ESHORT_OBJECT)) return "Integer";
			if (cls.equals(EcorePackage.Literals.ESTRING)) return "String";
			if (cls.equals(EcorePackage.Literals.EDOUBLE) || 
				cls.equals(EcorePackage.Literals.EDOUBLE_OBJECT) ||
				cls.equals(EcorePackage.Literals.EBIG_DECIMAL)) return "Real";
			return cls.getName();
		}
		else return cls.getName();
	}

	private String writeParams(EOperation op) {
		String paramList = "";
		boolean first = true;
		for (EParameter par : op.getEParameters()) {
			if (!first) paramList+=", ";
			first = false;
			paramList+= par.getName()+" : "+this.getType(par);
		}
		return paramList;
	}
	
	@Override
	public String getImport(String importFile) {
		return "import '"+importFile+"';\n";
	}

	public List<Method> getMethods(File f, EolModule module)  {
		// returns the list of methods in this file
		List<Method> methods = new ArrayList<>();
		boolean success = true;
		try {
			success = module.parse(f);
		} catch (Exception e) {	// the file does not parse!
			e.printStackTrace();
			return methods;
		}
							
		if (!success) return methods;
		for (Operation op : module.getDeclaredOperations()) {
			//EolType contextClass = op.getContextType(module.getContext());
			TypeExpression contextClass = op.getContextTypeExpression();
			Method m;
			if (contextClass!=null)
				m = new Method(contextClass.getName(), op.getName(), f);
			else
				m = new Method(null, op.getName(), f);
			setOverrideKind(op, m, module);
			methods.add(m);
		}
		return methods;
	}

	private void setOverrideKind(Operation op, Method m, EolModule module) {
		if (op.getAnnotationBlock()!=null) {
			for (Annotation ann : op.getAnnotationBlock().getAnnotations()) {
				if (this.isMerlinAnnotation(ann)) {
					String val = this.getAnnValue(ann, op, module);
					if (this.isOverride(ann)) {
						m.setOverride(this.getOverrideKind(val));
						if (m.getOverride()==null) {
							System.err.println("[merlin] EOL exporter: invalid override kind "+val+" setting default to "+OverrideKind.SUPER);
							m.setOverride(OverrideKind.SUPER);
						}
					}
					else {
						m.setOverride(this.getMergeKind(val));
						if (m.getOverride()==null) {
							System.err.println("[merlin] EOL exporter: invalid merge kind "+val+" setting default to "+OverrideKind.MERGE_AND);
							m.setOverride(OverrideKind.MERGE_AND);
						}
					}
				}
			}
		}
	}
	
	private String getAnnValue(Annotation ann, Operation op, EolModule module) {
		try {
			Object obj = ann.getValue(module.getContext());
			if (!(obj instanceof String)) return null;
			return (String)obj;
		} catch (EolRuntimeException e) {
			System.err.println("[merlin - Epsilon Exporter] Error parsing annotation of method "+op.getName()+" in file "+ann.getUri());
			return null;
		}
	}
	
	private boolean isMerlinAnnotation(Annotation ann) {
		String annName = ann.getName().toLowerCase(); 
		return annName.equals("override") || annName.equals("merge");
	}
	
	private boolean isOverride(Annotation ann) {
		String annName = ann.getName().toLowerCase(); 
		return annName.equals("override");
	}
	
	private OverrideKind getOverrideKind(String ob) {
		if (ob == null) return OverrideKind.SUPER;
		return OverrideKind.fromString((String)ob);
	}
	
	private OverrideKind getMergeKind(String ob) {
		if (ob == null) return OverrideKind.MERGE;
		return OverrideKind.fromString((String)ob);
	}
	
}
