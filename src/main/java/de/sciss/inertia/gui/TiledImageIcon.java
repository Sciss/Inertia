/*
 *  TiledImageIcon.java
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
 *		07-Aug-05	copied from de.sciss.eisenkraut.gui.TiledImageIcon
 */

package de.sciss.inertia.gui;

import java.awt.*;
import java.awt.image.*;
import javax.swing.*;

/**
 *  A <code>TiledImage</code> wrapper
 *  that implements the <code>Icon</code>
 *  interface which allows the use of
 *  a <code>TiledImage</code> in Swing
 *  components. Swing can use several
 *  icons per gadget representing different
 *  gadget states. <code>GraphicsUtil.createToolIcons()</code>
 *  helps to create a series of related icons.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.6, 01-Aug-04
 *
 *  @see		GraphicsUtil#createToolIcons( int )
 */
public class TiledImageIcon
implements Icon
{
	private final TiledImage	tImg;
	private final int			col, row;

	/**
	 *  Creates a new <code>TiledImageIcon</code> from
	 *  an given <code>TiledImage</code>, using one
	 *  particular tile of this image. In this way
	 *  multiply icons can share the same image file
	 *  and just use bits of it.
	 *
	 *  @param  source		<code>TiledImageIcon</code> which
	 *						contains the tile we want
	 *  @param  col			tile column index in the tiled image
	 *						(starting at zero)
	 *  @param  row			tile row index in the tiled image
	 *						(starting at zero)
	 */
	public TiledImageIcon( TiledImage source, int col, int row )
	{
		tImg		= source;
		this.col	= col;
		this.row	= row;
	}

// ---------------- Icon interface ---------------- 

	/**
	 *  Queries the icon width which is
	 *  identical to the tile width of the underlying
	 *  tiled image.
	 *
	 *  @return the width of the icon in pixels
	 *
	 *  @see	TiledImage#getTileWidth()
	 */
	public int getIconWidth()
	{
		return tImg.getTileWidth();
	}

	/**
	 *  Queries the icon height which is
	 *  identical to the tile height of the underlying
	 *  tiled image.
	 *
	 *  @return the height of the icon in pixels
	 *
	 *  @see	TiledImage#getTileHeight()
	 */
	public int getIconHeight()
	{
		return tImg.getTileHeight();
	}
	
	/**
	 *  Paints this icon into a graphics context
	 *  belonging to a GUI component.
	 *
	 *  @param  c   the Component to which the icon
	 *				is attached. This is used as
	 *				<code>ImageObserver</code>
	 *  @param  g   the <code>Graphics</code> context to paint into
	 *  @param  x   x offset in pixels in the graphics context
	 *  @param  y   y offset in pixels in the graphics context
	 *
	 *  @see	TiledImage#paintTile( Graphics, int, int, int, int, ImageObserver )
	 */
	public void paintIcon( Component c, Graphics g, int x, int y )
	{
		tImg.paintTile( g, x, y, col, row, c );
	}
}
