package merlin.common.transformation.analysis;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import de.ovgu.featureide.fm.core.configuration.Configuration;
import merlin.common.transformation.Method;
import merlin.common.transformation.OverrideKind;

public class ResolvedMethod {
	private Method method;
	private List<ResolvedMethod> overrides = new ArrayList<>();
	private Configuration cfg;
	
	public ResolvedMethod(Method m, Configuration cfg) {
		this.method = m;
		this.cfg = cfg;
	}
	
	public void setCompositionKind(OverrideKind ck) {
		this.method.setOverride(ck);
	}
		
	public boolean isOverriding() {
		return this.method.getOverride().isOverriding();
	}
	
	public boolean isMerge() {
		return this.method.getOverride().isMerge();
	}
	
	public File getFile() {
		return this.method.getFile();
	}
	
	public Method getMethod() {
		return this.method;
	}
	
	public List<ResolvedMethod> overrides() {
		return this.overrides;
	}
	
	public OverrideKind getOverrideKind() {
		return this.method.getOverride();
	}
	
	public Configuration getConfiguration() {
		return this.cfg;
	}
	
	@Override
	public boolean equals(Object o) {
		if (this==o) return true;
		if (!(o instanceof ResolvedMethod)) return false;
		ResolvedMethod rm = (ResolvedMethod)o;
		return  this.method.equals(rm.method) &&
				this.method.getFile().equals(rm.method.getFile()) &&
				equalConfig(rm);
	}

	private boolean equalConfig(ResolvedMethod rm) {
		return this.cfg == null ? rm.cfg == null : this.cfg.equals(rm.cfg);
	}
	
	@Override
	public int hashCode() {
		return this.method.hashCode()+7*this.method.getFile().hashCode()+11*this.cfgHashCode();
	}
	
	public int cfgHashCode() {
		return this.cfg == null ? 0 : this.cfg.hashCode();
	}
	
	@Override
	public String toString() {
		return this.method.toString();
	}
}
