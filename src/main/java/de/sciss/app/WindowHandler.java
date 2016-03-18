//
//  WindowHandler.java
//  FScape
//
//  Created by Hanns Holger Rutz on 21.05.05.
//  Copyright 2005 __MyCompanyName__. All rights reserved.
//

package de.sciss.app;

import java.awt.Font;
import java.util.Iterator;
import javax.swing.JFrame;

// @version	0.13, 15-Sep-05
public interface WindowHandler
{
	public static final Object OPTION_EXCLUDE_FONT		= "excludefont";	// value : java.util.List of components
	public static final Object OPTION_GLOBAL_MENUBAR	= "globalmenu";		// value : null

	public void addWindow( JFrame f, java.util.Map options );
	public void removeWindow( JFrame f, java.util.Map options );
	public Iterator getWindows();
	public Font getDefaultFont();
}
