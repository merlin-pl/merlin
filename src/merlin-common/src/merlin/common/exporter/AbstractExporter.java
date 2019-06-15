package merlin.common.exporter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import org.eclipse.emf.ecore.EPackage;

import merlin.common.transformation.Method;

public abstract class AbstractExporter {
	public abstract void export(EPackage p, String fname);
	public abstract String name();
	public abstract String extension();
	public abstract List<Method> getMethods(File f);
	
	/**
	 * Beware: we do not do anything with the overriden methods here!
	 * @param frags
	 * @param overrides
	 * @param merged 
	 * @param fileName
	 * @param importFile
	 */
	public void mergeFiles(List<File> frags, List<Method> overrides, List<List<Method>> merged, String fileName, String importFile) {
		try {
			PrintWriter outputFile = new PrintWriter(new File(fileName));
			outputFile.println(this.getImport(importFile));
			for (File f : frags) {
				String str = this.readFile(f);
				outputFile.println(str);
			}
			outputFile.close();
		} catch (FileNotFoundException e) {
			System.err.println("[merlin] could not write in "+fileName);
		}
	}
	
	public abstract String getImport(String importFile);
	
	protected String readFile(File f) {
		
		try (BufferedReader br = new BufferedReader(new FileReader(f))){
			StringBuilder sb = new StringBuilder();
			String line = br.readLine();

			while (line != null) {
				sb.append(line);
				sb.append(System.lineSeparator());
				line = br.readLine();
			}
			return sb.toString();
		}
		catch (IOException e) {
			System.err.println("[merlin] could not read from file "+f);
			return "-- [merlin] IO Error in file "+f+"\n";
		} 
	}
}
