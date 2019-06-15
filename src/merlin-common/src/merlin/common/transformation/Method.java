package merlin.common.transformation;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Method {
	private String className;
	private String methodName;
	private File   file;
	private OverrideKind override=OverrideKind.NONE;
	
	public Method(String cn, String mn, File file) {
		this.className = cn;
		this.methodName = mn;		
		this.file = file;
	}
	
	public File getFile() {
		return this.file;
	}
	
	public String getClassName() {
		return this.className;
	}
	
	public String getMethodName() {
		return this.methodName;
	}
	
	public OverrideKind getOverride() {
		return this.override;
	}
	
	public void setOverride(OverrideKind override) {
		this.override = override;
	}
	
	@Override
	public String toString() {
		return this.className+"::"+this.methodName;
	}
	
	@Override
	public boolean equals(Object o) {
		if (this==o) return true;
		if (!(o instanceof Method)) return false;
		Method m = (Method)o;
		return (equalName(m) &&
				this.methodName.equals(m.methodName));
	}
	
	public boolean identical(Method m) {
		return this.equals(m) && this.file.equals(m.file);
	}

	private boolean equalName(Method m) {
		if (this.className == null) return m.className==null;
		return this.className.equals(m.className);
	}
	
	@Override
	public int hashCode() {
		return (classHashCode() + 1031*this.methodName.hashCode());
	}

	private int classHashCode() {
		return this.className==null ? 0 : this.className.hashCode();
	}
	
	public static List<Method> intersection(List<Method> list1, List<Method> list2) {
		List<Method> res = new ArrayList<>();
		for (Method m : list1) {
			if (list2.contains(m)) res.add(m);
		}
		return res;
	}

	public boolean isOverriding() {
		return this.override.isOverriding();
	}
	
	public boolean isMerge() {
		return this.override.isMerge();
	}
}
