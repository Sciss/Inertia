/*
 *  TimelineAxis.java
 *  Eisenkraut
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
 *		07-Aug-05	copied from de.sciss.eisenkraut.timeline.TimelineAxis
 */

// XXX TO-DO : dispose, removeTimelineListener

package de.sciss.inertia.timeline;

import java.awt.*;
import java.awt.event.*;
import java.awt.font.*;
import java.awt.geom.*;
import java.text.*;
import java.util.*;
import java.util.prefs.*;
import javax.swing.*;
import javax.swing.undo.*;

// INERTIA
//import de.sciss.eisenkraut.*;
//import de.sciss.eisenkraut.edit.*;
//import de.sciss.eisenkraut.gui.*;
//import de.sciss.eisenkraut.session.*;
//import de.sciss.eisenkraut.util.*;
import de.sciss.inertia.gui.*;
import de.sciss.util.*;
import de.sciss.app.Document;
import de.sciss.inertia.util.PrefsUtil;

import de.sciss.app.AbstractApplication;
import de.sciss.app.DynamicAncestorAdapter;
import de.sciss.app.DynamicListening;
import de.sciss.app.DynamicPrefChangeManager;
import de.sciss.app.LaterInvocationManager;

import de.sciss.gui.HelpGlassPane;

import de.sciss.io.Span;

/**
 *  A GUI element for displaying
 *  the timeline's axis (ruler)
 *  which is used to display the
 *  time indices and to allow the
 *  user to position and select the
 *  timeline.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.3, 16-Jul-05
 */
public class TimelineAxis
extends Axis
implements	TimelineListener, MouseListener, MouseMotionListener,
			DynamicListening, LaterInvocationManager.Listener
{
	private final Timeline		timeline;
    private final LockManager	lm;
	private final int			doors;
	private final Document		doc;

	// when the user begins a selection by shift+clicking, the
	// initially fixed selection bound is saved to selectionStart.
	private long				selectionStart  = -1;
	private boolean				shiftDrag, altDrag;
    
	/**
	 *  Constructs a new object for
	 *  displaying the timeline ruler
	 *
	 *  @param  doc		session Session
	 */
	public TimelineAxis( Timeline timeline, LockManager lm, int doors, Document doc )
	{
		super( HORIZONTAL, 0 );
        
        this.timeline	= timeline;
		this.lm			= lm;
		this.doors		= doors;
		this.doc		= doc;
		
		// --- Listener ---
        new DynamicAncestorAdapter( this ).addTo( this );

        new DynamicAncestorAdapter( new DynamicPrefChangeManager(
			AbstractApplication.getApplication().getUserPrefs(), new String[] { PrefsUtil.KEY_TIMEUNITS }, this
		)).addTo( this );
		this.addMouseListener( this );
		this.addMouseMotionListener( this );

		// ------
        HelpGlassPane.setHelp( this, "TimelineAxis" );
	}
  
	private void recalcSpace()
	{
		final Span			visibleSpan;
		final double		d1;
		final VectorSpace	space;
	
		try {
			lm.waitShared( doors );
			visibleSpan = timeline.getVisibleSpan();
			if( (getFlags() & TIMEFORMAT) == 0 ) {
				space	= VectorSpace.createLinSpace( visibleSpan.getStart(),
													  visibleSpan.getStop(),
													  0.0, 1.0, null, null, null, null );
			} else {
				d1		= 1.0 / timeline.getRate();
				space	= VectorSpace.createLinSpace( visibleSpan.getStart() * d1,
													  visibleSpan.getStop() * d1,
													  0.0, 1.0, null, null, null, null );
			}
			setSpace( space );
		}
		finally {
			lm.releaseShared( doors );
		}
	}

	// Sync: attempts timeline
    private void dragTimelinePosition( MouseEvent e )
    {
        int				x   = e.getX();
        Span			span, span2;
        long			position;
// INERTIA
//		UndoableEdit	edit;
	   
        // translate into a valid time offset
		if( !lm.attemptExclusive( doors, 200 )) return;
		try {
            span        = timeline.getVisibleSpan();
            position    = span.getStart() + (long) ((double) x / (double) getWidth() *
                                                    (double) span.getLength());
            position    = Math.max( 0, Math.min( timeline.getLength(), position ));
            
            if( shiftDrag ) {
				span2	= timeline.getSelectionSpan();
				if( altDrag || span2.isEmpty() ) {
					selectionStart = timeline.getPosition();
					altDrag = false;
				} else if( selectionStart == -1 ) {
					selectionStart = Math.abs( span2.getStart() - position ) >
									 Math.abs( span2.getStop() - position ) ?
									 span2.getStart() : span2.getStop();
				}
				span	= new Span( Math.min( position, selectionStart ),
									Math.max( position, selectionStart ));
// INERTIA
//				edit	= new EditSetTimelineSelection( this, doc, span );
timeline.setSelectionSpan( this, span );
            } else {
				if( altDrag ) {
// INERTIA
//					edit	= new CompoundEdit();
//					edit.addEdit( new EditSetTimelineSelection( this, doc, new Span() ));
//					edit.addEdit( new EditSetTimelinePosition( this, doc, position ));
//					((CompoundEdit) edit).end();
timeline.setSelectionSpan( this, new Span() );
timeline.setPosition( this, position );
					altDrag = false;
				} else {
// INERTIA
//					edit	= new EditSetTimelinePosition( this, doc, position );
timeline.setPosition( this, position );
				}
            }
// INERTIA
//			doc.getUndoManager().addEdit( edit );
		}
		finally {
			lm.releaseExclusive( doors );
        }
    }

// ---------------- LaterInvocationManager.Listener interface ---------------- 

		// called by DynamicPrefChangeManager ; o = PreferenceChangeEvent
		public void laterInvocation( Object o )
		{
			final PreferenceChangeEvent	pce = (PreferenceChangeEvent) o;
			final String				key = pce.getKey();
			
// INERTIA
//			if( key.equals( PrefsUtil.KEY_TIMEUNITS )) {
//				int timeUnits = pce.getNode().getInt( key, 0 );
//				setFlags( timeUnits == 0 ? 0 : TIMEFORMAT );
//				recalcSpace();
//			}
		}

// ---------------- DynamicListening interface ---------------- 

    public void startListening()
    {
         timeline.addTimelineListener( this );
		 recalcSpace();
    }

    public void stopListening()
    {
         timeline.removeTimelineListener( this );
    }

// ---------------- MouseListener interface ---------------- 
// we're listening to the ourselves

	public void mouseEntered( MouseEvent e )
	{
//		if( isEnabled() ) dispatchMouseMove( e );
	}
	
	public void mouseExited( MouseEvent e ) {}

	public void mousePressed( MouseEvent e )
    {
		shiftDrag		= e.isShiftDown();
		altDrag			= e.isAltDown();
		selectionStart  = -1;
        dragTimelinePosition( e );
    }

	public void mouseReleased( MouseEvent e ) {}
	public void mouseClicked( MouseEvent e ) {}

// ---------------- MouseMotionListener interface ---------------- 
// we're listening to ourselves

    public void mouseMoved( MouseEvent e ) {}

	public void mouseDragged( MouseEvent e )
	{
        dragTimelinePosition( e );
	}

// ---------------- TimelineListener interface ---------------- 
  
   	public void timelineSelected( TimelineEvent e ) {}
	public void timelinePositioned( TimelineEvent e ) {}

	public void timelineChanged( TimelineEvent e )
	{
		recalcSpace();
	}

   	public void timelineScrolled( TimelineEvent e )
    {
		recalcSpace();
    }
}