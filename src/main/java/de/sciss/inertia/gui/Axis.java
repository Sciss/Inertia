/*
 *  Axis.java
 *  Eisenkraut
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
 *		07-Aug-05	copied from de.sciss.eisenkraut.gui.Axis
 */

package de.sciss.inertia.gui;

import java.awt.*;
import java.awt.event.*;
import java.awt.font.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.text.*;
import java.util.*;
import javax.swing.*;
import javax.swing.undo.*;

// INERTIA
//import de.sciss.eisenkraut.*;
//import de.sciss.eisenkraut.util.*;

/**
 *  A GUI element for displaying
 *  the timeline's axis (ruler)
 *  which is used to display the
 *  time indices and to allow the
 *  user to position and select the
 *  timeline.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.5, 03-Aug-05
 *
 *	@todo		negative minimum values
 *				result in missing tick lines
 */
public class Axis
extends JComponent
// implements SwingConstants
{
	private Dimension			recentSize		= null;
	private double				kPeriod			= 1000.0;
	private String[]			labels			= new String[0];
	private int[]				labelPos		= new int[0];
	private final GeneralPath   shpTicks		= new GeneralPath();

	private final int	orient;
	private VectorSpace space;

	private final Paint  pntBackground;
	private static final Font	fntLabel		= new Font( "Helvetica", Font.PLAIN, 10 );

	// the following are used for Number to String conversion using MessageFormat
	private static final String[] msgNormalPtrn = { "{0,number,0}",
											  "{0,number,0.0}",
											  "{0,number,0.00}",
											  "{0,number,0.000}" };
	private static final String[] msgTimePtrn = { "{0,number,integer}:{1,number,00}",
											  "{0,number,integer}:{1,number,00.0}",
											  "{0,number,integer}:{1,number,00.00}",
											  "{0,number,integer}:{1,number,00.000}" };

	private final long[]		normalRaster	= { 100000000, 10000000, 1000000, 100000, 10000, 1000, 100, 10 }; // , 1 };
	private final long[]		timeRaster		= { 60000000, 6000000, 600000, 60000, 10000, 1000, 100, 10 }; // , 1 };
	
	private final MessageFormat msgForm = new MessageFormat( msgNormalPtrn[0], Locale.US );  // XXX US locale
	private	final Object[]		msgArgs = new Object[2];
	private static final int MIN_LABEL_DISTANCE = 72;   // minimum distance between two time labels in pixels
	
	private static final int[] pntBarGradientPixels = { 0xFFB8B8B8, 0xFFC0C0C0, 0xFFC8C8C8, 0xFFD3D3D3,
														0xFFDBDBDB, 0xFFE4E4E4, 0xFFEBEBEB, 0xFFF1F1F1,
														0xFFF6F6F6, 0xFFFAFAFA, 0xFFFBFBFB, 0xFFFCFCFC,
														0xFFF9F9F9, 0xFFF4F4F4, 0xFFEFEFEF };
	private static final int barExtent = pntBarGradientPixels.length;

	private final AffineTransform trnsVertical = new AffineTransform();

	private String[]		msgPtrn;
	private long[]			labelRaster;
	private int				flags;

	/**
	 *	Defines the axis to have horizontal orient
	 */
	public static final int HORIZONTAL	= 0x00;
	/**
	 *	Defines the axis to have vertical orient
	 */
	public static final int VERTICAL	= 0x01;
	/**
	 *	Flag: Defines the axis to have flipped min/max values.
	 *	I.e. for horizontal orient, the maximum value
	 *	corresponds to the left edge, for vertical orient
	 *	the maximum corresponds to the bottom edge
	 */
	public static final int MIRROIR		= 0x02;
	/**
	 *	Flag: Requests the labels to be formatted as MIN:SEC.MILLIS
	 */
	public static final int TIMEFORMAT	= 0x04;

//	private static final int HV_MASK	= 0x01;

	/**
	 *  @param	orient	either HORIZONTAL or VERTICAL
	 */
	public Axis( int orient, int flags )
	{
		super();
		
		this.orient = orient;

		int imgWidth, imgHeight;
		BufferedImage img;
		
		if( orient == HORIZONTAL ) {
			setMaximumSize( new Dimension( getMaximumSize().width, barExtent ));
			setMinimumSize( new Dimension( getMinimumSize().width, barExtent ));
			setPreferredSize( new Dimension( getPreferredSize().width, barExtent ));
			imgWidth	= 1;
			imgHeight	= barExtent;
		} else {
			setMaximumSize( new Dimension( barExtent, getMaximumSize().height ));
			setMinimumSize( new Dimension( barExtent, getMinimumSize().height ));
			setPreferredSize( new Dimension( barExtent, getPreferredSize().height ));
			imgWidth	= barExtent;
			imgHeight	= 1;
		}
		
		setFlags( flags );
		
		img = new BufferedImage( imgWidth, imgHeight, BufferedImage.TYPE_INT_ARGB );
		img.setRGB( 0, 0, imgWidth, imgHeight, pntBarGradientPixels, 0, imgWidth );
		pntBackground = new TexturePaint( img, new Rectangle( 0, 0, imgWidth, imgHeight ));

		setOpaque( true );
  	}
	
	public void setFlags( int flags )
	{
		this.flags		= flags;
		
		if( (flags & TIMEFORMAT) == 0 ) {
			msgPtrn		= msgNormalPtrn;
			labelRaster	= normalRaster;
		} else {
			msgPtrn		= msgTimePtrn;
			labelRaster	= timeRaster;
		}

		recentSize  = null;			// triggers recalcLabels()
		if( isVisible() ) repaint();
	}

	public int getFlags()
	{
		return flags;
	}
	
	public void setSpace( VectorSpace space )
	{
		this.space = space;
		recentSize  = null;			// triggers recalcLabels()
		if( isVisible() ) repaint();
	}
	
	public void paintComponent( Graphics g )
	{
		super.paintComponent( g );
		
		final Dimension			d           = getSize();
        final Graphics2D		g2          = (Graphics2D) g;
//		final Stroke			strkOrig	= g2.getStroke();
		final AffineTransform	trnsOrig	= g2.getTransform();
		final FontMetrics		fm			= g2.getFontMetrics();

		final int				y;

		if( recentSize == null || d.width != recentSize.width || d.height != recentSize.height ) {
			recentSize = d;
			recalcLabels();
			if( orient == VERTICAL ) recalcTransforms();
		}

		g2.setPaint( pntBackground );
		g2.fillRect( 0, 0, recentSize.width, recentSize.height );

		g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF );

		if( orient == VERTICAL ) {
			g2.transform( trnsVertical );
			y   = recentSize.width - 3 - fm.getMaxDescent();
		} else {
			y   = recentSize.height - 3 - fm.getMaxDescent();
		}
		g2.setColor( Color.lightGray );
		g2.draw( shpTicks );
//		g2.setStroke( strkOrig );

		g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
		g2.setColor( Color.black );
		g2.setFont( fntLabel );

		for( int i = 0; i < labels.length; i++ ) {
			g2.drawString( labels[i], labelPos[i], y );
		}

		g2.setTransform( trnsOrig );
    }
    
	private void recalcTransforms()
	{
//		trnsVertical.setToRotation( -Math.PI / 2, (double) barExtent / 2,
//												  (double) barExtent / 2 );
		trnsVertical.setToRotation( -Math.PI / 2, (double) recentSize.height / 2,
												  (double) recentSize.height / 2 );
	}
  
	private void recalcLabels()
	{
		int			width, height, numTicks, numLabels, ptrnIdx;
		double		scale, pixelOff, pixelStep, tickStep, d1;
		long		valueOff, valueStep, raster;

		shpTicks.reset();
		if( space == null ) {
			labels		= new String[ 0 ];
			labelPos	= new int[ 0 ];
			return;
		}

		if( orient == HORIZONTAL ) {
			if( space.hlog ) {
				recalcLogLabels();
				return;
			}
			width		= recentSize.width;
			height		= recentSize.height;
			scale		= (double) width / (space.hmax - space.hmin);
			valueStep	= (long) (kPeriod * (space.hmax - space.hmin) / (width / MIN_LABEL_DISTANCE));
			d1			= kPeriod * space.hmin; // (double) visibleSpan.getStart() * kPeriod;
		} else {
			if( space.vlog ) {
				recalcLogLabels();
				return;
			}
			width		= recentSize.height;
			height		= recentSize.width;
			scale		= (double) width / (space.vmax - space.vmin);
			valueStep	= (long) (kPeriod * (space.vmax - space.vmin) / (width / MIN_LABEL_DISTANCE));
			d1			= kPeriod * space.vmin; // (double) visibleSpan.getStart() * kPeriod;
		}
		
		ptrnIdx = 3;
		raster	= 1;
		for( int i = 0; i < labelRaster.length; i++ ) {
			if( valueStep >= labelRaster[ i ]) {
				ptrnIdx	= Math.max( 0, i - 5 );
				raster	= labelRaster[ i ];
				break;
			}
		}
		
//		if( valueStep >= 1000000 ) {
//			ptrnIdx	= 0;
//			raster	= 1000000;
//		} else if( valueStep >= 100000 ) {
//			ptrnIdx	= 0;
//			raster	= 100000;
//		} else if( valueStep >= 10000 ) {
//			ptrnIdx	= 0;
//			raster	= 10000;
//		} else if( valueStep >= 1000 ) {
//			ptrnIdx	= 0;
//			raster	= 1000;
//		} else if( valueStep >= 100 ) {
//			ptrnIdx	= 1;
//			raster	= 100;
//		} else if( valueStep >= 10 ) {
//			ptrnIdx	= 2;
//			raster	= 10;
//		} else {
//			ptrnIdx	= 3;
//			raster	= 1;
//		}
		valueStep	= Math.max( 1, (valueStep + (raster >> 1)) / raster );
		if( valueStep > 9 ) {
			numTicks	= 5;
		} else {
			switch( (int) valueStep ) {
			case 2:
			case 4:
			case 8:
				numTicks	= 4;
				break;
			case 3:
			case 6:
				numTicks	= 6;
				break;
			case 7:
			case 9:
				valueStep	= 10;
				numTicks	= 5;
				break;
			default:
				numTicks	= 5;
				break;
			}
		}
		valueStep   *= raster;

		msgForm.applyPattern( msgPtrn[ ptrnIdx ]);
		valueOff	= (long) (d1 / valueStep) * valueStep;
		pixelOff	= (valueOff - d1) / kPeriod * scale + 0.5;
		pixelStep   = valueStep / kPeriod * scale;
		tickStep	= pixelStep / numTicks;
		
		numLabels	= (int) ((width - pixelOff + pixelStep - 1.0) / pixelStep);
		if( labels.length != numLabels ) labels = new String[ numLabels ];
		if( labelPos.length != numLabels ) labelPos = new int[ numLabels ];

		if( (flags & MIRROIR) != 0 ) {
			pixelOff	= width - pixelOff;
			tickStep	= -tickStep;
		}

//System.err.println( "valueOff = "+valueOff+"; valueStep = "+valueStep+"; pixelStep "+pixelStep+"; tickStep "+tickStep+
//					"; pixelOff "+pixelOff+"; d1 "+d1 );
		for( int i = 0; i < numLabels; i++ ) {
			if( (flags & TIMEFORMAT) == 0 ) {
				msgArgs[ 0 ]	= new Double( (double) valueOff / kPeriod );
			} else {
				msgArgs[ 0 ]	= new Long( valueOff / 60000 );
				msgArgs[ 1 ]	= new Double( (double) (valueOff % 60000) / 1000 );
			}
			labels[ i ]		= msgForm.format( msgArgs );
			labelPos[ i ]	= (int) pixelOff + 2;
			valueOff	   += valueStep;
			shpTicks.moveTo( (float) pixelOff, 1 );
			shpTicks.lineTo( (float) pixelOff, height - 2 );
			pixelOff	   += tickStep;
			for( int k = 1; k < numTicks; k++ ) {
				shpTicks.moveTo( (float) pixelOff, height - 4 );
				shpTicks.lineTo( (float) pixelOff, height - 2 );
				pixelOff += tickStep;
			}
		}
	}

	private void recalcLogLabels()
	{
		int				numLabels, width, height, numTicks, mult, expon, newPtrnIdx, ptrnIdx;
		double			spaceOff, factor, d1, pixelOff, min, max;

		if( orient == HORIZONTAL ) {
			width		= recentSize.width;
			height		= recentSize.height;
			min			= space.hmin;
			max			= space.hmax;
		} else {
			width		= recentSize.height;
			height		= recentSize.width;
			min			= space.vmin;
			max			= space.hmax;
		}
		
		factor	= Math.pow( max / min, (double) MIN_LABEL_DISTANCE / (double) width );
		expon	= (int) (Math.log( factor ) / Math.log( 10 ));
		mult	= (int) (Math.ceil( factor / Math.pow( 10, expon )) + 0.5);
		if( mult > 5 ) {
			expon++;
			mult = 1;
		} else if( mult > 3 ) {
			mult = 4;
		} else if( mult > 2 ) {
			mult = 5;
		}
		factor	= mult * Math.pow( 10, expon );
		
		numLabels = (int) (Math.ceil( Math.log( max/min ) / Math.log( factor )) + 0.5);
		if( labels.length != numLabels ) labels = new String[ numLabels ];
		if( labelPos.length != numLabels ) labelPos = new int[ numLabels ];

//		if( (min * (factor - 1.0)) % 10 == 0.0 ) {
//			numTicks	= 10;
//		} else {
			numTicks	= 8;
//		}
//		tickFactor	= Math.pow( factor, 1.0 / numTicks );

//System.err.println( "factor "+factor+"; expon "+expon+"; mult "+mult+"; tickFactor "+tickFactor+"; j "+j );

		ptrnIdx = -1;

		for( int i = 0; i < numLabels; i++ ) {
			spaceOff	= min * Math.pow( factor, i );
			newPtrnIdx	= 3;
			for( int k = 1000; k > 1 && (((spaceOff * k) % 1.0) == 0); k /= 10 ) {
				newPtrnIdx--;
			}
			if( ptrnIdx != newPtrnIdx ) {
				msgForm.applyPattern( msgPtrn[ newPtrnIdx ]);
				ptrnIdx = newPtrnIdx;
			}

			if( orient == HORIZONTAL ) {
				pixelOff	= space.hSpaceToUnity( spaceOff ) * width;
			} else {
				pixelOff	= space.vSpaceToUnity( spaceOff ) * width;
			}
//System.err.println( "#"+i+" : spaceOff = "+spaceOff+"; pixelOff "+pixelOff );
			msgArgs[ 0 ]	= new Double( spaceOff );
			labels[ i ]		= msgForm.format( msgArgs );
			labelPos[ i ]	= (int) pixelOff + 2;
			shpTicks.moveTo( (float) pixelOff, 1 );
			shpTicks.lineTo( (float) pixelOff, height - 2 );
			d1			= spaceOff * (factor - 1) / numTicks;
			for( int n = 1; n < numTicks; n++ ) {
				if( orient == HORIZONTAL ) {
					pixelOff	= space.hSpaceToUnity( spaceOff + d1 * n ) * width;
				} else {
					pixelOff	= space.vSpaceToUnity( spaceOff + d1 * n ) * width;
				}
				shpTicks.moveTo( (float) pixelOff, height - 4 );
				shpTicks.lineTo( (float) pixelOff, height - 2 );
			}
		}
	}
}