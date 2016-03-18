/*
 *  PopupListener.java
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
 *		07-Aug-05	copied from de.sciss.meloncillo.gui.PopupListener
 */

package de.sciss.inertia.gui;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

/**
 *  A simple class that can be registered
 *  to a component as a MouseListener.
 *  If a popup trigger is detected, a
 *  poupup menu passed to the constructor
 *  is shown.
 *
 *  A helper class that can be registered
 *  to a component as a MouseListener.
 *  When a popup trigger is detected, the
 *  popup menu specified in the constructor
 *  is displayed. The exact popup trigger
 *  depends on the OS, on MacOS X is corresponds
 *  to ctrl + mouse pressed, on Windows to
 *  right button click.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.6, 31-Jul-04
 *
 *  @see		javax.swing.JPopupMenu#isPopupTrigger( MouseEvent )
 *  @see		javax.swing.JPopupMenu#show( Component, int, int )
 */
public class PopupListener
extends MouseAdapter
{
	private JPopupMenu pop;

	/**
	 *  Creates a new <code>PopupListener</code>
	 *  for the given popup.
	 *
	 *  @param  pop		the <code>JPopupMenu</code> that
	 *					will be shown on the component to
	 *					which this <code>MouseAdapter</code>
	 *					is attached, when the user presses
	 *					he popup trigger.
	 */
	public PopupListener( JPopupMenu pop )
	{
		super();
		this.pop = pop;
	}

	/**
	 *  Show the popup menu on the component that
	 *  fired the event and at the location where
	 *  the mouse press occured, if the MouseEvent
	 *  is a popup trigger.
	 */
	public void mousePressed( MouseEvent e )
	{
		processMouse( e );
	}

	/**
	 *  Show the popup menu on the component that
	 *  fired the event and at the location where
	 *  the mouse release occured, if the MouseEvent
	 *  is a popup trigger.
	 */
	public void mouseReleased( MouseEvent e )
	{
		processMouse( e );
	}

	private void processMouse( MouseEvent e )
	{
		if( e.isPopupTrigger() ) {
			pop.show( e.getComponent(), e.getX(), e.getY() );
		}
	}
}