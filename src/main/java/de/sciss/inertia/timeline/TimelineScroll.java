/*
 *  TimelineScroll.java
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
 *		10-Aug-05	created from de.sciss.eisenkraut.timeline.TimelineScroll
 */

package de.sciss.inertia.timeline;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.prefs.*;
import javax.swing.*;
import javax.swing.plaf.*;
import javax.swing.plaf.basic.*;
import javax.swing.undo.*;

// INERTIA
//import de.sciss.eisenkraut.*;
//import de.sciss.eisenkraut.edit.*;
//import de.sciss.eisenkraut.gui.*;
//import de.sciss.eisenkraut.session.*;
//import de.sciss.eisenkraut.util.*;
import de.sciss.app.Document;
import de.sciss.util.LockManager;
import de.sciss.inertia.gui.GraphicsUtil;
import de.sciss.inertia.session.*;
import de.sciss.inertia.util.PrefsUtil;

import de.sciss.app.AbstractApplication;
import de.sciss.app.DynamicAncestorAdapter;
import de.sciss.app.DynamicListening;
import de.sciss.app.DynamicPrefChangeManager;
import de.sciss.app.LaterInvocationManager;

import de.sciss.gui.HelpGlassPane;

import de.sciss.io.Span;

/**
 *  A GUI element for allowing
 *  horizontal timeline scrolling.
 *  Subclasses <code>JScrollBar</code>
 *  simply to override the <code>paintComponent</code>
 *  method: an additional hairline is drawn
 *  to visualize the current timeline position.
 *  also a translucent blue rectangle is drawn
 *  to show the current timeline selection.
 *	<p>
 *	This class tracks the catch preferences
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.5, 03-Aug-05
 *
 *  @todo		the display properties work well
 *				with the Aqua look+and+feel, however
 *				are slightly wrong on Linux with platinum look+feel
 *				because the scroll gadgets have different positions.
 */
