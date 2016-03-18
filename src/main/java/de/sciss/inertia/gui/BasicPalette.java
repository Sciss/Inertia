/*
 *  BasicPalette.java
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
 *		11-Aug-05	copied from de.sciss.eisenkraut.gui.BasicPalette
 */

package de.sciss.inertia.gui;

// INERTIA
//import de.sciss.eisenkraut.*;
//import de.sciss.eisenkraut.util.*;

/**
 *  Common functionality for all application quasi-floating palettes.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.69, 24-Dec-04
 *
 *  @todo   global keyboard accelerator keys do not work on
 *          non-macos systems
 */
public class BasicPalette
extends BasicFrame
{
	/**
	 *  Constructs a new palette.
	 *
	 *  @param  title   title shown in the palette's title bar
	 */
	public BasicPalette( String title )
	{
		super( title );
	}
	
	protected boolean hasMenuBar()
	{
		return true; /* MRJAdapter.isSwingUsingScreenMenuBar(); */ // false on non-mac systems
	}
}