/*
 *  VectorDisplay.java
 *  Inertia
 *
 *  Copyright (c) 2004 Hanns Holger Rutz. All rights reserved.
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
 *		07-Aug-05	copied from de.sciss.meloncillo.gui.VectorDisplay
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

import de.sciss.app.*;
import de.sciss.io.*;

/**
 *  A <code>VectorDisplay</code> is a two dimensional
 *  panel which plots a sampled function (32bit float) and allows
 *  the user to edit this one dimensional data vector
 *  (or table, simply speaking). It implements
 *  the <code>EventManager.Processor</code> interface
 *  to handle custom <code>VectorDisplayEvent</code>s
 *  and implements the <code>VirtualSurface</code>
 *  interface to allow transitions between screen
 *  space and normalized virtual space.
 *  <p>
 *  Often it is convenient to attach a popup menu
 *  created by the static method in <code>VectorTransformer</code>.
 *  <p>
 *  Examples of using <code>VectorDisplay</code>s aer
 *  the <code>SigmaReceiverEditor</code> and the
 *  <code>SimpleTransmitterEditor</code>.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.72, 26-Mar-05
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
public class VectorDisplay
extends JComponent
implements VirtualSurface, EventManager.Processor
{
	/**
	 *	the internal vector storage
	 */
	protected float[]				vector;
	/**
	 *	the bounds of the display
	 */
	protected VectorSpace			space;

	private final GeneralPath		path				= new GeneralPath();
	private Shape					pathTrns;
	private TextLayout				txtLay				= null;
	private Rectangle2D				txtBounds;
	private Dimension				recentSize;
	private Image					image				= null;

	private static final Stroke		strkLine			= new BasicStroke( 0.5f );
	private static final Paint		pntArea				= new Color( 0x42, 0x5E, 0x9D, 0x7F );
	private static final Paint		pntLine				= Color.black;
	private static final Paint		pntLabel			= new Color( 0x00, 0x00, 0x00, 0x3F );
	
	private final AffineTransform	trnsScreenToVirtual = new AffineTransform();
	private final AffineTransform	trnsVirtualToScreen = new AffineTransform();

	private boolean					fillArea			= true;		// fill area under the vector polyline

	// --- event handling ---

	private final EventManager		elm					= new EventManager( this );

	// --- top painter ---

	private final java.util.List	collTopPainters		= new ArrayList();
	
	/**
	 *  Creates a new VectorDisplay with an empty vector.
	 *  The defaults are wrapX = false, wrapY = false,
	 *  min = 0, max = 1.0, fillArea = true, no label
	 */
	public VectorDisplay()
	{
		this( new float[ 0 ]);
	}
	
	/**
	 *  Creates a new VectorDisplay with an given initial vector.
	 *  The defaults are wrapX = false, wrapY = false,
	 *  min = 0, max = 1.0, fillArea = true, no label
	 *
	 *  @param  vector  the initial vector data
	 */
	public VectorDisplay( float[] vector )
	{
		setOpaque( false );
		recentSize  = new Dimension( 64, 16 );
		setMinimumSize( recentSize );
		setVector( null, vector );
	}

	/**
	 *  Replaces the existing vector by another one.
	 *  This dispatches a <code>VectorDisplayEvent</code>
	 *  to registered listeners.
	 *
	 *  @param  source  the source in the <code>VectorDisplayEvent</code>.
	 *					use <code>null</code> to prevent event dispatching.
	 *  @param  vector  the new vector data
	 */
	public void setVector( Object source, float[] vector )
	{
		this.vector = vector;
		
		if( space != null ) {
			recalcPath();
			if( isVisible() ) repaint();
		}
		if( source != null ) {
			dispatchChange( source, new Span( 0, vector.length ));
		}
	}
	
	/**
	 *  Gets the current data array.
	 *
	 *  @return		the current vector data of the editor. valid data
	 *				is from index 0 to the end of the array.
	 *
	 *  @warning			the returned array is not a copy and therefore
	 *						any modifications are forbidden. this also implies
	 *						that relevant data be copied by the listener
	 *						immediately upon receiving the vector.
	 *  @synchronization	should only be called in the event thread
	 */
	public float[] getVector()
	{
		return vector;
	}

	/**
	 *  Changes the horizontal and vertical
	 *	bounds of the display.
	 *
	 *	@param	source	if not <code>null</code>, a
	 *					<code>VectorDisplay.Event</code> (<code>SPACE</code>) is fired
	 *					with <code>source</code> as the initiator
	 *  @param  space	the new space
	 */
	public void setSpace( Object source, VectorSpace space )
	{
		this.space	= space;
		txtLay		= null;
		recalcPath();
		if( isVisible() ) repaint();
		
		if( source != null ) {
			dispatchSpaceChange( source, space );
		}
	}

	/**
	 *  Returns the current space
	 *	of the display.
	 *
	 *  @return		the current <code>VectorSpace</code> describing
	 *				the bounds of the display
	 */
	public VectorSpace getSpace()
	{
		return space;
	}

	/**
	 *  Set the graph display mode
	 *
	 *  @param  fillArea	if <code>false</code>, a hairline is
	 *						drawn to connect the sample values. if
	 *						<code>true</code>, the area below the
	 *						curve is additionally filled with a
	 *						translucent colour.
	 */
	public void setFillArea( boolean fillArea )
	{
		if( this.fillArea != fillArea ) {
			this.fillArea   = fillArea;
			repaint();
		}
	}

	public void paintComponent( Graphics g )
	{
		super.paintComponent( g );
		
		final Dimension d	= getSize();

		if( d.width != recentSize.width || d.height != recentSize.height ) {
			recentSize = d;
			recalcTransforms();
			recreateImage();
			redrawImage();
		} else if( pathTrns == null ) {
			recalcTransforms();
			recreateImage();	// XXX since we don't clear the background any more to preserve Aqua LAF
			redrawImage();
		}

		if( image != null ) {
			g.drawImage( image, 0, 0, this );
		}

		// --- invoke top painters ---
		if( !collTopPainters.isEmpty() ) {
			Graphics2D		g2			= (Graphics2D) g;
//			AffineTransform	trnsOrig	= g2.getTransform();

//			g2.transform( trnsVirtualToScreen );
			g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
			for( int i = 0; i < collTopPainters.size(); i++ ) {
				((TopPainter) collTopPainters.get( i )).paintOnTop( g2 );
			}
//			g2.setTransform( trnsOrig );
		}
	}
	
	// --- listener registration ---
	
	public void addListener( VectorDisplay.Listener listener )
	{
		elm.addListener( listener );
	}

	public void removeListener( VectorDisplay.Listener listener )
	{
		elm.removeListener( listener );
	}

	/**
	 *  Registers a new top painter.
	 *  If the top painter wants to paint
	 *  a specific portion of the surface,
	 *  it must make an appropriate repaint call!
	 *
	 *  @param  p   the painter to be added to the paint queue
	 *
	 *  @synchronization	this method must be called in the event thread
	 */
	public void addTopPainter( TopPainter p )
	{
		if( !collTopPainters.contains( p )) {
			collTopPainters.add( p );
		}
	}

	/**
	 *  Removes a registered top painter.
	 *
	 *  @param  p   the painter to be removed from the paint queue
	 *
	 *  @synchronization	this method must be called in the event thread
	 */
	public void removeTopPainter( TopPainter p )
	{
		collTopPainters.remove( p );
	}
	
	private void recreateImage()
	{
		if( image != null ) image.flush();
		image = createImage( recentSize.width, recentSize.height );
	}
	
	private void redrawImage()
	{
		if( image == null ) return;

		Graphics2D g2 = (Graphics2D) image.getGraphics();
		g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
		if( fillArea ) {
			g2.setPaint( pntArea );
			g2.fill( pathTrns );
		}
		g2.setStroke( strkLine );
		g2.setPaint( pntLine );
		g2.draw( pathTrns );
		if( space.hlabel != null ) {
			g2.setPaint( pntLabel );
			if( txtLay == null ) {
				txtLay		= new TextLayout( space.hlabel, getFont(), g2.getFontRenderContext() );
				txtBounds   = txtLay.getBounds();
			}
			txtLay.draw( g2, recentSize.width - (float) txtBounds.getWidth() - 4,
							 recentSize.height - (float) txtBounds.getHeight() );
		}
		g2.dispose();
	}

	/**
	 *  Recalculates a Java2D path from the vector
	 *  that will be used for painting operations
	 */
	protected void recalcPath()
	{
		float		f1;
		final float	f2 = (float) ((space.vmin - space.vmax) / recentSize.height + space.vmin);
		
		path.reset();
		if( vector.length > 0 ) {
			f1  = 1.0f / (float) vector.length;
			path.moveTo( -0.01f, f2 );
			path.lineTo( -0.01f, vector[0] );
			for( int i = 0; i < vector.length; i++ ) {
				path.lineTo( (float) i * f1, vector[i] );
			}
			path.lineTo( 1.01f, vector[vector.length-1] );
			path.lineTo( 1.01f, f2 );
			path.closePath();
// System.out.println( "recalced path" );
		}
		pathTrns = null;
	}

	public void processEvent( BasicEvent e )
	{
		VectorDisplay.Listener listener;
		
		for( int i = 0; i < elm.countListeners(); i++ ) {
			listener = (VectorDisplay.Listener) elm.getListener( i );
			switch( e.getID() ) {
			case VectorDisplay.Event.CHANGED:
				listener.vectorChanged( (VectorDisplay.Event) e );
				break;
			case VectorDisplay.Event.SPACE:
				listener.vectorSpaceChanged( (VectorDisplay.Event) e );
				break;
			default:
				assert false : e.getID();
			}
		} // for( i = 0; i < elm.countListeners(); i++ )
	}

	/**
	 *	A utility method to create and dispatch
	 *	a <code>VectorDisplay.Event</code> for
	 *	a vector content change
	 *
	 *	@param	source		who initiated the event
	 *	@param	changeSpan	the <code>Span</code> that describes the modified portion of the vector
	 */
	protected void dispatchChange( Object source, Span changeSpan )
	{
		VectorDisplay.Event e2 = new VectorDisplay.Event( source, VectorDisplay.Event.CHANGED,
														  System.currentTimeMillis(), 0, changeSpan );
		elm.dispatchEvent( e2 );
	}

	/**
	 *	A utility method to create and dispatch
	 *	a <code>VectorDisplay.Event</code> for
	 *	a space change
	 *
	 *	@param	source		who initiated the event
	 *	@param	newSpace	the <code>VectorSpace</code> that describes the modified space
	 */
	protected void dispatchSpaceChange( Object source, VectorSpace newSpace )
	{
		VectorDisplay.Event e2 = new VectorDisplay.Event( source, VectorDisplay.Event.SPACE,
														  System.currentTimeMillis(), 0, newSpace );
		elm.dispatchEvent( e2 );
	}

