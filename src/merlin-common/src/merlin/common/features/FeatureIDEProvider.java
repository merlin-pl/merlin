package merlin.common.features;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;

import de.ovgu.featureide.fm.core.base.IFeature;
import de.ovgu.featureide.fm.core.configuration.Configuration;
import de.ovgu.featureide.fm.core.configuration.SelectableFeature;

public class FeatureIDEProvider implements IFeatureProvider{
	private Configuration config;
	public FeatureIDEProvider(Configuration c) {
		this.config = c;
	}
	
	@Override
	public boolean isValidFeature(String s) {
		for (SelectableFeature f : config.getFeatures()) {
			if (s.equals(f.getName())) return true;
		}
		return false;
	}

	@Override
	public boolean getFeatureValue(String s) {	// The root is always selected, even though
		return this.config.getSelectedFeatureNames().contains(s);
	}

	@Override
	public void setFeatureModelFile(IFile f) {}

	@Override
	public IFile getFeatureModelFile() { return null; }

	@Override
	public IFeature getFeature(String name) {
		return this.config.getFeatureModel().getFeature(name);
	}

	@Override
	public List<IFeature> getFeatures() {
		return this.config.getFeatures().stream().map(sf -> sf.getFeature()).collect(Collectors.toList());
	}

}
