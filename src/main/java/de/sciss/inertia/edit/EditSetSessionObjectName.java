/*
 *  EditSetSessionObjectName.java
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
 *		12-Aug-05	copied from de.sciss.meloncillo.edit.EditSetSessionObjectName
 */

package de.sciss.inertia.edit;

import java.awt.*;
import java.awt.geom.*;
import java.util.*;
import javax.swing.*;
import javax.swing.undo.*;

// INERTIA
//import de.sciss.meloncillo.*;
//import de.sciss.meloncillo.session.*;
import de.sciss.inertia.session.Session;
import de.sciss.inertia.session.SessionObject;
/**
 *  An <code>UndoableEdit</code> that
 *  describes the renaming of a session object.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.6, 29-Jul-04
 *  @see		UndoManager
 */
public class EditSetSessionObjectName
extends BasicUndoableEdit
{
	private Object				source;
	private final Session		doc;
	private final SessionObject	so;
	private final String		oldName, newName;
	private final int			doors;

	/**
	 *  Create and perform this edit. This
	 *  invokes the <code>SessionObjectCollection.modified</code> method,
	 *  thus dispatching a <code>SessionCollection.Event</code>.
	 *
	 *  @param  source			who initiated the action
	 *  @param  doc				session object which contains
	 *							the session object
	 *  @param  so				the session object to be renamed
	 *  @param  name			the new name of the session object
	 *  @see	de.sciss.meloncillo.session.SessionCollection
	 *  @see	de.sciss.meloncillo.session.SessionCollection.Event
	 *  @synchronization		waitExclusive on doors
	 *  @warning				the serialization of session objects through
	 *							the session's xml file requires that
	 *							all session objects have different names(?).
	 */
	public EditSetSessionObjectName( Object source, Session doc, SessionObject so, String name, int doors )
	{
		super();
		this.source			= source;
		this.doc			= doc;
		this.so				= so;
		this.newName		= name;
		this.oldName		= so.getName();
		this.doors			= doors;
		perform();
		this.source			= this;
	}

	private void perform()
	{
		try {
			doc.bird.waitExclusive( doors );
			so.setName( newName );
			so.getMap().dispatchOwnerModification( source, SessionObject.OWNER_RENAMED, newName );
		}
		finally {
			doc.bird.releaseExclusive( doors );
		}
	}

	/**
	 *  Undo the edit.
	 *  Invokes the <code>SessionObjectCollection.modified</code>,
	 *  method, thus dispatching a <code>SessionCollection.Event</code>.
	 *
	 *  @synchronization	waitExlusive on doors.
	 */
	public void undo()
	{
		super.undo();
		try {
			doc.bird.waitExclusive( doors );
			so.setName( oldName );
			so.getMap().dispatchOwnerModification( source, SessionObject.OWNER_RENAMED, oldName );
		}
		finally {
			doc.bird.releaseExclusive( doors );
		}
	}
	
	/**
	 *  Redo the edit.
	 *  Invokes the <code>SessionObjectCollection.modified</code>,
	 *  method, thus dispatching a <code>SessionCollection.Event</code>.
	 *  The original event source is discarded.
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
		return getResourceString( "editSetSessionObjectName" );
	}
}