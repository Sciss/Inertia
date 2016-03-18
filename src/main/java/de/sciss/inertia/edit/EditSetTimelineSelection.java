/*
 *  EditSetTimelineSelection.java
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
 *		12-Aug-05	copied from de.sciss.eisenkraut.edit.EditSetTimelineSelection
 */

package de.sciss.inertia.edit;

import java.util.*;
import javax.swing.*;
import javax.swing.undo.*;

// INERTIA
//import de.sciss.eisenkraut.*;
//import de.sciss.eisenkraut.timeline.*;
//import de.sciss.eisenkraut.session.*;
//import de.sciss.eisenkraut.util.*;
import de.sciss.inertia.session.Session;

import de.sciss.io.Span;

/**
 *  An <code>UndoableEdit</code> that
 *  describes the modification of the
 *  timeline selected span.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.6, 29-Jul-04
 *  @see		UndoManager
 *  @see		EditRemoveTimeSpan
 */
public class EditSetTimelineSelection
extends BasicUndoableEdit
{
	private Object			source;
	private final Session   doc;
	private Span			oldSpan, newSpan;

	/**
	 *  Create and perform the edit. This method
	 *  invokes the <code>Timeline.setSelectionSpan</code> method,
	 *  thus dispatching a <code>TimelineEvent</code>.
	 *
	 *  @param  source		who originated the edit. the source is
	 *						passed to the <code>Timeline.setSelectionSpan</code> method.
	 *  @param  doc			session into whose <code>Timeline</code> is
	 *						to be selected / deselected.
	 *  @param  span		the new timeline selection span.
	 *  @synchronization	waitExclusive on DOOR_TIME
	 */
	public EditSetTimelineSelection( Object source, Session doc, Span span )
	{
		super();
		this.source		= source;
		this.doc		= doc;

		try {
			doc.bird.waitExclusive( Session.DOOR_TIME );
			this.oldSpan = doc.timeline.getSelectionSpan();
			this.newSpan = span;
			doc.timeline.setSelectionSpan( source, span );
			this.source  = this;
		}
		finally {
			doc.bird.releaseExclusive( Session.DOOR_TIME );
		}
	}

	/**
	 *  @return		false to tell the UndoManager it should not feature
	 *				the edit as a single undoable step in the history.
	 *				which is especially important since <code>TimelineAxis</code>
	 *				will generate lots of edits when the user drags
	 *				the timeline selection.
	 */
	public boolean isSignificant()
	{
		return false;
	}

	/**
	 *  Undo the edit
	 *  by calling the <code>Timeline.setSelectionSpan</code>,
	 *  method, thus dispatching a <code>TimelineEvent</code>.
	 *
	 *  @synchronization	waitExlusive on DOOR_TIME.
	 */
	public void undo()
	{
		super.undo();
		try {
			doc.bird.waitExclusive( Session.DOOR_TIME );
			doc.timeline.setSelectionSpan( source, oldSpan );
		}
		finally {
			doc.bird.releaseExclusive( Session.DOOR_TIME );
		}
	}
	
	/**
	 *  Redo the edit. The original source is discarded
	 *  which means, that, since a new <code>TimelineEvent</code>
	 *  is dispatched, even the original object
	 *  causing the edit will not know the details
	 *  of the action, hence thoroughly look
	 *  and adapt itself to the new edit.
	 *
	 *  @synchronization	waitExlusive on DOOR_TIME.
	 */
	public void redo()
	{
		super.redo();
		try {
			doc.bird.waitExclusive( Session.DOOR_TIME );
			doc.timeline.setSelectionSpan( source, newSpan );
		}
		finally {
			doc.bird.releaseExclusive( Session.DOOR_TIME );
		}
	}
	
	/**
	 *  Collapse multiple successive EditSetReceiverBounds edit
	 *  into one single edit. The new edit is sucked off by
	 *  the old one.
	 */
	public boolean addEdit( UndoableEdit anEdit )
	{
		if( anEdit instanceof EditSetTimelineSelection ) {
			this.newSpan = ((EditSetTimelineSelection) anEdit).newSpan;
			anEdit.die();
			return true;
		} else {
			return false;
		}
	}

	/**
	 *  Collapse multiple successive EditSetReceiverBounds edit
	 *  into one single edit. The old edit is sucked off by
	 *  the new one.
	 */
	public boolean replaceEdit( UndoableEdit anEdit )
	{
		if( anEdit instanceof EditSetTimelineSelection ) {
			this.oldSpan = ((EditSetTimelineSelection) anEdit).oldSpan;
			anEdit.die();
			return true;
		} else {
			return false;
		}
	}

	public String getPresentationName()
	{
		return getResourceString( "editSetTimelineSelection" );
	}
}