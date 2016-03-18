/*
 *  EditPutMapValue.java
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
 *		13-Aug-05	copied from de.sciss.meloncillo.edit.EditPutMapValue
 */

package de.sciss.inertia.edit;

import java.util.*;
import javax.swing.undo.*;

// INERTIA
//import de.sciss.meloncillo.*;
//import de.sciss.meloncillo.util.*;
import de.sciss.util.LockManager;
import de.sciss.util.MapManager;

/**
 *  An <code>UndoableEdit</code> that
 *  describes the modification of a map.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.72, 08-Apr-05
 *
 *  @see		UndoManager
 */
public class EditPutMapValue
extends BasicUndoableEdit
{
	private Object				source;
	private final LockManager	lm;
	private final MapManager	map;
	private final String		key;
	private final Object		oldValue, newValue;
	private final int			doors;

	/**
	 *  Create and perform this edit. This
	 *  invokes the map's <code>putValue</code> method,
	 *  thus dispatching a <code>MapManager.Event</code>.
	 *
	 *  @param  source			who initiated the action
	 *  @param  lm				the <code>LockManager</code> to use for synchronization
	 *							or <code>null</code>
	 *	@param	doors			the doors to sync on using <code>waitExclusive</code>
	 *  @param  map				the map to change (e.g. a session object's map)
	 *	@param	key				the map entry to change
	 *  @param  value			the new property value
	 *
	 *  @synchronization		<code>lm.waitExclusive()</code> on <code>doors</code>
	 */
	public EditPutMapValue( Object source, LockManager lm, int doors,
							MapManager map, String key, Object value )
	{
		super();
		this.source			= source;
		this.lm				= lm;
		this.doors			= doors;
		this.map			= map;
		this.key			= key;
		this.newValue		= value;
		this.oldValue		= map.getValue( key );
		perform();
		this.source			= this;
	}

	private void perform()
	{
		try {
			if( lm != null ) lm.waitExclusive( doors );
			map.putValue( source, key, newValue );
		}
		finally {
			if( lm != null ) lm.releaseExclusive( doors );
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
			if( lm != null ) lm.waitExclusive( doors );
			map.putValue( source, key, oldValue );
		}
		finally {
			if( lm != null ) lm.releaseExclusive( doors );
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

	public String getPresentationProperty()
	{
		return getResourceString( "editPutMapValue" );
	}
}