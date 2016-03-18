/*
 *  SyncCompoundEdit.java
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
 *		12-Aug-05	copied from de.sciss.eisenkraut.edit.SyncCompoundEdit
 */

package de.sciss.inertia.edit;

import javax.swing.undo.*;

// INERTIA
//import de.sciss.eisenkraut.util.*;
import de.sciss.util.LockManager;

import de.sciss.app.AbstractApplication;

/**
 *  This subclass of <code>CompoundEdit</code> is used
 *  to synchronize an Undo or Redo operation.
 *  If for example, several edits of writing to a
 *  MultirateTrackEditor are recorded, this happens
 *  in a synchronized block. This class guarantees
 *  that an appropriate synchronization is maintained
 *  in an undo / redo operation.
 *
 *  @author			Hanns Holger Rutz
 *  @version		0.3, 13-Jul-05
 */
public abstract class SyncCompoundEdit
extends CompoundEdit
{
	private final String presentationName;

	/**
	 *  The LockManager to use
	 *  for locking doors
	 */
	protected final LockManager lm;
	/**
	 *  The doors to lock exclusively
	 *  in undo / redo operation
	 */
	protected final int doors;

	/**
	 *  Creates a <code>CompountEdit</code> object, whose Undo/Redo
	 *  actions are synchronized.
	 *
	 *  @param  lm		the <code>LockManager</code> to use in synchronization
	 *  @param  doors   the doors to lock exclusively using the provided <code>LockManager</code>
	 */
	public SyncCompoundEdit( LockManager lm, int doors )
	{
		this( lm, doors, null );
	}

	/**
	 *  Creates a <code>CompountEdit</code> object with a given name, whose Undo/Redo
	 *  actions are synchronized.
	 *
	 *  @param  lm					the <code>LockManager</code> to use in synchronization
	 *  @param  doors				the doors to lock exclusively using the provided <code>LockManager</code>
	 *	@param	presentationName	text describing the compound edit
	 */
	public SyncCompoundEdit( LockManager lm, int doors, String presentationName )
	{
		super();
		this.lm					= lm;
		this.doors				= doors;
		this.presentationName	= presentationName;
	}
	
	/**
	 *  Performs undo on all compound sub edits within
	 *  a synchronization block.
	 *
	 *  @synchronization	waitExclusive on the given LockManager and doors
	 */
	public void undo()
	{
		try {
			lm.waitExclusive( doors );
			super.undo();
			undoDone();
		}
		finally {
			lm.releaseExclusive( doors );
		}
	}

	/**
	 *  Performs redo on all compound sub edits within
	 *  a synchronization block.
	 *
	 *  @synchronization	waitExclusive on the given LockManager and doors
	 */
	public void redo()
	{
		try {
			lm.waitExclusive( doors );
			super.redo();
			redoDone();
		}
		finally {
			lm.releaseExclusive( doors );
		}
	}

	/**
	 *  Cancels the compound edit and undos all sub edits
	 *  made so far.
	 *
	 *  @synchronization	waitExclusive on the given LockManager and doors.
	 *						<strong>however, the caller must
	 *						block all <code>addEdit</code>
	 *						and the <code>end</code> or <code>cancel</code> call
	 *						into a sync block itself to prevent confusion
	 *						by intermitting calls to the locked objects.</strong>
	 */
	public void cancel()
	{
		try {
			lm.waitExclusive( doors );
			end();
			super.undo();
			cancelDone();
		}
		finally {
			lm.releaseExclusive( doors );
		}
	}

	/**
	 *  This gets called after the undo
	 *  operation but still inside the
	 *  sync block. Subclasses can use this
	 *  to fire any notification events.
	 */
	protected abstract void undoDone();

	/**
	 *  This gets called after the redo
	 *  operation but still inside the
	 *  sync block. Subclasses can use this
	 *  to fire any notification events.
	 */
	protected abstract void redoDone();

	/**
	 *  This gets called after the cancel
	 *  operation but still inside the
	 *  sync block. Subclasses can use this
	 *  to fire any notification events.
	 */
	protected abstract void cancelDone();

	public String getPresentationName()
	{
		if( presentationName != null ) {
			return presentationName;
		} else {
			return super.getPresentationName();
		}
	}

	public String getUndoPresentationName()
	{
		if( presentationName != null ) {
			return( getResourceString( "menuUndo" ) + " " + presentationName );
		} else {
			return super.getUndoPresentationName();
		}
	}

	public String getRedoPresentationName()
	{
		if( presentationName != null ) {
			return( getResourceString( "menuRedo" ) + " " + presentationName );
		} else {
			return super.getRedoPresentationName();
		}
	}

	protected String getResourceString( String key )
	{
		return AbstractApplication.getApplication().getResourceString( key );
	}
}
