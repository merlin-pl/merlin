package merlin.analysis.validate.annotations;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;

import merlin.common.annotations.MerlinAnnotationStructure;
import merlin.common.features.IFeatureProvider;
import merlin.common.issues.IssueLevel;
import merlin.common.issues.ValidationIssue;

public class FeatureModelAnnotationCheck extends AnnotationCheck{
	private IProject project;
	private static final String DEFAULT_FILE_NAME = "model.xml";
	
	public FeatureModelAnnotationCheck(IFeatureProvider prov, IProject project) {
		super(prov);
		this.project = project; // project where the feature model will be looked for
	}
	
	@Override public Class<?> appliesAt() {
		return EPackage.class;
	}
	
	@Override public List<ValidationIssue> check(EObject obj, boolean existingErrors) {
		List<ValidationIssue> list = new ArrayList<>();
		if (!(obj instanceof EPackage)) return list;
		EPackage pck = (EPackage)obj;
		EAnnotation ann = pck.getEAnnotation(MerlinAnnotationStructure.FEATURE_ANNOTATION);
		if (ann==null) {
			list.add(new ValidationIssue("Package "+pck.getName()+" lacks feature model annotation", 
										 IssueLevel.ERROR, 
										 pck,
										 true));	// Stopping error
			this.setFeatureFile(pck, DEFAULT_FILE_NAME);
			return list;
		}
		String fmodel = ann.getDetails().get(MerlinAnnotationStructure.FEATURE_FILE);
		if (fmodel == null) {
			list.add(new ValidationIssue("Package "+pck.getName()+" lacks feature model annotation", 
					 IssueLevel.ERROR, 
					 pck,
					 true));	// Stopping error
			this.setFeatureFile(pck, DEFAULT_FILE_NAME);
			return list;
		}
		list.addAll(this.setFeatureFile(pck, fmodel));
		//System.out.println("[merlin] Feature model check successful on "+pck.getName()+"!");
		return list;
	}

	private List<ValidationIssue> setFeatureFile(EPackage pck, String fmodel) {
		List<ValidationIssue> list = new ArrayList<>();
		IFile featureModel = this.fileExists(fmodel);
		if (featureModel == null) {
			list.add(new ValidationIssue("Feature model "+fmodel+" not found", 
					 IssueLevel.ERROR, 
					 pck,
					 true));	// Stopping error
			return list;
		}
		this.provider.setFeatureModelFile(featureModel);
		return list;
	}

	private IFile fileExists(String fmodel) {
		IFile f = null;
		if ((f = project.getFile(fmodel))!=null) {
			return f.exists() ? f : null; 
		}		
		return null;
	}	
}
