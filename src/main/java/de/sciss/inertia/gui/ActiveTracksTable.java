/*
 *  ActiveTracksTable.java
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

package de.sciss.inertia.gui;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;

import de.sciss.inertia.session.*;

import de.sciss.app.AbstractApplication;
import de.sciss.app.WindowHandler;

import de.sciss.gui.GUIUtil;
import de.sciss.gui.ModificationButton;

public class ActiveTracksTable
extends de.sciss.inertia.gui.SessionCollectionTable
{
	public ActiveTracksTable( final Session doc )
	{
		super( FLAG_IMMUTABLE );
		setCollection( doc.tracks, doc.activeTracks, doc.bird, Session.DOOR_TRACKS, doc.getUndoManager() );
	}
	
	protected SessionObject createNewSessionObject( SessionCollection scAll )
	{
		throw new IllegalStateException( "Immutable" );
	}
}