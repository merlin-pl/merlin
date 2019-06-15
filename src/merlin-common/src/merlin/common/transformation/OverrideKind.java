package merlin.common.transformation;

import java.util.Arrays;

public enum OverrideKind {
	ALL("all"),
	NONE("none"),
	SUPER("super"),
	SUBS("subs"),
	
	MERGE_AND("and"), 
	MERGE_OR("or"), 
	MERGE_ADD("add"), 
	MERGE_MULTIPLY("multiply"), 
	MERGE_UNION("union", false), 
	MERGE_INTERSECTION("intersection", false),
	MERGE("");

	private String strName;
	private boolean isOperator=true;
	
	public String mergeOperation() {
		switch (this) {
		case MERGE_AND : return "and";
		case MERGE_OR : return "or";
		case MERGE_ADD : return "+";
		case MERGE_MULTIPLY : return "*";
		case MERGE_UNION : return "includingAll";
		case MERGE_INTERSECTION: return "intersection";
		default: return "";
		}
	}
	
	public boolean isOperator() {
		return this.isOperator;
	}
	
	@Override
	public String toString() {
		return this.strName;
	}
	
	private OverrideKind(String str) {
		this.strName = str;
	}
	
	private OverrideKind(String str, boolean operator) {
		this.strName = str;
		this.isOperator = operator;
	}
	
	public static OverrideKind fromString(String s) {
		if (s==null) return null;
		String lower = s.toLowerCase();
		for (OverrideKind ok : OverrideKind.values()) {
			if (ok.strName.equals(lower)) return ok;
		}
		return null;
	}
	
	public boolean isOverriding() {
		return Arrays.asList(ALL, NONE, SUPER, SUBS).contains(this);
	}
	
	public boolean isMerge() {
		return !this.isOverriding();
	}
}
