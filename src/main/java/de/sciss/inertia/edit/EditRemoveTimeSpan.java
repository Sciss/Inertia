/*
 *  EditRemoveTimeSpan.java
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
 *		12-Aug-05	de.sciss.eisenkraut.edit.EditRemoveTimeSpan
 */

package de.sciss.inertia.edit;

import java.util.*;
import javax.swing.*;
import javax.swing.undo.*;

// INERTIA
//import de.sciss.eisenkraut.*;
//import de.sciss.eisenkraut.session.*;
//import de.sciss.eisenkraut.timeline.*;
//import de.sciss.eisenkraut.util.*;
import de.sciss.inertia.session.Session;

import de.sciss.app.AbstractApplication;

import de.sciss.io.Span;

/**
 *  An <code>UndoableEdit</code> that
 *  describes the removal of a time span
 *  from the session's timeline.
 *  Actually a <code>CompoundEdit</code>
 *  composed of <code>EditSetTimelineScroll</code> (optional),
 *  <code>EditSetTimelineSelection</code> (optional),
 *  <code>EditSetTimelinePosition</code> (optional)
 *  and <code>EditSetTimelineLength</code>.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.4, 27-Jul-05
 *  @see		UndoManager
 *  @see		EditSetTimelineLength
 *  @see		EditSetTimelineSelection
 *  @see		EditSetTimelineScroll
 */
public class EditRemoveTimeSpan
extends BasicSyncCompoundEdit
{
	/**
	 *  Create and perform the edit. If the visible
	 *  span's stop exceeds the new truncated timeline
	 *  length, the visible span will be shifted back
	 *  in time. if the timeline selection overlaps with
	 *  the removed span, it will be truncated. if the
	 *  timeline position lies within the removed span,
	 *  it will be set to <code>span.getStart()</code>.
	 *  if the position lies after the removed span it
	 *  will be shifted back in time.
	 *
	 *  @param  source		who originated the edit
	 *  @param  doc			session from whose timeline the span is to be removed
	 *  @param  span		the time span to remove. note that selection
	 *						span and visible are possible modified in
	 *						the course of the performance of the edit, the
	 *						timeline position might be altered.
	 *  @synchronization	waitExclusive on DOOR_TIME
	 *  @todo				as far as i see the (pratically irrelevant) case
	 *						in which the selection start is smaller than
	 *						the span to remove is not handled properly.
	 */
	public EditRemoveTimeSpan( Object source, Session doc, Span span )
	{
		super( doc.bird, Session.DOOR_TIME, AbstractApplication.getApplication().getResourceString( "editRemoveTimeSpan" ));
	
		long	spanLength, position, newLength;
		Span	selectionSpan, visibleSpan;

		try {
			doc.bird.waitExclusive( Session.DOOR_TIME );
			spanLength	= span.getLength();
			visibleSpan = doc.timeline.getVisibleSpan();
			newLength   = doc.timeline.getLength() - spanLength;
			if( visibleSpan.getStop() > newLength ) {
				this.addEdit( new EditSetTimelineScroll( source, doc,
					new Span( Math.max( 0, newLength - visibleSpan.getLength() ), newLength )));
			}
			selectionSpan = doc.timeline.getSelectionSpan();
			if( selectionSpan.contains( span.getStart() )) {
				if( selectionSpan.contains( span.getStop() )) {
					this.addEdit( new EditSetTimelineSelection( source, doc,
						new Span( selectionSpan.getStart(), selectionSpan.getStop() - spanLength )));
				} else {
					this.addEdit( new EditSetTimelineSelection( source, doc,
						new Span( selectionSpan.getStart(), span.getStart() )));
				}
			} else if( selectionSpan.getStart() > span.getStart() ) {
				this.addEdit( new EditSetTimelineSelection( source, doc,
					new Span( selectionSpan.getStart() - spanLength, selectionSpan.getStop() - spanLength )));
			}
			position = doc.timeline.getPosition();
			if( span.contains( position )) {
				this.addEdit( new EditSetTimelinePosition( source, doc, span.getStart() ));
			} else if( position >= span.getStop() ) {
				this.addEdit( new EditSetTimelinePosition( source, doc, position - spanLength ));
			}
			
			doc.markers.removeSpan( source, span, this );
			
			this.addEdit( new EditSetTimelineLength( source, doc, newLength ));
			this.end();
		}
		finally {
			doc.bird.releaseExclusive( Session.DOOR_TIME );
		}
	}
}