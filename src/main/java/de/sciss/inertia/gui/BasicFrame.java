/*
 *  BasicFrame.java
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
 *		11-Aug-05	copied from de.sciss.eisenkraut.gui.BasicFrame
 *					; added getClassPrefs() ; restoreFromPrefs now protected
 */

package de.sciss.inertia.gui;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.prefs.*;
import javax.swing.*;

import de.sciss.inertia.Main;
import de.sciss.inertia.util.PrefsUtil;

import de.sciss.app.AbstractApplication;

import de.sciss.gui.HelpGlassPane;

/**
 *  Common functionality for all application windows.
 *  This class provides means for storing and recalling
 *  window bounds in preferences. All subclass windows
 *  will get a copy of the main menubar as well.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.69, 24-Dec-04
 *
 *  @todo   the window bounds prefs storage sucks like hell
 *          ; there's a bug: if recall-window-bounds is deactivated
 *          the prefs are loaded nevertheless, hence when restarting
 *          the application, the bounds will be those of the
 *          last loaded session
 */
public class BasicFrame
extends JFrame
{
	private Main							root;

	private ComponentListener				cmpListener;
	private WindowListener					winListener;

	// windows bounds get saved to a sub node inside the shared node
	// the node's name is the class name's last part (omitting the package)
	private final Preferences               classPrefs;
    
	/**
	 *  Constructs a new frame.
	 *  A preference node of the subclasse's class name
	 *  is created inside the main preferences' NODE_SHARED
	 *  node. Listeners are installed to track changes
	 *  of the window bounds and visibility which are then saved to the
	 *  class preferences.
	 *
	 *  @param  title   title shown in the frame's title bar
	 */
	public BasicFrame( String title )
	{
		super( title );

		String  className   = getClass().getName();
		classPrefs			= AbstractApplication.getApplication().getUserPrefs().node(
								className.substring( className.lastIndexOf( '.' ) + 1 ));
		
		cmpListener = new ComponentAdapter() {
			public void componentResized( ComponentEvent e )
			{
				classPrefs.put( PrefsUtil.KEY_SIZE, PrefsUtil.dimensionToString( getSize() ));
			}

			public void componentMoved( ComponentEvent e )
			{
				classPrefs.put( PrefsUtil.KEY_LOCATION, PrefsUtil.pointToString( getLocation() ));
			}

			public void componentShown( ComponentEvent e )
			{
				classPrefs.putBoolean( PrefsUtil.KEY_VISIBLE, true );
			}

			public void componentHidden( ComponentEvent e )
			{
				classPrefs.putBoolean( PrefsUtil.KEY_VISIBLE, false );
			}
		};
		winListener = new WindowAdapter() {
			public void windowOpened( WindowEvent e )
			{
				classPrefs.putBoolean( PrefsUtil.KEY_VISIBLE, true );
			}

			public void windowClosing( WindowEvent e )
			{
				classPrefs.putBoolean( PrefsUtil.KEY_VISIBLE, false );
				
			}
		};
		
		addComponentListener( cmpListener );
		addWindowListener( winListener );
   	}
	
	protected Preferences getClassPrefs()
	{
		return classPrefs;
	}
    
	/**
	 *  Frees resources, clears references
	 */
	public void dispose()
	{
		if( root != null ) {
			if( hasMenuBar() ) root.menuFactory.forgetAbout( this );
			root.addComponent( getClass().getName(), null );
//			classPrefs  = null;
			root		= null;
		}
		super.dispose();
	}

	/**
	 *  Restores this frame's bounds and visibility
	 *  from its class preferences.
	 *
	 */
	protected void restoreFromPrefs()
	{
		String		sizeVal = classPrefs.get( PrefsUtil.KEY_SIZE, null );
		String		locVal  = classPrefs.get( PrefsUtil.KEY_LOCATION, null );
		String		visiVal	= classPrefs.get( PrefsUtil.KEY_VISIBLE, null );
		Rectangle   r		= getBounds();

		Dimension d			= PrefsUtil.stringToDimension( sizeVal );
		if( d == null || alwaysPackSize() ) {
			pack();
			d				= getSize();
		}

		r.setSize( d );
		Point p = PrefsUtil.stringToPoint( locVal );
		if( p != null ) {
			r.setLocation( p );
		}
		setBounds( r );
		invalidate();
		validate();
		if( visiVal != null ) {
			setVisible( new Boolean( visiVal ).booleanValue() );
		}
	}

	/**
	 *  Queries whether this frame's bounds
	 *  should be packed automatically to the
	 *  preferred size independent of
	 *  concurrent preference settings
	 *
	 *  @return	<code>true</code>, if the frame wishes
	 *			to be packed each time a custom setSize()
	 *			would be applied in the course of a
	 *			preference recall. The default value
	 *			of <code>true</code> can be modified by
	 *			subclasses by overriding this method.
	 *  @see	java.awt.Window#pack()
	 */
	protected boolean alwaysPackSize()
	{
		return true;
	}

	/**
	 *  Queries whether this frame should
     *  have a copy of the menu bar. The default
     *  implementation returns true, basic palettes
     *  will return false.
	 *
	 *  @return	<code>true</code>, if the frame wishes
	 *			to be given a distinct menu bar
	 */
	protected boolean hasMenuBar()
	{
		return true;
	}

	/**
	 *	MenuFactory uses this method to replace dummy
	 *	menu items such as File->Save with real actions
	 *	depending on the concrete frame. By default this
	 *	method just returns <code>dummyAction</code>, indicating
	 *	that there is no replacement for the dummy action.
	 *	Subclasses may check the provided <code>ID</code>
	 *	and return replacement actions instead.
	 *
	 *	@param	ID	an identifier for the menu item, such
	 *				as <code>MenuFactory.MI_FILE_SAVE</code>.
	 *
	 *  @return		the action to use instead of the inactive
	 *				dummy action, or <code>dummyAction</code> if no
	 *				specific action exists (menu item stays ghosted)
	 *
	 *	@see	MenuFactory#MI_FILE_SAVE
	 *	@see	MenuFactory#gimmeSomethingReal( BasicFrame )
	 */
	protected Action replaceDummyAction( int ID, Action dummyAction )
	{
		return dummyAction;
	}

	/**
	 *  Subclasses should call this
	 *  after having constructed their GUI.
	 *  Then this method will attach a copy of the main menu
	 *  from <code>root.menuFactory</code> and
	 *  restore bounds from preferences.
	 *
	 *  @param  root	application root
	 *
	 */
	protected void init( Main root )
	{
		this.root = root;
		if( hasMenuBar() ) root.menuFactory.gimmeSomethingReal( this );
		HelpGlassPane.attachTo( this );
		restoreFromPrefs();
	}
}