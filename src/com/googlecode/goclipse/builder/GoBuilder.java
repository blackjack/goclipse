package com.googlecode.goclipse.builder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;

import com.googlecode.goclipse.Activator;
import com.googlecode.goclipse.Environment;
import com.googlecode.goclipse.go.lang.lexer.Lexer;
import com.googlecode.goclipse.go.lang.lexer.Tokenizer;
import com.googlecode.goclipse.go.lang.model.Package;
import com.googlecode.goclipse.go.lang.parser.PackageParser;

/**
 * This class is the target called by the Eclipse environment to
 * build the active Go projects in the workspace.
 */
public class GoBuilder extends IncrementalProjectBuilder {
	
	public static final String  BUILDER_ID = "com.googlecode.goclipse.goBuilder";
	private Map<String, String> goEnv      = new HashMap<String, String>();
	private GoCompiler 		    compiler;
	
	private boolean onlyFullBuild = false;
	
	/**
	 * 
	 */
	class CollectResourceDeltaVisitor implements IResourceDeltaVisitor {
		
		List<IResource> added   = new ArrayList<IResource>();
		List<IResource> removed = new ArrayList<IResource>();
		List<IResource> changed = new ArrayList<IResource>();

		@Override
		public boolean visit(IResourceDelta delta) throws CoreException {
			IResource resource = delta.getResource();
			
			if (resource instanceof IFile && resource.getName().endsWith(GoConstants.GO_SOURCE_FILE_EXTENSION)) {
				
				switch (delta.getKind()) {
					case IResourceDelta.ADDED:
						// handle added resource
						added.add(resource);
						break;
						
					case IResourceDelta.REMOVED:
						// handle removed resource
						removed.add(resource);
						break;
						
					case IResourceDelta.CHANGED:
						// handle changed resource
						changed.add(resource);
						break;
				}
			}
			// return true to continue visiting children.
			return true;
		}

		public List<IResource> getAdded() {
			return added;
		}

		public List<IResource> getRemoved() {
			return removed;
		}

		public List<IResource> getChanged() {
			return changed;
		}
	}

	/**
	 * 
	 */
	class CollectResourceVisitor implements IResourceVisitor {
		private List<IResource> collected = new ArrayList<IResource>();

		@Override
		public boolean visit(IResource resource) {
			if (resource instanceof IFile && resource.getName().endsWith(".go")) {
				collected.add(resource);
			}
			// return true to continue visiting children.
			return true;
		}

		public List<IResource> getCollectedResources() {
			return collected;
		}
	}

	/**
	 * Check to see if the Go compiler has been updated since the last build took place.
	 */
	public static void checkForCompilerUpdates(boolean delay) {
		Job job = new GoCompilerUpdateJob();
		
		if (delay) {
			job.schedule(4000);
		} else {
			job.schedule();
		}
	}

	/**
	 * 
	 */
	public GoBuilder() {}
	
	/**
	 * 
	 */
	@Override
	@SuppressWarnings("rawtypes")
	protected IProject[] build(int kind, Map args, IProgressMonitor monitor) throws CoreException {
		
		IProject project = getProject();
		
		if (!checkBuild()) {
			return null;
		}
		
		if (compiler.requiresRebuild(project)) {
			kind = FULL_BUILD;
		}
		
		try {
			
			if (kind == FULL_BUILD || onlyFullBuild) {
				fullBuild(monitor);
				onlyFullBuild = false;
			} else {
				IResourceDelta delta = getDelta(project);
				if (delta == null) {
					fullBuild(monitor);
				} else {
					incrementalBuild(delta, monitor);
				}
			}
			
			compiler.updateVersion(project);
			
		} catch(Exception e) {
			Activator.logError(e);
		}
		
		// no project dependencies (yet)
		return null;
	}

	/**
	 * @return
	 * @throws CoreException
	 */
	private boolean checkBuild() throws CoreException {
		
		if (!Environment.INSTANCE.isValid()){
			MarkerUtilities.addMarker(getProject(), GoConstants.INVALID_PREFERENCES_MESSAGE);
			return false;
			
		} else {
			
			if (compiler == null){
				compiler = new GoCompiler();
			}
			
			return true;
		}
	}

