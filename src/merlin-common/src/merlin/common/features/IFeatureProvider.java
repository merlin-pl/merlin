package merlin.common.features;

import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IFile;

import de.ovgu.featureide.fm.core.base.IFeature;

public interface IFeatureProvider {
	boolean isValidFeature(String s);
	boolean getFeatureValue(String s);
	void 	setFeatureModelFile(IFile f);	
	IFile 	getFeatureModelFile();
	IFeature getFeature(String name);
	List<IFeature> getFeatures();
	default List<IFeature> getFeaturesInConstraints() {
		return Collections.emptyList();
	}
}
