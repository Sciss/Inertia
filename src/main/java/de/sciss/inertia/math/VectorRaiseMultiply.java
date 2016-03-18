/*
 *  VectorRaiseMultiply.java
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
 *		07-Aug-05	copied from de.sciss.meloncillo.math.VectorRaiseMultiply
 *
 *  XXX TO-DO : unify transform() and RenderPlugIn
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
//import de.sciss.meloncillo.plugin.*;
//import de.sciss.meloncillo.render.*;
//import de.sciss.meloncillo.session.*;
import de.sciss.inertia.gui.*;

import de.sciss.app.*;
import de.sciss.gui.*;

import de.sciss.util.NumberSpace;

/**
 *  Raises the vector data to the power of a constant and/or
 *  multiplies the data by a constant. This class
 *  implements the <code>RenderPlugIn</code> interface
 *  and can therefore be used to filter trajectory data.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 01-Jan-05
 */
public class VectorRaiseMultiply
extends VectorTransformer
// INERTIA
//implements RenderPlugIn
{
	private final PrefCheckBox		ggRaise, ggMultiply;
	private final PrefNumberField	ggRaiseVal, ggMultiplyVal;
	private final JPanel			msgPane;

	private boolean  raise			= false;
	private boolean  multiply		= false;
	private double   raiseVal		= 1.0;
	private double   multiplyVal	= 1.0;

	// prefs keys
	private static final String		KEY_RAISE		= "raise";
	private static final String		KEY_RAISEVAL	= "raiseval";
	private static final String		KEY_MULTIPLY	= "multiply";
	private static final String		KEY_MULTIPLYVAL	= "multiplyval";

	// context options map
	private static final String	KEY_PRODC	= "prodc";

	public VectorRaiseMultiply()
	{
		final de.sciss.app.Application	app = AbstractApplication.getApplication();
		
		msgPane			= new JPanel( new SpringLayout() );
		
		ggRaise			= new PrefCheckBox( app.getResourceString( "vectorRaise" ));
		ggRaiseVal		= new PrefNumberField();
		ggRaiseVal.setSpace( NumberSpace.genericDoubleSpace );
		ggRaiseVal.setNumber( new Double( 1.0 ));
		ggMultiply		= new PrefCheckBox( app.getResourceString( "vectorMultiply" ));
		ggMultiplyVal	= new PrefNumberField();
		ggMultiplyVal.setSpace( NumberSpace.genericDoubleSpace );
		ggMultiplyVal.setNumber( new Double( 1.0 ));

		msgPane.add( ggRaise );
		msgPane.add( ggRaiseVal );
		msgPane.add( ggMultiply );
		msgPane.add( ggMultiplyVal );
		
		GUIUtil.makeCompactSpringGrid( msgPane, 2, 2, 4, 2, 4, 2 );	// #row #col initx inity padx pady
        HelpGlassPane.setHelp( msgPane, "VectorTransformRaiseMultiply" );
	}

	public void setPreferences( Preferences prefs )
	{
		ggRaise.setPreferences( prefs, KEY_RAISE );
		ggRaise.setPreferences( prefs, KEY_RAISEVAL );
		ggMultiply.setPreferences( prefs, KEY_MULTIPLY );
		ggMultiply.setPreferences( prefs, KEY_MULTIPLYVAL );
	}

	public boolean query( Component parent )
	{
		int	result;
		
//		ggRaise.setSelected( raise );
//		ggMultiply.setSelected( multiply );
//		ggRaiseVal.setNumber( new Double( raiseVal ));
//		ggMultiplyVal.setNumber( new Double( multiplyVal ));
		
		result = JOptionPane.showOptionDialog( parent, msgPane,
			AbstractApplication.getApplication().getResourceString( "vectorRaiseMultiply" ),
			JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
			null, queryOptions, queryOptions[1] );

		if( result == 1 ) {
			raise		= ggRaise.isSelected(); 
			multiply	= ggMultiply.isSelected(); 
			raiseVal	= ggRaiseVal.getNumber().doubleValue();
			multiplyVal = ggMultiplyVal.getNumber().doubleValue();

			if( raise || multiply ) {
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
		int		i;
		float   mul, f1;
		double  d1;
		float   min		= (float) spc.min;
		float   max		= (float) spc.max;
		
		if( len <= 0 ) return orig;
		
		if( raise ) {
			for( i = 0; i < len; i++ ) {
				f1  = orig[ i ];
				if( f1 >= 0.0f ) {
					d1 = Math.pow( f1, raiseVal );
				} else {
					d1 = -Math.pow( -f1, raiseVal );
				}
				if( (d1 == Double.NaN) || (d1 == Double.NEGATIVE_INFINITY) ) {
					trans[i] = min;
				} else if( d1 == Double.POSITIVE_INFINITY ) {
					trans[i] = max;
				} else {
					trans[i] = (float) d1;
				}
			}
		} else {
			System.arraycopy( orig, 0, trans, 0, len );
		}
		if( multiply ) {
			mul = (float) multiplyVal;
			for( i = 0; i < len; i++ ) {
				trans[i] *= mul;
			}
		}
		
		return trans;
	}
	
// ------------------ RenderPlugIn interface ------------------
	
// INERTIA
//	public void init( Main root, Session doc )
//	{
//		// XXX
//	}
//
//	public JComponent getSettingsView( PlugInContext context )
//	{
//		return msgPane;
//	}
//
//	public boolean producerBegin( RenderContext context, RenderSource source )
//	throws IOException
//	{
//		ProducerContext	prodc;
//		int				trnsIdx;
//		boolean			success		= false;
//
//		prodc						= new ProducerContext();
//		prodc.consumer				= (RenderConsumer) context.getOption( RenderContext.KEY_CONSUMER );
//		context.moduleMap.put( KEY_PRODC, prodc );
//		prodc.outLength				= context.getTimeSpan().getLength();
//		prodc.progLen				= prodc.outLength;
//		prodc.progOff				= 0;
////		prodc.spc					= new NumberSpace( 0.0, 1.0, 0.0 );
//		prodc.source				= new RenderSource( source );
//		prodc.source.trajBlockBuf   = new float[ source.numTrns ][ source.numRcv ][ 0 ];
//		prodc.bufSize				= 0;
//
//		if( prodc.consumer != null ) {
//			success				= prodc.consumer.consumerBegin( context, prodc.source );
//		} else {
//			success				= true;
//		}
//
//		return success;
//	}
//
//	public boolean producerRender( RenderContext context, RenderSource source )
//	throws IOException
//	{
//		ProducerContext prodc		= (ProducerContext) context.moduleMap.get( KEY_PRODC );
//		RenderConsumer  consumer	= (RenderConsumer) context.getOption( RenderContext.KEY_CONSUMER );
//		int				dim			= ((Integer) context.getOption( VectorTransformFilter.KEY_DIMENSION )).intValue();
//		int				len			= source.blockBufLen;
//		float[]			inBuf, outBuf;
//		int				i, j, trnsIdx;
//		float			f1;
//		double			d1;
//		boolean			success		= false;
//
//		if( prodc.bufSize < len ) {  // re-alloc bigger buffer
//			prodc.source.trajBlockBuf   = new float[ source.numTrns ][ 2 ][ len ];
//			prodc.bufSize				= len;
//		}
//
//		for( trnsIdx = 0; trnsIdx < source.numTrns; trnsIdx++ ) {
//			inBuf   = source.trajBlockBuf[ trnsIdx ][ dim ];
//			outBuf  = prodc.source.trajBlockBuf[ trnsIdx ][ dim ];
//
//			if( raise ) {
//				for( i = 0, j = source.blockBufOff; i < len; i++, j++ ) {
//					f1  = inBuf[ j ];
//					if( f1 >= 0.0f ) {
//						d1 = Math.pow( f1, raiseVal );
//					} else {
//						d1 = -Math.pow( -f1, raiseVal );
//					}
//					if( (d1 == Double.NaN) || (d1 == Double.NEGATIVE_INFINITY) ) {
//						outBuf[i] = 0.0f; // min;
//					} else if( d1 == Double.POSITIVE_INFINITY ) {
//						outBuf[i] = 1.0f; // max;
//					} else {
//						outBuf[i] = (float) d1;
//					}
//				}
//			} else {
//				System.arraycopy( inBuf, source.blockBufOff, outBuf, 0, len );
//			}
//			if( multiply ) {
//				f1 = (float) multiplyVal;
//				for( i = 0; i < len; i++ ) {
//					outBuf[i] *= f1;
//				}
//			}
//		}
//		i = 1 - dim;	// copy the bypassed dimension (in fact it could be omitted but this a cleaner approach)
//		for( trnsIdx = 0; trnsIdx < source.numTrns; trnsIdx++ ) {
//			System.arraycopy( source.trajBlockBuf[ trnsIdx ][ i ], source.blockBufOff,
//							  prodc.source.trajBlockBuf[ trnsIdx ][ i ], 0, len );
//		}
//
//		prodc.source.blockSpan		= source.blockSpan;
//		prodc.source.blockBufLen	= len;
//		if( prodc.consumer != null ) {
//			success					= prodc.consumer.consumerRender( context, prodc.source );
//		} else {
//			success					= true;
//		}
//		prodc.progOff			   += len;
//		((RenderHost) context.getHost()).setProgression( (float) prodc.progOff / (float) prodc.progLen );
//
//		return success;
//	}
//
//	public boolean producerFinish( RenderContext context, RenderSource source )
//	throws IOException
//	{
//		VectorRaiseMultiply.ProducerContext prodc =
//			(VectorRaiseMultiply.ProducerContext) context.moduleMap.get( KEY_PRODC );
//
//		if( prodc.consumer != null ) {
//			return prodc.consumer.consumerFinish( context, prodc.source );
//		} else {
//			return true;
//		}
//	}
//
//	public void producerCancel( RenderContext context, RenderSource source )
//	throws IOException
//	{
//		VectorRaiseMultiply.ProducerContext prodc =
//			(VectorRaiseMultiply.ProducerContext) context.moduleMap.get( KEY_PRODC );
//
//		if( prodc.consumer != null ) {
//			prodc.consumer.consumerCancel( context, prodc.source );
//		}
//	}

// -------- internal class --------
// INERTIA
//	private class ProducerContext
//	{
//		private long				outLength, progOff, progLen;
////		private NumberSpace			spc;
//		private RenderSource		source;
//		private int					bufSize;
//		private RenderConsumer		consumer;
//	}
}