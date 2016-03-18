/*
 *  EditSetTimelinePosition.java
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
 *		12-Aug-05	copied from de.sciss.eisenkraut.edit.EditSetTimelinePosition
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

/**
 *  An <code>UndoableEdit</code> that
 *  describes the change of the current
 *  timeline position.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.6, 29-Jul-04
 *  @see		UndoManager
 *  @see		EditRemoveTimeSpan
 *
 *	@todo		edits should be queued 
 *				temporarily in the undo manager
 *				until a significant edit is
 *				performed, so that the redo
 *				tree is not destroyed immediately
 */
public class EditSetTimelinePosition
extends BasicUndoableEdit
{
	private Object			source;
	private final Session   doc;
	private long			oldPosition, newPosition;

	/**
	 *  Create and perform the edit. It invokes the
	 *  <code>Timeline.setPosition</code> method,
	 *  thus dispatching a <code>TimelineEvent</code>.
	 *
	 *  @param  source		who originated the edit. the source is
	 *						passed to the <code>Timeline.setPosition</code> method.
	 *  @param  doc			session into whose <code>Timeline</code> is
	 *						affected.
	 *  @param  position	the new timeline length in sample frames. the caller
	 *						is responsible for checking the validity of this value.
	 *  @synchronization	waitExclusive on DOOR_TIME
	 */
	public EditSetTimelinePosition( Object source, Session doc, long position )
	{
		super();
		this.source			= source;
		this.doc			= doc;
		this.newPosition	= position;

		try {
			doc.bird.waitExclusive( Session.DOOR_TIME );
			this.oldPosition= doc.timeline.getPosition();
			doc.timeline.setPosition( source, newPosition );
		}
		finally {
			doc.bird.releaseExclusive( Session.DOOR_TIME );
		}

		this.source			= this;
	}

	/**
	 *  @return		false to tell the UndoManager it should not feature
	 *				the edit as a single undoable step in the history.
	 *				which is especially important since <code>TimelineAxis</code>
	 *				will generate lots of edits when the user drags
	 *				the timeline position.
	 */
	public boolean isSignificant()
	{
		return false;
	}

	/**
	 *  Undo the edit
	 *  by calling the <code>Timeline.setPosition</code>,
	 *  method, thus dispatching a <code>TimelineEvent</code>.
	 *
	 *  @synchronization	waitExlusive on DOOR_TIME.
	 */
	public void undo()
	{
		super.undo();
		try {
			doc.bird.waitExclusive( Session.DOOR_TIME );
			doc.timeline.setPosition( source, oldPosition );
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
			doc.timeline.setPosition( source, newPosition );
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
		if( anEdit instanceof EditSetTimelinePosition ) {
			this.newPosition = ((EditSetTimelinePosition) anEdit).newPosition;
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
		if( anEdit instanceof EditSetTimelinePosition ) {
			this.oldPosition = ((EditSetTimelinePosition) anEdit).oldPosition;
			anEdit.die();
			return true;
		} else {
			return false;
		}
	}

	public String getPresentationName()
	{
		return getResourceString( "editSetTimelinePosition" );
	}
}