// ---------------- VirtualSurface interface ---------------- 

	/*
	 *  Recalculates the transforms between
	 *  screen and virtual space
	 */
	private void recalcTransforms()
	{
// System.out.println( "recalc trns for "+recentSize.width+" x "+recentSize.height );

		trnsVirtualToScreen.setToTranslation( 0.0, recentSize.height );
		trnsVirtualToScreen.scale( recentSize.width,recentSize.height / (space.vmin - space.vmax) );
		trnsVirtualToScreen.translate( 0.0, -space.vmin );
		trnsScreenToVirtual.setToTranslation( 0.0, space.vmin );
		trnsScreenToVirtual.scale( 1.0 / recentSize.width, (space.vmin - space.vmax) / recentSize.height );
		trnsScreenToVirtual.translate( 0.0, -recentSize.height );

		pathTrns = path.createTransformedShape( trnsVirtualToScreen );
	}
	
	/**
	 *  Converts a location on the screen
	 *  into a point the virtual space.
	 *  Neither input nor output point need to
	 *  be limited to particular bounds
	 *
	 *  @param  screenPt		point in screen space
	 *  @return the input point transformed to virtual space
	 */
	public Point2D screenToVirtual( Point2D screenPt )
	{
		return trnsScreenToVirtual.transform( screenPt, null );
	}

	/**
	 *  Converts a shape in the screen space
	 *  into a shape in the virtual space.
	 *
	 *  @param  screenShape		arbitrary shape in screen space
	 *  @return the input shape transformed to virtual space
	 */
	public Shape screenToVirtual( Shape screenShape )
	{
		return trnsScreenToVirtual.createTransformedShape( screenShape );
	}

	/**
	 *  Converts a point in the virtual space
	 *  into a location on the screen.
	 *
	 *  @param  virtualPt   point in the virtual space whose
	 *						visible bounds are (0, 0 ... 1, 1)
	 *  @return				point in the display space
	 */
	public Point2D virtualToScreen( Point2D virtualPt )
	{
		return trnsVirtualToScreen.transform( virtualPt, null );
	}

	/**
	 *  Converts a shape in the virtual space
	 *  into a shape on the screen.
	 *
	 *  @param  virtualShape	arbitrary shape in virtual space
	 *  @return the input shape transformed to screen space
	 */
	public Shape virtualToScreen( Shape virtualShape )
	{
		return trnsVirtualToScreen.createTransformedShape( virtualShape );
	}

	/**
	 *  Converts a rectangle in the virtual space
	 *  into a rectangle suitable for Graphics clipping
	 *
	 *  @param  virtualClip		a rectangle in virtual space
	 *  @return the input rectangle transformed to screen space,
	 *			suitable for graphics clipping operations
	 */
	public Rectangle virtualToScreenClip( Rectangle2D virtualClip )
	{
		Point2D screenPt1 = trnsVirtualToScreen.transform( new Point2D.Double( virtualClip.getMinX(),
																			   virtualClip.getMinY() ), null );
		Point2D screenPt2 = trnsVirtualToScreen.transform( new Point2D.Double( virtualClip.getMaxX(),
																			   virtualClip.getMaxY() ), null );
	
		return new Rectangle( (int) Math.floor( screenPt1.getX() ), (int) Math.floor( screenPt1.getY() ),
							  (int) Math.ceil( screenPt2.getX() ), (int) Math.ceil( screenPt2.getY() ));
	}

	/**
	 *  No snapping is available at the moment, so
	 *  simply returns the input point
	 */
	public Point2D snap( Point2D freePt, boolean virtual )
	{
		return freePt;
	}

