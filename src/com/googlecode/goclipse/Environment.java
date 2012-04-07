package com.googlecode.goclipse;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.InvalidPropertiesFormatException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.IPreferencesService;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.util.Util;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import com.googlecode.goclipse.builder.Arch;
import com.googlecode.goclipse.builder.GoConstants;
import com.googlecode.goclipse.builder.ProcessIStreamFilter;
import com.googlecode.goclipse.preferences.PreferenceConstants;

/**
 * Provides environmental utility methods for acquiring and storing a user
 * session's contextual data.
 * 
 * @author steel
 */
public class Environment {

	private static final String PROJECT_SOURCE_FOLDERS = "com.googlecode.goclipse.environment.source.folders";
	private static final String PROJECT_PKG_OUTPUT_FOLDERS = "com.googlecode.goclipse.environment.pkg.output.folders";
	private static final String PROJECT_BIN_OUTPUT_FOLDERS = "com.googlecode.goclipse.environment.bin.output.folders";

	public static final String DEFAULT_PKG_OUTPUT_FOLDER = "pkg";
	public static final String DEFAULT_BIN_OUTPUT_FOLDER = "bin";

	public static final boolean DEBUG = Boolean.getBoolean("goclipse.debug");
	
	public static final Environment INSTANCE = new Environment();
	private final IPreferencesService preferences;
	private Map<String, Properties> propertiesMap = new HashMap<String, Properties>();
	private String depToolPath;
	
	private static final int DEP_TOOL_VERSION = 3;

	private Environment() {
		preferences = Platform.getPreferencesService();
	}

	/**
	 * 
	 * @return true if the preferences have been set for all values
	 */
	public boolean isValid() {
		String goarch = Activator.getDefault().getPreferenceStore().getString(
				PreferenceConstants.GOARCH);
		
		String goos = Activator.getDefault().getPreferenceStore().getString(
				PreferenceConstants.GOOS);
		
		String goroot = Activator.getDefault().getPreferenceStore().getString(
				PreferenceConstants.GOROOT);
		
		if (goroot == null || goroot.length() == 0 || goos == null
				|| goos.length() == 0 || goarch == null || goarch.length() == 0) {
				//Activator.getDefault().getLog().log(new Status(Status.ERROR, Activator.PLUGIN_ID, GoConstants.INVALID_PREFERENCES_MESSAGE));
			return false;
		}
		
		return true;
	}
	
	class MsgFilter implements ProcessIStreamFilter {
		public boolean hadError = false;
		@Override
		public void process(InputStream iStream) {
			try {
				
				InputStreamReader isr = new InputStreamReader(iStream);
			    BufferedReader br = new BufferedReader(isr);
			    String line;
		      
			    while ((line = br.readLine()) != null) {
		        	Activator.logInfo("error in parse connector:" + line);
		        	hadError = true;
		        }
			    
			} catch(Exception e) {
				Activator.logInfo(e);
			}
		}

		@Override
		public void clear() {
			hadError = false;
		}
		
	}

	private void saveSource(File toolsFile, URL srcURL, String outPath) {
		if (srcURL != null) {
			
			try {
				File outFile = new File(outPath);
				InputStream in = srcURL.openStream();

				// For Overwrite the file.
				OutputStream out = new FileOutputStream(outFile);

				byte[] buf = new byte[1024];
				int len;
				while ((len = in.read(buf)) > 0) {
					out.write(buf, 0, len);
				}
				
				in.close();
				out.close();
				Activator.logInfo("File copied:" + outPath);
				
			} catch (IOException e) {
				Activator.logError(e);
			}
		}
	}

	/**
	 * @param project
	 * @return
	 */
	private Properties getProperties(IProject project) {
		
		Properties properties = propertiesMap.get(project.getName());
		
		if (properties == null) {
			properties = loadProperties(project);
			propertiesMap.put(project.getName(), properties);
		}
		
		return properties;
	}

