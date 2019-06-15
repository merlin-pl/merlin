package merlin.common.issues;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;

public class ValidationIssue {
	private IssueLevel level;
	private String issue;
	private EObject where;
	private boolean isStopping = false;
	
	public ValidationIssue(String issue, IssueLevel level, EObject where) {
		this.issue = issue;
		this.level = level;
		this.where = where;
	}
	
	public ValidationIssue(String issue, IssueLevel level, EObject where, boolean isStopping) {
		this.issue = issue;
		this.level = level;
		this.where = where;
		this.isStopping = isStopping;
	}
	
	public boolean isStopping() {
		return this.isStopping;
	}
	
	public IssueLevel getLevel() {
		return level;
	}

	public String getIssue() {
		return issue;
	}

	public EObject getWhere() {
		return where;
	}

	public String getWhereName() {
		if (where instanceof EClass)             return "class    " + ((EClass)  where).getName();
		if (where instanceof EPackage)           return "package  " + ((EPackage)where).getName();
		if (where instanceof EStructuralFeature) return "property " + (((EStructuralFeature)where).getEContainingClass().getName()) + "." + ((EStructuralFeature)where).getName();
		if (where instanceof ENamedElement)      return ((ENamedElement)where).getName();
		return "";
	}
	
	@Override
	public String toString() {
		return this.issue;
	}
	
	@Override 
	public boolean equals(Object o) {
		if (o==this) return true;
		if (!(o instanceof ValidationIssue)) return false;
		ValidationIssue vi = (ValidationIssue)o;
		return vi.issue.equals(this.issue);
	}
	
	@Override
	public int hashCode() {
		return this.issue.hashCode();
	}
}
