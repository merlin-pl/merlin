package merlin.analysis.validate.annotations.ocl;

import java.util.List;

import de.ovgu.featureide.fm.core.base.IFeature;
import merlin.common.annotations.formulas.AndFormula;
import merlin.common.annotations.formulas.BoolFormula;
import merlin.common.annotations.formulas.ClauseFeature;
import merlin.common.annotations.formulas.ImpliesFormula;
import merlin.common.annotations.formulas.NotFormula;
import merlin.common.annotations.formulas.OrFormula;
import merlin.common.annotations.presenceCondition.FormulaFeature;
import merlin.common.annotations.presenceCondition.IParserAction;
import merlin.common.annotations.presenceCondition.Operator;

public class BoolFormulaBuilder implements IParserAction{
	private BoolFormula formula; 
	private List<IFeature> features;
	
	public BoolFormulaBuilder(List<IFeature> features) {
		this.features = features;
	}
	
	public void exec (FormulaFeature f1, FormulaFeature f2, Operator op) {
		BoolFormula loper = null, roper = null;
		
		if (op==null) {
			this.formula = new ClauseFeature(this.get(f1.getName()));
			return;
		}
		
		if (this.get(f1.getName()) == null) {
			// This means we need to take currentFormula as left operand
			loper = this.formula;
		} 
		else loper = new ClauseFeature(this.get(f1.getName()));
		
		if (op.isBinary()) {
			roper = new ClauseFeature(this.get(f2.getName()));
		}
			
		switch(op) {
			case AND : this.formula = new AndFormula(loper, roper); break;
			case OR : this.formula = new OrFormula(loper, roper); break;
			case IMPLIES: this.formula = new ImpliesFormula(loper, roper); break;
			case NOT: this.formula = new NotFormula(loper);
		}
	}

	private IFeature get(String name) {
		for (IFeature f : this.features) {
			if (f.getName().equals(name)) return f;
		}
		return null;
	}
	
	public BoolFormula getFormula() {
		return this.formula;
	}
}