public class TimelineScroll
extends JScrollBar
implements AdjustmentListener, TimelineListener, DynamicListening, LaterInvocationManager.Listener
{
	public static final int TYPE_UNKNOWN	= 0;
	public static final int TYPE_DRAG		= 1;
	public static final int TYPE_TRANSPORT	= 2;

    private final Timeline		timeline;
    private final Document		doc;
	private final LockManager	lm;
	private final int			doors;

	private Dimension	recentSize		= getMinimumSize();
    private Shape		shpSelection	= null;
    private final Shape[] shpPosition;
	private Span		timelineSel		= null;
	private long		timelineLen		= 0;
	private int			timelineLenShift= 0;
	private final long[] timelinePos;
	private Span		timelineVis		= new Span();
	private boolean		prefCatch;
	
	private final	Object	adjustmentSource	= new Object();
    
    private static final Color		colrSelection   = GraphicsUtil.colrSelection;
    private static final Stroke		strkPosition    = new BasicStroke( 0.5f );
    private final Color[]			colrPosition;

	private final int trackMarginLeft;
	private final int trackMargin;
	
	private int activeChannel	= 0;

	/**
	 *  Constructs a new <code>TimelineScroll</code> object.
	 *
	 *  @param  doc		session Session
	 *
	 *	@todo	a clean way to determine the track rectangle ...
	 */
    public TimelineScroll( de.sciss.inertia.session.Session doc )
    {
        super( HORIZONTAL );
		
		this.timeline	= doc.timeline;
        this.doc		= doc;
		this.lm			= doc.bird;
		this.doors		= Session.DOOR_TIME;
		
		timelinePos		= new long[ doc.layers.getNumLayers() ];
		shpPosition		= new Shape[ timelinePos.length ];

		colrPosition	= new Color[ timelinePos.length ];
		for( int ch = 0; ch < colrPosition.length; ch++ ) {
			colrPosition[ ch ]	= Color.getHSBColor( (float) ch / colrPosition.length, 0.75f, 0.75f );
		}

		LookAndFeel laf = UIManager.getLookAndFeel();
		if( (laf != null) && laf.isNativeLookAndFeel() && (laf.getName().indexOf( "Aqua" ) >= 0) ) {
			trackMarginLeft = 6;  // for Aqua look and feel	
			trackMargin		= 39;
		} else {
			trackMarginLeft = 16;	// works for Metal, Motif, Liquid, Metouia
			trackMargin		= 32;
		}

		timelineLen = timeline.getLength();
		timelineVis = timeline.getVisibleSpan();
		for( timelineLenShift = 0; (timelineLen >> timelineLenShift) > 0x3FFFFFFF; timelineLenShift++ );
		recalcTransforms();
		recalcBoundedRange();

		// --- Listener ---
		
		new DynamicAncestorAdapter( this ).addTo( this );
        this.addAdjustmentListener( this );

        new DynamicAncestorAdapter( new DynamicPrefChangeManager( AbstractApplication.getApplication().getUserPrefs(),
			new String[] { PrefsUtil.KEY_CATCH }, this )).addTo( this );
        
		setFocusable( false );
		
        HelpGlassPane.setHelp( this, "TimelineScroll" );
    }
    
	/**
	 *  Paints the normal scroll bar using
	 *  the super class's method. Additionally
	 *  paints timeline position and selection cues
	 */
    public void paintComponent( Graphics g )
    {
        super.paintComponent( g );
 
		Dimension   d           = getSize();
        Graphics2D  g2          = (Graphics2D) g;
		Stroke		strkOrig	= g2.getStroke();
		Paint		pntOrig		= g2.getPaint();

		if( d.width != recentSize.width || d.height != recentSize.height ) {
			recentSize = d;
			recalcTransforms();
		}
        
        if( shpSelection != null ) {
            g2.setColor( colrSelection );
            g2.fill( shpSelection );
        }
		g2.setStroke( strkPosition );
		for( int ch = 0; ch < shpPosition.length; ch++ ) {
			if( shpPosition[ ch ] != null ) {
				g2.setColor( colrPosition[ ch ]);
				g2.draw( shpPosition[ ch ]);
			}
		}

        g2.setStroke( strkOrig );
		g2.setPaint( pntOrig );
    }

    private void recalcBoundedRange()
    {
		final int len	= (int) (timelineLen >> timelineLenShift);
		final int len2	= (int) (timelineVis.getLength() >> timelineLenShift);
		if( len > 0 ) {
			if( !isEnabled() ) setEnabled( true );
			setValues( (int) (timelineVis.getStart() >> timelineLenShift), len2, 0, len );   // val, extent, min, max
			setUnitIncrement( Math.max( 1, (int) (len2 >> 5) ));             // 1/32 extent
			setBlockIncrement( Math.max( 1, (int) ((len2 * 3) >> 2) ));      // 3/4 extent
		} else {
			if( isEnabled() ) setEnabled( false );
			setValues( 0, 100, 0, 100 );	// full view will hide the scrollbar knob
		}
    }
	
	public void setActiveChannel( int ch )
	{
		activeChannel = ch;
	}

    /*
     *  Calculates virtual->screen coordinates
     *  for timeline position and selection
     */
    private void recalcTransforms()
    {
        double  scale, x;

//for( int i = 0; i < getComponentCount(); i++ ) {
//	Component c = getComponent( i );
//	System.err.println( "scroll container component : "+c.getClass().getName()+" ; at "+c.getLocation().x+", "+
//		c.getLocation().y+"; w = "+c.getWidth()+"; h = "+c.getHeight() );
//}
//        
		if( timelineLen > 0 ) {
			scale           = (double) (recentSize.width - trackMargin) / (double) timelineLen;
			if( timelineSel != null ) {
				shpSelection = new Rectangle2D.Double( timelineSel.getStart() * scale + trackMarginLeft, 0,
													   timelineSel.getLength() * scale, recentSize.height );
			} else {
				shpSelection = null;                   
			}
			for( int ch = 0; ch < shpPosition.length; ch++ ) {
				x					= timelinePos[ ch ] * scale + trackMarginLeft;
				shpPosition[ ch ]   = new Line2D.Double( x, 0, x, recentSize.height );
			}
		} else {
			for( int ch = 0; ch < shpPosition.length; ch++ ) {
				shpPosition[ ch ]	= null;
			}
			shpSelection	= null;
		}
    }
    
	/**
	 *  Updates the red hairline representing
	 *  the current timeline position in the
	 *  overall timeline span.
	 *  Called directly from TimelineFrame
	 *  to improve performance. Don't use
	 *  elsewhere.
	 *
	 *  @param  pos			new position in absolute frames
	 *  @param  patience	allowed graphic update interval
	 *
	 *  @see	java.awt.Component#repaint( long )
	 */
	public void setPosition( int ch, long pos, long patience, int type )
	{
		timelinePos[ ch ] = pos;

		if( (ch == activeChannel) && prefCatch && timelineVis.contains( timelinePos[ ch ]) &&
			(timelineVis.getStop() < timelineLen) &&
			!timelineVis.contains( pos + (type == TYPE_TRANSPORT ? timelineVis.getLength() >> 3 : 0) )) {
			
			if( (lm != null) && !lm.attemptExclusive( doors, 250 )) return;
			try {
				long start, stop;
				
				start	= pos;
				if( type == TYPE_TRANSPORT ) {
					start -= timelineVis.getLength() >> 3;
				} else if( type == TYPE_DRAG ) {
					if( timelineVis.getStop() <= pos ) {
						start -= timelineVis.getLength();
					}
				} else {
					start -= timelineVis.getLength() >> 2;
				}
				stop	= Math.min( timelineLen, Math.max( 0, start ) + timelineVis.getLength() );
				start	= Math.max( 0, stop - timelineVis.getLength() );
//				if( (stop > start) && ((start != timelineVis.getStart()) || (stop != timelineVis.getStop())) ) {
				if( stop > start ) {
					// it's crucial to update internal var timelineVis here because
					// otherwise the delay between emitting the edit and receiving the
					// change via timelineScrolled might be two big, causing setPosition
					// to fire more than one edit!
					timelineVis = new Span( start, stop );
// INERTIA
//					doc.getUndoManager().addEdit( new EditSetTimelineScroll( this, doc, timelineVis ));
timeline.setVisibleSpan( this, timelineVis );
					return;
				}
			}
			finally {
				if( lm != null ) lm.releaseExclusive( doors );
			}
		}
		recalcTransforms();
		repaint( patience );
	}
	
// ---------------- DynamicListening interface ---------------- 

    public void startListening()
    {
        timeline.addTimelineListener( this );
		recalcTransforms();
        repaint();
    }

    public void stopListening()
    {
        timeline.removeTimelineListener( this );
    }
 
// ---------------- LaterInvocationManager.Listener interface ---------------- 

	// o instanceof PreferenceChangeEvent
	public void laterInvocation( Object o )
	{
		final String  key	= ((PreferenceChangeEvent) o).getKey();
		final String  value	= ((PreferenceChangeEvent) o).getNewValue();

		if( key.equals( PrefsUtil.KEY_CATCH )) {
			prefCatch	= Boolean.valueOf( value ).booleanValue();
			if( (prefCatch == true) && !(timelineVis.contains( timelinePos[ activeChannel ]))) {
				if( (lm != null) && !lm.attemptExclusive( doors, 250 )) return;
				try {
					long start	= Math.max( 0, timelinePos[ activeChannel ] - (timelineVis.getLength() >> 2) );
					long stop	= Math.min( timelineLen, start + timelineVis.getLength() );
					start		= Math.max( 0, stop - timelineVis.getLength() );
					if( stop > start ) {
// INERTIA
//						doc.getUndoManager().addEdit( new EditSetTimelineScroll( this, doc, new Span( start, stop )));
timeline.setVisibleSpan( this, new Span( start, stop ));
					}
				}
				finally {
					if( lm != null ) lm.releaseExclusive( doors );
				}
			}
		}
	}

// ---------------- TimelineListener interface ---------------- 

	public void timelineSelected( TimelineEvent e )
    {
		try {
			if( lm != null ) lm.waitShared( doors );
			timelineSel = timeline.getSelectionSpan();
		}
		finally {
			if( lm != null ) lm.releaseShared( doors );
		}
		recalcTransforms();
        repaint();
    }
    
	public void timelineChanged( TimelineEvent e )
    {
		try {
			if( lm != null ) lm.waitShared( doors );
			timelineLen = timeline.getLength();
			timelineVis = timeline.getVisibleSpan();
			for( timelineLenShift = 0; (timelineLen >> timelineLenShift) > 0x3FFFFFFF; timelineLenShift++ );
			recalcTransforms();
			recalcBoundedRange();
		}
		finally {
			if( lm != null ) lm.releaseShared( doors );
		}
        repaint();
    }

	// ignored since the timeline frame will inform us
	public void timelinePositioned( TimelineEvent e ) {}

    public void timelineScrolled( TimelineEvent e )
    {
		try {
			if( lm != null ) lm.waitShared( doors );
			timelineVis = timeline.getVisibleSpan();
			if( e.getSource() != adjustmentSource ) {
				recalcBoundedRange();
			}
		}
		finally {
			if( lm != null ) lm.releaseShared( doors );
		}
    }

// ---------------- AdjustmentListener interface ---------------- 
// we're listening to ourselves

    public void adjustmentValueChanged( AdjustmentEvent e )
    {
		if( isEnabled() ) {
			if( (lm != null) && !lm.attemptExclusive( doors, 200 )) return;
			try {
				Span oldVisi = timeline.getVisibleSpan();
				Span newVisi = new Span( this.getValue() << timelineLenShift,
										 (this.getValue() + this.getVisibleAmount()) << timelineLenShift );
				if( !newVisi.equals( oldVisi )) {
					if( prefCatch && oldVisi.contains( timelinePos[ activeChannel ]) &&
						!newVisi.contains( timelinePos[ activeChannel ])) {

						AbstractApplication.getApplication().getUserPrefs().putBoolean( PrefsUtil.KEY_CATCH, false );
					}
// INERTIA
//					doc.getUndoManager().addEdit( new EditSetTimelineScroll( adjustmentSource, doc, newVisi ));
timeline.setVisibleSpan( adjustmentSource, newVisi );
				}
			}
			finally {
				if( lm != null ) lm.releaseExclusive( doors );
			}
        }
    }
}