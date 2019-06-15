package merlin.common.annotations.formulas;

import org.prop4j.Node;

import merlin.common.annotations.formulas.cnf.CNFFormula;
import merlin.common.features.IFeatureProvider;

public class Constant extends Literal {
	private String value;

	public Constant (String v) {
		this.value = v;
	}
	
	@Override
	public CNFFormula toCNF() {
		return null;
	}

	@Override public String toString() {
		return value.toLowerCase();
	}
	
	@Override
	public boolean eval(IFeatureProvider fp) {
		return "true".equals(value.toLowerCase());
	}

	@Override
	public Node toFeatureIDENode() {
		return new org.prop4j.Literal(value.toLowerCase());
	}
}
