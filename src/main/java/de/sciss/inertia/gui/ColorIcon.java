//
//  ColorIcon.java
//  Inertia
//
//  Created by SeaM on 30.09.05.
//  Copyright 2005 __MyCompanyName__. All rights reserved.
//

package de.sciss.inertia.gui;

import java.awt.*;
import javax.swing.*;

public class ColorIcon
implements Icon
{
	private int		width		= 32;
	private int		height		= 16;
	private Color	colrFill;

	public ColorIcon( float hue )
	{
		setColor( Color.getHSBColor( hue, 0.75f, 0.75f ));
	}

	public ColorIcon( Color colr )
	{
		setColor( colr );
	}
	
	public void setColor( Color colr )
	{
		colrFill = colr;
	}
	
	public void setIconSize( Dimension d )
	{
		setIconSize( d.width, d.height );
	}
	
	public void setIconSize( int width, int height )
	{
		this.width	= width;
		this.height	= height;
	}

	public int getIconWidth()
	{
		return width;
	}

	public int getIconHeight()
	{
		return height;
	}

	public void paintIcon( Component c, Graphics g, int x, int y )
	{
		g.setColor( Color.black );
		g.drawRect( x, y, width - 1, height - 1 );
		g.setColor( colrFill );
		g.fillRect( x + 1, y + 1, width - 2, height - 2 );
	}
}