	/**
	 * @param project
	 * @return
	 */
	private Properties loadProperties(IProject project) {
		
		Properties properties = new Properties();
		
		try {
			properties.loadFromXML(new FileInputStream(project
					.getWorkingLocation(Activator.PLUGIN_ID)
					+ "/properties.xml"));
			
		} catch (InvalidPropertiesFormatException e) {
			Activator.logError(e);
		} catch (FileNotFoundException e) {
			IPath path = project.getWorkingLocation(Activator.PLUGIN_ID);
			try {
	            properties.storeToXML(new FileOutputStream(path.toOSString()
	            		+ "/properties.xml", false), " this is a comment");
            } catch (FileNotFoundException e1) {
            	Activator.logError(e);
            } catch (IOException e1) {
            	Activator.logError(e);
            }
			saveProperties(project);
		} catch (IOException e) {
			Activator.logError(e);
		}
		
		return properties;
	}

	/**
	 * @param project
	 * @return
	 */
	private void saveProperties(IProject project) {
		if(project==null){
			Activator.logError("Null project given while atempting to save properties.");
			return;
		}
		
		Properties properties = getProperties(project);
		IPath path = project.getWorkingLocation(Activator.PLUGIN_ID);
		
		try {
			Activator.logInfo("writing to "
					+ path.toOSString()
					+ "/properties.xml");
			
			properties.storeToXML(new FileOutputStream(path.toOSString()
					+ "/properties.xml", false), " this is a comment");
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * @return the preferences
	 */
	public IPreferencesService getPreferences() {
		return preferences;
	}

	/**
	 * Determines the current project of the active context; if possible. else
	 * return null.
	 * 
	 * @return IProject | null
	 */
	public IProject getCurrentProject() {
		IProject project = null;

		try {
			IWorkbench iWorkbench 			  = PlatformUI.getWorkbench();
			IWorkbenchWindow iWorkbenchWindow = iWorkbench.getActiveWorkbenchWindow();
			if (iWorkbenchWindow != null) {
				IWorkbenchPage iWorkbenchPage 	  = iWorkbenchWindow.getActivePage();
				ISelection iSelection 			  = iWorkbenchPage.getSelection();
				iSelection 						  = iWorkbenchPage.getSelection();
				
				if(iSelection instanceof TextSelection){
					IEditorInput editorInput = iWorkbenchWindow.getActivePage().getActiveEditor().getEditorInput();
					if(editorInput instanceof IFileEditorInput){
						return ((IFileEditorInput)editorInput).getFile().getProject();
					}
				}
				
				project = extractSelection(iSelection).getProject();
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}

		if (project == null) {
			IWorkspace root = ResourcesPlugin.getWorkspace();
			IProject[] projects = root.getRoot().getProjects();

			for (int i = 0; i < projects.length - 1; i++) {
				for (int j = i + 1; j < projects.length; j++) {
					if (projects[i].getName().compareToIgnoreCase(
							projects[j].getName()) > 0) { // ascending
						// sort
						IProject temp = projects[i];
						projects[i] = projects[j]; // swapping
						projects[j] = temp;

					}
				}
			}

			if (projects.length > 0) {
				return projects[0];
			}
		}
		
		return project;
	}
	
	/**
	 * 
	 * @return
	 */
	public String getAbsoluteProjectPath(){
		IProject project = getCurrentProject();
		IPath path = ResourcesPlugin.getWorkspace().getRoot().getRawLocation();
		return path.toOSString()+"/"+project.getName();
	}

	/**
	 * Returns the selected resource
	 * 
	 * @param sel
	 * @return
	 */
	public IResource extractSelection(ISelection sel) {
		if (!(sel instanceof IStructuredSelection))
			return null;
		IStructuredSelection ss = (IStructuredSelection) sel;
		Object element = ss.getFirstElement();
		if (element instanceof IResource)
			return (IResource) element;
		if (!(element instanceof IAdaptable))
			return null;
		IAdaptable adaptable = (IAdaptable) element;
		Object adapter = adaptable.getAdapter(IResource.class);
		return (IResource) adapter;
	}


	/**
	 * Returns the active editor or null if there is not one
	 * 
	 * @return
	 */
	public IEditorPart getActiveEditor() {
		IWorkbench iworkbench = PlatformUI.getWorkbench();
		if (iworkbench != null) {
			IWorkbenchWindow iworkbenchwindow = iworkbench
					.getActiveWorkbenchWindow();
			if (iworkbenchwindow != null) {
				IWorkbenchPage iworkbenchpage = iworkbenchwindow
						.getActivePage();
				if (iworkbenchpage != null) {
					return iworkbenchpage.getActiveEditor();
				}
			}
		}
		return null;
	}

	/**
	 * Get the shell of the Active Workbench Window
	 * 
	 * @return
	 */
	public Shell getShell() {
		IWorkbench workbench = PlatformUI.getWorkbench();
		IWorkbenchWindow wWindow = workbench.getActiveWorkbenchWindow();
		return wWindow.getShell();
	}

	/**
	 * Sets the source folder properties on the given project
	 * 
	 * @param sourceFolders
	 */
	public void setSourceFolders(IProject project, String[] sourceFolders) {
		Properties properties = getProperties(project);
		String sourceFolderStr = "";
		for (String folder : sourceFolders) {
			sourceFolderStr = sourceFolderStr + folder + ";";
		}
		properties.put(PROJECT_SOURCE_FOLDERS, sourceFolderStr);
		saveProperties(project);
	}

	/**
	 * Return a list of source folders for the currently active project.
	 * 
	 * @return
	 */
	public String[] getSourceFoldersAsStringArray(IProject project) {
		Properties properties = getProperties(project);
		String str = properties.getProperty(PROJECT_SOURCE_FOLDERS);
		if (str != null) {
			return str.split(";");
		} else {
			return new String[] {};
		}
	}

	/**
	 * Return a list of source folders for the currently active project.
	 * 
	 * @return
	 */
	public List<IFolder> getSourceFolders(IProject project) {
		List<IFolder> result = new ArrayList<IFolder>();
		
		for (String path : getSourceFoldersAsStringArray(project)) {
			IResource resource = project.findMember(path);
			
			if (resource instanceof IFolder) {
				result.add((IFolder)resource);
			}
			
		}
		
		return result;
	}

	/**
	 * Set the output folder for the given project
	 */
	public void setPkgOutputFolder(IProject project, IPath outputFolder) {
		Properties properties = getProperties(project);
		properties.setProperty(PROJECT_PKG_OUTPUT_FOLDERS, outputFolder.toOSString());
		saveProperties(project);
	}

	/**
	 * Return the output folder for the active project
	 */
	public IPath getPkgOutputFolder(IProject project) {
		Properties properties = getProperties(project);
		String property = properties.getProperty(PROJECT_PKG_OUTPUT_FOLDERS);
		if (property == null) {
			return getDefaultPkgOutputFolder();
		} else {
			IPath path = Path.fromOSString(property);
			return path;
		}
	}

	private IPath getDefaultPkgOutputFolder() {
		String goarch = Activator.getDefault().getPreferenceStore()
		.getString(PreferenceConstants.GOARCH);
		String goos = Activator.getDefault().getPreferenceStore()
		.getString(PreferenceConstants.GOOS);
		return Path.fromOSString(DEFAULT_PKG_OUTPUT_FOLDER).append(goos+"_"+goarch);
	}

//	/**
//	 * Set the output folder for the active project
//	 */
//	public void setBinOutputFolder(IPath outputFolder) {
//		setBinOutputFolder(getCurrentProject(), outputFolder);
//	}

	/**
	 * Set the output folder for the given project
	 */
	public void setBinOutputFolder(IProject project, IPath outputFolder) {
		Properties properties = getProperties(project);
		properties.setProperty(PROJECT_BIN_OUTPUT_FOLDERS, outputFolder.toOSString());
		saveProperties(project);
	}

	/**
	 * Return the output folder for the active project
	 */
	public IPath getBinOutputFolder(IProject project) {
		Properties properties = getProperties(project);
		String property       = properties.getProperty(PROJECT_BIN_OUTPUT_FOLDERS);
		if (property == null) {
			return Path.fromOSString(DEFAULT_BIN_OUTPUT_FOLDER);
		} else {
			IPath path = Path.fromOSString(property);
			return path;
		}
	}

	public IPath getDefaultCmdSourceFolder() {
		return Path.fromOSString("src");
	}

	public IPath getDefaultPkgSourceFolder() {
		return Path.fromOSString("src");
	}

	public boolean isStandardLibrary(IProject project, String packagePath) {
		
		String libraryName = packagePath + GoConstants.GO_LIBRARY_FILE_EXTENSION;
		String goroot = Activator.getDefault().getPreferenceStore().getString(PreferenceConstants.GOROOT);
		
		File libFile = Path.fromOSString(goroot).append(getDefaultPkgOutputFolder()).append(libraryName).toFile();
		return libFile.exists();
	}

	/**
	 * @param path
	 * @return
	 */
	public boolean isCmdFile(IPath path) {
		return getDefaultCmdSourceFolder().isPrefixOf(path);
	}

	/**
	 * @param path
	 * @return
	 */
	public boolean isPkgFile(IPath path) {
		return getDefaultPkgSourceFolder().isPrefixOf(path);
	}

	/**
	 * @return
	 */
	public Arch getArch() {
		String goarch = Activator.getDefault().getPreferenceStore().getString(PreferenceConstants.GOARCH);
		return Arch.getArch(goarch);
	}

	/**
	 * @return
	 */
	public String getExecutableExtension() {
		if (PreferenceConstants.OS_WINDOWS.equals(Activator.getDefault().getPreferenceStore().getString(PreferenceConstants.GOOS))){
			return ".exe";
		}
		return "";
	}
	
	/**
	 * @param project
	 * @param folder
	 * @return
	 */
	public boolean isCmdSrcFolder(IProject project, IFolder folder){
		
		if (project == null || folder == null) {
			return false;
		}
		
		boolean isSrcFolder = false;
		String path = folder.getLocation().toOSString();
		
		for (IFolder src:getSourceFolders(project)){
			if (src.getLocation().toOSString().equals(path)) {
				isSrcFolder = true;
				break;
			}
		}
		
		if (!isSrcFolder) {
			return false;
		}
		
		if (folder.getName().equals("cmd") || folder.getName().equals("src")) {
			return true;
		}
		
		return false;
	}

	
	/**
	 * Determines via the file suffix if the file is a test file or not.
	 * @param file
	 * @return
	 */
	public boolean isNonTestSourceFile(IFile file){
		
		if (file==null) {
			return false;
		}
		
		if (file.getName().endsWith("_test.go")) {
			return false;
		}
		
		return true;
	}

	/**
	 * 
	 * @param project
	 * @param file
	 * @return
	 */
	public boolean isSourceFile(IProject project, IFile file) {
		IPath p = file.getProjectRelativePath();
	    IResource res = project.findMember(p);
	    if ( res==null ) {
	    	return false;
	    }
	    
	    if ( !file.getName().endsWith(GoConstants.GO_SOURCE_FILE_EXTENSION) ) {
	    	return false;
	    }
	    
	    for (IFolder folder : getSourceFolders(project)) {
	    	if(file.getLocation().toOSString().startsWith(folder.getLocation().toOSString())) {
	    		return true;
	    	}
	    }
	    
	    return false;
    }
	
	/**
	 * @param project
	 * @return
	 */
	public String[] getGoPath(IProject project) {

		String[] path   = { "" };
		String   goPath = null;
		
		// Project property takes precedence
		if (project != null) {
			goPath = getProperties(project).getProperty(GoConstants.GOPATH);
		}
		
		// Plug-in property comes next
		if (goPath == null || goPath == "") {
			goPath = Activator.getDefault().getPreferenceStore().getString(PreferenceConstants.GOPATH);
		}

		// last ditch effort via a system environment variable
		if (goPath == null || goPath == "") {
			goPath = System.getenv(GoConstants.GOPATH);
		}

		// If null, we give up and just return the default...
		// which is essentially an empty string
		if (goPath == null) {
			return path;
		}
		
		if (Util.isWindows() && goPath.contains(";")){
			path = goPath.split(";");
		
		} else if (goPath.contains(":")) {
			path =  goPath.split(":");
			
		} else {
			path[0] = goPath;
		}

		return path;
	}
}

