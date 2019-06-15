package merlin.concepts.concept;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.internal.resources.File;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.emf.common.util.EMap;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EOperation;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.resource.Resource;

import merlin.common.annotations.MerlinAnnotationStructure;
import merlin.common.utils.EMFUtils;
import merlin.common.utils.FileUtils;
import merlin.common.utils.MerlinAnnotationUtils;

public class ConceptSynthesizer {
	@SuppressWarnings("restriction")
	public void generateConcept(File selected) {
		IProject project = selected.getProject();
		IFolder folder = null;
		try {
			folder = this.createFolders(project, "bindings", "structure");
		} catch (CoreException e) {
			System.err.println("[merlin] could not create folder in: "+project.getLocation().toOSString()+"/bindings/structure");
			return;
		}	
		List<EPackage> packages = EMFUtils.readEcore(FileUtils.getIFile(selected));
		System.out.println("Packages = "+packages);
		String conceptName = selected.getName();
		conceptName = conceptName.replaceFirst(".ecore", "Concept.ecore");
		this.synthesizeConcept(folder, conceptName, packages);
	}
	
	private void synthesizeConcept(IFolder folder, String fileName, List<EPackage> packages) {
		// Basically, we just remove those classifiers with presence condition not equal (syntactically) to true
		for (EPackage p : packages) {
			if (p.getEAnnotation(MerlinAnnotationStructure.FEATURE_ANNOTATION) != null) {
				List<EClassifier> cl2keep = new ArrayList<>();
				for (EClassifier cl : p.getEClassifiers()) {
					EAnnotation an = MerlinAnnotationUtils.getClassMerlinAnnotation(cl);
					if (an==null || "true".equals(an.getDetails().get(MerlinAnnotationStructure.PRESENCE_CONDITION))) {
						cl2keep.add(cl);
						this.handleFeatures(cl);
						this.convertToOperations(cl);
						this.handleOCLConstraints(cl);
					}
				}
				p.getEClassifiers().retainAll(cl2keep);								
			}
			MerlinAnnotationUtils.removeAllMerlinAnnotations(p);
		}
		this.generateDefaultBinding(folder, packages);
		for (EPackage p : packages) 
			p.setNsURI(p.getNsURI()+"concept");
		
		saveConcept(folder, fileName, packages);
	}

	// retains OCL constraints with presence condition equal to true
	private void handleOCLConstraints(EClassifier cl) {
		if (!(cl instanceof EClass)) return;
		EMap<String, String> constrs = EMFUtils.getInvariants(cl);
		List<String> invToDelete = new ArrayList<>();		
		
		for (String invName : constrs.keySet()) {
			EAnnotation an = MerlinAnnotationUtils.getInvariantMerlinAnnotation(cl, invName);
			if (an!=null && !"true".equals(an.getDetails().get(MerlinAnnotationStructure.PRESENCE_CONDITION)))
				invToDelete.add(invName);
		}
		
		EMFUtils.removeInvariants(invToDelete, (EClass)cl);
	}

	private void generateDefaultBinding(IFolder folder, List<EPackage> packages) {
		IProject prj = folder.getProject();
		IFolder  fld = null;
		try {
			fld = this.createFolders(prj, "bindings", "structure");	
		} catch (CoreException e) {
			System.out.println("[merlin] Could not create folder bindings in project "+prj.getName());
			return;
		}
		IFile bindingFile = fld.getFile("default.ocl");
		PrintWriter writer;
		try {
			writer = new PrintWriter(bindingFile.getLocation().toOSString(), "UTF-8");
		} catch (FileNotFoundException | UnsupportedEncodingException e) {
			System.out.println("[merlin] Could not create default binding file in project "+prj.getName());
			return;
		}
		synthesizeBinding(packages.get(0), writer);	// TODO: MMPL with more than one package
		writer.close();
	}

	private void synthesizeBinding(EPackage pack, PrintWriter writer) {
		writer.println("-- @presenceCondition = true");
		writer.println("import '"+pack.getNsURI()+"'\n");
		writer.println("package "+pack.getName());
		
		for (EClassifier cl : pack.getEClassifiers()) {
			if (!(cl instanceof EClass)) continue;
			EClass cls = (EClass)cl;
			if (hasStructuralOperations(cls))
				writer.println("context "+cls.getName()+"\n");
			for (EOperation op : cls.getEOperations()) {
				EAnnotation an = op.getEAnnotation(MerlinAnnotationStructure.ANNOTATION_NAME);
				if (an!=null && an.getDetails().containsValue("structure")) {
					writer.println("   def: "+op.getName()+"() : "+this.writeType(op)+" = ");
					writer.print("    self."+op.getName());
					if      (this.isSet(op))        writer.println("->asSet()");
					else if (this.isOrderedSet(op)) writer.println("->asOrderedSet()");
					else writer.println();
					//this.printInvariant(op, writer);
				}
			}
		}
		writer.println("endpackage");
	}

