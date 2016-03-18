/*
 *  AbstractWindowHandler.java
 *  de.sciss.app package
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
 *		20-May-05	created
 */

package de.sciss.gui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Iterator;
import javax.swing.JFrame;

import de.sciss.app.AbstractApplication;

/**
 *  A rudimentary implementation of the <code>de.sciss.app.WindowHandler</code>
 *	interface.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.16, 15-Sep-05
 */
public abstract class AbstractWindowHandler
implements de.sciss.app.WindowHandler
{
	private final java.util.List	collWindows	= new ArrayList();

	protected AbstractWindowHandler()
	{
		super();
	}

	/**
	 *	@sync	this method is synchronized
	 */
	public void addWindow( JFrame f, java.util.Map options )
	{
		synchronized( collWindows ) {
			if( collWindows.contains( f )) {
				throw new RuntimeException( "duplicate window registration : "+f.getTitle() );
			}
			collWindows.add( f );
		}
	}

	/**
	 *	@sync	this method is synchronized
	 */
	public void removeWindow( JFrame f, java.util.Map options )
	{
		synchronized( collWindows ) {
			if( !collWindows.remove( f )) {
				throw new RuntimeException( "tried to remove unknown window : "+f.getTitle() );
			}
		}
	}

	public Iterator getWindows()
	{
		synchronized( collWindows ) {
			return collWindows.iterator();	// XXX or a copy?
		}
	}

	public static void setDeepFont( Container c, java.util.List exclude )
	{
		AbstractWindowHandler.setDeepFont( c,
			AbstractApplication.getApplication().getWindowHandler().getDefaultFont(), exclude );
	}

	private static void setDeepFont( Container c, Font fnt, java.util.List exclude )
	{
		final Component[] comp = c.getComponents();
		
		c.setFont( fnt );
		for( int i = 0; i < comp.length; i++ ) {
			if( (exclude != null) && exclude.contains( comp[ i ])) continue;

			if( comp[ i ] instanceof Container ) {
				setDeepFont( (Container) comp[i], fnt, exclude );
			} else {
				comp[ i ].setFont( fnt );
			}
		}
	}
}