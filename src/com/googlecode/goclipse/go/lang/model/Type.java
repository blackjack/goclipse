package com.googlecode.goclipse.go.lang.model;

import org.eclipse.swt.graphics.Image;

import com.googlecode.goclipse.Activator;

/**
 * 
 */
public class Type extends Node {
	
	private static final long serialVersionUID = 1L;
	
	private TypeClass typeClass = TypeClass.UNKNOWN;
	

	/**
	 * @return the typeClass
	 */
	public TypeClass getTypeClass() {
		return typeClass;
	}

	/**
	 * @param typeClass
	 *            the typeClass to set
	 */
	public void setTypeClass(TypeClass typeClass) {
		this.typeClass = typeClass;
	}

	/**
	 * @return the image
	 */
	@Override
	public Image getImage() {
		
		if (typeClass == TypeClass.STRUCT) {
			return Activator.getImage("icons/struct.png");
		}
		else if (typeClass == TypeClass.INTERFACE) {
			return Activator.getImage("icons/interface.gif");
		}
		else{
			return Activator.getImage("icons/type.png");
		}

	}
}
