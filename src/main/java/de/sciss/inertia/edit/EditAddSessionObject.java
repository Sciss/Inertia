/*
 *  EditAddSessionObject.java
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
 *		13-Aug-05	created
 */

package de.sciss.inertia.edit;

import java.util.*;
import javax.swing.*;
import javax.swing.undo.*;

import de.sciss.util.LockManager;
import de.sciss.inertia.session.SessionCollection;
import de.sciss.inertia.session.SessionObject;

/**
 *  An <code>UndoableEdit</code> that
 *  describes the adding of new receivers
 *  to the session.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.71, 22-Jan-05
 *  @see		UndoManager
 *  @see		EditRemoveSessionObjects
 */
public class EditAddSessionObject
extends BasicUndoableEdit
{
	private final SessionObject		so;
	private final int				index;
	private Object					source;
	private final LockManager		lm;
	private final int				doors;
	private final SessionCollection	quoi;

	/**
	 *  Create and perform this edit. This
	 *  invokes the <code>SessionObjectCollection.addAll</code> method,
	 *  thus dispatching a <code>SessionCollection.Event</code>.
	 *
	 *  @param  source			who initiated the action
	 *  @synchronization		waitExclusive on doors
	 */
	public EditAddSessionObject( Object source, SessionCollection quoi,
								 SessionObject so, int index, LockManager lm, int doors )
	{
		super();
		this.source				= source;
		this.quoi				= quoi;
		this.so					= so;
		this.index				= index;
		this.lm					= lm;
		this.doors				= doors;
		perform();
		this.source				= this;
	}

	private void perform()
	{
		try {
			lm.waitExclusive( doors );
			quoi.add( source, index, so );
		}
		finally {
			lm.releaseExclusive( doors );
		}
	}

	/**
	 *  @synchronization	waitExlusive on doors.
	 */
	public void undo()
	{
		super.undo();
		try {
			lm.waitExclusive( doors );
			quoi.remove( source, so );
		}
		finally {
			lm.releaseExclusive( doors );
		}
	}
	
	/**
	 *  Redo the add operation.
	 *  The original source is discarded
	 *  which means, that, since a new <code>SessionCollection.Event</code>
	 *  is dispatched, even the original object
	 *  causing the edit will not know the details
	 *  of the action, hence thoroughly look
	 *  and adapt itself to the new edit.
	 *
	 *  @synchronization	waitExlusive on doors.
	 */
	public void redo()
	{
		super.redo();
		perform();
	}

	public String getPresentationName()
	{
		return getResourceString( "editAddSessionObjects" );
	}
}