	/**
	 * @param pmonitor
	 * @throws CoreException
	 */
	protected void fullBuild(final IProgressMonitor pmonitor)
			throws CoreException {
		Activator.logInfo("fullBuild");
		
		final SubMonitor monitor = SubMonitor.convert(pmonitor, 2000);
		CollectResourceVisitor crv = new CollectResourceVisitor();
		getProject().accept(crv);
		monitor.worked(20);

		List<IResource> toCompile = new ArrayList<IResource>();
		toCompile.addAll(crv.getCollectedResources());  // full build means
														// everything should be
														// compiled
		
		clean(monitor.newChild(10));
		
		doBuild(toCompile, monitor);
		Activator.logInfo("fullBuild - done");
		getProject().refreshLocal(IResource.DEPTH_INFINITE, null);
	}

	/**
	 * @param fileList
	 * @param monitor
	 * @throws CoreException
	 */
	private void doBuild(List<IResource> fileList, final SubMonitor monitor) throws CoreException {
		
		IProject project = getProject();
		Set<String> packages = new HashSet<String>();
		
		int cost = 2000/(fileList.size()+1);  // not looking for complete accuracy, just some feedback
		
		for(IResource filename:fileList) {
			File file = new File(filename.getLocation().toOSString());
			
			if ( file.isFile() ) {
				
				try {
					
					if ( isCommandFile(file) ){
						// if it is a command file, compile as such
						monitor.beginTask("Compiling command file "+file.getName(), cost);
						compiler.compileCmd(project, monitor.newChild(100), file);
						
					} else {
						// else if not a command file, schedule to build the package
						String pkgpath = computePackagePath(file);
						

						if ( !packages.contains(pkgpath) ) {
							monitor.beginTask("Compiling package "+file.getName().replace(".go", ""), cost);
							compiler.compilePkg(project, monitor.newChild(100), pkgpath, file);
							packages.add(pkgpath);
						}
					}
					
				} catch (IOException e) {
					Activator.logError(e);
				}
			}
		}
	}
	
	/**
	 * 
	 * @param file
	 * @return
	 */
	public final String computePackagePath(File file) {
		IProject project = getProject();
		final IPath projectLocation = project.getLocation();
		final IFile ifile = project.getFile(file.getAbsolutePath().replace(project.getLocation().toOSString(), ""));
		
		IPath pkgFolder = Environment.INSTANCE.getPkgOutputFolder();
		String pkgname  = ifile.getParent().getLocation().toOSString();
		pkgname = pkgname.replace(projectLocation.toOSString(), "");
		String[] split = pkgname.split(File.separator);
		String path = projectLocation.toOSString()+"/"+pkgFolder;
		for(int i = 2; i< split.length; i++){
			path += "/"+split[i];
		}
		
		return path+GoConstants.GO_LIBRARY_FILE_EXTENSION;
	}


	/**
	 * @param delta
	 * @param pmonitor
	 * @throws CoreException
	 */
	protected void incrementalBuild(IResourceDelta delta,
			IProgressMonitor pmonitor) throws CoreException {
		
		SubMonitor monitor = SubMonitor.convert(pmonitor, 170);
		
		// collect resources
		CollectResourceDeltaVisitor crdv = new CollectResourceDeltaVisitor();
		delta.accept(crdv);

		monitor.worked(20);
		// remove
		List<IResource> toRemove = crdv.getRemoved();
		
		if (toRemove.size() > 0){
			fullBuild(pmonitor);
		} else {
			Activator.logInfo("incrementalBuild");
			
			// compile
			List<IResource> resourcesToCompile = new ArrayList<IResource>();
			resourcesToCompile.addAll(crdv.getAdded());
			resourcesToCompile.addAll(crdv.getChanged());
	
			IProject project 	  = getProject();
			Set<String> toCompile = new HashSet<String>();
			Set<String> packages  = new HashSet<String>();
			
			for (IResource res : resourcesToCompile) {
				File file = res.getLocation().toFile();
				
				if ( file.isFile() ) {
					
					try {
						if ( isCommandFile(file) ){
							// if it is a command file, compile as such
							compiler.compileCmd(project, monitor.newChild(100), file);
							
						} else {
							fullBuild(pmonitor);
//							// else if not a command file, schedule to build the package
//							String pkgpath = computePackagePath(file);
//
//							/**
//							 * Not only do we have to compile this package, but we have
//							 * to (afterwards) compile every package that depends on this
//							 * one.
//							 */
//							Set<String> depends = DependencyGraph.getForProject(project).getReverseDependencies(pkgpath);
//							for(String s:depends){
//								System.out.println("> "+s);
//							}
//
//							if ( !packages.contains(pkgpath) ) {
//								compiler.compilePkg(project, monitor.newChild(100), pkgpath, file);
//								packages.add(pkgpath);
//							}
						}
						
					} catch (IOException e) {
						Activator.logError(e);
					}
					
				} else {
					
					// schedule folder for compilation of the contents
					toCompile.add(res.getLocation().toOSString());
				}
			}
			
			Activator.logInfo("incrementalBuild - done");
		}
	}

