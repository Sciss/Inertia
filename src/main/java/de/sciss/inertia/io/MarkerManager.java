/*
 *  MarkerManager.java
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
 *		12-Aug-05	created from de.sciss.eisenkraut.io.MarkerManager ;
 *					uses new Comparable features of the Marker class
 */

package de.sciss.inertia.io;

import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import javax.swing.undo.*;

import de.sciss.app.AbstractApplication;
import de.sciss.app.BasicEvent;
import de.sciss.app.EventManager;

import de.sciss.io.AudioFileDescr;
import de.sciss.io.Marker;
import de.sciss.io.Span;

// INERTIA
//import de.sciss.eisenkraut.*;
//import de.sciss.eisenkraut.edit.*;
//import de.sciss.eisenkraut.session.*;
import de.sciss.inertia.edit.SyncCompoundEdit;

/**
 *	Note: all stop indices are considered <STRONG>inclusive</STRONG>
 *	unlike in practically all other classes.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.1, 12-Aug-05
 *
 *	@todo		should make use of <code>Collections.binarySearch</code>
 *				instead of using its own search algorithm
 */
public class MarkerManager
implements EventManager.Processor
{
	private final EventManager	elm	 = new EventManager( this );

	private java.util.List	collMarks	= new ArrayList();

	private static final int NUM_QUICKREF	= 4;
	private final int[] quickRef	= new int[ NUM_QUICKREF ];
	private int quickRefCirc		= 0;

//	private final LockManager	lm;
//	private final int			doors;

//	public MarkerManager( LockManager lm, int doors )
	public MarkerManager()
	{
//		this.lm		= lm;
//		this.doors	= doors;
	}
	
	// clears list and copies all markers from afd
	public void copyFromAudioFile( AudioFileDescr afd )
	{
		Marker mark;
	
		collMarks.clear();
		java.util.List marks = (java.util.List) afd.getProperty( AudioFileDescr.KEY_MARKERS );
		int removed = 0;
		if( marks != null ) {
			marks	= new ArrayList( marks );
			Collections.sort( marks );
//			marks	= Marker.sort( marks );
cleanLp1:	while( !marks.isEmpty() ) {
				mark	= (Marker) marks.get( marks.size() - 1 );
				if( mark.pos <= afd.length ) break cleanLp1;
				marks.remove( marks.size() - 1 );
				removed++;
			}
cleanLp2:	while( !marks.isEmpty() ) {
				mark	= (Marker) marks.get( 0 );
				if( mark.pos >= 0 ) break cleanLp2;
				marks.remove( 0 );
				removed++;
			}
			
			collMarks.addAll( marks );
			
			if( removed > 0 ) {
				System.err.println( "Warning: removed " + removed + " illegal marker positions!" );
			}
		}
	}

	// copies all markers to afd
	public void copyToAudioFile( AudioFileDescr afd )
	{
		afd.setProperty( AudioFileDescr.KEY_MARKERS, new ArrayList( collMarks ));
	}
		
	public void clear( Object source )
	{
		collMarks.clear();
	}
	
	public void removeSpan( Object source, Span span )
	{
		this.removeSpan( source, span, null );
	}

	public void removeSpan( Object source, Span span, SyncCompoundEdit ce )
	{
		final int				startIdx, stopIdx;
		final Span				modSpan;
		final java.util.List	collRemoved, collShifted;
		final long				delta;
		Marker					oldMark;
		UndoableEdit			edit;

//System.err.println( "mm : removeSpan " + span.getStart() + " ... " + span.getStop() + " (len " + span.getLength() + ")");
//debugDump();
	
		delta		= -span.getLength();
		if( delta == 0 ) return;
		startIdx	= indexOf( span.getStart(), true );
		if( startIdx == -1 ) return;
		stopIdx		= indexOf( span.getStop(), false, startIdx );

		modSpan		= new Span( span.getStart(), ((Marker) collMarks.get( collMarks.size() - 1 )).pos );
		collRemoved = new ArrayList( collMarks.subList( startIdx, collMarks.size() ));
		collShifted = new ArrayList( collMarks.size() - stopIdx - 1 );
		
		for( int i = stopIdx + 1; i < collMarks.size(); i++ ) {
			oldMark = (Marker) collMarks.get( i );
			collShifted.add( new Marker( oldMark.pos + delta, oldMark.name ));
		}
		
		edit = new Edit( collRemoved, EDIT_REMOVE, startIdx );
		if( ce != null ) ce.addEdit( edit );
		edit = new Edit( collShifted, EDIT_ADD, startIdx );
		if( ce != null ) ce.addEdit( edit );
		
		if( source != null ) {
			edit = new DispatchEdit( source, modSpan );
			if( ce != null ) ce.addEdit( edit );
		}

//debugDump();
	}

	public void insertSpan( Object source, Span span )
	{
		this.insertSpan( source, span, null );
	}

	public void insertSpan( Object source, Span span, SyncCompoundEdit ce )
	{
		final int				startIdx;
		final Span				modSpan;
		final java.util.List	collRemoved, collShifted;
		final long				delta;
		Marker					oldMark;
		UndoableEdit			edit;
		
//System.err.println( "mm : insertSpan " + span.getStart() + " ... " + span.getStop() + " (len " + span.getLength() + ")");
//debugDump();

		delta	= span.getLength();
		if( delta == 0 ) return;
		startIdx	= indexOf( span.getStart(), true );
		if( startIdx == -1 ) return;

		modSpan		= new Span( span.getStart(), ((Marker) collMarks.get( collMarks.size() - 1 )).pos );
		collRemoved = new ArrayList( collMarks.subList( startIdx, collMarks.size() ));
		collShifted = new ArrayList( collMarks.size() - startIdx );
		
		for( int i = startIdx; i < collMarks.size(); i++ ) {
			oldMark = (Marker) collMarks.get( i );
			collShifted.add( new Marker( oldMark.pos + delta, oldMark.name ));
		}
		
		edit = new Edit( collRemoved, EDIT_REMOVE, startIdx );
		if( ce != null ) ce.addEdit( edit );
		edit = new Edit( collShifted, EDIT_ADD, startIdx );
		if( ce != null ) ce.addEdit( edit );
		
		if( source != null ) {
			edit = new DispatchEdit( source, modSpan );
			if( ce != null ) ce.addEdit( edit );
		}
//debugDump();
	}

	public void addMarker( Object source, Marker mark )
	{
		this.addMarker( source, mark, null );
	}

	public void addMarkers( Object source, java.util.List markers, SyncCompoundEdit ce )
	{
		for( int i = 0; i < markers.size(); i++ ) {
			this.addMarker( source, (Marker) markers.get( i ), ce );
		}
	}

	public void addMarker( Object source, Marker mark, SyncCompoundEdit ce )
	{
		final int idx;
		final java.util.List	collInserted;
		UndoableEdit			edit;
		
		idx				= indexOf( mark.pos, false ) + 1;
		collInserted	= new ArrayList( 1 );
		collInserted.add( mark );
		edit			= new Edit( collInserted, EDIT_ADD, idx );
		if( ce != null ) ce.addEdit( edit );
	
		if( source != null ) {
			edit = new DispatchEdit( source, new Span( mark.pos, mark.pos ));	// + 1 ?
			if( ce != null ) ce.addEdit( edit );
		}
	}

	// note : stopIdx is inclusive!
	public void removeMarkers( Object source, int startIdx, int stopIdx )
	{
		this.removeMarkers( source, startIdx, stopIdx, null );
	}

	// note : stopIdx is inclusive!
	public void removeMarkers( Object source, int startIdx, int stopIdx, SyncCompoundEdit ce )
	{
		final Span				modSpan;
		final java.util.List	collRemoved;
		UndoableEdit			edit;
	
		if( stopIdx < startIdx ) return;

		modSpan = new Span( ((Marker) collMarks.get( startIdx )).pos,
							((Marker) collMarks.get( stopIdx )).pos );	// + 1 ?
	
		collRemoved = new ArrayList( collMarks.subList( startIdx, stopIdx + 1 ));
		edit		= new Edit( collRemoved, EDIT_REMOVE, startIdx );
		if( ce != null ) ce.addEdit( edit );
		
		if( source != null ) {
			edit = new DispatchEdit( source, modSpan );
			if( ce != null ) ce.addEdit( edit );
		}
	}

	/*
	 *  Get an Action object that will dump the
	 *  list of markers
	 *
	 *  @param  root	application root
	 *  @return <code>Action</code> suitable for attaching to a <code>JMenuItem</code>.
	 */
//	public static Action getDebugDumpAction( final Main root )
//	{
//		return new AbstractAction( "Dump marker list" ) {
//			public void actionPerformed( ActionEvent e )
//			{
//				MarkerManager mm;
//								
//				try {
//					lm.waitExclusive( doors );
//					mm = root.getDocument().markers;
//					mm.debugDump();
//				}
//				finally {
//					lm.releaseExclusive( doors );
//				}
//			}
//		};
//	}

	public void debugDump()
	{
		Marker mark;
	
		System.err.println( "mm : has " + collMarks.size()+" markers: " );
		for( int i = 0; i < collMarks.size(); i++ ) {
			mark = (Marker) collMarks.get( i );
			System.err.println( "   " + i + " : pos " + mark.pos + " ; name " + mark.name );
		}
	}

//---------
// folgende methoden sind auskommentiert,
// weil vollkommen un-debugged. ausserdem
// noch fehlende integration mit Edit

//	public void clearSpan( Object source, Span span )
//	{
//		final int startIdx	= indexOf( span.getStart(), true );
//		if( startIdx == -1 ) return;
//		int stopIdx	= indexOf( span.getStop(), false, startIdx + 1 );
//		if( stopIdx == -1 ) stopIdx = collMarks.size() - 1;
//		
//		this.removeMarkers( source, startIdx, stopIdx );
//	}

//	public void moveMarker( Object source, int idx, long newPos )
//	{
//		final Marker oldMark = (Marker) collMarks.get( idx );
//	
//		this.replaceMarker( source, oldMark, new Marker( newPos, oldMark.name ));
//	}

//	public void addMarkers( Object source, java.util.List markers )
//	{
//		if( markers.isEmpty() ) return;
//		
//		Marker	mark;
//		long	start	= Long.MAX_VALUE;
//		long	stop	= Long.MIN_VALUE;
//		
//		for( int i = 0; i < markers.size(); i++ ) {
//			mark	= (Marker) markers.get( i );
//			this.addMarker( null, mark );
//			if( mark.pos < start ) start = mark.pos;
//			if( mark.pos > stop )  stop  = mark.pos;
//		}
//		
//		if( source != null ) dispatchModification( source, new Span( start, stop + 1 ));
//	}
//---------
	
	public void replaceMarker( Object source, Marker oldMark, Marker newMark )
	{
		this.replaceMarker( source, oldMark, newMark, null );
	}

	public void replaceMarker( Object source, Marker oldMark, Marker newMark, SyncCompoundEdit ce )
	{
		final int			oldIdx;
		final Span			modSpan;
		final UndoableEdit	edit;
	
		oldIdx	= indexOf( oldMark );
		if( oldIdx == -1 ) {
			System.err.println( "MarkerManager.replaceMarker : '" + oldMark.name + "' not found!" );
			return;
		}
	
		modSpan	= new Span( Math.min( oldMark.pos, newMark.pos ), Math.max( oldMark.pos, newMark.pos ));

		this.removeMarkers( null, oldIdx, oldIdx, ce );
		this.addMarker( null, newMark, ce );
		
		if( source != null ) {
			edit = new DispatchEdit( source, modSpan );
			if( ce != null ) ce.addEdit( edit );
		}
	}

	public java.util.List getMarkers( Span span, boolean plusBefore, boolean plusAfter )
	{
		final int startIdx = Math.max( 0, indexOf( span.getStart(), true ) - 1 );
		int stopIdx	= indexOf( span.getStop(), plusAfter, startIdx );
		if( stopIdx == -1 ) stopIdx = Math.max( startIdx, collMarks.size() ) - 1;
		
		return( new ArrayList( collMarks.subList( startIdx, stopIdx + 1 )));
	}

	public java.util.List getMarkers( int startIdx, int stopIdx )
	{
		return( new ArrayList( collMarks.subList( startIdx, stopIdx + 1 )));
	}

	public Marker getMarker( int idx )
	{
		return( (Marker) collMarks.get( idx ));
	}

	public int getNumMarkers()
	{
		return( collMarks.size() );
	}
	
	// note : greedy search!
	public int indexOf( Marker mark )
	{
		return( collMarks.indexOf( mark ));
	}
	
	public java.util.List getAllMarkers()
	{
		return( new ArrayList( collMarks ));
	}
		
	public int indexOf( long pos, boolean rightHand )
	{
		return indexOf( pos, rightHand, collMarks.size() >> 1 );	// start in da middle
	}

	private int indexOf( long pos, boolean rightHand, int startIdx )
	{
		int			i, mul, stepSize;
		Marker		mark, mark2;
		final int	size	= collMarks.size();

		if( startIdx >= size ) return -1;
		
		// quickly review the last queries since it's likely
		// that we've looked up an immediate neighbour already
		for( i = 0; i < NUM_QUICKREF; i++ ) {
			if( quickRef[ i ] >= size ) continue;
			mark = (Marker) collMarks.get( quickRef[ i ]);
			if( quickRef[ i ] > 0 ) {
				mark2 = (Marker) collMarks.get( quickRef[ i ] - 1 );
				if( (mark.pos >= pos) && (mark2.pos <= pos) ) return quickIndexOf( pos, rightHand, quickRef[ i ]);
			} else if( quickRef[ i ] + 1 < size ) {
				mark2 = (Marker) collMarks.get( quickRef[ i ] + 1 );
				if( (mark2.pos >= pos) && (mark.pos <= pos) ) return quickIndexOf( pos, rightHand, quickRef[ i ]);
			}
		}

		i		= startIdx;
		mark	= (Marker) collMarks.get( i );
		if( mark.pos == pos ) return quickIndexSave( pos, rightHand, i );

		if( mark.pos < pos ) {
			stepSize	= (size - i) >> 1;
			mul			= 1;
		} else {
			stepSize	= i >> 1;
			mul			= -1;
		}
		
		do {
			i += mul * stepSize;
			if( i < 0 ) {
				i = 0;
				mul	= 1;
			} else if( i >= size ) {
				i	= size - 1;
				mul	= -1;
			} else {
				mark = (Marker) collMarks.get( i );
				if( mark.pos == pos ) return quickIndexSave( pos, rightHand, i );
				if( mark.pos < pos ) {
					mul	= 1;
				} else {
					mul = -1;
				}
			}
			stepSize >>= 1;
		} while( stepSize > 2 );
		
		return quickIndexSave( pos, rightHand, i );
	}

	private int quickIndexSave( long pos, boolean rightHand, int idx )
	{
		final int result = quickIndexOf( pos, rightHand, idx );
		if( result != -1 ) {
			quickRef[ quickRefCirc ] = result;
			quickRefCirc = (quickRefCirc + 1) % NUM_QUICKREF;
		}
		return result;
	}

	private int quickIndexOf( long pos, boolean rightHand, int idx )
	{
		Marker		mark;
		long		lastPos;
		final int	size		= collMarks.size();

		mark = (Marker) collMarks.get( idx );
		
		// look'ed up pos is garantueed <= pos of marker whose index is returned
		// vice versa: returned marker's pos is garantueed >= given pos
		if( rightHand ) {
			if( mark.pos >= pos ) {	// try to go back in time
				for( --idx; idx >= 0; idx-- ) {
					mark = (Marker) collMarks.get( idx );
					if( mark.pos < pos ) return idx + 1;
				}
				return 0;
			
			} else {	// try to advance in time
				for( ++idx; idx < size; idx++ ) {
					mark = (Marker) collMarks.get( idx );
					if( mark.pos >= pos ) return idx;
				}
				return -1;
			}
	
		// look'ed up pos is garantueed > pos of marker whose index is returned
		// vice versa: returned marker's pos is garantueed < given pos
		} else {
			if( mark.pos >= pos ) {	// try to go back in time ; crucial to account for possible duplicate mark.pos
				for( --idx; idx >= 0; idx-- ) {
					mark	= (Marker) collMarks.get( idx );
					if( mark.pos < pos ) return idx;
				}
				return -1;
				
			} else {	// try to advance in time
				for( ++idx; idx < size; idx++ ) {
					mark = (Marker) collMarks.get( idx );
					if( mark.pos >= pos ) return idx - 1;
				}
				return size - 1;
			}
		}
	}

	public void addListener( MarkerManager.Listener listener )
	{
		elm.addListener( listener );
	}

	public void removeListener( MarkerManager.Listener listener )
	{
		elm.removeListener( listener );
	}

	private void dispatchModification( Object source, Span span )
	{
		elm.dispatchEvent( new MarkerManager.Event( this, source, span ));
	}

// --------------------- EventManager.Processor interface ---------------------
	
	/**
	 *  This is called by the EventManager
	 *  if new events are to be processed. This
	 *  will invoke the listener's <code>propertyChanged</code> method.
	 */
	public void processEvent( BasicEvent e )
	{
		MarkerManager.Listener listener;
		int i;
		MarkerManager.Event mme = (MarkerManager.Event) e;
		
		for( i = 0; i < elm.countListeners(); i++ ) {
			listener = (MarkerManager.Listener) elm.getListener( i );
			switch( e.getID() ) {
			case MarkerManager.Event.MODIFIED:
				listener.markersModified( mme );
				break;
			default:
				assert false : e.getID();
				break;
			}
		} // for( i = 0; i < this.countListeners(); i++ )
	}

// --------------------- internal interfaces ---------------------

	/**
	 *  A simple interface describing
	 *  the method that gets called from
	 *  the event dispatching thread when
	 *  new objects have been queued.
	 */
	public interface Listener
	{
		public void markersModified( MarkerManager.Event e );
	}
	
// --------------------- internal classes ---------------------

	public static class Event
	extends BasicEvent
	{
		public static final int MODIFIED		= 0;
		
		private final MarkerManager mm;
		private final Span			span;

		public Event( MarkerManager mm, Object source, Span affectedSpan )
		{
			super( source, MODIFIED, System.currentTimeMillis() );
			
			this.mm			= mm;
			this.span		= affectedSpan;
		}
		
		public MarkerManager getManager()
		{
			return mm;
		}

		public Span getAffectedSpan()
		{
			return span;
		}
		
		/**
		 *  Returns false always at the moment
		 */
		public boolean incorporate( BasicEvent oldEvent )
		{
			return false;
		}
	}
	
	// undable edits
	
	private static final int EDIT_ADD		= 0;
	private static final int EDIT_REMOVE	= 1;
	
	private class Edit
	extends AbstractUndoableEdit
	{
		private final int				cmd;
		private final java.util.List	coll;
		private final int				idx;
		private final String			key;
	
		private Edit( java.util.List coll, int cmd, int idx )
		{
			this( coll, cmd, idx, "editChangeMarkerList" );
		}

		private Edit( java.util.List coll, int cmd, int idx, String key )
		{
			this.coll	= coll;
			this.cmd	= cmd;
			this.idx	= idx;
			this.key	= key;
			
			perform();
		}

		private void perform()
		{
			switch( cmd ) {
			case EDIT_ADD:
				collMarks.addAll( idx, coll );
				break;
			case EDIT_REMOVE:
				collMarks.removeAll( coll );
				break;
			default:
				assert false : cmd;
			}
		}
		
		public void undo()
		{
			super.undo();
			
			switch( cmd ) {
			case EDIT_ADD:
				collMarks.removeAll( coll );
				break;
			case EDIT_REMOVE:
				collMarks.addAll( idx, coll );
				break;
			default:
				assert false : cmd;
			}
		}
		
		public void redo()
		{
			super.redo();
			perform();
		}
		
		public String getPresentationName()
		{
			return AbstractApplication.getApplication().getResourceString( key );
		}
	}

	private class DispatchEdit
	extends AbstractUndoableEdit
	{
		private Object		source;
		private final Span	span;
	
		private DispatchEdit( Object source, Span span )
		{
			this.source	= source;
			this.span	= span;
			
			perform();
			this.source	= this;	// ensure "foreign" event source when re-doing!
		}

		private void perform()
		{
			dispatchModification( source, span );
		}
		
		public void undo()
		{
			super.undo();
			perform();
		}
		
		public void redo()
		{
			super.redo();
			perform();
		}
		
		public String getPresentationName()
		{
			return AbstractApplication.getApplication().getResourceString( "editChangeMarkerList" );
		}
	}
}