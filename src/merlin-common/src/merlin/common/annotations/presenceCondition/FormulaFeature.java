package merlin.common.annotations.presenceCondition;

public class FormulaFeature {
	private boolean value;
	private String name;
	
	public FormulaFeature(String name) {
		this.name = name;
	}
	
	public FormulaFeature(String name, boolean val) {
		this.name = name;
		this.value = val;
	}
	
	public void setValue(boolean v) {
		this.value = v;
	}
	
	public String getName() {
		return this.name;
	}
	
	public boolean getValue() {
		return this.value;
	}
}
