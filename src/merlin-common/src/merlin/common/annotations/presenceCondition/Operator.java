package merlin.common.annotations.presenceCondition;

import merlin.common.annotations.formulas.AndFormula;
import merlin.common.annotations.formulas.BoolFormula;
import merlin.common.annotations.formulas.ImpliesFormula;
import merlin.common.annotations.formulas.NotFormula;
import merlin.common.annotations.formulas.OrFormula;

public enum Operator {
	NOT("not"){
		@Override
		public boolean operate(boolean op1, boolean op2) {			
			return !op1;
		}
		
		@Override public boolean isBinary() { return false; }

		@Override
		public BoolFormula getBoolFormula(BoolFormula op1, BoolFormula op2) {
			return new NotFormula(op1);
		}
		
		@Override
		public int precedence() { return 4; }
	}, 
	AND("and") {
		@Override
		public boolean operate(boolean op1, boolean op2) {
			return op1&&op2;
		}

		@Override
		public BoolFormula getBoolFormula(BoolFormula op1, BoolFormula op2) {
			return new AndFormula(op1, op2);
		}
		
		@Override
		public int precedence() { return 3; }
	}, 
	OR("or") {
		@Override
		public boolean operate(boolean op1, boolean op2) {
			return op1||op2;
		}

		@Override
		public BoolFormula getBoolFormula(BoolFormula op1, BoolFormula op2) {
			return new OrFormula(op1, op2);
		}		
		
		@Override
		public int precedence() { return 2; }
	}, 
	IMPLIES("implies") {
		@Override
		public boolean operate(boolean op1, boolean op2) {
			return (!op1)||op2;
		}

		@Override
		public BoolFormula getBoolFormula(BoolFormula op1, BoolFormula op2) {
			return new ImpliesFormula(op1, op2);
		}	
		
		@Override
		public int precedence() { return 1; }
	},
	LEFT_PAR("(") {

		@Override
		public boolean operate(boolean op1, boolean op2) {
			return op1;
		}

		@Override
		public BoolFormula getBoolFormula(BoolFormula op1, BoolFormula op2) {
			return null;
		}		
	},
	RIGHT_PAR(")") {

		@Override
		public boolean operate(boolean op1, boolean op2) {
			return op1;
		}

		@Override
		public BoolFormula getBoolFormula(BoolFormula op1, BoolFormula op2) { return null; }
		
	};
	
	private String symbol;
	public String getOperator() {
		return this.symbol;
	}
	Operator(String op) {
		this.symbol = op;
	}
	
	public boolean isBinary() { return true; } 
	public abstract boolean operate(boolean op1, boolean op2);
	public abstract BoolFormula getBoolFormula(BoolFormula op1, BoolFormula op2);
	public int precedence() {return -1;}
}
