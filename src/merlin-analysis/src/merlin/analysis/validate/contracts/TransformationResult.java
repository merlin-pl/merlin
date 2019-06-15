package merlin.analysis.validate.contracts;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TransformationResult {
	private List<String> errors = new ArrayList<>();
	
	public void addError (String error) { errors.add(error); }
	
	public boolean hasErrors() { return !errors.isEmpty(); }
	public String  getErrors() { return errors.stream().map(Object::toString).collect(Collectors.joining("\n")); }
}