// ----------- internal event class -----------

	/**
	 *  This kind of event is fired
	 *  from a <code>VectorDisplay</code> when
	 *  the user modified the vector's values
	 *  or the <code>setVector</code> method
	 *  was called.
	 *
	 *  @author		Hanns Holger Rutz
	 *  @version	0.72, 26-Mar-05
	 *
	 *  @see		VectorDisplay#addListener( VectorDisplay.Listener )
	 *  @see		VectorDisplay#setVector( Object, float[] )
	 *  @see		VectorDisplay.Listener
	 */
	public class Event
	extends BasicEvent
	{
	// --- ID values ---
		/**
		 *  returned by getID() : the vector content changed. 
		 *  in this case actionObj is a Span object
		 *  describing the span whose content changed.
		 */
		public static final int CHANGED	= 0;
		/**
		 *  returned by getID() : the vector space changed. 
		 *  in this case actionObj is the new space object.
		 */
		public static final int SPACE	= 1;

		private int		actionID;   // currently not in use
		private Object	actionObj;

		/**
		 *  Constructs a new <code>VectorDisplay.Event</code>.
		 *
		 *  @param  source		who originated the action / event
		 *  @param  ID			<code>CHANGED</code> or <code>SPACE</code>
		 *  @param  when		system time when the event occured
		 *  @param  actionID	unused, must be zero at the moment
		 *  @param  actionObj	for ID == CHANGED, the changed horizontal
		 *						vector span. for ID == SPACE, the new space
		 */
		public Event( Object source, int ID, long when, int actionID, Object actionObj )
		{
			super( source, ID, when );
		
			this.actionID   = actionID;
			this.actionObj  = actionObj;
		}
		
		/**
		 *  Currently unused
		 */
		public int getActionID()
		{
			return actionID;
		}

		/**
		 *  When getID() returns CHANGED
		 *  the returned object is a Span
		 *  describing the beginning and ending of
		 *  the changed content. For SPACE
		 *	the new <code>VectorSpace</code>.
		 *
		 *  @return		an event ID dependent object
		 */
		public Object getActionObject()
		{
			return actionObj;
		}

		public boolean incorporate( BasicEvent oldEvent )
		{
			if( oldEvent instanceof VectorDisplay.Event &&
				this.getSource() == oldEvent.getSource() &&
				this.getID() == oldEvent.getID() ) {
				
				switch( this.getID() ) {
				case CHANGED:
					this.actionObj  = Span.union(
						(Span) actionObj,
						(Span) ((VectorDisplay.Event) oldEvent).getActionObject() );
					break;
				
				case SPACE:
					break;
				
				default:
					assert false : this.getID();
					break;
				}
				return true;

			} else return false;
		}
	}

// ----------- internal listener class -----------

	/**
	 *  Interface for listening
	 *  to switches of the GUI tools
	 *
	 *  @author		Hanns Holger Rutz
	 *  @version	0.72, 26-Mar-05
	 *
	 *  @see		VectorDisplay#addListener( VectorDisplay.Listener )
	 *  @see		VectorDisplay#setVector( Object, float[] )
	 *  @see		VectorDisplay.Event
	 */
	public interface Listener
	extends EventListener
	{
		/**
		 *  Notifies the listener that
		 *  a float array vector was modified.
		 *
		 *  @param  e   the event describing
		 *				the vector change
		 */
		public void vectorChanged( VectorDisplay.Event e );

		/**
		 *  Notifies the listener that
		 *  a float array vector was modified.
		 *
		 *  @param  e   the event describing
		 *				the vector change
		 */
		public void vectorSpaceChanged( VectorDisplay.Event e );
	}
}