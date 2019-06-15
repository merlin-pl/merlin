package merlin.analysis.validate.annotations;

import java.io.IOException;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.emf.common.util.EMap;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;

import de.ovgu.featureide.fm.core.base.IFeature;
import merlin.common.annotations.MerlinAnnotationStructure;
import merlin.common.features.IFeatureProvider;
import merlin.common.issues.IssueLevel;
import merlin.common.issues.ValidationIssue;
import merlin.common.utils.EMFUtils;
import merlin.common.utils.MerlinAnnotationUtils;

public class UnusedFeaturesCheck extends AnnotationCheck {

	public UnusedFeaturesCheck(IFeatureProvider pr) {
		super(pr);
	}

	public Class<?> appliesAt() {
		return EPackage.class;
	}
	
	@Override
	public List<ValidationIssue> check(EObject obj, boolean existingErrors) {
		List<ValidationIssue> warnings = new ArrayList<>();
		
		if (!(obj instanceof EPackage)) return warnings;
		
		EPackage pack = (EPackage)obj;
		
		Set<String> unusedFeatureNames = getUnusedFeatureNames(pack, true);
		
		String feats = "";
		for (String feat : unusedFeatureNames) 
			feats+=" "+feat;
		if (!feats.equals("")) warnings.add(new ValidationIssue("Unused features:"+feats, IssueLevel.WARNING, pack));
		
		return warnings;
	}
	
	// onlyLeaves: true to return only leaf features, false to return any unused feature
	public Set<String> getUnusedFeatureNames(EPackage pack, boolean onlyLeaves) {
		Set<String> usedFeatures   = this.getFeatureNames(pack);
		usedFeatures.addAll(this.provider.getFeaturesInConstraints().stream().map(f -> f.getName()).collect(Collectors.toList()));
		Set<String> unusedFeatures = new LinkedHashSet<>();		
		for (IFeature feature : this.provider.getFeatures()) 
			if (!onlyLeaves || !feature.getStructure().hasChildren())
				if (!usedFeatures.contains(feature.getName()))
					unusedFeatures.add(feature.getName());
		return unusedFeatures;
	}

	private Set<String> getFeatureNames(EPackage pack) {
		Set<String> foundFeatureNames = new LinkedHashSet<>();
		
		for (EClassifier cl : pack.getEClassifiers()) {
			String pc = MerlinAnnotationUtils.getPresenceCondition(cl);
			if (pc!=null) foundFeatureNames.addAll(this.getFeaturesInCondition(pc));
			
			if (cl instanceof EClass) {
				// Now the presence condition of OCL invariants
				EClass clss = (EClass)cl;
				
				addModifierFeatures(foundFeatureNames, cl);
				
				EMap<String, String> invariants = EMFUtils.getInvariants(cl);
				for (String invName : invariants.keySet()) {
					pc = MerlinAnnotationUtils.getInvariantPresenceCondition(clss, invName);
					if (pc!=null) foundFeatureNames.addAll(this.getFeaturesInCondition(pc));
				}
						
				for (EStructuralFeature sf : clss.getEStructuralFeatures()) {
					pc = MerlinAnnotationUtils.getPresenceCondition(sf);
					if (pc!=null) foundFeatureNames.addAll(this.getFeaturesInCondition(pc));
					
					// Now get the features of the modifier conditions
					addModifierFeatures(foundFeatureNames, sf);
				}
			}
		}
		return foundFeatureNames;
	}

	private void addModifierFeatures(Set<String> foundFeatureNames, ENamedElement sf) {
		EAnnotation an = sf.getEAnnotation(MerlinAnnotationStructure.MODIFIER_ANNOTATION);
		if (an!=null) {
			String mc = an.getDetails().get(MerlinAnnotationStructure.MODIFIER_CONDITION);
			if (mc != null) foundFeatureNames.addAll(this.getFeaturesInCondition(mc));
		}
	}

	private Collection<? extends String> getFeaturesInCondition(String pc) {
		Set<String> features = new LinkedHashSet<>();
		StreamTokenizer st = new StreamTokenizer(new StringReader(pc));
		st.wordChars('_', '_');
		try {
			while(st.nextToken() != StreamTokenizer.TT_EOF){
				if (st.ttype == StreamTokenizer.TT_WORD&& this.provider.isValidFeature(st.sval)) 
					features.add(st.sval);				
			}
		} catch (IOException e) {
			System.err.println("[merlin] Error parsing condition "+pc);			
		}
		return features;
	}
}
