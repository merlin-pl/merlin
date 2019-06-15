package merlin.common.annotations.formulas;

import org.prop4j.Node;

import merlin.common.annotations.formulas.cnf.CNFFormula;
import merlin.common.annotations.presenceCondition.Operator;
import merlin.common.features.IFeatureProvider;

public abstract class BoolFormula {
	public abstract CNFFormula toCNF();
	public abstract boolean eval(IFeatureProvider fp);
	public Operator toOperator() {
		return null;
	}
	public abstract Node toFeatureIDENode();
}
