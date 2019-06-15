package merlin.common.annotations.formulas;

import org.prop4j.Node;

import merlin.common.annotations.formulas.cnf.CNFFormula;
import merlin.common.annotations.presenceCondition.Operator;
import merlin.common.features.IFeatureProvider;

public class OrFormula extends BinaryFormula {
	public OrFormula(BoolFormula l, BoolFormula r) {
		this.left = l;
		this.right = r;
	}

	@Override
	public CNFFormula toCNF() {
		CNFFormula l1 = this.left.toCNF();
		CNFFormula l2 = this.right.toCNF();
		return l1.disjunction(l2);
	}

	@Override
	public boolean eval(IFeatureProvider fp) {
		return this.left.eval(fp) || this.right.eval(fp);
	}
	
	public Operator toOperator() {
		return Operator.OR;
	}

	@Override
	public Node toFeatureIDENode() {
		return new org.prop4j.Or(this.left.toFeatureIDENode(), this.right.toFeatureIDENode());
	}
}
