package merlin.analysis.validate.properties;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import merlin.analysis.validate.properties.PropertyChecker.ProblemSpace;
import merlin.analysis.validate.properties.PropertyChecker.SolutionArity;
import merlin.common.issues.IssueLevel;
import merlin.common.issues.ValidationIssue;

public class PropertyResult {
	private String outputfolder = null;
	private SolutionArity arity = null;
	private ProblemSpace problem = null;
	private int solutions      = -1;
	private int solvings       = -1; // invocations to solver (may differ from #solutions when partial configurations are allowed)
	private List<String> errors = new ArrayList<>();
	
	public PropertyResult (SolutionArity arity, ProblemSpace problem) {
		setArity(arity);
		setProblem(problem);
	}
	
	public void setOutputfolder   (String outputfolder)  { this.outputfolder = outputfolder; }
	public void setArity          (SolutionArity arity)  { this.arity = arity; }
	public void setProblem        (ProblemSpace problem) { this.problem = problem; }
	public void setSolutions      (int solutions)        { this.solutions = solutions; }
	public void setSolvings       (int solvings)         { this.solvings = solvings; }
	public void addError          (String error)         { errors.add(error); }
	public void addError          (List<ValidationIssue> issues) { for (ValidationIssue issue : issues) if (issue.getLevel() == IssueLevel.ERROR) errors.add(issue.getIssue()); }
	
	public int     getSolutions() { return solutions; }
	public int     getSolvings()  { return solvings; }
	public boolean hasErrors()    { return !errors.isEmpty(); }
	public String  getErrors()    { return errors.stream().map(Object::toString).collect(Collectors.joining("\n")); }
	
	/**
	 * @return
	 */
	public String getSummary() {
		String summary  = "";		
		if (solutions > -1) {
			
			if (problem == ProblemSpace.EXISTS) {
				if      (solutions == 0)             summary = "No metamodel has instances satisfying the property.";
				else if (arity == SolutionArity.ONE) summary = "At least one metamodel has instances satisfying the property.";
				else                                 summary = solutions + " metamodels have instances that satisfy the property.";
			}	
			
			else if (problem == ProblemSpace.FORALL) {
				if (solutions == 0)                  summary = "There is no metamodel with all instances satisfying the property.";
				else if (arity == SolutionArity.ONE) summary = "There is at least 1 metamodel with all instances satisfying the property.";
				else                                 summary = "There are " + solutions + " metamodels with all instances satisfying the property.";
			}	
			
			else if (problem == ProblemSpace.NOTEXISTS) {
				if      (solutions == 0)             summary = "All metamodels have instances satisfying the property.";
				else if (arity == SolutionArity.ONE) summary = "At least 1 metamodel has no instances satisfying the property.";
				else                                 summary = solutions + " metamodels have no instances satisfying the property.";
			}	
			
			if (solutions>0 && outputfolder!=null) summary += "\nSee generated witness/es in folder " + outputfolder + ".";		
		}
		return summary;		
	}
}
