//
//  WindowHandler.java
//  Inertia
//
//  Created by Hanns Holger Rutz on 21.05.05.
//  Copyright 2005 __MyCompanyName__. All rights reserved.
//
//		07-Aug-05	copied from de.sciss.eisenkraut.gui.WindowHandler

package de.sciss.inertia.gui;

import java.awt.Font;

import de.sciss.gui.AbstractWindowHandler;

public class WindowHandler
extends AbstractWindowHandler
{
	public WindowHandler()
	{
		super();
	}
	
	public Font getDefaultFont()
	{
		return GraphicsUtil.smallGUIFont;
	}
}
