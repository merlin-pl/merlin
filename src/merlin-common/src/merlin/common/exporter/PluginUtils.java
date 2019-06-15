package merlin.common.exporter;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.Platform;

public class PluginUtils {
	public static final String EXPORTER_ID = "merlin-common.exporter";
	
	public static String getExtension(String technology) {
		AbstractExporter ae = getExporterWithTechnology(technology);
		if (ae==null) return ".ocl";
		return ae.extension();
	}
	
	public static IConfigurationElement[] getExtensionImplementations() {
		for (IExtensionPoint p : Platform.getExtensionRegistry().getExtensionPoints()) {
			if (p.getUniqueIdentifier().equals(EXPORTER_ID)) 
				return p.getConfigurationElements();			
		}
		return null;
	}
	
	public static List<AbstractExporter> getExporters() {
		List<AbstractExporter> exporters = new ArrayList<>();
		for (IConfigurationElement ce : getExtensionImplementations()) {
			try {
				final Object o = ce.createExecutableExtension("logic");
				AbstractExporter ae = (AbstractExporter)o;
				exporters.add(ae);
			} catch (CoreException ex) {
				System.out.println(ex.getMessage());
			}
		}
		return exporters;
	}
	
	public static AbstractExporter getExporterWithExtension(String ext) {
		for (AbstractExporter ae : getExporters()) {
			if (ae.extension().equals(ext)) return ae;
		}
		return null;
	}

	public static AbstractExporter getExporterWithTechnology(String technology) {
		for (AbstractExporter ae : getExporters()) {
			if (ae.name().toLowerCase().equals(technology.toLowerCase())) return ae;
		}
		return null;
	}
}
