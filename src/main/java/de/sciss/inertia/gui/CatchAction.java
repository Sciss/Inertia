/*
 *  CatchAction.java
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
 *		13-Aug-05	copied from de.sciss.eisenkraut.gui.CatchAction
 */

package de.sciss.inertia.gui;

import java.awt.*;
import java.awt.event.*;
import java.util.prefs.*;
import javax.swing.*;
import javax.swing.event.*;

// INERTIA
//import de.sciss.eisenkraut.*;
//import de.sciss.eisenkraut.util.*;
import de.sciss.inertia.util.PrefsUtil;

import de.sciss.app.DynamicAncestorAdapter;
import de.sciss.app.DynamicPrefChangeManager;
import de.sciss.app.LaterInvocationManager;

/**
 *	A class implementing the <code>Action</code> interface
 *	which deals with the catch (timeline position) setting. Each instance
 *	generates a toggle button suitable for attaching to a tool bar;
 *	this button reflects the catch preferences settings.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.72, 27-Mar-05
 */
public class CatchAction
extends AbstractAction
implements LaterInvocationManager.Listener
{
	private final JToggleButton b;
	private final Preferences	prefs;

	/**
	 *	Creates a new instance of an action
	 *	that tracks blending changes
	 */
	public CatchAction( Preferences prefs )
	{
		super();
		this.prefs	= prefs;
		b			= new JToggleButton( this );
		GraphicsUtil.setToolIcons( b, GraphicsUtil.createToolIcons( GraphicsUtil.ICON_CATCH ));
		new DynamicAncestorAdapter( new DynamicPrefChangeManager( prefs,
			new String[] { PrefsUtil.KEY_CATCH }, this )).addTo( b );
	}
	
	/**
	 *	Returns the toggle button
	 *	which is connected to this action.
	 *
	 *	@return	a toggle button which is suitable for tool bar display
	 */
	public JToggleButton getButton()
	{
		return b;
	}

	private void updateButtonState()
	{
		b.setSelected( prefs.getBoolean( PrefsUtil.KEY_CATCH, false ));
	}
	
	public void actionPerformed( ActionEvent e )
	{
		prefs.putBoolean( PrefsUtil.KEY_CATCH, b.isSelected() );
	}

	// o instanceof PreferenceChangeEvent
	public void laterInvocation( Object o )
	{
		updateButtonState();
	}
}
