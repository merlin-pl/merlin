package merlin.common.annotations.presenceCondition;

import java.io.IOException;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.NoSuchElementException;
import java.util.Set;

import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EcoreFactory;

import merlin.common.annotations.formulas.BoolFormula;
import merlin.common.annotations.formulas.ClauseFeature;
import merlin.common.annotations.formulas.Constant;
import merlin.common.features.IFeatureProvider;
import merlin.common.issues.IssueLevel;
import merlin.common.issues.ValidationIssue;

/**
 * Parses a logical expression. Inspired by Dijkstra's Shunting-yard algorithm
 */
public class ConditionParser {
	private StreamTokenizer stk;
	private Deque<BoolFormula> operandStack = new ArrayDeque<>();
	private Deque<Operator> operatorStack = new ArrayDeque<>();
	private Set<ValidationIssue> errors = new LinkedHashSet<ValidationIssue>();
	private BoolFormula ast;
		
	private ENamedElement context;
	private IFeatureProvider provider;
	
	public ConditionParser(String condition, ENamedElement context, IFeatureProvider prv) {
		this.context = context;
		this.stk = new StreamTokenizer(new StringReader(condition));
		this.stk.wordChars('_', '_');
		this.provider = prv;
	}
	
	public ConditionParser(String condition, IFeatureProvider prv) {
		this.context = EcoreFactory.eINSTANCE.createEClass();
		this.context.setName("void");
		this.stk = new StreamTokenizer(new StringReader(condition));
		this.stk.wordChars('_', '_');
		this.provider = prv;
	}
	
	private void handleWord() {
		String token = this.stk.sval;
//        System.out.println(" token = "+token);
        if (this.isLiteral(token)) {  // true or false
        	this.operandStack.push(new Constant(token));
        }
        else if (this.isFeature(token)) {	// a feature    		
	        if (!this.provider.isValidFeature(token))
	        	errors.add(new ValidationIssue("Invalid feature '"+token+"' in presence condition", IssueLevel.ERROR, this.context));
	        this.operandStack.push(new ClauseFeature(this.provider.getFeature(token)));
        }
        else {	// an operator
        	// check precedence before pushing
        	Operator current = this.getOperator(this.stk.sval);
        	boolean end = false;
        	while (!end) {
        		Operator topOp=this.operatorStack.peek();
        		if (topOp!=null && topOp.precedence() >= current.precedence() ) {
//        			System.out.println("[merlin] Higher precedence on top");
        			this.evalStack();
        		} else 
        			end = true;
        	}
        	this.operatorStack.push(this.getOperator(this.stk.sval));
        }
	}
	
	// Evaluates whatever is in the stack
	private Operator evalStack() {
		Operator op = this.operatorStack.pop();
		BoolFormula op1 = this.operandStack.pop();
		if (!op.equals(Operator.NOT) && !op.equals(Operator.LEFT_PAR)) {
    		BoolFormula op2 = this.operandStack.pop();
    		this.operandStack.push(op.getBoolFormula(op2, op1));
    	} else if (op.equals(Operator.NOT))
    		this.operandStack.push(op.getBoolFormula(op1, null));
    	else // LEFT_PAR
    		this.operandStack.push(op1);
		return op;
	}
	
	private void handleClosePar() {
		Operator op;
    	do {
    		try {
		    	op = this.evalStack();
    		} catch (NoSuchElementException e) {
    			errors.add(new ValidationIssue("Unmatched ')' in presence condition", IssueLevel.ERROR, this.context));
	    		return;
    		}
    	} while (!op.equals(Operator.LEFT_PAR));
	}
	
	private boolean evaluate() {
		while (!this.operatorStack.isEmpty()) {
			Operator op = this.operatorStack.pop();
	    	BoolFormula op1 = this.operandStack.poll();
	    	if (op1==null) {
    			errors.add(new ValidationIssue("Displaced "+op+" in presence condition", IssueLevel.ERROR, this.context));
    			return false;
    		}
	    	if (op.equals(Operator.LEFT_PAR)) {
	    		errors.add(new ValidationIssue("Unmatched '(' in presence condition", IssueLevel.ERROR, this.context));
	    		continue;
	    	}
	    	if (op.equals(Operator.RIGHT_PAR)) {
	    		errors.add(new ValidationIssue("Unmatched ')' in presence condition", IssueLevel.ERROR, this.context));
	    		continue;
	    	}
	    	if (!op.equals(Operator.NOT)) {	// Binary operator
	    		BoolFormula op2 = this.operandStack.poll();
	    		if (op2==null) {
	    			errors.add(new ValidationIssue("Displaced "+op+" in presence condition", IssueLevel.ERROR, this.context));
	    			return false;
	    		}
	    		this.operandStack.push(op.getBoolFormula(op2, op1));
	    	} else { // Unary operator
	    		this.operandStack.push(op.getBoolFormula(op1, null));
	    	}
		}
		BoolFormula result = this.operandStack.poll();
		if (result == null ) {
			errors.add(new ValidationIssue("Missing operand(s) in presence condition", IssueLevel.ERROR, this.context));
			return false;
		}
		this.ast = result;
		return this.ast.eval(this.provider);
	}
	
	public BoolFormula getAST() {
		return this.ast;
	}
	
	public boolean eval() {
		this.parse();	// TODO: Handle errors?
		return this.result;
	}
	
	private boolean result;
	
	public Collection<ValidationIssue> parse() {
		try {
			while(this.stk.nextToken() != StreamTokenizer.TT_EOF){
				if(this.stk.ttype == StreamTokenizer.TT_WORD) 
					this.handleWord();
				else if (this.stk.ttype=='(') {
//					System.out.println(" token = (");
					this.operatorStack.push(this.getOperator("("));
				}
				else if (this.stk.ttype==')') 
					this.handleClosePar();
				else 
					errors.add(	new ValidationIssue("Element "+this.stk.ttype+" not recognized in presence condition", 
								IssueLevel.ERROR, 
								this.context));
			}
		} catch (IOException e) {
			e.printStackTrace();
			return errors;
		}

		this.result = this.evaluate();
		return errors;
	}

	private boolean isLiteral(String tok) {
		if ("true".equals(tok.toLowerCase())) return true;
		if ("false".equals(tok.toLowerCase())) return true;
		return false;
	}
	
	private boolean isFeature(String tok) {
		for (Operator o : Operator.values()) {
			if (o.getOperator().equals(tok)) return false;
		}
		return true;
	}
	
	private Operator getOperator(String tok) {
		for (Operator o : Operator.values()) {
			if (o.getOperator().equals(tok)) return o;
		}
		return null;
	}
}

