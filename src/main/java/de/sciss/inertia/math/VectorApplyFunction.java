/*
 *  VectorApplyFunction.java
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
 *		07-Aug-05	copied from de.sciss.meloncillo.math.VectorApplyFunction
 *
 *  XXX TO-DO : unify transform() and RenderPlugIn
 *  no prefs present --> phase default is 1.0 ???
 */

package de.sciss.inertia.math;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.prefs.*;
import javax.swing.*;

// INERTIA
//import de.sciss.meloncillo.*;
//import de.sciss.meloncillo.gui.*;
//import de.sciss.meloncillo.io.*;
//import de.sciss.meloncillo.plugin.*;
//import de.sciss.meloncillo.render.*;
//import de.sciss.meloncillo.session.*;
//import de.sciss.meloncillo.util.*;
import de.sciss.inertia.gui.*;

import de.sciss.app.*;
import de.sciss.gui.*;
import de.sciss.io.IOUtil;

import de.sciss.util.NumberSpace;

/**
 *  The synthesis of a <code>VectorTransformer</code>
 *  and the <code>Function</code> class. The
 *  transform will generate the wave function output,
 *  optionally mix it or multiply it by the input vector.
 *  It implements the <code>RenderPlugIn</code> interface
 *  and can therefore be used to filter trajectory data.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.731, 29-Apr-05
 */
