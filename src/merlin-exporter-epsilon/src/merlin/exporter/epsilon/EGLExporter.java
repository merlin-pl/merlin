package merlin.exporter.epsilon;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.epsilon.egl.EglFileGeneratingTemplateFactory;
import org.eclipse.epsilon.egl.EglTemplate;
import org.eclipse.epsilon.egl.exceptions.EglRuntimeException;
import org.eclipse.epsilon.egl.internal.EglModule;
import org.eclipse.epsilon.egl.internal.EglPreprocessorModule;
import org.eclipse.epsilon.eol.EolModule;
import org.eclipse.epsilon.eol.dom.Operation;
import org.eclipse.epsilon.eol.dom.TypeExpression;

import merlin.common.transformation.Method;

public class EGLExporter extends EOLExporter {

	public EGLExporter() {
//		System.out.println("[merlin] EGL exporter");
	}
	
	@Override
	public String name() {
		return "EGL";
	}

	@Override
	public String extension() {
		return ".egl";
	}

	protected String preExport() {
		return "[%";
	}
	
	protected String postExport() {
		return "%]";
	}
	
	@Override
	public String getImport(String importFile) {
		return "[%import '"+importFile+"';%]\n";
	}
	
	public List<Method> getMethods(File f, EolModule module)  {
		// returns the list of methods in this file
		List<Method> methods = new ArrayList<>();
		EglPreprocessorModule pm = ((EglModule)module).getPreprocessorModule();
		for (Operation op : pm.getDeclaredOperations()) {
			//EolType contextClass = op.getContextType(module.getContext());
			TypeExpression contextClass = op.getContextTypeExpression();
			Method m;
			if (contextClass!=null)
				m = new Method(contextClass.getName(), op.getName(), f);
			else
				m = new Method(null, op.getName(), f);
			methods.add(m);
		}
		return methods;
	}
	
	@Override
	public List<Method> getMethods(File f) {
		List<Method> methods = new ArrayList<>();
		EglFileGeneratingTemplateFactory fact = new EglFileGeneratingTemplateFactory();
		try {
			EglTemplate template = fact.load(f);
			return this.getMethods(f, (EglModule)template.getModule());
		} catch (EglRuntimeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return methods;
	}

}