	/*private void printInvariant(EOperation op, PrintWriter writer) {
		if (op.getLowerBound()!=0) {
			writer.println("   inv "+op.getName()+"LowerBound : ");
			if (! op.isMany())
				writer.println("       self."+op.getName()+".isOclUndefined()");
			else
				writer.println("       self."+op.getName()+".size()>"+op.getLowerBound());
		}
		if (op.getUpperBound()!=-1) {
			writer.println("   inv "+op.getName()+"LowerBound : ");
			
		}
	}*/

	private boolean hasStructuralOperations(EClass cls) {
		return cls.getEOperations().
					stream().
					anyMatch(op -> {
						EAnnotation an = op.getEAnnotation(MerlinAnnotationStructure.ANNOTATION_NAME);
						return (an!=null && an.getDetails().containsValue("structure")); 
					});
	}

	private String writeType(EOperation oper) {
		String type = "";
		
		if (this.isSet(oper)) type+="Set(";
		if (this.isOrderedSet(oper)) type+="OrderedSet(";
		else if (this.isSequence(oper)) type+="Sequence(";
		else if (this.isBag(oper)) type+="Bag(";
		
		switch (oper.getEType().getName()) {
		case "EString" : type+= "String"; break;
		case "EInteger" : type+= "Integer"; break;
		case "EBoolean" : type+= "Boolean"; break;
		default: type+= oper.getEType().getName();	// TODO: other data types!
		}
		
		if (oper.isMany()) type+=")";
		return type;
	}

	private boolean isSet(EOperation oper) {
		return oper.isMany() && oper.isUnique() && !oper.isOrdered();
	}
	
	private boolean isOrderedSet(EOperation oper) {
		return oper.isMany() && oper.isUnique() && oper.isOrdered();
	}
	
	private boolean isSequence(EOperation oper) {
		return oper.isMany() && oper.isOrdered();
	}
	
	private boolean isBag(EOperation oper) {
		return oper.isMany() && !isSet(oper) && !isSequence(oper);
	}

	private void convertToOperations(EClassifier cl) {
		// Converts all features into operations
		if (!(cl instanceof EClass)) return;
		EClass cls = (EClass)cl;
		for (EStructuralFeature sf : cls.getEStructuralFeatures()) {
			EOperation op = EcoreFactory.eINSTANCE.createEOperation();
			op.setName(sf.getName());
			op.setEType(sf.getEType());
			op.setLowerBound(sf.getLowerBound());
			op.setUpperBound(sf.getUpperBound());
			op.setUnique(sf.isUnique());
			op.setOrdered(sf.isOrdered());
			cls.getEOperations().add(op);
			EAnnotation an = EcoreFactory.eINSTANCE.createEAnnotation();
			an.setSource(MerlinAnnotationStructure.ANNOTATION_NAME);
			an.getDetails().put("source", "structure");
			op.getEAnnotations().add(an);
			// TODO: Generate pre and post conditions
		}
		cls.getEStructuralFeatures().clear();
	}

	private void handleFeatures(EClassifier cl) {
		if (cl instanceof EClass){
			EAnnotation an;
			List<EStructuralFeature> feat2keep = new ArrayList<>();
			for (EStructuralFeature sf : ((EClass)cl).getEStructuralFeatures()) {
				an = sf.getEAnnotation(MerlinAnnotationStructure.ANNOTATION_NAME);
				if (an == null || "true".equals(an.getDetails().get(MerlinAnnotationStructure.PRESENCE_CONDITION))) 
					feat2keep.add(sf);
			}
			((EClass)cl).getEStructuralFeatures().retainAll(feat2keep);
		}
	}

	private void saveConcept(IFolder folder, String fileName, List<EPackage> packages) {
		URI path = URI.createFileURI(new java.io.File(folder.getLocation().toOSString()+IPath.SEPARATOR+fileName).getAbsolutePath());
		Resource oldr = packages.get(0).eResource();
		Resource newr = oldr.getResourceSet().createResource(path);
		newr.getContents().addAll(oldr.getContents());
		try {
			newr.save(null);
		} catch (IOException e) {
			System.err.println("[merlin] Could not write concept to "+folder.getLocation().toOSString());
		}
	}
	
	// TODO: Move to utils?
	private IFolder createFolders(IProject prj, String... folders) throws CoreException {
		if (folders.length==0) return prj.getFolder(".");
		IFolder currentFolder = null;
		String folder = ".";
		for (String fld : folders) {
			folder += IPath.SEPARATOR+fld;
			currentFolder = prj.getFolder(folder);
			if (!currentFolder.exists())
				currentFolder.create(IResource.NONE, true, null);
		}
		return currentFolder;
	}
}
