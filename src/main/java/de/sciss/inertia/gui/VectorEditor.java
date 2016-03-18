/*
 *  VectorEditor.java
 *  Meloncillo
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
 *		07-Aug-05	copied from de.sciss.meloncillo.gui.VectorEditor
 */

package de.sciss.inertia.gui;

import java.awt.*;
import java.awt.event.*;
import java.awt.font.*;
import java.awt.geom.*;
import java.util.*;
import javax.swing.*;

// INERTIA
//import de.sciss.meloncillo.util.*;

import de.sciss.io.*;

/**
 *  A <code>VectorEditor</code> is a two dimensional
 *  panel which plots a sampled function (32bit float) and allows
 *  the user to edit this one dimensional data vector
 *  (or table, simply speaking). It implements
 *  the <code>EventManager.Processor</code> interface
 *  to handle custom <code>VectorDisplay.Event</code>s
 *  and implements the <code>VirtualSurface</code>
 *  interface to allow transitions between screen
 *  space and normalized virtual space.
 *  <p>
 *  Often it is convenient to attach a popup menu
 *  created by the static method in <code>VectorTransformer</code>.
 *  <p>
 *  Examples of using <code>VectorEditor</code>s aer
 *  the <code>SigmaReceiverEditor</code> and the
 *  <code>SimpleTransmitterEditor</code>.
 *	<p>
 *	This class implements <code>ToolActionListener</code>,
 *	therefore it's allowed to attach a <code>VectorEditor</code>
 *	to a <code>ToolBar</code>.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.65, 11-Aug-04
 *
 *  @see		de.sciss.meloncillo.math.VectorTransformer#createPopupMenu( VectorEditor )
 *  @see		VectorDisplay.Listener
 *  @see		VectorDisplay.Event
 *  @see		de.sciss.meloncillo.receiver.SigmaReceiverEditor
 *  @see		de.sciss.meloncillo.transmitter.SimpleTransmitterEditor
 *
 *  @todo		a vertical (y) wrapping mode should be implemented
 *				similar to x-wrap, useful for circular value spaces like angles
 *  @todo		due to a bug in horizontal wrapping, the modified span
 *				information is wrong?
 */
