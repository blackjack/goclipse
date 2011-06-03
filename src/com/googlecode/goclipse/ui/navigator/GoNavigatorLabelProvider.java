package com.googlecode.goclipse.ui.navigator;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;

/**
 * 
 * 
 * @author Mihailo Vasiljevic
 */
public class GoNavigatorLabelProvider extends LabelProvider {

	public GoNavigatorLabelProvider() {

	}
	
	@Override
	public Image getImage(Object element) {
		if (element instanceof GoSourceFolder) {
			return ImageCache.getInstance().getCachedImage(ImageCache.SOURCE_FOLDER_ICON_PATH);
		} else if (element instanceof GoPackage) {
			return ImageCache.getInstance().getCachedImage(ImageCache.PACKAGE_ICON_PATH);
		}
		
		return null;
	}

	@Override
	public String getText(Object element) {
		if (element instanceof IProject) {
			return ((IProject) element).getName();
		} else if (element instanceof IGoElement) {
			return ((IGoElement) element).getName();
		} else if (element instanceof IResource) {
			return ((IResource) element).getName();
		}
		
		return null;
	}

}
