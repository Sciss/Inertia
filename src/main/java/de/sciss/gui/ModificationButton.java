/*
 *  ModificationButton.java
 *  de.sciss.gui
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
 *		03-Aug-05	created
 */

package de.sciss.gui;

import java.awt.*;
import java.awt.geom.*;
import javax.swing.*;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.16, 15-Sep-05
 */
public class ModificationButton
extends JButton
{
	private static final long serialVersionUID = 0x050915L;

	public static final int DEFAULT_SIZE	= 13;

	public static final int TYPE_NORMAL		= 0;
	public static final int TYPE_PRESSED	= 1;
	public static final int TYPE_DISABLED	= 2;

	public static final int SHAPE_PLUS		= 0;
	public static final int SHAPE_MINUS		= 1;
	public static final int SHAPE_INFO		= 2;
	public static final int SHAPE_LIST		= 3;

	private static final String[] toolTip	= {
		"buttonAddItemTT", "buttonDelItemTT", "buttonInfoTT", "buttonListTT"
	};

	protected final Icon icnNormal, icnPressed, icnDisabled;

	protected ModificationButton( Icon icnNormal, Icon icnPressed, Icon icnDisabled )
	{
		super();

		this.icnNormal		= icnNormal;
		this.icnPressed		= icnPressed;
		this.icnDisabled	= icnDisabled;

		final int		width	= icnNormal.getIconWidth();
		final int		height	= icnNormal.getIconHeight();
		final Dimension d		= new Dimension( width + 8, height + 4 );
	
//		setBorderPainted( false );
		setBorder( BorderFactory.createEmptyBorder( 2, 4, 2, 4 ));
		setMargin( new Insets( 2, 4, 2, 4 ));
		setPreferredSize( d );
		setMinimumSize( d );
		setMaximumSize( d );
		setContentAreaFilled( false );
		setFocusable( false );
	}

	public ModificationButton( int shape, int size )
	{
		this( new ModificationIcon( shape, TYPE_NORMAL, size ),
			  new ModificationIcon( shape, TYPE_PRESSED, size ),
			  new ModificationIcon( shape, TYPE_DISABLED, size ));

		setToolTipText( GUIUtil.getResourceString( toolTip[ shape ]));
	}

	public ModificationButton( int shape )
	{
		this( shape, DEFAULT_SIZE );
	}
	
	// overriden without calling super
	// to avoid lnf border painting
	// which is happening despite setting our own border
	public void paintComponent( Graphics g )
	{
		final Icon icn;
	
		if( isEnabled() ) {
			if( getModel().isPressed() ) {
				icn = icnPressed;
			} else {
				icn = icnNormal;
			}
		} else {
			icn = icnDisabled;
		}
		icn.paintIcon( this, g, 4, 2 );
	}

	private static class ModificationIcon
	implements Icon
	{
		private static final Color[] colr		= new Color[] {
			new Color( 0x6C, 0x6C, 0x6C, 0xFF ),	// normal
			new Color( 0x36, 0x36, 0x36, 0xFF ),	// pressed
			new Color( 0x6C, 0x6C, 0x6C, 0x7F )		// disabled
		};

		private final Area	icon;
		private final Color	color;
		private final int	size;

		public ModificationIcon( int shape, int type, int size, Color color )
		{
			this.color	= color;
			this.size	= size;
		
			icon = new Area( new Ellipse2D.Float( 0.0f, 0.0f, size, size ));

			final float scale = (float) size / (float) DEFAULT_SIZE;
			
			switch( shape ) {
			case SHAPE_PLUS:
				icon.subtract( new Area( new Rectangle2D.Float(
					3.0f * scale, 5.5f * scale, 7.0f * scale, 2.0f * scale )));
				icon.subtract( new Area( new Rectangle2D.Float(
					5.5f * scale, 3.0f * scale, 2.0f * scale, 7.0f * scale )));
				break;
				
			case SHAPE_MINUS:
				icon.subtract( new Area( new Rectangle2D.Float(
					3.0f * scale, 5.5f * scale, 7.0f * scale, 2.0f * scale )));
				break;

			case SHAPE_INFO:
				icon.subtract( new Area( new Ellipse2D.Float(
					5.5f * scale, 3.0f * scale, 2.0f * scale, 2.0f * scale )));
				final GeneralPath gp = new GeneralPath();
				gp.moveTo( 7.5f * scale, 5.5f * scale );
				gp.lineTo( 4.5f * scale, 6.0f * scale );
				gp.lineTo( 4.5f * scale, 6.25f * scale );
				gp.lineTo( 5.5f * scale, 6.5f * scale );
				gp.lineTo( 5.5f * scale, 9.5f * scale );
				gp.lineTo( 4.5f * scale, 9.75f * scale );
				gp.lineTo( 4.5f * scale, 10.0f * scale );
				gp.lineTo( 8.5f * scale, 10.0f * scale );
				gp.lineTo( 8.5f * scale, 9.75f * scale );
				gp.lineTo( 7.5f * scale, 9.5f * scale );
				gp.closePath();
				icon.subtract( new Area( gp ));
				break;

			case SHAPE_LIST:
				icon.subtract( new Area( new Rectangle2D.Float(
					3.0f * scale, 2.5f * scale, 7.0f * scale, 2.0f * scale )));
				icon.subtract( new Area( new Rectangle2D.Float(
					3.0f * scale, 5.5f * scale, 7.0f * scale, 2.0f * scale )));
				icon.subtract( new Area( new Rectangle2D.Float(
					3.0f * scale, 8.5f * scale, 7.0f * scale, 2.0f * scale )));
				break;

			default:	// ignore
				break;
			}
		}
		
		public ModificationIcon( int shape, int type, int size )
		{
			this( shape, type, size, colr[ type ]);
		}

		public ModificationIcon( int shape, int type )
		{
			this( shape, type, DEFAULT_SIZE );
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
			Graphics2D g2 = (Graphics2D) g;
			g2.translate( x, y );
			g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
			g2.setColor( color );
			g2.fill( icon );
			g2.translate( -x, -y );
		}
	}
}
