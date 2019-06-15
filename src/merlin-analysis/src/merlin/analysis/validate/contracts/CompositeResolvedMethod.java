package merlin.analysis.validate.contracts;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import de.ovgu.featureide.fm.core.base.IFeature;
import de.ovgu.featureide.fm.core.configuration.Configuration;
import merlin.common.transformation.analysis.ResolvedMethod;

public class CompositeResolvedMethod {
	private List<ResolvedMethod> yes = new ArrayList<>();
	private List<ResolvedMethod> no  = new ArrayList<>();
	
	public void addYes (ResolvedMethod rm) { yes.add(rm); }
	public void addNo  (ResolvedMethod rm) { no .add(rm); }
	public ResolvedMethod yes (int i) { return i>=0 && i<=yes.size()? yes.get(i) : null; }
	public ResolvedMethod no  (int i) { return i>=0 && i<=no .size()? no .get(i) : null; }
	public List<ResolvedMethod> yes () { List<ResolvedMethod> rms = new ArrayList<>(); rms.addAll(yes); return rms; }
	public List<ResolvedMethod> no  () { List<ResolvedMethod> rms = new ArrayList<>(); rms.addAll(no);  return rms; }
	
	/**
	 * composite body of resolved methods 
	 */
	public String body(Map<ResolvedMethod, String> bodies) {
		String body = "";
		for (ResolvedMethod rm : yes) {
			String resolved_body = body(rm, bodies);				
			if (resolved_body != null) {
				if (rm==yes.get(0))                         body += resolved_body; // do not add merge operation to first method body 
				else if (rm.getOverrideKind().isOperator()) body += " " + rm.getOverrideKind().mergeOperation() + " " + resolved_body; 
				else                                        body += "->" + rm.getOverrideKind().mergeOperation() + "(" + resolved_body + ")"; 
			}
		}
		return body;
	}
		
	/**
	 * composite formula of resolved methods
	 */
	public String formula () {
		String formula = "";
		for (ResolvedMethod rm : yes) formula +=     "(" + formula(rm.getConfiguration()) + ") and ";
		for (ResolvedMethod rm : no)  formula += "not (" + formula(rm.getConfiguration()) + ") and ";
		int last = formula.lastIndexOf("and");
		if (last > 0) formula = formula.substring(0, last-1);
		return formula;
	}
	
	/**
	 * overrides
	 */
	public List<ResolvedMethod> overrides () {
		List<ResolvedMethod> overrides = new ArrayList<>();
		yes.forEach(rm -> overrides.addAll(rm.overrides()));
		return overrides;
	}
	
	/**
	 * toString of formula
	 */
	private String formula (Configuration configuration) {
		String formula = ""; 
		for (IFeature feature : configuration.getSelectedFeatures())   formula +=          "fm." + feature.getName() + " and ";
		for (IFeature feature : configuration.getUnSelectedFeatures()) formula += "not " + "fm." + feature.getName() + " and ";
		int last = formula.lastIndexOf("and");
		if (last > 0) formula = formula.substring(0, last-1);
		return formula;
	}
	
	/**
	 * body of method
	 */
	private String body (ResolvedMethod method, Map<ResolvedMethod, String> bodies) {
		return bodies.get(method);
	}
}