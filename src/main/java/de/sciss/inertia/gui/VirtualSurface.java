/*
 *  VirtualSurface.java
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
 *		07-Aug-05	copied from de.sciss.meloncillo.gui.VirtualSurface
 */

package de.sciss.inertia.gui;

import java.awt.*;
import java.awt.geom.*;

/**
 *  An interface for providing translations between an
 *  abstract ("geometric") virtual space and a concrete
 *  space displayed on screen (usually in a Component).
 *  These spaces are cartesian two dimensional.
 *  <p>
 *  By convention, the abstract space is usually normalized to
 *  a width and height of 1.0. Transformation between the
 *  two spaces is usally realized through a <code>AffineTransform</code>.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.67, 02-Sep-04
 *
 *  @see	java.awt.geom.AffineTransform
 */
public interface VirtualSurface
{
	/**
	 *  Snap a point to neighbouring objects on the surface
	 *
	 *  @param  freePt  'unsnapped' point
	 *  @param  virtual whether freePt is in virtual (true)
	 *					or screen (false) space
	 *  @return			the snapped point or the original
	 *					point if snapping deactivated or no
	 *					neighbouring points found. the point
	 *					will be in virtual or screen space
	 *					depending on the value of 'virtual'!
	 */
	public Point2D snap( Point2D freePt, boolean virtual );

	/**
	 *  Converts a location on the screen
	 *  into a point the virtual space.
	 *  Neither input nor output point need to
	 *  be limited to particular bounds
	 *
	 *  @param  screenPt	point in the display space
	 *  @return				point in the virtual space
	 */
	public Point2D screenToVirtual( Point2D screenPt );

	/**
	 *  Converts a point in the virtual space
	 *  into a location on the screen.
	 *  Neither input nor output point need to
	 *  be limited to particular bounds
	 *
	 *  @param  virtualPt   point in the virtual space
	 *  @return				point in the display space
	 */
	public Point2D virtualToScreen( Point2D virtualPt );

	/**
	 *  Converts a rectangle in the virtual space
	 *  into a rectangle suitable for Graphics clipping
	 *
	 *  @param  virtualClip		a rectangle in virtual space
	 *  @return the input rectangle transformed to screen space,
	 *			possibly with extra margin padding to ensure
	 *			correct graphics clipping of decorated shapes
	 */
	public Rectangle virtualToScreenClip( Rectangle2D virtualClip );
	
	/**
	 *  Converts a shape in the virtual space
	 *  into a shape on the screen.
	 *
	 *  @param  virtualShape	arbitrary shape in virtual space
	 *  @return the input shape transformed to screen space
	 */
	public Shape virtualToScreen( Shape virtualShape );

	/**
	 *  Converts a shape in the screen space
	 *  into a shape in the virtual space.
	 *
	 *  @param  screenShape		arbitrary shape in screen space
	 *  @return the input shape transformed to virtual space
	 */
	public Shape screenToVirtual( Shape screenShape );
}
