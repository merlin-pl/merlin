package merlin.featureide.composer.transformations;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EPackage;

import de.ovgu.featureide.fm.core.configuration.Configuration;
import merlin.common.annotations.MerlinAnnotationStructure;
import merlin.common.concepts.ConfigurationFragmentPool;
import merlin.common.concepts.SelectedConcepts;
import merlin.common.exporter.AbstractExporter;
import merlin.common.exporter.PluginUtils;
import merlin.common.issues.ValidationIssue;
import merlin.common.transformation.Method;
import merlin.common.transformation.analysis.MethodResolver;
import merlin.common.transformation.analysis.TransformationProductChecker;

public class TransformationProductGenerator {
	private ArrayList<String> oclErrors = new ArrayList<>();
	private ArrayList<ValidationIssue> trafoErrors = new ArrayList<>();
	private Configuration cfg;
	private EPackage root;
	private TransformationProductChecker tpc;
	private IFile ifile;
	
	public TransformationProductGenerator (Configuration cfg, EPackage root, IFile f) {
		this.cfg = cfg;
		this.root = root;
		this.ifile = f;
		this.tpc = new TransformationProductChecker(root);
	}
	
	public TransformationProductGenerator (Configuration cfg, EPackage root, IFile f, MethodResolver mr) {
		this.cfg = cfg;
		this.root = root;
		this.ifile = f;
		this.tpc = new TransformationProductChecker(mr);
	}	
	
	public List<ValidationIssue> getTrafoErrors() {
		return this.trafoErrors;
	}
	
	public void generate(URI path) {
		this.exportMetaModels(path);
		tpc.allChecks(Collections.singletonMap(this.root, this.ifile));	
		this.trafoErrors.addAll(tpc.getTrafoErrors());
		this.oclErrors.addAll(tpc.getOclErrors());
		this.mergeTrafoFragments(path);	
	}
	
	public void exportMetaModels(URI path) {
		String fpath = path.toFileString();

		for ( EPackage pck : SelectedConcepts.get()) {
			EAnnotation trafo = pck.getEAnnotation(MerlinAnnotationStructure.TRANSFORMATION);
			if (trafo==null) continue;
			String technology = trafo.getDetails().get(MerlinAnnotationStructure.TRAFO_TECHNOLOGY);
			if (technology==null) continue;
			AbstractExporter ae = PluginUtils.getExporterWithTechnology(technology);
			if (ae==null) continue;
			String fname = fpath.replace(".ecore", ae.extension());
			ae.export(this.root, fname);
		}
	}
	
	public void mergeTrafoFragments(URI path) {
		for (ConfigurationFragmentPool cfp: SelectedConcepts.getConfigs()) {
			String extension = cfp.extension();
			if (extension.equals(".ocl")) continue;
			AbstractExporter ae = PluginUtils.getExporterWithExtension(extension);
			if (ae==null) {
				this.oclErrors.add("No exporter for files of type "+extension);
				continue;
			}
			List<File> frags = cfp.getFragmentsCompatibleWith(this.cfg);
			frags = frags.stream().filter(f -> f.getName().endsWith(extension)).collect(Collectors.toList());
			//this.checkRepeatedCode(ae, frags, cfp);
			String fileName = path.toString().replace(".ecore", "trafo"+extension);
			fileName = fileName.replaceFirst("file:/", "");	// TODO: handle properly
			List<Method> overrides = this.tpc.getResolver().getOverridenMethods(frags);
			List<List<Method>> merged = this.tpc.getResolver().getMergedMethods(frags);
			//System.out.println("[merlin] "+merged);
			/*if (frags.size()>0 && cfp.isRootFile(frags.get(0))) { // We may have to override some methods
				overrides = this.getOverrideMethods(ae, frags.get(0), frags.subList(1, frags.size()));
				System.out.println("[merlin] Shoud override: "+overrides);				
			}*/
			ae.mergeFiles(frags, overrides, merged, fileName, path.lastSegment().replace(".ecore", extension)); 
		}
	}
	
	private List<Method> getOverrideMethods(AbstractExporter ae, File file, List<File> frags) {
		List<Method> overrideMethods = new ArrayList<>();
		List<Method> rootMeths = ae.getMethods(file);
		for (File f : frags) {
			List<Method> fmeths = ae.getMethods(f);
			List<Method> common = Method.intersection(rootMeths, fmeths);
			overrideMethods.addAll(common);
		}
		return overrideMethods;
	}

	public List<String> getOclErrors() {
		return this.oclErrors;
	}

}