public class VectorEditor
extends VectorDisplay
implements MouseListener, MouseMotionListener, ToolActionListener
{
	private boolean		wrapX		= false;	// wrap mouse coordinates when dragging
	private boolean		wrapY		= false;	// wrap mouse coordinates when dragging

	private Point2D		dndRecentPt = null;
	private Span		dndSpan;
	private boolean		pencilActive= false;

	/**
	 *  Creates a new VectorEditor with an empty vector.
	 *  The defaults are wrapX = false, wrapY = false,
	 *  min = 0, max = 1.0, fillArea = true, no label
	 */
	public VectorEditor()
	{
		this( new float[ 0 ]);
	}
	
	/**
	 *  Creates a new VectorEditor with an given initial vector.
	 *  The defaults are wrapX = false, wrapY = false,
	 *  min = 0, max = 1.0, fillArea = true, no label
	 *
	 *  @param  vector  the initial vector data
	 */
	public VectorEditor( float[] vector )
	{
		super( vector );
	}

	/**
	 *  Changes the horizontal and vertical
	 *	bounds of the display. These bounds
	 *	define also the allowed range for editing.
	 *	Note that the current vector is left untouched,
	 *	even if values lie outside the new
	 *	allowed range.
	 */
	public void setSpace( Object source, VectorSpace space )
	{
		super.setSpace( source, space );
	}

	/**
	 *  Decides whether the mouse coordinates
	 *  should be horizontally or verically wrapped in
	 *  drawing operations. Useful when dealing with circular
	 *  spaces like angles.
	 *
	 *  @param  wrapX   if <code>true</code>, the user's drawing
	 *					operations are wrapped beyond the left or right
	 *					margin of the display
	 *  @param  wrapY   if <code>true</code>, the user's drawing
	 *					operations are wrapped beyond the top or bottom
	 *					margin of the display
	 *
	 *  @todo   wrapY   is ignored at the moment
	 */
	public void setWrapping( boolean wrapX, boolean wrapY )
	{
		this.wrapX  = wrapX;
		this.wrapY  = wrapY;
	}
	/**
	 *  Gets the horizontal wrapping mode
	 *
	 *  @return		whether values are horizontally wrapped
	 */
	public boolean getWrapX()
	{
		return wrapX;
	}

	/**
	 *  Gets the vertical wrapping mode
	 *
	 *  @return		whether values are vertically wrapped
	 */
	public boolean getWrapY()
	{
		return wrapY;
	}

// ---------------- ToolActionListener interface ---------------- 

	public void toolChanged( ToolActionEvent e )
	{
		dndRecentPt	= null;
		
		if( e.getToolAction().getID() == ToolAction.PENCIL ) {
			if( !pencilActive ) {
				this.addMouseListener( this );
				this.addMouseMotionListener( this );
				pencilActive = true;
				this.setCursor( e.getToolAction().getDefaultCursor() );
			}
		} else {
			this.setCursor( null );
			if( pencilActive ) {
				this.removeMouseListener( this );
				this.removeMouseMotionListener( this );
				pencilActive = false;
			}
		}
	}

// ---------------- MouseListener interface ---------------- 
// listening to ourselves

	public void mouseEntered( MouseEvent e ) {}
	public void mouseExited( MouseEvent e ) {}

	public void mousePressed( MouseEvent e )
	{
		int x;
	
		dndRecentPt = screenToVirtual( e.getPoint() );
		x			= (int) (dndRecentPt.getX() * vector.length);
		dndSpan		= new Span( x, x );
	}

	public void mouseReleased( MouseEvent e )
	{
		if( dndRecentPt != null ) {
			// clean up after DnD
			dndRecentPt = null;
			if( !dndSpan.isEmpty() ) {
				dispatchChange( this, dndSpan );
			}
		}
	}

	public void mouseClicked( MouseEvent e ) {}

// ---------------- MouseMotionListener interface ---------------- 
// listening to ourselves

	public void mouseMoved( MouseEvent e ) {}

	public void mouseDragged( MouseEvent e )
	{
		if( dndRecentPt == null ) return;

		final Point2D	dndCurrentPt	= screenToVirtual( e.getPoint() );
		int				startX, stopX;
		double			d1, d2, d3, d4;
		
		if( dndCurrentPt.getX() < dndRecentPt.getX() ) {
			d1		= dndCurrentPt.getX() * vector.length;
			d2		= dndRecentPt.getX() * vector.length;
			d3		= dndRecentPt.getY() - dndCurrentPt.getY();
			d4		= dndCurrentPt.getY();
		} else {
			d1		= dndRecentPt.getX() * vector.length;
			d2		= dndCurrentPt.getX() * vector.length;
			d3		= dndCurrentPt.getY() - dndRecentPt.getY();
			d4		= dndRecentPt.getY();
		}

		if( d1 != d2 ) {
			d3 /= d2 - d1;  // tangente
			if( wrapX ) {   // -------------- wrapped calculation --------------
				while( (d1 < 0.0) || (d2 < 0.0) ) {
					d1  += vector.length;
					d2  += vector.length;
				}
				startX  = (int) d1;
				stopX   = (int) (Math.ceil( d2 ) + 0.5);
				for( int i = startX; i < stopX; i++ ) {
					vector[ i % vector.length ] = (float) Math.min( space.vmax,
						Math.max( space.vmin, ((double) i - d1) * d3 + d4 ));
				}
				if( d1 < 0.0 || startX > vector.length ) {
					startX = 0;
				}
				if( d2 < 0.0 || stopX > vector.length ) {
					stopX = vector.length;
				}

			} else {			// -------------- limited calculation --------------
				startX  = Math.max( 0, (int) d1);
				stopX   = Math.min( vector.length, (int) (Math.ceil( d2 ) + 0.5) );
				for( int i = startX; i < stopX; i++ ) {
					vector[ i ] = (float) Math.min( space.vmax,
						Math.max( space.vmin, ((double) i - d1) * d3 + d4 ));
				}
			}

			dndRecentPt = dndCurrentPt;
			recalcPath();
			repaint();
			dndSpan = new Span( Math.min( startX, dndSpan.getStart() ), Math.max( stopX, dndSpan.getStop() ));
		}
	}
}