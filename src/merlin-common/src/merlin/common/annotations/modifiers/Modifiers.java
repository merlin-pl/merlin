package merlin.common.annotations.modifiers;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Modifiers {
	public static final String ABSTRACT_MODIFIER = "abstract";
	public static final String CONTAINMENT_MODIFIER = "containment";
	public static final String EXTENDS_MODIFIER = "extend";
	public static final String INTERFACE_MODIFIER = "interface";
	public static final String MAX_MODIFIER = "max";
	public static final String MIN_MODIFIER = "min";
	public static final String ORDERED_MODIFIER = "ordered";
	public static final String REDUCE_MODIFIER = "reduce";
	public static final String UNIQUE_MODIFIER = "unique";
	
	public static List<String> conflictsWith(String modifier) {
		switch(modifier) {
		case EXTENDS_MODIFIER : return Arrays.asList(REDUCE_MODIFIER);
		case REDUCE_MODIFIER : 	return Arrays.asList(EXTENDS_MODIFIER);
		}
		return Collections.emptyList(); 
	}
	
	public static List<String> allModifiers() {
		return Arrays.asList(	ABSTRACT_MODIFIER, 
								CONTAINMENT_MODIFIER,
								EXTENDS_MODIFIER,
								INTERFACE_MODIFIER,
								MAX_MODIFIER,
								MIN_MODIFIER,
								ORDERED_MODIFIER,
								REDUCE_MODIFIER,
								UNIQUE_MODIFIER);
	}
}
