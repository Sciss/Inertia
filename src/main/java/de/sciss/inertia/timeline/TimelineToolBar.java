/*
 *  TimelineToolBar.java
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
 *		13-Aug-05	copied from de.sciss.eisenkraut.timeline.TimelineToolBar
 */

package de.sciss.inertia.timeline;

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
//import de.sciss.eisenkraut.*;
//import de.sciss.eisenkraut.edit.*;
//import de.sciss.eisenkraut.gui.*;
//import de.sciss.eisenkraut.io.*;
//import de.sciss.eisenkraut.
import de.sciss.inertia.gui.CatchAction;
import de.sciss.inertia.gui.ToolAction;
import de.sciss.inertia.gui.ToolActionEvent;
import de.sciss.inertia.gui.ToolActionListener;
import de.sciss.inertia.gui.ToolBar;

import de.sciss.app.AbstractApplication;

import de.sciss.gui.DoClickAction;
import de.sciss.gui.GUIUtil;
import de.sciss.gui.HelpGlassPane;

/**
 *	A palette of tools for editing
 *	objects in the timline frame. Window
 *	key commands are installed: F1 to F6
 *	for the tools and capslock for toggling
 *	the blending option.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.72, 25-Mar-05
 */
public class TimelineToolBar
extends ToolBar
{
	/**
	 *	Creates a tool palette with
	 *	default buttons for editing the timeline frame.
	 *
	 *	@param	root	Application root
	 */
// INERTIA
//	public TimelineToolBar( Main root )
	public TimelineToolBar()
	{
// INERTIA
//		super( root, ToolBar.HORIZONTAL );
super( ToolBar.HORIZONTAL );

		ToolAction			toolAction;
// INERTIA
//		BlendingAction		actionBlending;
		CatchAction			actionCatch;
		Icon[]				icons;
		JToggleButton		toggle;
		final Preferences	prefs = AbstractApplication.getApplication().getUserPrefs();

		actionCatch		= new CatchAction( prefs ); // .node( PrefsUtil.NODE_SHARED ));
		toggle			= actionCatch.getButton();
// INERTIA
//		root.menuFactory.addGlobalKeyCommand( new DoClickAction( toggle, KeyStroke.getKeyStroke( KeyEvent.VK_V, 0 )));
GUIUtil.createKeyAction( toggle, KeyStroke.getKeyStroke( KeyEvent.VK_V, 0 ));
		HelpGlassPane.setHelp( toggle, "ToolCatch" );
		this.addToggleButton( toggle, 2 );
		this.addSeparator();

		toolAction		= new ToolAction( ToolAction.POINTER );
        toggle			= new JToggleButton( toolAction );
		toolAction.setIcons( toggle );
		GUIUtil.createKeyAction( toggle, KeyStroke.getKeyStroke( KeyEvent.VK_F1, 0 ));
        HelpGlassPane.setHelp( toggle, "TimelineToolPointer" );
  		this.addToggleButton( toggle, 0 );
        
		toolAction		= new ToolAction( ToolAction.LINE );
        toggle			= new JToggleButton( toolAction );
		toolAction.setIcons( toggle );
		GUIUtil.createKeyAction( toggle, KeyStroke.getKeyStroke( KeyEvent.VK_F2, 0 ));
        HelpGlassPane.setHelp( toggle, "TimelineToolLine" );
toolAction.setEnabled( false );	// XXX not yet implemented
  		this.addToggleButton( toggle, 0 );

		toolAction		= new ToolAction( ToolAction.PENCIL );
        toggle			= new JToggleButton( toolAction );
		toolAction.setIcons( toggle );
		GUIUtil.createKeyAction( toggle, KeyStroke.getKeyStroke( KeyEvent.VK_F3, 0 ));
        HelpGlassPane.setHelp( toggle, "TimelineToolPencil" );
  		this.addToggleButton( toggle, 0 );
      
		toolAction		= new ToolAction( ToolAction.ZOOM );
        toggle			= new JToggleButton( toolAction );
		toolAction.setIcons( toggle );
		GUIUtil.createKeyAction( toggle, KeyStroke.getKeyStroke( KeyEvent.VK_F4, 0 ));
        HelpGlassPane.setHelp( toggle, "TimelineToolZoom" );
  		this.addToggleButton( toggle, 0 );
      
// INERTIA
//		this.addSeparator();
//		actionBlending  = new BlendingAction( prefs.node( BlendingAction.DEFAULT_NODE ));
//		toggle			= actionBlending.getButton();
//		root.menuFactory.addGlobalKeyCommand( new DoClickAction( toggle, KeyStroke.getKeyStroke( KeyEvent.VK_CAPS_LOCK, 0 )));
//		HelpGlassPane.setHelp( toggle, "ToolBlending" );
//		this.addToggleButton( toggle, 1 );
	}
}