public class VectorApplyFunction
extends VectorTransformer
implements LaterInvocationManager.Listener
// INERTIA
// RenderPlugIn
{
	private final PrefComboBox		ggFunction, ggApplication;
	private final PrefCheckBox		ggBipolar;
	private final PrefNumberField   ggAmplitude, ggPeriods, ggBrownish, ggPhase;
	private final JPanel			msgPane;
	private final JLabel			lbPhase, lbPeriods, lbBrownish;

	private static final int		FUNC_CONSTANT   = 0;
	private static final int		FUNC_FALLINGSAW = 1;
	private static final int		FUNC_RISINGSAW  = 2;
	private static final int		FUNC_GAUSSIAN   = 3;
	private static final int		FUNC_TRIANGLE   = 4;
	private static final int		FUNC_SINE		= 5;
	private static final int		FUNC_SQUARE		= 6;
	private static final int		FUNC_NOISE		= 7;
	private static final String[]   funcNames		= { "vectorFunctionConstant",   "vectorFunctionFallingSaw",
														"vectorFunctionRisingSaw",  "vectorFunctionGaussian",
														"vectorFunctionTriangle",   "vectorFunctionSine",
														"vectorFunctionSquare",		"vectorFunctionNoise" };
	private static final Class[]	funcClasses		= { Function.Constant.class,	Function.FallingSaw.class,
														Function.RisingSaw.class,	null,
														Function.Triangle.class,	Function.Sine.class,
														Function.Square.class,		Function.Noise.class };

	private static final int		APP_REPLACE		= 0;
	private static final int		APP_ADD			= 1;
	private static final int		APP_MULT		= 2;
	private static final String[]   appNames		= { "vectorFunctionReplace", "vectorFunctionAdd",
														"vectorFunctionMult" };

	// defaults
	private int		function	= FUNC_CONSTANT;
	private int		application	= APP_REPLACE;
	private double  amplitude	= 1.0;
	private double  periods		= 1.0;
	private double  phase		= 0.0;
	private double  brownish	= 0.0;
	private boolean bipolar		= false;
	private boolean periodic	= false;
	private boolean noise		= false;
	private boolean hasParam	= false;

	private final DynamicPrefChangeManager  dpl;
	
	// prefs keys
	private static final String		KEY_FUNCTION	= "function";
	private static final String		KEY_APPLICATION = "application";
	private static final String		KEY_BIPOLAR		= "bipolar";
	private static final String		KEY_AMPLITUDE	= "amplitude";
	private static final String		KEY_PERIODS		= "periods";
	private static final String		KEY_BROWNISH	= "brownish";
	private static final String		KEY_PHASE		= "phase";
	
	// context options map
	private static final String	KEY_PRODC	= "prodc";

	public VectorApplyFunction()
	{
		final GridBagLayout				lay			= new GridBagLayout();
		final GridBagConstraints		con			= new GridBagConstraints();
		final Insets					ascetic		= new Insets( 2, 4, 2, 4 );
		JLabel							lb;
		final de.sciss.app.Application	app			= AbstractApplication.getApplication();
	
		msgPane			= new JPanel( lay );

		ggFunction		= new PrefComboBox();
		for( int i = 0; i < funcNames.length; i++ ) {
			ggFunction.addItem( new StringItem( funcNames[i], app.getResourceString( funcNames[i] )));
		}
		ggFunction.setSelectedIndex( FUNC_CONSTANT );
		ggAmplitude		= new PrefNumberField();
		ggAmplitude.setSpace( NumberSpace.genericDoubleSpace );
		ggAmplitude.setNumber( new Double( 1.0 ));
		ggPeriods		= new PrefNumberField();
		ggPeriods.setSpace( NumberSpace.genericDoubleSpace );
		ggPeriods.setNumber( new Double( 1.0 ));
		ggBrownish		= new PrefNumberField();
		ggBrownish.setSpace( NumberSpace.genericDoubleSpace );
		ggBrownish.setNumber( new Double( 1.0 ));
		ggPhase			= new PrefNumberField();
		ggPhase.setSpace( NumberSpace.genericDoubleSpace ); // XX unit label "\u00B0"
		ggPhase.setNumber( new Double( 0.0 ));
		ggBipolar		= new PrefCheckBox();
		ggApplication	= new PrefComboBox();
		for( int i = 0; i < appNames.length; i++ ) {
			ggApplication.addItem( new StringItem( appNames[i], app.getResourceString( appNames[i] )));
		}
		ggApplication.setSelectedIndex( APP_REPLACE );

		lb				= new JLabel( app.getResourceString( "vectorFunctionName" ));
		con.anchor		= GridBagConstraints.WEST;
		con.gridwidth	= 1;
		con.fill		= GridBagConstraints.HORIZONTAL;
		con.insets		= ascetic;
		lay.setConstraints( lb, con );
		msgPane.add( lb );
		con.gridwidth	= GridBagConstraints.REMAINDER;
		con.weightx		= 1.0;
		lay.setConstraints( ggFunction, con );
		msgPane.add( ggFunction );
		lb				= new JLabel( app.getResourceString( "vectorFunctionAmplitude" ));
		con.gridwidth	= 1;
		con.weightx		= 0.0;
		lay.setConstraints( lb, con );
		msgPane.add( lb );
		con.gridwidth	= GridBagConstraints.REMAINDER;
		con.weightx		= 1.0;
		lay.setConstraints( ggAmplitude, con );
		msgPane.add( ggAmplitude );
		lbPeriods		= new JLabel( app.getResourceString( "vectorFunctionPeriods" ));
		con.gridwidth	= 1;
		con.weightx		= 0.0;
		lay.setConstraints( lbPeriods, con );
		msgPane.add( lbPeriods );
		con.gridwidth	= GridBagConstraints.REMAINDER;
		con.weightx		= 1.0;
		lay.setConstraints( ggPeriods, con );
		msgPane.add( ggPeriods );
		lbBrownish		= new JLabel( app.getResourceString( "vectorFunctionBrownish" ));
		con.gridwidth	= 1;
		con.weightx		= 0.0;
		lay.setConstraints( lbBrownish, con );
		msgPane.add( lbBrownish );
		con.gridwidth	= GridBagConstraints.REMAINDER;
		con.weightx		= 1.0;
		lay.setConstraints( ggBrownish, con );
		msgPane.add( ggBrownish );
		lbPhase			= new JLabel( app.getResourceString( "vectorFunctionPhase" ));
		con.gridwidth	= 1;
		con.weightx		= 0.0;
		lay.setConstraints( lbPhase, con );
		msgPane.add( lbPhase );
		con.gridwidth	= GridBagConstraints.REMAINDER;
		con.weightx		= 1.0;
		lay.setConstraints( ggPhase, con );
		msgPane.add( ggPhase );
		lb				= new JLabel( app.getResourceString( "vectorFunctionBipolar" ));
		con.gridwidth	= 1;
		con.weightx		= 0.0;
		lay.setConstraints( lb, con );
		msgPane.add( lb );
		con.gridwidth	= GridBagConstraints.REMAINDER;
		con.weightx		= 1.0;
		lay.setConstraints( ggBipolar, con );
		msgPane.add( ggBipolar );
		lb				= new JLabel( app.getResourceString( "vectorFunctionApplication" ));
		con.gridwidth	= 1;
		con.weightx		= 0.0;
		lay.setConstraints( lb, con );
		msgPane.add( lb );
		con.gridwidth	= GridBagConstraints.REMAINDER;
		con.weightx		= 1.0;
		lay.setConstraints( ggApplication, con );
		msgPane.add( ggApplication );
		
        HelpGlassPane.setHelp( msgPane, "VectorTransformApplyFunction" );

		// --- Listener ---
		dpl = new DynamicPrefChangeManager( null, new String[] { KEY_FUNCTION }, this );
		new DynamicAncestorAdapter( dpl ).addTo( msgPane );
	}

	public void setPreferences( Preferences prefs )
	{
		dpl.setPreferences( prefs );

		ggFunction.setPreferences( prefs, KEY_FUNCTION );
		ggAmplitude.setPreferences( prefs, KEY_AMPLITUDE );
		ggPhase.setPreferences( prefs, KEY_PHASE );
		ggBipolar.setPreferences( prefs, KEY_BIPOLAR );
		ggApplication.setPreferences( prefs, KEY_APPLICATION );
		ggPeriods.setPreferences( prefs, KEY_PERIODS );
		ggBrownish.setPreferences( prefs, KEY_BROWNISH );
	}

	private void guiToInternal()
	{
		function	= ggFunction.getSelectedIndex();
		amplitude   = ggAmplitude.getNumber().doubleValue();
		phase		= ggPhase.getNumber().doubleValue();
		bipolar		= ggBipolar.isSelected();
		application = ggApplication.getSelectedIndex();
		periods		= ggPeriods.getNumber().doubleValue();
		brownish	= ggBrownish.getNumber().doubleValue();
		periodic	= !(function == FUNC_CONSTANT || function == FUNC_NOISE);
		noise		= function == FUNC_NOISE;
	}

	public boolean query( Component parent )
	{
		int	result;
		
		result = JOptionPane.showOptionDialog( parent, msgPane,
			AbstractApplication.getApplication().getResourceString( "vectorApplyFunction" ),
			JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
			null, queryOptions, queryOptions[1] );

		return( result == 1 );
	}
	
	public float[] transform( float[] orig, NumberSpace spc, boolean wrapX, boolean wrapY )
	throws IOException
	{
		guiToInternal();
	
		int			len			= orig.length;
		float[]		trans		= new float[ len ];
		int			i;
		double		freq		= Math.PI * 2 * periods / len;
		Function	f;
		Class		c;
		double		parameter   = periodic ? periods : (noise ? brownish : 0);

		if( len <= 0 ) return orig;
		
		// XXX
		if( function < 0 || function >= funcClasses.length ) {
			throw new IOException( "illegal function index : "+function );
		}
		c = funcClasses[ function ];
		if( c == null ) {
			throw new IOException( "not yet implemented : "+funcNames[function] );
		}
		if( application < 0 || application >= appNames.length ) {
			throw new IOException( "illegal application index : "+application );
		}

		try {
			f   = (Function) c.newInstance();
			f.init( amplitude, parameter, bipolar );
			f.eval( trans, 0, len, Math.toRadians( phase ), freq );		// buf, off, len, phase, freq
			switch( application ) {
			case APP_ADD:
				for( i = 0; i < len; i++ ) {
					trans[i] += orig[i];
				}
				break;
			case APP_MULT:
				for( i = 0; i < len; i++ ) {
					trans[i] *= orig[i];
				}
				break;
			case APP_REPLACE:
				break;
			default:
				assert false : application;
				break;
			}
		}
		catch( InstantiationException e1 ) {
			throw IOUtil.map( e1 );
		}
		catch( IllegalAccessException e2 ) {
			throw IOUtil.map( e2 );
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
//		guiToInternal();
//
//		VectorApplyFunction.ProducerContext	prodc;
//		boolean				success		= false;
//		Class				c;
//		double				parameter   = periodic ? periods : (noise ? brownish : 0);
//
//		// XXX
//		if( function < 0 || function >= funcClasses.length ) {
//			throw new IOException( "illegal function index : "+function );
//		}
//		c = funcClasses[ function ];
//		if( c == null ) {
//			throw new IOException( "not yet implemented : "+funcNames[function] );
//		}
//		if( application < 0 || application >= appNames.length ) {
//			throw new IOException( "illegal application index : "+application );
//		}
//
//		prodc						= new VectorApplyFunction.ProducerContext();
//		prodc.consumer				= (RenderConsumer) context.getOption( RenderContext.KEY_CONSUMER );
//		context.moduleMap.put( KEY_PRODC, prodc );
//		prodc.outLength				= context.getTimeSpan().getLength();
//		prodc.progLen				= prodc.outLength;
//		prodc.progOff				= 0;
////		prodc.spc					= new NumberSpace( 0.0, 1.0, 0.0 );
//		prodc.source				= new RenderSource( source );
//		prodc.source.trajBlockBuf   = new float[ source.numTrns ][ source.numRcv ][ 0 ];
//		prodc.bufSize				= 0;
//		prodc.freq					= Math.PI * 2 * periods / prodc.outLength;
//		prodc.phase					= Math.toRadians( phase );
//
//		try {
//			prodc.f					= (Function) c.newInstance();
//			prodc.f.init( amplitude, parameter, bipolar );
//			
//			if( prodc.consumer != null ) {
//				success				= prodc.consumer.consumerBegin( context, prodc.source );
//			} else {
//				success				= true;
//			}
//		}
//		catch( InstantiationException e1 ) {
//			throw IOUtil.map( e1 );
//		}
//		catch( IllegalAccessException e2 ) {
//			throw IOUtil.map( e2 );
//		}
//
//		return success;
//	}
//
//	public boolean producerRender( RenderContext context, RenderSource source )
//	throws IOException
//	{
//		VectorApplyFunction.ProducerContext prodc =
//			(VectorApplyFunction.ProducerContext) context.moduleMap.get( KEY_PRODC );
//		int					dim			= ((Integer) context.getOption( VectorTransformFilter.KEY_DIMENSION )).intValue();
//		int					len			= source.blockBufLen;
//		float[]				inBuf, outBuf;
//		int					i, j, trnsIdx;
//		double				phase;
//		boolean				success		= false;
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
//			phase   = prodc.phase + prodc.freq * (source.blockSpan.getStart() - context.getTimeSpan().getStart());
//			prodc.f.eval( outBuf, 0, len, phase, prodc.freq );		// buf, off, len, phase, freq
//			switch( application ) {
//			case APP_ADD:
//				for( i = 0, j = source.blockBufOff; i < len; i++, j++ ) {
//					outBuf[i] += inBuf[j];
//				}
//				break;
//			case APP_MULT:
//				for( i = 0, j = source.blockBufOff; i < len; i++, j++ ) {
//					outBuf[i] *= inBuf[j];
//				}
//				break;
//			case APP_REPLACE:
//				break;
//			default:
//				assert false : application;
//				break;
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
//		VectorApplyFunction.ProducerContext prodc =
//			(VectorApplyFunction.ProducerContext) context.moduleMap.get( KEY_PRODC );
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
//		VectorApplyFunction.ProducerContext prodc =
//			(VectorApplyFunction.ProducerContext) context.moduleMap.get( KEY_PRODC );
//
//		if( prodc.consumer != null ) {
//			prodc.consumer.consumerCancel( context, prodc.source );
//		}
//	}

// ---------------- LaterInvocationManager.Listener interface ---------------- 

	// o instanceof PreferenceChangeEvent
	public void laterInvocation( Object o )
	{
		String		key		= ((PreferenceChangeEvent) o).getKey();
		String		value   = ((PreferenceChangeEvent) o).getNewValue();
		Preferences prefs   = ((PreferenceChangeEvent) o).getNode();
		String		lbText;
		boolean		pack	= false;
		Window		ancestor;

		if( key.equals( KEY_FUNCTION )) {

			for( function = 0; function < funcNames.length; function++ ) {
				if( funcNames[ function ].equals( value )) break;
			}
			periodic	= !(function == FUNC_CONSTANT || function == FUNC_NOISE);
			noise		= function == FUNC_NOISE;
			hasParam	= noise || periodic;
			
			if( ggPeriods.isVisible() != periodic ) {
				ggPeriods.setVisible( periodic );
				lbPeriods.setVisible( periodic );
				pack	= true;
			}
			if( ggPhase.isVisible() != periodic ) {
				ggPhase.setVisible( periodic );
				lbPhase.setVisible( periodic );
				pack	= true;
			}
			if( ggBrownish.isVisible() != noise ) {
				ggBrownish.setVisible( noise );
				lbBrownish.setVisible( noise );
				pack	= true;
			}
			if( pack ) {
				ancestor = (Window) SwingUtilities.getAncestorOfClass( Window.class, msgPane );
				if( ancestor != null ) ancestor.pack();
			}
		}
	}

// -------- internal class --------
// INERTIA
//	private class ProducerContext
//	{
//		private long				outLength, progOff, progLen;
////		private NumberSpace			spc;
//		private RenderSource		source;
//		private int					bufSize;
//		private Function			f;
//		private double				freq, phase;
//		private RenderConsumer		consumer;
//	}
}