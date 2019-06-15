package merlin.common.annotations.formulas;

public abstract class BinaryFormula extends BoolFormula {
	protected BoolFormula left, right;
	
	public BoolFormula left() { return this.left;}
	public BoolFormula right() { return this.right;}
	
}
