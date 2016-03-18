/*
 *  DocumentHandler.java
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
 *      07-Aug-05	copied from de.sciss.eisenkraut.session.DocumentHandler
 */

package de.sciss.inertia.session;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.5, 02-Aug-05
 */
public class DocumentHandler
extends de.sciss.app.AbstractDocumentHandler
{
	public DocumentHandler()
	{
		super( true );	// we are multi-document aware
	}
}
