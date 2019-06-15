package merlin.common.annotations.formulas;

import java.util.List;

import org.prop4j.Node;

import merlin.common.annotations.formulas.cnf.CNFClause;
import merlin.common.annotations.formulas.cnf.CNFFormula;
import merlin.common.annotations.presenceCondition.Operator;
import merlin.common.features.IFeatureProvider;

public class AndFormula extends BinaryFormula{
		
	public AndFormula(BoolFormula l, BoolFormula r) {
		this.left = l;
		this.right = r;
	}

	@Override
	public CNFFormula toCNF() {
		List<CNFClause> clauses = this.left.toCNF().clauses();
		clauses.addAll(this.right.toCNF().clauses());
		return new CNFFormula(clauses);
	}

	@Override
	public boolean eval(IFeatureProvider fp) {
		return this.left.eval(fp) && this.right.eval(fp);
	}
	
	public Operator toOperator() {
		return Operator.AND;
	}

	@Override
	public Node toFeatureIDENode() {
		return new org.prop4j.And(this.left.toFeatureIDENode(), this.right.toFeatureIDENode());
	}
}
