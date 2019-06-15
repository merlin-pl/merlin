package merlin.common.features;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;

import de.ovgu.featureide.fm.core.ExtensionManager.NoSuchExtensionException;
import de.ovgu.featureide.fm.core.base.IConstraint;
import de.ovgu.featureide.fm.core.base.IFeature;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.base.impl.FMFactoryManager;
import de.ovgu.featureide.fm.core.base.impl.FMFormatManager;
import de.ovgu.featureide.fm.core.functional.Functional;
import de.ovgu.featureide.fm.core.io.IFeatureModelFormat;
import de.ovgu.featureide.fm.core.io.manager.SimpleFileHandler;

/**
 * A feature provider that obtains the feature values directly from the XML file
 */
public class DefaultFeatureProvider implements IFeatureProvider {
	private IFile featureModelFile;
	private IFeatureModel fm;
	
	public DefaultFeatureProvider() { }
	
	public DefaultFeatureProvider(IFile inputFile) {
		this.featureModelFile = inputFile;
		this.createFeatureModel();
	}
	
	public IFeatureModel getFeatureModel() {
		return this.fm;
	}
	
	@Override
	public List<IFeature> getFeaturesInConstraints() {
		List<IFeature> features = new ArrayList<>();
		for (IConstraint ic : this.fm.getConstraints()) {
			features.addAll(ic.getContainedFeatures());
		}
		return features;
	}
	
	private void createFeatureModel() {
		String contents = this.readFile(this.featureModelFile).toString();
		final IFeatureModelFormat format = FMFormatManager.getInstance().getFormatByContent(contents, this.featureModelFile.getLocation().toOSString());
		try {
			fm = FMFactoryManager.getFactory(this.featureModelFile.getLocation().toString(), format).createFeatureModel();
		} catch (final NoSuchExtensionException e) {
			fm = FMFactoryManager.getDefaultFactory().createFeatureModel();
		}
		fm.setSourceFile(this.featureModelFile.getLocation().toFile().toPath());
		try {
			SimpleFileHandler.load(this.featureModelFile.getContents(), fm, format);
		} catch (final CoreException e) {
			System.err.println(e);
		}
	}
	
	private StringBuilder readFile(IFile inputFile) {
		StringBuilder sb = new StringBuilder();
		if (inputFile==null) return sb;
		try (BufferedReader br = new BufferedReader(new FileReader(inputFile.getLocation().toFile()))){
		    String line = br.readLine();

		    while (line != null) {
		        sb.append(line);
		        line = br.readLine();
		    }
		} catch (IOException e) {
			e.printStackTrace();
		} 
		return sb;
	}
	
	@Override
	public boolean isValidFeature(String s) {
		if (this.featureModelFile==null) return false;
		for (IFeature feat : this.fm.getFeatures()) {
			if (feat.getName().equals(s)) return true;
		}
		return false;
	}
	
	public IFeature getFeature(String name) {
		return this.fm==null ? null : this.fm.getFeature(name);
	}

/*	@Override
	public boolean isValidFeature(String token) {
		if (this.featureModelFile==null) return false;
		File file = this.featureModelFile.getRawLocation().makeAbsolute().toFile();
		// TODO: Cache this
		List<String> features = this.getFeatures(file);
		
		for (String s : features) {
			if (s.toUpperCase().equals(token.toUpperCase())) return true;
		}
		
		return false;
	}
	
	private List<String> getFeatures(File file) {
	    StringBuilder sb = new StringBuilder();
		try (BufferedReader br = new BufferedReader(new FileReader(file))){
		    String line = br.readLine();

		    while (line != null) {
		        sb.append(line);
		        line = br.readLine();
		    }
		    String everything = sb.toString();
		} catch (IOException e) {
			e.printStackTrace();
		} 
		return this.getFeatures(sb);
	}
	
	private List<String> getFeatures(StringBuilder sb) {
		Set<String> features = new HashSet<String>();
		
		String fm = sb.toString();
		
		int idx = 0;
		while (idx >= 0) {
			int occur = fm.indexOf("name=\"", idx);	// TODO: improve robustness... this should be a regular expression
			if (occur >= 0) {
				String featureName = fm.substring(occur+6);	// TODO: improve robustness
				int occur2 = featureName.indexOf("\"");
				featureName = featureName.substring(0, occur2);
				features.add(featureName);
				idx = occur+1;
			} else { 
				idx = occur;
			}
		}
		
		return new ArrayList<String>(features);
	}*/

	@Override
	public boolean getFeatureValue(String s) {
		return false;
	}

	@Override
	public void setFeatureModelFile(IFile f) {
		this.featureModelFile = f;
		this.createFeatureModel();
	}

	@Override
	public IFile getFeatureModelFile() {
		return this.featureModelFile;
	}

	@Override
	public List<IFeature> getFeatures() {
		return this.featureModelFile==null && this.fm==null? 
				Collections.emptyList() :
				Functional.toList(this.fm.getFeatures());
	}
}
