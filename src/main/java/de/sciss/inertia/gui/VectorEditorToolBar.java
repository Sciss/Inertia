/*
 *  VectorEditorToolBar.java
 *  Inertia
 *
 *  Copyright (c) 2004-2005 Hanns Holger Rutz. All rights reserved.
 *
 *	This software is free software; you can redistribute it and/or
 *	modify it under the terms of the GNU General Public License
 *	as published by the Free Software Foundation; either
 *	version 2, june 1991 of the License, or (at your option) any later version.
 *
 *	This software is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *	General Public License for more details.
 *
 *	You should have received a copy of the GNU General Public
 *	License (gpl.txt) along with this software; if not, write to the Free Software
 *	Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 *
 *
 *  Changelog:
 *		07-Aug-05	created from de.sciss.meloncillo.gui.VectorEditorToolBar
 */

package de.sciss.inertia.gui;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.util.*;
import java.util.prefs.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.undo.*;

// INERTIA
//import de.sciss.meloncillo.*;
//import de.sciss.meloncillo.edit.*;
//import de.sciss.meloncillo.gui.*;
//import de.sciss.meloncillo.io.*;
//import de.sciss.meloncillo.receiver.*;
//import de.sciss.meloncillo.session.*;
//import de.sciss.meloncillo.util.*;

import de.sciss.app.EventManager;
import de.sciss.gui.*;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.72, 25-Mar-05
 */
public class VectorEditorToolBar
extends ToolBar
implements EventManager.Processor
{
	private final EventManager elm = new EventManager( this );

	/**
	 *	Creates a tool palette with
	 *	default buttons for editing the timeline frame.
	 *
	 */
// INERTIA
//	public VectorEditorToolBar( Main root )
	public VectorEditorToolBar()
	{
// INERTIA
//		super( root, ToolBar.HORIZONTAL );
		super( HORIZONTAL );

		ToolAction			toolAction;
		Icon[]				icons;
		JToggleButton		toggle;

		toolAction		= new ToolAction( ToolAction.POINTER );
        toggle			= new JToggleButton( toolAction );
		toolAction.setIcons( toggle );
		GUIUtil.createKeyAction( toggle, KeyStroke.getKeyStroke( KeyEvent.VK_F1, 0 ));
        HelpGlassPane.setHelp( toggle, "VectorEditorToolPointer" );
		this.addToggleButton( toggle, 0 );
        
		toolAction		= new ToolAction( ToolAction.LINE );
        toggle			= new JToggleButton( toolAction );
		toolAction.setIcons( toggle );
		GUIUtil.createKeyAction( toggle, KeyStroke.getKeyStroke( KeyEvent.VK_F2, 0 ));
        HelpGlassPane.setHelp( toggle, "VectorEditorToolLine" );
toolAction.setEnabled( false );	// XXX not yet implemented
		this.addToggleButton( toggle, 0 );

		toolAction		= new ToolAction( ToolAction.PENCIL );
        toggle			= new JToggleButton( toolAction );
		toolAction.setIcons( toggle );
		GUIUtil.createKeyAction( toggle, KeyStroke.getKeyStroke( KeyEvent.VK_F3, 0 ));
        HelpGlassPane.setHelp( toggle, "VectorEditorToolPencil" );
		this.addToggleButton( toggle, 0 );
	}
}