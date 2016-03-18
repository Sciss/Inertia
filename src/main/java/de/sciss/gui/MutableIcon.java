/*
 *  MutableIcon.java
 *  de.sciss.gui package
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
 *  Change log:
 *		08-Sep-05	created
 */

package de.sciss.gui;

import java.awt.*;
import java.awt.geom.*;
import javax.swing.*;

/**
 *	Utility icons whose shape can change.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.6, 08-Sep-05
 */
public class MutableIcon
implements Icon
{
	public static final int DEFAULT_SIZE	= 13;
	
	public static final int INVISIBLE		= 0;
	public static final int WRITE_PROTECTED	= 1;

	private final int	size;
	private int			id;

	private static final Color	colrOutline	= Color.black; // new Color( 0x36, 0x36, 0x36, 0xFF );
	private static final Stroke strkOutline	= new BasicStroke( 0.5f );
	private static final Color	colrFill	= new Color( 0x36, 0x36, 0x36, 0xA0 );
	
	private Shape shpOutline;
	private Shape shpFill;

	public MutableIcon()
	{
		this( DEFAULT_SIZE );
	}

	public MutableIcon( int size )
	{
		this.size = size;
		setID( INVISIBLE );
	}
	
	public void setID( int id )
	{
		if( this.id == id ) return;
	
		this.id	= id;
		
		if( id == INVISIBLE ) return;

		GeneralPath gp;
		Area		a;

//		final float scale = (float) size / (float) DEFAULT_SIZE;
		final float sizeM1	= size - 2;
		
		switch( id ) {
		case WRITE_PROTECTED:
			gp = new GeneralPath();
			
			gp.moveTo( 0.375f * sizeM1, 0.825f * sizeM1 );
			gp.lineTo( 0.0f, sizeM1 );
			gp.lineTo( 0.175f * sizeM1, 0.625f * sizeM1 );
			gp.lineTo( 0.375f * sizeM1, 0.825f * sizeM1 );
			gp.lineTo( sizeM1, 0.20f * sizeM1 );
			gp.lineTo( 0.8f * sizeM1, 0.0f );
//			gp.lineTo( 0.9f * sizeM1, 0.3f * sizeM1 );
//			gp.lineTo( 0.7f * sizeM1, 0.1f * sizeM1 );
			gp.lineTo( 0.175f * sizeM1, 0.625f * sizeM1 );

			gp.moveTo( 0.9f * sizeM1, 0.3f * sizeM1 );
			gp.lineTo( 0.7f * sizeM1, 0.1f * sizeM1 );
//			gp.moveTo( sizeM1, 0.20f * sizeM1 );
//			gp.lineTo( 0.775f * sizeM1, 0.0f * sizeM1 );
//			gp.lineTo( 0.775f * sizeM1, 0.0f * sizeM1 );

			gp.moveTo( 0.0f, 0.0f );
			gp.lineTo( sizeM1, sizeM1 );

			shpOutline = gp;
			
			gp = new GeneralPath();
			gp.moveTo( 0.1875f * sizeM1, 0.9125f * sizeM1 );
			gp.lineTo( 0.0f, sizeM1 );
			gp.lineTo( 0.0875f * sizeM1, 0.8125f * sizeM1 );
			gp.closePath();
			a	= new Area( gp );
			
			gp = new GeneralPath();
			gp.moveTo( 0.275f * sizeM1, 0.725f * sizeM1 );
			gp.lineTo( 0.375f * sizeM1, 0.825f * sizeM1 );
			gp.lineTo( sizeM1, 0.20f * sizeM1 );			
			gp.lineTo( 0.9f * sizeM1, 0.1f * sizeM1 );
			gp.closePath();
			a.add( new Area( gp ));
			
			shpFill = a;
			break;
			
		default:
			throw new IllegalArgumentException();
		}
	}
	
	public int getIconWidth()
	{
		return size;
	}
	
	public int getIconHeight()
	{
		return size;
	}
	
	public void paintIcon( Component c, Graphics g, int x,  int y )
	{
		if( id == INVISIBLE ) return;
	
		final Graphics2D		g2			= (Graphics2D) g;
		final AffineTransform	atOrig		= g2.getTransform();
		final Stroke			strkOrig	= g2.getStroke();
		
		g2.translate( x + 1, y + 2 );
		g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
		g2.setColor( Color.white );
		g2.draw( shpOutline );
		g2.translate( 0, -1 );
		g2.setColor( colrOutline );
		g2.setStroke( strkOutline );
		g2.draw( shpOutline );
		
		g2.setColor( colrFill );
		g2.fill( shpFill );
		
		g2.setTransform( atOrig );
		g2.setStroke( strkOrig );
	}
}