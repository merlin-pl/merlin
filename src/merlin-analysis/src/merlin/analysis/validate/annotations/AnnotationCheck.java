package merlin.analysis.validate.annotations;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EObject;

import merlin.common.annotations.presenceCondition.ConditionParser;
import merlin.common.features.IFeatureProvider;
import merlin.common.issues.ValidationIssue;

public abstract class AnnotationCheck {
	protected static Map<Class<?>, Set<AnnotationCheck>> checks = new LinkedHashMap<>();
	protected IFeatureProvider provider;
	
	public static Set<AnnotationCheck> getChecksFor(Class<?> cl) {
		Set<AnnotationCheck> cks = new LinkedHashSet<>();
		for (Class<?> cls : AnnotationCheck.checks.keySet()) {
			try {
				cl.asSubclass(cls);
				// compatible
				cks.addAll(AnnotationCheck.checks.get(cls));
			} catch (ClassCastException ce) {
				// not compatible
			}
		}
		return cks;
	}
	
	public static void reset() { checks.clear(); }
	
	public AnnotationCheck(IFeatureProvider pr) {
		if (checks.get(this.appliesAt())==null) checks.put(this.appliesAt(), new LinkedHashSet<>());
		checks.get(this.appliesAt()).add(this);
		this.provider = pr;
	}
	
	public Class<?> appliesAt() {
		return EClass.class;
	}
	
	public List<ValidationIssue> check(EObject obj, boolean existingErrors) {
		return Collections.emptyList();
	}
	
	@Override
	public boolean equals(Object o) {
		if (!(o instanceof AnnotationCheck)) return false;
		if (o==this) return true;
		AnnotationCheck ac = (AnnotationCheck)o;
		return ac.getClass().equals(this.getClass());
	}
	
	@Override
	public int hashCode() {
		return this.getClass().hashCode();
	}
	
	protected Collection<ValidationIssue> checkCondition(String condition, ENamedElement cls) {
		ConditionParser cp = new ConditionParser(condition, cls, this.provider);
		return cp.parse();
	}
	
}
