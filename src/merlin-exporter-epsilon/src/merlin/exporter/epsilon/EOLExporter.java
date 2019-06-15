package merlin.exporter.epsilon;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.epsilon.eol.EolModule;

import merlin.common.transformation.Method;

public class EOLExporter extends EpsilonExporter {

	private Map<Method, String> signatures = new LinkedHashMap<>();
	private Map<Method, Integer> indexes = new LinkedHashMap<>();
	
	public EOLExporter() {
//		System.out.println("[merlin] EOL exporter constructor");
	}
	
	public void init(List<List<Method>> merges) {
		this.signatures.clear();
		for (List<Method> lm : merges) {
			if (lm.size()>0) this.indexes.put(lm.get(0), 0);
		}
	}

	@Override
	public void mergeFiles(List<File> frags, List<Method> overrides, List<List<Method>> merges, String fileName, String importFile) {
		this.init(merges);
		try {
			PrintWriter outputFile = new PrintWriter(new File(fileName));
			outputFile.println(this.getImport(importFile));

			int overrideCounter = 0;
			for (File f : frags) {
				String str = this.readFile(f);
				str = this.overrideMethod(f, overrides, str, overrideCounter);
				str = this.mergeMethod(f, merges, str);
				outputFile.println(str);
				overrideCounter++;
			}
			this.createMergedMethods(outputFile, merges);
			outputFile.close();
		} catch (FileNotFoundException e) {
			System.err.println("[merlin] could not write in "+fileName);
		}		
	}

	private void createMergedMethods(PrintWriter outputFile, List<List<Method>> merges) {
		outputFile.println("// ---------------------------- merged method calls\n\n");
		for (Method m : this.signatures.keySet()) {
			if (this.indexes.get(m)<=1) continue;
			outputFile.println("// ---- merged with "+m.getOverride().mergeOperation());
			outputFile.println(this.signatures.get(m));
			outputFile.println("{");
			outputFile.print("   return ");
			String aux = "";
			for (int i = 0; i < this.indexes.get(m); i++) {
				if (i>0) {
					if (m.getOverride().isOperator())
						outputFile.print(" "+m.getOverride().mergeOperation()+" ");
					else {
						aux += "."+m.getOverride().mergeOperation()+"(";
						outputFile.print("."+m.getOverride().mergeOperation()+"(");
					}
				}
				aux += "self."+this.getMethodCall(i, m);
				outputFile.print("self."+this.getMethodCall(i, m));
				if (i>0 && !m.getOverride().isOperator()) {
					outputFile.print(")");
					aux += ")";
				}
			}
			outputFile.println(";");
			outputFile.println("}");
		}
	}

	private String getMethodCall(int idx, Method m) {
		// TODO: pass parameters
		List<String> parameterName = this.getParameterNames(this.signatures.get(m));
		String result = m.getMethodName()+"__merged_"+idx+"(";
		boolean first = true;
		for (String par : parameterName) {
			if (!first) result+=", ";
			result+=par;
			first = false;
		}
		return result+")";
	}

	private List<String> getParameterNames(String signature) {
		List<String> result = new ArrayList<>();
		int idx = signature.indexOf("(");
		signature = signature.substring(idx+1);
		idx = signature.indexOf(":");
		while (idx != -1) {
			String param = signature.substring(0, idx);
			if (!param.contains(")"))		// otherwise we jumped out to the result specification
				result.add(param.trim());
			signature = signature.substring(idx+1);
			idx = signature.indexOf(",");
			if (idx == -1 ) return result;
			signature = signature.substring(idx+1);
			idx = signature.indexOf(":");
		}
		return result;
	}

	private String mergeMethod(File f, List<List<Method>> merges, String str) {
		for (List<Method> lm : merges) {
			if (lm.size()<=1) continue;
			for (Method m : lm ) {
				if (m.getFile().equals(f)) {
					int counter = this.indexes.get(m);
					this.signatures.put(m, this.getSignature(m, str));
					str = str.replaceFirst("operation\\s+"+m.getClassName()+"\\s+"+m.getMethodName(), 
							                 "// [merlin] DEBUG: merged\n"
							                 + "operation "+m.getClassName()+" "+m.getMethodName()+"__merged_"+counter);
					counter++;
					this.indexes.put(m, counter);
				}
			}
		}
		return str;
	}

	private String getSignature(Method m, String str) {
		Pattern signre = Pattern.compile("operation\\s+"+m.getClassName()+"\\s+"+m.getMethodName()+"\\s*[(]");
		Matcher matcher = signre.matcher(str);
		if (!matcher.find()) return null;
		int idx = matcher.start();
		int end = str.indexOf("{", idx);
		return str.substring(idx, end);
	}

	private String overrideMethod(File f, List<Method> overrides, String code, int counter) {
		for (Method m : overrides) {
			if (m.getFile().equals(f))
				code = code.replaceFirst("operation\\s+"+m.getClassName()+"\\s+"+m.getMethodName(), 
						                 "// [merlin] DEBUG: overriden from default configuration\n"
						                 + "operation "+m.getClassName()+" "+m.getMethodName()+"__deactivated_"+counter);
		}
		return code;
	}

	@Override
	public String name() {
		return "EOL";
	}

	@Override
	public String extension() {
		return ".eol";
	}

	@Override
	public List<Method> getMethods(File f)  {
		// returns the list of methods in this file
		EolModule module = new EolModule();
		return this.getMethods(f, module);
	}

}
