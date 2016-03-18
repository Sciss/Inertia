/*
 *  VectorSetMean.java
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
 *		07-Aug-05	copied from de.sciss.meloncillo.math.VectorSetMean
 */

package de.sciss.inertia.math;

import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.prefs.*;
import javax.swing.*;

// INERTIA
//import de.sciss.meloncillo.*;
//import de.sciss.meloncillo.gui.*;
import de.sciss.inertia.gui.*;

import de.sciss.app.*;
import de.sciss.gui.*;

import de.sciss.util.NumberSpace;

/**
 *  Calculates the mean value of a vector
 *  and adds a DC offset such that a specified
 *  new mean value is obtained.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 01-Jan-05
 *
 *  @todo		make a RenderPlugIn version
 */
public class VectorSetMean
extends VectorTransformer
{
	private final PrefNumberField	ggMeanVal;
	private final JPanel			msgPane;

	private double   meanVal		= 0.5;

	// prefs keys
	private static final String		KEY_MEAN		= "mean";

	public VectorSetMean()
	{
		msgPane			= new JPanel( new SpringLayout() );

		ggMeanVal		= new PrefNumberField();
		ggMeanVal.setSpace( NumberSpace.genericDoubleSpace );

		msgPane.add( new JLabel( AbstractApplication.getApplication().getResourceString( "vectorMean" )));
		msgPane.add( ggMeanVal );

		GUIUtil.makeCompactSpringGrid( msgPane, 1, 2, 4, 2, 4, 2 );	// #row #col initx inity padx pady
        HelpGlassPane.setHelp( msgPane, "VectorTransformSetMean" );
	}

	public boolean query( Component parent )
	{
		int	result;
		
//		ggMeanVal.setNumber( new Double( meanVal ));
		
		result = JOptionPane.showOptionDialog( parent, msgPane,
			AbstractApplication.getApplication().getResourceString( "vectorSetMean" ),
			JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
			null, queryOptions, queryOptions[1] );

		if( result == 1 ) {
			meanVal	= ggMeanVal.getNumber().doubleValue();
			return true;
		} else {
			return false;
		}
	}

	public void setPreferences( Preferences prefs )
	{
		ggMeanVal.setPreferences( prefs, KEY_MEAN );
	}
	
	public float[] transform( float[] orig, NumberSpace spc, boolean wrapX, boolean wrapY )
	throws IOException
	{
		int		len		= orig.length;
		float[] trans	= new float[ len ];
		int		i;
		float   add;
		double  d1		= 0.0;
		
		if( len <= 0 ) return orig;
		
		for( i = 0; i < len; i++ ) {
			d1 += orig[ i ];
		}
		add = (float) (meanVal - d1 / len);
		for( i = 0; i < len; i++ ) {
			trans[i] = orig[i] + add;
		}
		
		return trans;
	}
}
