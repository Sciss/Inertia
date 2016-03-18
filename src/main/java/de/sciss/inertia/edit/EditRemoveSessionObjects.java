/*
 *  EditRemoveSessionObjects.java
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
 *		13-Aug-05	copied from de.sciss.meloncillo.edit.EditRemoveSessionObjects
 */

package de.sciss.inertia.edit;

import java.util.*;
import javax.swing.*;
import javax.swing.undo.*;

// INERTIA
//import de.sciss.meloncillo.*;
//import de.sciss.meloncillo.session.*;
import de.sciss.util.LockManager;
import de.sciss.inertia.session.SessionCollection;

/**
 *  An <code>UndoableEdit</code> that
 *  describes the removal of receivers
 *  from the session.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.71, 22-Jan-05
 *  @see		UndoManager
 *  @see		EditAddSessionObjects
 */
public class EditRemoveSessionObjects
extends BasicUndoableEdit
{
	private final LockManager		lm;
	private final java.util.List	collSessionObjects;
	private Object					source;
	private final int				doors;
	private final SessionCollection	quoi;

	/**
	 *  Create and perform this edit. This
	 *  invokes the <code>SessionObjectCollection.addAll</code> method,
	 *  thus dispatching a <code>SessionCollection.Event</code>.
	 *
	 *  @param  source				who initiated the action
	 *  @param  doc					session object to which the
	 *								receivers are added
	 *  @param  collSessionObjects  collection of objects to
	 *								remove from the session.
	 *  @see	de.sciss.meloncillo.session.SessionCollection
	 *  @see	de.sciss.meloncillo.session.SessionCollection.Event
	 *  @synchronization		waitExclusive on doors
	 */
	public EditRemoveSessionObjects( Object source, SessionCollection quoi,
									 java.util.List collSessionObjects, LockManager lm, int doors )
	{
		super();
		this.source				= source;
		this.collSessionObjects	= new ArrayList( collSessionObjects );
		this.lm					= lm;
		this.doors				= doors;
		this.quoi				= quoi;
		perform();
		this.source				= this;
	}

	private void perform()
	{
		try {
			lm.waitExclusive( doors );
			quoi.removeAll( source, collSessionObjects );
		}
		finally {
			lm.releaseExclusive( doors );
		}
	}

	/**
	 *  Undo the edit
	 *  by calling the <code>SessionObjectCollection.addAll</code> method,
	 *  thus dispatching a <code>SessionCollection.Event</code>
	 *
	 *  @synchronization	waitExclusive on doors
	 */
	public void undo()
	{
		super.undo();
		try {
			lm.waitExclusive( doors );
			quoi.addAll( source, collSessionObjects );
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
	 *  of the action, hence thorougly look
	 *  and adapt itself to the new edit.
	 *
	 *  @synchronization	waitExclusive on doors
	 */
	public void redo()
	{
		super.redo();
		perform();
	}

	public String getPresentationName()
	{
		return getResourceString( "editRemoveSessionObjects" );
	}
}