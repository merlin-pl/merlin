package merlin.compare.comparison;

import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.compare.Comparison;
import org.eclipse.emf.compare.EMFCompare;
import org.eclipse.emf.compare.Match;
import org.eclipse.emf.compare.scope.DefaultComparisonScope;
import org.eclipse.emf.compare.scope.IComparisonScope;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

public class EcoreComparator {
	private Comparison comparison;
	private Resource ecoreResource, pLineResource;
	private IFile featureModel;
	
	public EcoreComparator (String ecorePath, String mmplPath, IFile featureModel) {
		URI uri1 = URI.createFileURI(ecorePath);
	    URI uri2 = URI.createFileURI(mmplPath);

	    Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());

	    ResourceSet resourceSet1 = new ResourceSetImpl();
	    ResourceSet resourceSet2 = new ResourceSetImpl();

	    this.ecoreResource = resourceSet1.getResource(uri1, true);
	    this.pLineResource = resourceSet2.getResource(uri2, true);
	    this.featureModel  = featureModel;

	    IComparisonScope scope = new DefaultComparisonScope(resourceSet1, resourceSet2, null);
	    this.comparison = EMFCompare.builder().build().compare(scope);

	    this.printMatches(comparison.getMatches());	    
	}
	
	public void printMatch(Match eq, String indent) {
		if (eq==null) return;
		String left = eq.getLeft() != null ? eq.getLeft().toString() : "[NONE]";
		String right = eq.getRight() != null ? eq.getRight().toString() : "[NONE]";
		System.out.println(indent+left+" <--> "+right);
		for (Match m : eq.getSubmatches()) {
			this.printMatch(m, indent+"  ");
		}
	}
	
	public void printMatches(List<Match> matches) {
	     for (Match eq : matches) {
	    	this.printMatch(eq, "");
	    }
	}

	public Comparison getComparison() {
		return comparison;
	}

	public Resource getEcoreResource() {
		return this.ecoreResource;
	}

	public Resource getPLineResource() {
		return this.pLineResource;
	}

	public IFile getFeatureModel() {
		return this.featureModel;
	}
}
