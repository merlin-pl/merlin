package merlin.common.transformation.analysis;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import merlin.common.concepts.ConfigurationFragmentPool;
import merlin.common.concepts.SelectedConcepts;
import merlin.common.transformation.Method;
import merlin.common.utils.EMFUtils;
import merlin.common.utils.OclToStringVisitor;

public class OCLOperationsBodyCache {
	
	public enum ConfigurationDirection { 
		UNDER,	// to get methods of configs under this one (more specific -> used when config is a partial configuration) 
		OVER 	// to get methods of configs over this one (more general -> used when config is a final, specific configuration)
	}  
	private ConfigurationDirection direction;
	private Configuration config;
	private EPackage root;
	private Map<ResolvedMethod, String> bodies = new LinkedHashMap<>();
	private Map<List<File>, List<Map<OCL, File>>> oclEnvs;
	private List<String> oclErrors = new ArrayList<>();
	
	public OCLOperationsBodyCache(Configuration cfg, EPackage root, ConfigurationDirection cd) {
		this.config = cfg;
		this.root = root;
		this.direction = cd;
		
		oclEnvs = this.readCompatibleOCLDocuments(cfg);
		this.retrieveOperationBodies(oclEnvs);
	}
	
	public Configuration getConfig() {
		return this.config;
	}
	
	public String getBody(Method m) {
		for (ResolvedMethod rm : this.bodies.keySet()) {
			if (rm.getMethod().identical(m)) return this.bodies.get(rm);
		}
		return "";
	}
	
	public Map<List<File>, List<Map<OCL, File>>> oclEnvs() {
		return this.oclEnvs;
	}
	
	private Map<List<File>, List<Map<OCL, File>>> readCompatibleOCLDocuments(Configuration cfg ) {
		Map<List<File>, List<Map<OCL, File>>> envs = new LinkedHashMap<>();
		
		EPackage.Registry registry = new EPackageRegistryImpl();
		registry.put(root.getNsURI(), root);
		EcoreEnvironmentFactory envFactory = new EcoreEnvironmentFactory(registry);
				
		// Now look for all concepts which contribute somehow to OCL
		for (ConfigurationFragmentPool pool : SelectedConcepts.getConfigs()) {	
			List<File> oclFrags;
			if (this.direction.equals(ConfigurationDirection.OVER))
				oclFrags = pool.getFragmentsCompatibleWith(cfg);
			else
				oclFrags = pool.getFragmentsUnder(cfg);
			//List<Method> mths   = this.mr.getOverridenMethods(oclFrags);
			envs.put(oclFrags, new ArrayList<Map<OCL, File>>());
			for (File f : oclFrags) {
				if (!f.getName().endsWith(".ocl")) continue;
				OCL ocl = OCL.newInstance(envFactory);
				ocl = readOCLDocument(f, ocl);			
				envs.get(oclFrags).add(Collections.singletonMap(ocl, f));
			}
		}
		return envs;
	}
	
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
			
	private void retrieveOperationBodies(Map<List<File>, List<Map<OCL, File>>> oclEnvs) {
		for (List<File> frgs : oclEnvs.keySet()) {
			for (Map<OCL, File> pair : oclEnvs.get(frgs)) {
				for (OCL ocl : pair.keySet()) {
					File file = pair.get(ocl);
					List<Constraint> constr = ocl.getConstraints();
					for (Constraint c : constr) {
						EOperation context= EMFUtils.getOperation(c);
						if (context != null ) {
							EClass owner = (EClass)context.eContainer();
							Method thisM = new Method (owner.getName(), context.getName(), file);
							ResolvedMethod rm = new ResolvedMethod(thisM, this.getConfigOfPath(file));
							OclToStringVisitor visitor = new OclToStringVisitor(ocl.getEnvironment());
							String newBody = c.getSpecification().getBodyExpression().accept(visitor);
							this.bodies.put(rm, newBody);
						}
					}
				}
			}
		}
	}
	
	private Configuration getConfigOfPath(File file) {
		for (ConfigurationFragmentPool cfp: SelectedConcepts.getConfigs()) {
			List<Configuration> cfgs = cfp.getConfigs(file);
			if (cfgs.size()>0) return cfgs.get(0);
		}
		return null;
	}

	public Map<ResolvedMethod, String> getMethodBodies() {		
		return this.bodies;
	}

	public List<String> oclErrors() {
		return this.oclErrors;
	}
}
