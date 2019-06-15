package merlin.analysis.validate.annotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;

import merlin.common.analysis.FeatureSolver;
import merlin.common.annotations.MerlinAnnotationStructure;
import merlin.common.annotations.modifiers.Modifiers;
import merlin.common.features.IFeatureProvider;
import merlin.common.issues.IssueLevel;
import merlin.common.issues.ValidationIssue;
import merlin.common.utils.MerlinAnnotationUtils;

public class InheritanceCyclesCheck extends AnnotationCheck {

	public InheritanceCyclesCheck(IFeatureProvider pr) {
		super(pr);
	}
	
	public Class<?> appliesAt() {
		return EPackage.class;
	}
	
	public List<ValidationIssue> check(EObject obj, boolean existingErrors) {
		List<ValidationIssue> issues = new ArrayList<>();
		
		if (!(obj instanceof EPackage)) return issues;
		EPackage root = (EPackage)obj;
		
		List<List<EClass>> cycles = getAllCycles(root);	// gets all cycles
		
		for (List<EClass> cycle : cycles) {
			String cond = this.getCondition(cycle);
			System.out.println("[MERLIN] Cycle "+cycle);
			System.out.println("[MERLIN] Condition "+cond);
			// Now this has to be unsat! (otherwise we have a cycle)
			FeatureSolver fs = new FeatureSolver(cycle.get(0), this.provider.getFeatureModelFile());
			fs.addConstraint("not ("+cond+")");
			if (fs.isSat()) {				
				issues.add(new ValidationIssue(	"An inheritance cycle "+this.cycleToString(cycle)+
												" apperas in configuration "+fs.getModel(true)
						  , IssueLevel.ERROR, cycle.get(0)));
			}		
		}
		
				
		return issues;
	}
	
	private String cycleToString(List<EClass> cycle) {
		String str = "[";
		boolean first = true;
		for (EClass cl : cycle) {
			if (!first) str += ", ";
			str+=cl.getName();			
			first = false;
		}
		return str+"]";
	}

	public String getCondition(List<EClass> path) {
		if (path.size()==0 || path.size()==1) return "";
		EClass pointer = path.get(0);
		String current = "";
		for (EClass cls : path.subList(1, path.size())) {
			String condition = this.getInheritanceCondition(pointer, cls);
			if (!"true".equals(condition)) {
				if (current.equals(""))
					current = "("+condition+")";
				else
					current += " and ("+condition+")";
			}			
			pointer = cls;
		}
		return current;
	}
	
	private String getInheritanceCondition(EClass source, EClass cls) {
		List<EAnnotation> modifiers = MerlinAnnotationUtils.getModifiers(source);
		for (EAnnotation a : modifiers) {
			if (a.getDetails().keySet().contains(Modifiers.REDUCE_MODIFIER)) {
				String[] classes = a.getDetails().get(Modifiers.REDUCE_MODIFIER).split("\\s+");
				if (Arrays.asList(classes).contains(cls.getName())) {
					return "not ("+a.getDetails().get(MerlinAnnotationStructure.PRESENCE_CONDITION)+")";
				}
			}
			if (a.getDetails().keySet().contains(Modifiers.EXTENDS_MODIFIER)) {
				String[] classes = a.getDetails().get(Modifiers.EXTENDS_MODIFIER).split("\\s+");
				if (Arrays.asList(classes).contains(cls.getName())) {
					return a.getDetails().get(MerlinAnnotationStructure.PRESENCE_CONDITION);
				}
			}
		}
		return "true";
	}
	
	private List<List<EClass>> getAllCycles(EPackage source) {
		List<List<EClass>> result = new ArrayList<List<EClass>>();
		
		for (EObject obj : source.eContents()) {
			if (!(obj instanceof EClass)) continue;
			EClass src = (EClass)obj;
			if (this.isInSomeCycle(result, src)) continue;
			List<EClass> visited = new ArrayList<EClass>();
			visited.add(src);
			List<List<EClass>> cycles = new ArrayList<List<EClass>>();
			this.findCycles(src, src, visited, cycles);
			result.addAll(cycles);
		}
		
		return result;
	}

	private void findCycles(EClass src, EClass current, List<EClass> visited, List<List<EClass>> cycles) {
		if (src == current && visited.size()>1) { // cycle found
			List<EClass> copy = new ArrayList<>();
			copy.addAll(visited);
			cycles.add(copy);
			return;
		}
		List<EClass> supers = new ArrayList<EClass>();
		supers.addAll(current.getESuperTypes());
		supers.addAll(this.getExtends(current));
		for (EClass cl : supers) {
			if (visited.contains(cl) && cl!=src) continue;
			visited.add(cl);
			this.findCycles(src, cl, visited, cycles);
			visited.remove(cl);
		}
	}

	private List<EClass> getExtends(EClass source) {
		List<EClass> xtnds = new ArrayList<>(); 
		List<EAnnotation> modifiers = MerlinAnnotationUtils.getModifiers(source);
		for (EAnnotation a : modifiers) {
			if (a.getDetails().keySet().contains(Modifiers.EXTENDS_MODIFIER)) {
				String[] classes = a.getDetails().get(Modifiers.EXTENDS_MODIFIER).split("\\s+");
				for (String cls : classes) {
					EClassifier klass = source.getEPackage().getEClassifier(cls);
					if (klass instanceof EClass) 
						xtnds.add((EClass) klass);
				}
			}
		}
		return xtnds;
	}

	private boolean isInSomeCycle(List<List<EClass>> cycles, EClass node) {
		for (List<EClass> cycle : cycles) 
			if (cycle.contains(node)) return true;
		
		return false;
	}

}
