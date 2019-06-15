package merlin.analysis.use;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EPackage;

public class UseSearchScope {

	public static final int DEFAULT_CLASS_LOWER_BOUND = 0;
	public static final int DEFAULT_CLASS_UPPER_BOUND = 5;
	public static final int DEFAULT_ATT_LOWER_BOUND   = 0;
	public static final int DEFAULT_ATT_UPPER_BOUND   = -1;	
	public static final int DEFAULT_REF_LOWER_BOUND   = 0;
	public static final int DEFAULT_REF_UPPER_BOUND   = 10;
	
	private Map<String, ClassBounds> scope = new HashMap<String, ClassBounds>();
	
	// initialize search bounds
	public UseSearchScope (List<EPackage> packages) {
		if (packages != null)
			for (EPackage p : packages)
				addPackage(p);
	}
	
	public void addPackage (EPackage epackage) {
		if (epackage != null)
			for (EClassifier cl : epackage.getEClassifiers()) 
				if (cl instanceof EClass) 
					if (!scope.containsKey(cl.getName()))
						scope.put(cl.getName(), new ClassBounds((EClass)cl));
	}
	
	public int getLowerBounds (String classname) {
		return scope.containsKey(classname)? 
				scope.get(classname).getLowerBound() : 
				0; 
	}
	
	public int getUpperBounds (String classname) {
		return scope.containsKey(classname)? 
				scope.get(classname).getUpperBound() : 
				0; 
	}
	
	public boolean setLowerBounds (String classname, int lowerBound) {
		if (scope.containsKey(classname)) {
			scope.get(classname).setLowerBound(lowerBound);
			return true;
		}
		return false;
	}
		
	public boolean setUpperBounds (String classname, int upperBound) {
		if (scope.containsKey(classname)) {
			scope.get(classname).setUpperBound(upperBound);
			return true;
		}
		return false;
	}
	
	public int getLowerBounds (String classname, String refname) {
		return scope.containsKey(classname)? 
				scope.get(classname).getLowerBound(refname) : 
				0; 
	}
	
	public int getUpperBounds (String classname, String refname) {
		return scope.containsKey(classname)? 
				scope.get(classname).getUpperBound(refname) : 
				0; 
	}
	
	public boolean setLowerBounds (String classname, String refname, int lowerBound) {
		if (scope.containsKey(classname)) {
			scope.get(classname).setLowerBound(refname, lowerBound);
			return true;
		}
		return false;
	}
	
	public boolean setUpperBounds (String classname, String refname, int upperBound) {
		if (scope.containsKey(classname)) {
			scope.get(classname).setUpperBound(refname, upperBound);
			return true;
		}
		return false;
	}
	
	// [lowerBound..upperBound]
	private abstract class Bounds {
		private int lowerBound;
		private int upperBound;
		protected Bounds (int lowerBound, int upperBound) { 
			setLowerBound(lowerBound);
			setUpperBound(upperBound);
		}
		public void setLowerBound(int lowerBound) { this.lowerBound = lowerBound; }
		public void setUpperBound(int upperBound) { this.upperBound = upperBound; }		
		public int  getLowerBound() { return this.lowerBound; }
		public int  getUpperBound() { return this.upperBound; }
	}	
	
	// search scope for a class and its features
	private class ClassBounds extends Bounds {
		private Map<String, Bounds> features = new HashMap<String, Bounds>();
		
		public ClassBounds (EClass cl) { 
			super(DEFAULT_CLASS_LOWER_BOUND, DEFAULT_CLASS_UPPER_BOUND);
			cl.getEAllAttributes().stream().forEach(ref -> features.put(ref.getName(), new AttributeBounds()));
			cl.getEAllReferences().stream().forEach(ref -> features.put(ref.getName(), new ReferenceBounds()));
		} 
		
		// setter/getter methods for feature bounds
		public boolean setLowerBound(String refname, int lowerBound) {
			if (features.containsKey(refname)) {
				features.get(refname).setLowerBound(lowerBound);
				return true;
			}
			return false;
		}
		public boolean setUpperBound(String refname, int upperBound) {
			if (features.containsKey(refname)) {
				features.get(refname).setUpperBound(upperBound);
				return true;
			}
			return false;
		}
		public int getLowerBound(String refname) {
			return features.containsKey(refname)?
					features.get(refname).getLowerBound() :
					0;
		}
		public int getUpperBound(String refname) {
			return features.containsKey(refname)?
					features.get(refname).getUpperBound() :
					0;
		}
	}
	
	// search scope for a reference
	private class ReferenceBounds extends Bounds { 
		public ReferenceBounds() { 
			super(DEFAULT_REF_LOWER_BOUND, DEFAULT_REF_UPPER_BOUND);   
		} 
	}

	// search scope for an attribute
	private class AttributeBounds extends Bounds { 
		public AttributeBounds() { 
			super(DEFAULT_ATT_LOWER_BOUND, DEFAULT_ATT_UPPER_BOUND);   
		} 
	}
}
