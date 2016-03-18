/*
 *  VectorSmooth.java
 *  Meloncillo
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
 *		07-Aug-05	copied from de.sciss.meloncillo.math.VectorSmooth
 */

package de.sciss.inertia.math;

import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.prefs.*;

import de.sciss.util.NumberSpace;

// INERTIA
//import de.sciss.meloncillo.gui.*;

/**
 *  Smoothes out abrupt changes in the vector
 *  data by applying a moving average filter.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.6, 04-Aug-04
 *
 *  @todo		this is very cheesy now, since 
 *				it just averages two adjectant samples.
 *				there should be a filter length parameter.
 *  @todo		make a RenderPlugIn version
 */
public class VectorSmooth
extends VectorTransformer
{
	public void setPreferences( Preferences prefs )
	{
		// ...
	}

	public boolean query( Component parent )
	{
		return true;
	}

	public float[] transform( float[] orig, NumberSpace spc, boolean wrapX, boolean wrapY )
	throws IOException
	{
		int		len		= orig.length;
		float[] trans	= new float[ len ];
		int		i;
		
		if( len > 1 ) {
			trans[0]		= (orig[0] + orig[1]) / 2;
			trans[len-1]	= (orig[len-2] + orig[len-1]) / 2;
		}
		for( i = 1; i < len-1; i++ ) {
			trans[i]		= (orig[i-1] + orig[i] + orig[i+1]) / 3;
		}
		
		return trans;
	}
}
