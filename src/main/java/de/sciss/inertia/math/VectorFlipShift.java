/*
 *  VectorFlipShift.java
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
 *		07-Aug-05	copied from de.sciss.meloncillo.math.VectorFlipShift
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
 *  Flips and/or shifts ordinate indices or abscissa
 *  data.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.731, 29-Apr-05
 *
 *  @todo		make a RenderPlugIn version
 */
public class VectorFlipShift
extends VectorTransformer
{
	private final PrefCheckBox			ggFlipX, ggFlipY, ggShiftX, ggShiftY;
	private final PrefNumberField		ggShiftXVal, ggShiftYVal;
	private final JPanel				msgPane;

	private boolean  flipX			= false;
	private boolean  flipY			= false;
	private boolean  shiftX			= false;
	private boolean  shiftY			= false;
	private double   shiftXVal		= 0.0;
	private double   shiftYVal		= 0.0;

	// prefs keys
	private static final String		KEY_FLIPX		= "flipx";
	private static final String		KEY_FLIPY		= "flipy";
	private static final String		KEY_SHIFTX		= "shiftx";
	private static final String		KEY_SHIFTY		= "shifty";
	private static final String		KEY_SHIFTXVAL	= "shiftxval";
	private static final String		KEY_SHIFTYVAL   = "shiftyval";

	public VectorFlipShift()
	{
		int								rows;
		final de.sciss.app.Application	app	= AbstractApplication.getApplication();
	
		msgPane		= new JPanel( new SpringLayout() );
		
		ggFlipX		= new PrefCheckBox( app.getResourceString( "vectorFlipX" ));
		ggFlipY		= new PrefCheckBox( app.getResourceString( "vectorFlipY" ));
		ggShiftX	= new PrefCheckBox( app.getResourceString( "vectorShiftX" ));
		ggShiftXVal	= new PrefNumberField();
		ggShiftXVal.setSpace( NumberSpace.genericDoubleSpace );
		ggShiftY	= new PrefCheckBox( app.getResourceString( "vectorShiftY" ));
		ggShiftYVal	= new PrefNumberField();
		ggShiftYVal.setSpace( NumberSpace.genericDoubleSpace );

		rows		= 0;
		msgPane.add( ggFlipX );
		msgPane.add( new JLabel() );
		rows++;
		msgPane.add( ggFlipY );
		msgPane.add( new JLabel() );
		rows++;
		msgPane.add( ggShiftX );
		msgPane.add( ggShiftXVal );
		rows++;
		msgPane.add( ggShiftY );
		msgPane.add( ggShiftYVal );
		rows++;

		GUIUtil.makeCompactSpringGrid( msgPane, rows, 2, 4, 2, 4, 2 );	// #row #col initx inity padx pady
        HelpGlassPane.setHelp( msgPane, "VectorTransformFlipShift" );
	}

	public void setPreferences( Preferences prefs )
	{
		ggFlipX.setPreferences( prefs, KEY_FLIPX );
		ggFlipY.setPreferences( prefs, KEY_FLIPY );
		ggShiftX.setPreferences( prefs, KEY_SHIFTX );
		ggShiftXVal.setPreferences( prefs, KEY_SHIFTY );
		ggShiftY.setPreferences( prefs, KEY_SHIFTXVAL );
		ggShiftYVal.setPreferences( prefs, KEY_SHIFTYVAL );
	}

	public boolean query( Component parent )
	{
		int	result;
		
//		ggFlipX.setSelected( flipX );
//		ggFlipY.setSelected( flipY );
//		ggShiftX.setSelected( shiftX );
//		ggShiftY.setSelected( shiftY );
//		ggShiftXVal.setNumber( new Double( shiftXVal ));
//		ggShiftYVal.setNumber( new Double( shiftYVal ));
		
		result = JOptionPane.showOptionDialog( parent, msgPane,
			AbstractApplication.getApplication().getResourceString( "vectorFlipShift" ),
			JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
			null, queryOptions, queryOptions[1] );

		if( result == 1 ) {
			flipX		= ggFlipX.isSelected(); 
			flipY		= ggFlipY.isSelected(); 
			shiftX		= ggShiftX.isSelected(); 
			shiftY		= ggShiftY.isSelected();
			shiftXVal   = ggShiftXVal.getNumber().doubleValue();
			shiftYVal   = ggShiftYVal.getNumber().doubleValue();

			if( flipX || flipY || shiftX || shiftY ) {
				return true;
			}
		}
		return false;
	}
	
	public float[] transform( float[] orig, NumberSpace spc, boolean wrapX, boolean wrapY )
	throws IOException
	{
		int		len		= orig.length;
		float[] trans	= new float[ len ];
		int		i, origIdx, origInc, repeatStart = 0, repeatStop = 0, repeatVal = 0;
		float   mul, add, f1;
		float   min		= (float) spc.min;
		float   max		= (float) spc.max;
		
		if( len <= 0 ) return orig;
		
		if( flipX ) {
			origIdx = len - 1;
			origInc = -1;
		} else {
			origIdx = 0;
			origInc = 1;
		}
		if( flipY ) {
			mul		= -1.0f;
			add		= min + max;
		} else {
			mul		= 1.0f;
			add		= 0.0f;
		}
		if( shiftY ) {
			add    += shiftYVal;
		}
		if( shiftX ) {
			i   = (int) (len * Math.min( 1.0, Math.max( 0.0, Math.abs( shiftXVal ))) + 0.5);
			if( shiftXVal >= 0.0 ) {	// shift right, i.e. decrease orig start idx
				origIdx    -= i;
				while( origIdx < 0 ) origIdx += len;
				repeatStart = 0;
				repeatStop  = i;
				repeatVal   = 0;
			} else {					// shift left, i.e. increase orig start idx
				origIdx    += i;
				repeatStart = len - i;
				repeatStop  = len;
				repeatVal   = len - 1;
			}
		}
		
		for( i = 0; i < len; i++, origIdx += origInc ) {
			trans[i] = orig[ origIdx % len ] * mul + add;
		}
//		handleWrapY( edit, trans );
		if( shiftX && !wrapX ) {
			f1  = orig[ repeatVal ];
			for( i = repeatStart; i < repeatStop; i++ ) {
				trans[ i ] = f1;
			}
		}

		return trans;
	}
}
