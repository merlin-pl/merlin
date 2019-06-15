package merlin.common.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

import merlin.common.issues.IssueLevel;
import merlin.common.issues.ValidationIssue;

public class FileUtils {
	/**
	 * Gets all files recursively in folder, with a given extension
	 * @param folder
	 * @param extension
	 * @return
	 */
	public static List<File> getAllFiles(File folder, String extension) {
		List<File> files = new ArrayList<File>();
		File[] contents = folder.listFiles();
		if (contents!=null) {
			for (File f : contents) {
				if (f.isFile() && f.getName().endsWith(extension)) files.add(f);
				else if (f.isDirectory()) files.addAll(FileUtils.getAllFiles(f, extension));
			}
		}
		return files;
	}
	
	public static List<File> getFiles(File folder, String extension) {
		List<File> files = new ArrayList<File>();
		File[] contents = folder.listFiles();
		if (contents!=null) {
			for (File f : contents) {
				if (f.isFile() && f.getName().endsWith(extension)) files.add(f);				
			}
		}
		return files;
	}
	
	public static IFile getIFile(File file) {
		IWorkspace workspace= ResourcesPlugin.getWorkspace();    
		IPath location= Path.fromOSString(file.getAbsolutePath()); 
		return workspace.getRoot().getFileForLocation(location);
	}
	
	public static IFile getIFile(String path) {
		if (path.startsWith("//")) path = path.substring(2);
		// case a) relative path
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IPath      location  = Path.fromOSString(path); 
		IFile      ifile     = workspace.getRoot().getFileForLocation(location);
		if (ifile!=null) return ifile;
		// case b) full path
		String wslocation       = workspace.getRoot().getLocation().toOSString();		
		IPath  relativelocation = location.makeRelativeTo(Path.fromOSString(wslocation));
		ifile = workspace.getRoot().getFile(relativelocation);
		return ifile;
	}
	
	@SuppressWarnings("restriction")
	public static IFile getIFile(org.eclipse.core.internal.resources.File file) {
		IWorkspace workspace= ResourcesPlugin.getWorkspace();    
		IPath location= Path.fromOSString(file.getLocation().toOSString()); 
		return workspace.getRoot().getFileForLocation(location);
	}
	
	public static void updateMarkers (IFile file, List<ValidationIssue> issues) {
		String MARKER_PROBLEM = "merlin.marker.problem";
		try {
			file.deleteMarkers(MARKER_PROBLEM, true, IResource.DEPTH_INFINITE);
			for (ValidationIssue issue : issues) {
				IMarker marker = file.createMarker(MARKER_PROBLEM);
				marker.setAttribute(IMarker.MESSAGE,  issue.getIssue());
				marker.setAttribute(IMarker.SEVERITY, issue.getLevel()==IssueLevel.ERROR? IMarker.SEVERITY_ERROR : (issue.getLevel()==IssueLevel.WARNING? IMarker.SEVERITY_WARNING : IMarker.SEVERITY_INFO));
				marker.setAttribute(IMarker.LOCATION, issue.getWhereName());
			}
		} 
		catch (CoreException e) {
//			System.err.println("[merlin] Problem deleting markers from "+file+" "+e);
		}
	}

	public static void cleanMarkers(IFile file) {
		String MARKER_PROBLEM = "merlin.marker.problem";
		try {
			file.deleteMarkers(MARKER_PROBLEM, true, IResource.DEPTH_INFINITE);
		} 
		catch (CoreException e) {}
	}
	
	public static String readFile(File f) {
		try (BufferedReader br = new BufferedReader(new FileReader(f))){
		    StringBuilder sb = new StringBuilder();
		    String line = br.readLine();

		    while (line != null) {
		        sb.append(line);
		        sb.append(System.lineSeparator());
		        line = br.readLine();
		    }
		    return sb.toString();
		} catch(IOException exp) {
			return null;
		}
	}
}
