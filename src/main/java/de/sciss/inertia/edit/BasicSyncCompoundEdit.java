/*
 *  BasicSyncCompoundEdit.java
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
 *		12-Aug-05	copied from de.sciss.eisenkraut.edit.BasicSyncCompoundEdit
 */

package de.sciss.inertia.edit;

import javax.swing.undo.*;

// INERTIA
//import de.sciss.eisenkraut.util.*;
import de.sciss.util.LockManager;

/**
 *  This subclass of <code>SyncCompoundEdit</code> is 
 *  the most basic extension ob the abstract class
 *  which simply puts empty bodies for the abstract methods.
 *
 *  @author			Hanns Holger Rutz
 *  @version		0.6, 03-Aug-04
 *  @see			de.sciss.meloncillo.util.LockManager
 */
public class BasicSyncCompoundEdit
extends SyncCompoundEdit
{
	/**
	 *  Creates a <code>CompountEdit</code> object, whose Undo/Redo
	 *  actions are synchronized.
	 *
	 *  @param  lm		the <code>LockManager</code> to use in synchronization
	 *  @param  doors   the doors to lock exclusively using the provided <code>LockManager</code>
	 */
	public BasicSyncCompoundEdit( LockManager lm, int doors )
	{
		super( lm, doors );
	}

	/**
	 *  Creates a <code>CompountEdit</code> object with a given name, whose Undo/Redo
	 *  actions are synchronized.
	 *
	 *  @param  lm					the <code>LockManager</code> to use in synchronization
	 *  @param  doors				the doors to lock exclusively using the provided <code>LockManager</code>
	 *	@param	presentationName	text describing the compound edit
	 */
	public BasicSyncCompoundEdit( LockManager lm, int doors, String representationName )
	{
		super( lm, doors, representationName );
	}
	
	/**
	 *  Does nothing
	 */
	protected void undoDone() {}
	/**
	 *  Does nothing
	 */
	protected void redoDone() {}
	/**
	 *  Does nothing
	 */
	protected void cancelDone() {}
}