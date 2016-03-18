/*
 *  HibernationGlassPane.java
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
 *		28-Aug-05	copied from de.sciss.meloncillo.gui.HibernationGlassPane
 */

package de.sciss.inertia.gui;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

//import de.sciss.meloncillo.util.*;

/**
 *  A component suitable for using as
 *  a frame's glass pane : it add's a
 *  semi transparent colour on top which
 *  gives the frame a ghosted kind of
 *  look, while blocking mouse events
 *  between the frame's top and a specified
 *  bottommost gadget. Usually used in
 *  conjunction with a NoFocusTraversalPolicy.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.6, 31-Jul-04
 *
 *  @see	de.sciss.meloncillo.plugin.AbstractPlugInFrame#hibernation( boolean )
 *  @see	javax.swing.JFrame#setGlassPane( Component )
 *  @see	NoFocusTraversalPolicy
 *
 *	@todo		keyboard focus is 'lost' when the pane is disabled
 */
public class HibernationGlassPane
extends JComponent
implements MouseListener // , MouseMotionListener
{
	private final   Color		colrBg		= new Color( 0xF0, 0xF0, 0xF0, 0x7F );
	private final   JComponent  bottomMost;
	private final   Container   contentPane;
	
	/**
	 *  Creates a new dimming glass pane.
	 *  When attached to a <code>JFrame</code> using
	 *  its <code>setGlassPane</code> method,
	 *  mouse events are blocked if they occur
	 *  between the content pane's top and the
	 *  given bottom most gadget. Therefore, gadgets
	 *  appearing below the given bottom most gadget
	 *  will still look normal and be reacting to
	 *  mouse clicks.
	 *
	 *  @param  bottomMost		the component in the frame that
	 *							specifies the bottom of the ghosting
	 *							paint operation and mouse event blocking.
	 *  @param  contentPane		the frame's content pane is used as the
	 *							top most gadget (avoiding to paint over
	 *							the frame's menu or title bar). this is
	 *							also used for translating mouse coordinates.
	 */
	public HibernationGlassPane( JComponent bottomMost, Container contentPane )
	{
		this.bottomMost		= bottomMost;
		this.contentPane	= contentPane;
		
		addMouseListener( this );
//		addMouseMotionListener( this );
	}
	
	/**
	 *  Paints translucent grey over
	 *  the rectangle given by the content pane's
	 *  top margin and the bottom most gadget's
	 *  bottom margin.
	 */
	public void paintComponent( Graphics g )
	{
//		super.paintComponent( g );
		
		Rectangle   r		= g.getClipBounds();
		Point		gpPoint = SwingUtilities.convertPoint( bottomMost, 0, bottomMost.getHeight(), this );

		if( r.y < gpPoint.y ) {
			g.setColor( colrBg );
			g.fillRect( 0, 0, getWidth(), gpPoint.y );
		}
	}
	
	/**
	 *  Redispatches mouse events if they
	 *  occur below the bottom most gadget.
	 */
	public void mousePressed( MouseEvent e )	{ redispatchMouseEvent( e ); }
	/**
	 *  Redispatches mouse events if they
	 *  occur below the bottom most gadget.
	 */
	public void mouseReleased( MouseEvent e )	{ redispatchMouseEvent( e ); }
	/**
	 *  Redispatches mouse events if they
	 *  occur below the bottom most gadget.
	 */
	public void mouseClicked( MouseEvent e )	{ redispatchMouseEvent( e ); }
	/**
	 *  Redispatches mouse events if they
	 *  occur below the bottom most gadget.
	 */
	public void mouseEntered( MouseEvent e )	{ redispatchMouseEvent( e ); }
	/**
	 *  Redispatches mouse events if they
	 *  occur below the bottom most gadget.
	 */
	public void mouseExited( MouseEvent e )		{ redispatchMouseEvent( e ); }
//  public void mouseMoved( MouseEvent e )		{ redispatchMouseEvent( e ); }
//	public void mouseDragged( MouseEvent e )	{ redispatchMouseEvent( e ); }
	
	private void redispatchMouseEvent( MouseEvent e )
	{
		Point		gpPoint = e.getPoint();
		Point		cPoint	= SwingUtilities.convertPoint( bottomMost, 0, bottomMost.getHeight(), contentPane );
		Point		cPoint2	= SwingUtilities.convertPoint( this, gpPoint, contentPane );
		Component   comp;
		
		if( cPoint2.y > cPoint.y ) {
			comp = SwingUtilities.getDeepestComponentAt( contentPane, cPoint2.x, cPoint2.y );
			if( comp != null ) {
				comp.dispatchEvent( SwingUtilities.convertMouseEvent( this, e, comp ));
			}
		}
	}
} // class HibernationGlassPane
