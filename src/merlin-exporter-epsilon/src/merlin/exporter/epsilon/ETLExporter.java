package merlin.exporter.epsilon;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.epsilon.etl.EtlModule;
import org.eclipse.epsilon.etl.dom.TransformationRule;

import merlin.common.transformation.Method;
import merlin.common.transformation.Rule;

public class ETLExporter extends EpsilonExporter {

	@Override
	public String name() {
		return "ETL";
	}

	@Override
	public String extension() {
		return ".etl";
	}

	@Override
	public List<Method> getMethods(File f) {
		// returns the list of methods in this file
		List<Method> methods = new ArrayList<>();
		EtlModule module = new EtlModule();

		methods.addAll(this.getMethods(f, module));
		
		for (TransformationRule tr : module.getDeclaredTransformationRules()) {
			Rule m = new Rule(null, tr.getName(), f);
			methods.add(m);
		}

		return methods;
	}
	
	@Override
	public void mergeFiles(List<File> frags, List<Method> overrides, List<List<Method>> merges, String fileName, String importFile) {
		try {
			PrintWriter outputFile = new PrintWriter(new File(fileName));
			outputFile.println(this.getImport(importFile));
			String code = this.readFile(frags.get(0));
			for (Method m : overrides) {
				if (m instanceof Rule) {
					code = code.replaceFirst("rule\\s+"+m.getMethodName(), 
			                 "// [merlin] DEBUG: "+m.getMethodName()+" overriden from default configuration\n"
			                 + "/*rule "+m.getMethodName()+"__deactivated");
					int ruleInit = code.indexOf("/*rule "+m.getMethodName()+"__deactivated");
					int ruleEnd  = code.indexOf("}", ruleInit);
					String newCode = code.substring(0, ruleInit) + code.substring(ruleEnd+1);
					code = newCode;
				}
				else if (m instanceof Method)
					code = code.replaceFirst("operation\\s+"+m.getClassName()+"\\s+"+m.getMethodName(), 
						                 "// [merlin] DEBUG: overriden from default configuration\n"
						                 + "operation "+m.getClassName()+" "+m.getMethodName()+"__deactivated");
			}
			outputFile.println(code);
			for (File f : frags.subList(1, frags.size())) {
				String str = this.readFile(f);
				outputFile.println(str);
			}
			outputFile.close();
		} catch (FileNotFoundException e) {
			System.err.println("[merlin] could not write in "+fileName);
		}		
	}
}
