/*
 *  NumberListener.java
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
 *		07-Aug-05	copied from de.sciss.meloncillo.gui.NumberListener
 */

package de.sciss.inertia.gui;

import java.awt.*;
import java.awt.event.*;
import java.util.*;

/**
 *  Interface for listening
 *  the changes of the contents
 *  of a <code>NumberField</code> gadget
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.6, 31-Jul-04
 *
 *  @see	NumberField#addNumberListener( NumberListener )
 */
public interface NumberListener
extends EventListener
{
	/**
	 *  Notifies the listener that
	 *  a number changed occured.
	 *
	 *  @param  e   the event describing
	 *				the number change
	 */
	public void numberChanged( NumberEvent e );
}