package merlin.common.annotations.formulas;

import org.prop4j.Node;

import merlin.common.annotations.formulas.cnf.CNFFormula;
import merlin.common.annotations.presenceCondition.Operator;
import merlin.common.features.IFeatureProvider;

public class ImpliesFormula extends BinaryFormula {
	public ImpliesFormula(BoolFormula l, BoolFormula r) {
		this.left = l;
		this.right = r;
	}

	@Override
	public CNFFormula toCNF() {
		BoolFormula equiv = new OrFormula(new NotFormula(left), right);
		return equiv.toCNF();
	}

	@Override
	public boolean eval(IFeatureProvider fp) {
		return !this.left.eval(fp) || this.right.eval(fp);
	}
	
	public Operator toOperator() {
		return Operator.IMPLIES;
	}

	@Override
	public Node toFeatureIDENode() {
		return new org.prop4j.Implies(this.left.toFeatureIDENode(), this.right.toFeatureIDENode());
	}
}