	/**
	 * TODO this needs to be centralized into a common index...
	 * 
	 * @param file
	 * @return
	 * @throws IOException
	 */
	private boolean isCommandFile(File file) throws IOException {
		Lexer 		  lexer         = new Lexer();
		Tokenizer 	  tokenizer 	= new Tokenizer(lexer);
		PackageParser packageParser = new PackageParser(tokenizer);
		
		BufferedReader reader = new BufferedReader(new FileReader(file));
		String temp = "";
		StringBuilder builder = new StringBuilder();
		while( (temp = reader.readLine()) != null ) {
			builder.append(temp);
			builder.append("\n");
		}
		reader.close();
		lexer.scan(builder.toString());
		Package pkg = packageParser.getPckg();
		
		if (pkg != null && "main".equals(pkg.getName())) {
			return true;
		}
		
		return false;
	}

	@Override
	protected void clean(IProgressMonitor monitor) throws CoreException {
		IProject project = getProject();
		
		MarkerUtilities.deleteAllMarkers(project);
		
		IPath binPath = Environment.INSTANCE.getBinOutputFolder(project);
		File binFolder = new File(project.getLocation().append(binPath).toOSString());
		
		if (binFolder.exists()) {
			deleteFolder(binFolder, true);
		}
		
		IPath pkgPath = Environment.INSTANCE.getPkgOutputFolder(project);
		
		File pkgFolder = new File(project.getLocation().append(pkgPath).toOSString());
		
		if (pkgFolder.exists()) {
			deleteFolder(pkgFolder, true);
		}
		
		project.accept(new IResourceVisitor() {
			@Override
			public boolean visit(IResource resource) throws CoreException {
				IPath relativePath = resource.getProjectRelativePath();
				Environment instance = Environment.INSTANCE;
				String lastSegment = relativePath.lastSegment();
				IPath rawLocation = resource.getRawLocation();
				if (rawLocation != null) {
					File file = rawLocation.toFile();
					if (file.exists() && file.isDirectory() &&
						(instance.isCmdFile(relativePath) || instance.isPkgFile(relativePath))
						&& (lastSegment.equals(GoConstants.OBJ_FILE_DIRECTORY) || lastSegment.equals(GoConstants.TEST_FILE_DIRECTORY)))
					{
						deleteFolder(file, true);
					}
				}
				return resource instanceof IContainer;
			}
		}, IResource.DEPTH_INFINITE, false);
		
		project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
	}

	private boolean deleteFolder(File f, boolean justContents) {
		if (!f.exists()) {
			return false;
		}
		if (f.isDirectory()) {
			String[] children = f.list();
			for (int i = 0; i < children.length; i++) {
				boolean success = deleteFolder(new File(f, children[i]), false);
				if (!success) {
					return false;
				}
			}
			if (!justContents) {
				f.delete();
			}
		} else {
			f.delete();
		}
		return true;
	}

	public static boolean dependenciesExist(IProject project,
			String[] dependencies) {
		if (project != null){
			for (String dependency : dependencies) {
				IResource member = project.findMember(dependency);
				if (member==null || !member.getRawLocation().toFile().exists()){
					return false;
				}
			}
			return true;
		} else {
			return false;
		}
	}

}
