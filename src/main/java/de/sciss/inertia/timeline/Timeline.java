/*
 *  Timeline.java
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
 *		07-Aug-05	copied from de.sciss.eisenkraut.timeline.Timeline
 *					; allows null sources
 */

package de.sciss.inertia.timeline;

import java.io.*;
import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.*;

// INERTIA
//import de.sciss.eisenkraut.*;
//import de.sciss.eisenkraut.io.*;
//import de.sciss.eisenkraut.util.*;
import de.sciss.inertia.session.*;
import de.sciss.util.MapManager;

import de.sciss.app.BasicEvent;
import de.sciss.app.EventManager;

import de.sciss.io.Span;

/**
 *  This class describes the document's timeline
 *  properties, e.g. length, selection, visible span.
 *  It contains an event dispatcher for TimelineEvents
 *  which get fired when methods like setPosition are
 *  called.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.2, 12-May-05
 *
 *	@todo		a session length greater than approx.
 *				16 1/2 minutes produces deadlocks when
 *				select all is called on the timeline frame!!
 */
public class Timeline
extends AbstractSessionObject
implements EventManager.Processor
{
	private static final String  MAP_KEY_RATE			= "rate";
	private static final String  MAP_KEY_LENGTH			= "len";
	private static final String  MAP_KEY_POSITION		= "pos";

	private int  rate;				// sampleframes per second
	private long length;		    // total number of sampleframes
	private long position;			// current head position
    private Span visibleSpan;		// what's being viewed in the TimelineFrame
    private Span selectionSpan;

	/**
	 *	Name attribute of the object
	 *	element in a session's XML file
	 */
	public static final String XML_OBJECT_NAME			= "timeline";

	// --- event handling ---

	private final EventManager elm = new EventManager( this );

	/**
	 *  Creates a new empty timeline
	 */
	public Timeline()
	{
		super();
		MapManager	map	= getMap();

		map.putContext( this, MAP_KEY_RATE, new MapManager.Context( 0, MapManager.Context.TYPE_INTEGER, null, null, null,
																	new Integer( 1000 )));
		map.putContext( this, MAP_KEY_LENGTH, new MapManager.Context( 0, MapManager.Context.TYPE_LONG, null, null, null,
																	  new Long( 0 )));
		map.putContext( this, MAP_KEY_POSITION, new MapManager.Context( 0, MapManager.Context.TYPE_LONG, null, null, null,
																		new Long( 0 )));

		clear( this );
		setName( XML_OBJECT_NAME );
	}
	
	/**
	 *  Clears the timeline, i.e. the length is set to zero,
	 *  selection and visible span are cleared.
	 *  The caller should ensure
	 *  that event dispatching is paused!
	 *
	 *  @param  source  who originated the action
	 */
	public void clear( Object source )
	{
		setRate( source, 1000 );
		setPosition( source, 0 );
		setSelectionSpan( source, new Span() );
		setVisibleSpan( source, new Span() );
		setLength( source, 0 );
		getMap().clearValues( source );
	}

	/**
	 *  Pauses the event dispatching. No
	 *  events are destroyed, only execution
	 *  is deferred until resumeDispatcher is
	 *  called.
	 */
	public void pauseDispatcher()
	{
		elm.pause();
	}

	/**
	 *  Resumes the event dispatching. Pending
	 *  events will be diffused during the
	 *  next run of the event thread.
	 */
	public void resumeDispatcher()
	{
		elm.resume();
	}

	/**
	 *  Queries the timeline sample rate
	 *
	 *  @return the rate of timeline data (trajectories etc.)
	 *			in frames per second
	 */
	public int getRate()
	{
		return rate;
	}

	/**
	 *  Queries the timeline's length
	 *
	 *  @return the timeline length in frames
	 */
	public long getLength()
	{
		return length;
	}

	/**
	 *  Queries the timeline's position
	 *
	 *  @return the current timeline offset (the position
	 *			of the vertical bar in the timeline frame)
	 *			in frames
	 */
	public long getPosition()
	{
		return position;
	}

	/**
	 *  Queries the timeline's visible span
	 *
	 *  @return the portion of the timeline currently
	 *			visible in the timeline frame. start and
	 *			stop are measured in frames
	 */
	public Span getVisibleSpan()
	{
		return( new Span( visibleSpan ));
	}

	/**
	 *  Queries the timeline's selected span
	 *
	 *  @return the portion of the timeline currently
	 *			selected (highlighted blue). start and
	 *			stop are measured in frames
	 */
	public Span getSelectionSpan()
	{
		return( new Span( selectionSpan ));
	}

	/**
	 *  Changes the timeline sample rate. This
	 *  fires a <code>TimelineEvent</code> (<code>CHANGED</code>). Note
	 *  that there's no corresponding undoable edit.
	 *
	 *  @param  source  the source of the <code>TimelineEvent</code>
	 *  @param  rate	the new rate of timeline data (trajectories etc.)
	 *					in frames per second
	 *
	 *  @see	TimelineEvent#CHANGED
	 */
    public void setRate( Object source, int rate )
    {
        this.rate = rate;
		if( source != null ) dispatchChange( source );
		getMap().putValue( this, MAP_KEY_RATE, new Integer( rate ));
    }

	/**
	 *  Changes the timeline's length. This is
	 *  normally done indirectly by creating an
	 *  <code>EditInsertTimeSpan</code> or
	 *  <code>EditRemoveTimeSpan</code> object.
	 *  Alternatively <code>EditSetTimelineLength</code> provides
	 *  a more low level object.
	 *  This fires a <code>TimelineEvent</code> (<code>CHANGED</code>).
	 *
	 *  @param  source  the source of the <code>TimelineEvent</code>
	 *  @param  length  the new timeline length in frames
	 *
	 *  @see	de.sciss.meloncillo.edit.EditInsertTimeSpan
	 *  @see	de.sciss.meloncillo.edit.EditRemoveTimeSpan
	 *  @see	de.sciss.meloncillo.edit.EditSetTimelineLength
	 *  @see	TimelineEvent#CHANGED
	 */
    public void setLength( Object source, long length )
    {
        this.length = length;
        if( source != null ) dispatchChange( source );
		getMap().putValue( this, MAP_KEY_LENGTH, new Long( length ));
    }

	/**
	 *  Changes the current the timeline's playback/insert position. This
	 *  fires a <code>TimelineEvent</code> (<code>POSITIONED</code>).
	 *  Often you'll want to use an <code>EditSetTimelinePosition</code> object.
	 *
	 *  @param  source		the source of the <code>TimelineEvent</code>
	 *  @param  position	the new timeline offset (the position
	 *						of the vertical bar in the timeline frame)
	 *						in frames
	 *
	 *  @see	de.sciss.meloncillo.edit.EditSetTimelinePosition
	 *  @see	TimelineEvent#POSITIONED
	 */
    public void setPosition( Object source, long position )
	{
        this.position = position;
        if( source != null ) dispatchPosition( source );
		getMap().putValue( this, MAP_KEY_POSITION, new Long( position ));
	}

	/**
	 *  Changes the timeline's visible span. This
	 *  fires a <code>TimelineEvent</code> (<code>SCROLLED</code>).
	 *  Often you'll want to use an <code>EditSetTimelineScroll</code> object.
	 *
	 *  @param  source	the source of the <code>TimelineEvent</code>
	 *  @param  span	the portion of the timeline that should be
	 *					displayed in the timeline frame. start and
	 *					stop are measured in frames
	 *
	 *  @see	de.sciss.meloncillo.edit.EditSetTimelineScroll
	 *  @see	TimelineEvent#SCROLLED
	 */
     public void setVisibleSpan( Object source, Span span )
	{
        this.visibleSpan = new Span( span );
        if( source != null ) dispatchScroll( source );
	}

	/**
	 *  Changes the timeline's selected span. This
	 *  fires a <code>TimelineEvent</code> (<code>SELECTED</code>).
	 *  Often you'll want to use an <code>EditSetTimelineSelection</code> object.
	 *
	 *  @param  source	the source of the <code>TimelineEvent</code>
	 *  @param  span	the portion of the timeline which should be
	 *					selected (highlighted blue). start and
	 *					stop are measured in frames
	 *
	 *  @see	de.sciss.meloncillo.edit.EditSetTimelineSelection
	 *  @see	TimelineEvent#SELECTED
	 */
    public void setSelectionSpan( Object source, Span span )
	{
		this.selectionSpan = new Span( span );
		if( source != null ) dispatchSelection( source );
	}

	/**
	 *  Register a <code>TimelineListener</code>
	 *  which will be informed about changes of
	 *  the timeline (i.e. changes in rate and length,
	 *  scrolling in the timeline frame and selection
	 *  of timeline portions by the user).
	 *
	 *  @param  listener	the <code>TimelineListener</code> to register
	 *  @see	de.sciss.meloncillo.util.EventManager#addListener( Object )
	 */
	public void addTimelineListener( TimelineListener listener )
	{
		elm.addListener( listener );
	}

	/**
	 *  Unregister a <code>TimelineListener</code>
	 *  from receiving timeline events.
	 *
	 *  @param  listener	the <code>TimelineListener</code> to unregister
	 *  @see	de.sciss.meloncillo.util.EventManager#removeListener( Object )
	 */
	public void removeTimelineListener( TimelineListener listener )
	{
		elm.removeListener( listener );
	}

	/**
	 *  This is called by the EventManager
	 *  if new events are to be processed
	 */
	public void processEvent( BasicEvent e )
	{
		TimelineListener listener;
		int i;
		
		for( i = 0; i < elm.countListeners(); i++ ) {
			listener = (TimelineListener) elm.getListener( i );
			switch( e.getID() ) {
			case TimelineEvent.CHANGED:
				listener.timelineChanged( (TimelineEvent) e );
				break;
			case TimelineEvent.POSITIONED:
				listener.timelinePositioned( (TimelineEvent) e );
				break;
			case TimelineEvent.SELECTED:
				listener.timelineSelected( (TimelineEvent) e );
				break;
			case TimelineEvent.SCROLLED:
				listener.timelineScrolled( (TimelineEvent) e );
				break;
			}
		} // for( i = 0; i < elm.countListeners(); i++ )
	}

	// utility function to create and dispatch a TimelineEvent
	private void dispatchChange( Object source )
	{
		TimelineEvent e2 = new TimelineEvent( source, TimelineEvent.CHANGED,
										      System.currentTimeMillis(), 0, null );
		elm.dispatchEvent( e2 );
	}

	// utility function to create and dispatch a TimelineEvent
	private void dispatchPosition( Object source )
	{
		TimelineEvent e2 = new TimelineEvent( source, TimelineEvent.POSITIONED,
											  System.currentTimeMillis(), 0, new Double( getPosition() ));
		elm.dispatchEvent( e2 );
	}
    

	// utility function to create and dispatch a TimelineEvent
	private void dispatchSelection( Object source )
	{
		TimelineEvent e2 = new TimelineEvent( source, TimelineEvent.SELECTED,
											  System.currentTimeMillis(), 0, getSelectionSpan() );
		elm.dispatchEvent( e2 );
	}

	// utility function to create and dispatch a TimelineEvent
	private void dispatchScroll( Object source )
	{
		TimelineEvent e2 = new TimelineEvent( source, TimelineEvent.SCROLLED,
											  System.currentTimeMillis(), 0, getVisibleSpan() );
		elm.dispatchEvent( e2 );
	}

// ---------------- SessionObject interface ---------------- 

	/**
	 *  This simply returns <code>null</code>!
	 */
	public Class getDefaultEditor()
	{
		return null;
	}

// ---------------- MapManager.Listener interface ---------------- 

	public void mapChanged( MapManager.Event e )
	{
		super.mapChanged( e );
		
		final Object source = e.getSource();
		
		if( source == this ) return;
		
		final Set	keySet		= e.getPropertyNames();
		Object		val;
		boolean		dChange		= false;
		boolean		dPosition	= false;

		if( keySet.contains( MAP_KEY_RATE )) {
			val		= e.getManager().getValue( MAP_KEY_RATE );
			if( val != null ) {
				rate	= ((Number) val).intValue();
				dChange	= true;
			}
		}
		if( keySet.contains( MAP_KEY_LENGTH )) {
			val		= e.getManager().getValue( MAP_KEY_LENGTH );
			if( val != null ) {
				length	= ((Number) val).longValue();
				dChange	= true;
				if( visibleSpan.isEmpty() && length > 0 ) setVisibleSpan( this, new Span( 0, length ));
			}
		}
		if( keySet.contains( MAP_KEY_POSITION )) {
			val		= e.getManager().getValue( MAP_KEY_POSITION );
			if( val != null ) {
				position	= ((Number) val).longValue();
				dPosition	= true;
			}
		}

		if( dChange ) dispatchChange( source );
		if( dPosition ) dispatchPosition( source );
	}

// ---------------- XMLRepresentation interface ---------------- 

	public void toXML( org.w3c.dom.Document domDoc, Element node, Map options )
	throws IOException
	{
		super.toXML( domDoc, node, options );
	}

	/**
	 *  The caller (usually Session) should clear the timeline
	 *  before invoking this method.
	 */
	public void fromXML( org.w3c.dom.Document domDoc, Element node, Map options )
	throws IOException
	{
		super.fromXML( domDoc, node, options );
	}
}