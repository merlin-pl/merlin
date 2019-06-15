package merlin.common.annotations.formulas;

import org.prop4j.Node;

import merlin.common.annotations.formulas.cnf.CNFFormula;
import merlin.common.annotations.presenceCondition.Operator;
import merlin.common.features.IFeatureProvider;

public class NotFormula extends BoolFormula {
	private BoolFormula formula;
	public NotFormula(BoolFormula formula) {
		this.formula = formula;
	}
	@Override
	public CNFFormula toCNF() {
		if (formula instanceof ClauseFeature) {
			ClauseFeature cf = (ClauseFeature)formula;
			return new CNFFormula(cf.negate());
		}
		else if (formula instanceof AndFormula) {
			AndFormula af = (AndFormula)this.formula;
			return new OrFormula( new NotFormula(af.left()), new NotFormula(af.right())).toCNF(); 
		}
		else if (formula instanceof OrFormula) {
			OrFormula af = (OrFormula)this.formula;
			return new AndFormula( new NotFormula(af.left), new NotFormula(af.right)).toCNF();
		}
		else if (formula instanceof ImpliesFormula) {
			ImpliesFormula af = (ImpliesFormula)this.formula;
			return new AndFormula( af.left, new NotFormula( af.right)).toCNF();
		}
		else { // NOT
			NotFormula nf = (NotFormula)this.formula;
			return nf.formula.toCNF();
		}
		// no more cases!
	}
	@Override
	public boolean eval(IFeatureProvider fp) {
		return !this.formula.eval(fp);
	}
	
	public Operator toOperator() {
		return Operator.NOT;
	}
	@Override
	public Node toFeatureIDENode() {
		return new org.prop4j.Not(this.formula.toFeatureIDENode());
	}
}
