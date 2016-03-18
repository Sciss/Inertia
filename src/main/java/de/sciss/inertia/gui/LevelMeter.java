/*
 *  LevelMeter.java
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
 *  Change log:
 *		01-Oct-05	created
 *		06-Oct-05	added orientation support
 */

// INERTIA
//package de.sciss.eisenkraut.gui;
package de.sciss.inertia.gui;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.beans.*;
import javax.swing.*;

/**
 *	A level (volume) meter GUI component. The component
 *	is a vertical bar displaying a green-to-reddish bar
 *	for the peak amplitude and a blue bar for RMS value.
 *	<p>
 *	To animate the bar, call <code>setPeakAndRMS</code> at a
 *	regular interval, typically around every 30 milliseconds
 *	for a smooth look.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.25, 06-Oct-05
 *
 *	@todo	allow linear display (now it's hard coded logarithmic)
 *	@todo	dispose method
 *	@todo	add optional labels
 *	@todo	allow to change the bar width (now hard coded to 12 pixels)
 */
public class LevelMeter
extends JComponent
{
	/**
	 *	Value for orientation : display horizontally
	 *	(minimum on the left, maximum on the right)
	 */
	public static final int		HORIZONTAL		= 0;
	/**
	 *	Value for orientation : display vertically
	 *	(minimum on the bottom, maximum on the top)
	 */
	public static final int		VERTICAL		= 1;
	private static final int	NO_ORIENTATION	= 2;

	private int					holdDuration	= 1800;	// milliseconds peak hold

	private float				peak			= -160f;
	private float				rms				= -160f;
	private float				hold			= -160f;
	private float				peakNorm		= 0.0f;
	private float				rmsNorm			= 0.0f;
	private float				holdNorm		= 0.0f;
	
	private int					recentExt	= 0;
	private long				lastUpdate		= System.currentTimeMillis();
	private long				holdEnd			= lastUpdate;
	
	private boolean				holdPainted		= true;
	private boolean				rmsPainted		= true;
	
	private boolean				logarithmic		= true;			// XXX fixed for now
	private float				fallSpeed		= 0.05f;		// decibels per millisec
	private float				holdFallSpeed	= 0.015f;		// decibels per millisec
	private float				floorWeight		= 1.0f / 40;	// -1 / minimumDecibels

	private static final int[] bgPixelsV ={	0xFF000000, 0xFF343434, 0xFF484848, 0xFF5C5C5C, 0xFF5C5C5C,
											0xFF5C5C5C, 0xFF5C5C5C, 0xFF5C5C5C, 0xFF484848, 0xFF343434,
											0xFF000000, 0xFF000000, 0xFF000000, 0xFF000000, 0xFF000000,
											0xFF000000, 0xFF000000, 0xFF000000, 0xFF000000, 0xFF000000 };

	private static final int[] bgPixelsH ={ 0xFF000000, 0xFF000000, 0xFF343434, 0xFF000000, 0xFF484848, 0xFF000000,
											0xFF5C5C5C, 0xFF000000, 0xFF5C5C5C, 0xFF000000, 0xFF5C5C5C, 0xFF000000,
											0xFF5C5C5C, 0xFF000000, 0xFF5C5C5C, 0xFF000000, 0xFF484848, 0xFF000000,
											0xFF343434, 0xFF000000 };

	private static final int[] rmsTopColor = {	0x000068, 0x5537B9, 0x764EE5, 0x9062E8, 0x8B63E8,
												0x8360E8, 0x7C60E8, 0x8876EB, 0x594CB4, 0x403A63 };
	private static final int[] rmsBotColor = {	0x000068, 0x2F4BB6, 0x4367E2, 0x577FE5, 0x577AE5,
												0x5874E6, 0x596FE6, 0x6B7AEA, 0x4851B1, 0x393D62 };

	private static final int[] peakTopColor = {	0x000000, 0xB72929, 0xFF3C3C, 0xFF6B6B, 0xFF6B6B,
												0xFF6B6B, 0xFF6B6B, 0xFFA7A7, 0xFF3C3C, 0xB72929 };

	private static final int[] peakBotColor = {	0x000000, 0x008E00, 0x00C800, 0x02FF02, 0x02FF02,
												0x02FF02, 0x02FF02, 0x68FF68, 0x00C800, 0x008E00 };

	private Paint			pntBg, pntRMS, pntPeak;
	private BufferedImage	imgBg, imgRMS, imgPeak;
	
	private static final double logPeakCorr	= 20.0 / Math.log( 10 );
	private static final double logRMSCorr	= 10.0 / Math.log( 10 );
	
	private final int	size	= 10;	// XXX fixed for now
	private Insets	insets;

	private int extHold, extPeak, extRMS;
	
	private int		orient	= NO_ORIENTATION;
	private boolean isVert;

	/**
	 *	Creates a new vertical level meter with default
	 *	ballistics and bounds.
	 */
	public LevelMeter()
	{
		this( VERTICAL );
	}
	
	/**
	 *	Creates a new level meter with default
	 *	ballistics and bounds.
	 *
	 *	@paran	orient	layout of the meter: either <code>HORIZONTAL</code> or <code>VERTICAL</code>
	 */
	public LevelMeter( int orient )
	{
		super();

		setOpaque( true );
		
		setOrientation( orient );
		
		addPropertyChangeListener( "border", new PropertyChangeListener() {
			public void propertyChange( PropertyChangeEvent e )
			{
				updateInsets();
			}
		});
	}
	
	public void setOrientation( int orient )
	{
		if( orient < HORIZONTAL || orient > VERTICAL ) throw new IllegalArgumentException( String.valueOf( orient ));

		this.orient	= orient;
		isVert		= orient == VERTICAL;
		
		setBorder( BorderFactory.createEmptyBorder( isVert ? 2 : 1, 1, 1, isVert ? 1 : 2 ));

		updateInsets();
		
		recentExt	= -1;
		repaint();
	}
	
	/**
	 *	Decides whether the peak indicator should be
	 *	painted or not. By default the indicator is painted.
	 *
	 *	@param	onOff	<code>true</code> to have the indicator painted,
	 *					<code>false</code> to switch it off
	 */
	public void setHoldPainted( boolean onOff )
	{
		holdPainted	= onOff;
		repaint();
	}
	
	/**
	 *	Decides whether the blue RMS bar should be
	 *	painted or not. By default the bar is painted.
	 *
	 *	@param	onOff	<code>true</code> to have the RMS values painted,
	 *					<code>false</code> to switch them off
	 */
	public void setRMSPainted( boolean onOff )
	{
		rmsPainted	= onOff;
		repaint();
	}
	
	/**
	 *	Sets the peak indicator hold time. Defaults to 1800 milliseconds.
	 *
	 *	@param	millis	new peak hold time in milliseconds. Note that
	 *					the special value <code>-1</code> means infinite
	 *					peak hold. In this case, to clear the indicator,
	 *					call <code>clearHold</code>
	 */
	public void setHoldDuration( int millis )
	{
		holdDuration	= millis == -1 ? Integer.MAX_VALUE: millis;
		holdEnd			= System.currentTimeMillis();
	}
	
	/**
	 *	Clears the peak hold
	 *	indicator. Note that you will need
	 *	to call <code>setPeakAndRMS</code> successively
	 *	for the graphics to be updated.
	 */
	public void clearHold()
	{
		hold		= -160f;
		holdNorm	= 0.0f;
	}
	
	/**
	 *	Adjusts the speed of the peak and RMS bar falling down.
	 *	Defaults to 50 decibels per second. At the moment,
	 *	rise (attack) speed is infinite.
	 *
	 *	@param	decibelsPerSecond	the amount of decibels by which the bars
	 *								falls in one second
	 */
	public void setFallSpeed( float decibelsPerSecond )
	{
		fallSpeed = decibelsPerSecond / 1000;
	}

	/**
	 *	Adjusts the speed of the peak hold indicator falling down.
	 *	Defaults to 15 decibels per second.
	 *
	 *	@param	decibelsPerSecond	the amount of decibels by which the peak indicator
	 *								falls in one second
	 */
	public void setHoldFallSpeed( float decibelsPerSecond )
	{
		holdFallSpeed = decibelsPerSecond / 1000;
	}

	/**
	 *	Adjusts the minimum displayed amplitude, that is the
	 *	amplitude corresponding to the bottom of the bar.
	 *	Defaults to -40 decibels. At the moment, the maximum
	 *	amplitude is fixed to 0 decibels (1.0 linear).
	 *
	 *	@param	decibels	the amplitude corresponding to the
	 *						minimum bar extent
	 */
	public void setMinAmplitude( float decibels )
	{
		floorWeight = -1.0f / decibels;
		setPeakAndRMS( this.peak, this.rms );
	}

	private void updateInsets()
	{
		insets = getInsets();
		if( isVert ) {
			final int w = size + insets.left + insets.right;
			setMinimumSize(   new Dimension( w, 8 ));
			setPreferredSize( new Dimension( w, 128 ));
			setMaximumSize(   new Dimension( w, Integer.MAX_VALUE ));
		} else {
			final int h = size + insets.top + insets.bottom;
			setMinimumSize(   new Dimension( 8, h ));
			setPreferredSize( new Dimension( 128, h ));
			setMaximumSize(   new Dimension( Integer.MAX_VALUE, h ));
		}
	}
	
	/**
	 *	Updates the meter. This will call the component's paint
	 *	method to visually reflect the new values. Call this method
	 *	regularly for a steady animated meter.
	 *	<p>
	 *	If you have switched off RMS painted, you may want to
	 *	call <code>setPeak</code> alternatively.
	 *	<p>
	 *	When your audio engine is idle, you may want to stop meter updates.
	 *	You can use the following formula to calculate the maximum delay
	 *	of the meter display to be safely at minimum levels after starting
	 *	to send zero amplitudes:
	 *	</p><UL>
	 *	<LI>for peak hold indicator not painted : delay[sec] = abs(minAmplitude[dB]) / fallTime[dB/sec]
	 *	+ updatePeriod[sec]</LI>
	 *	<LI>for painted peak hold : the maximum of the above value and
	 *	delay[sec] = abs(minAmplitude[dB]) / holdFallTime[dB/sec] + holdTime[sec] + updatePeriod[sec]
	 *	</LI>
	 *	</UL><P>
	 *	Therefore, for the default values of 1.8 sec hold time, 15 dB/sec hold fall time and -40 dB
	 *	minimum amplitude, at a display period of 30 milliseconds, this yields a
	 *	delay of around 4.5 seconds. Accounting for jitter due to GUI slowdown, in ths case it should be
	 *	safe to stop meter updates five seconds after the audio engine stopped.
	 *
	 *	@param	peak	peak amplitude (linear) between zero and one.
	 *	@param	rms		mean-square amplitude (linear). note : despite the name,
	 *					this is considered mean-square, not root-mean-square. this
	 *					method does the appropriate conversion on the fly!
	 *
	 *	@synchronization	this method is thread safe
	 */
	public void setPeakAndRMS( float peak, float rms )
	{
		final long	now			= System.currentTimeMillis();
		final float maxFall		= fallSpeed * (lastUpdate - now);	// a negative value
		final int	oldExtHold	= extHold;
		final int	oldExtPeak	= extPeak;
		final int	oldExtRMS	= extRMS;

//		if( logarithmic ) {
			peak		= (float) (Math.log( peak ) * logPeakCorr);
			this.peak  += Math.max( maxFall, peak - this.peak );
			peakNorm	= Math.max( 0.0f, Math.min( 1.0f, this.peak * floorWeight + 1 ));

			if( rmsPainted ) {
				rms			= (float) (Math.log( rms ) * logRMSCorr);
				this.rms   += Math.max( maxFall, rms  - this.rms );
				rmsNorm		= Math.max( 0.0f, Math.min( 1.0f, this.rms  * floorWeight + 1 ));
			}
			
			if( holdPainted ) {
				if( this.peak > hold ) {
					hold	= this.peak;
					holdNorm= peakNorm;
					holdEnd	= now + holdDuration;
				} else if( now > holdEnd ) {
					hold   += Math.max( holdFallSpeed * (lastUpdate - now), this.peak - hold );
					holdNorm= Math.max( 0.0f, Math.min( 1.0f, hold * floorWeight + 1 ));
				}
			}
			
//		} else {
//	
//			this.peak	= peak;
//			this.rms	= rms;
//		}

		lastUpdate	= now;
		recentExt	= isVert ? (getHeight() - insets.top  - insets.bottom + 1) & ~1 :
							   (getWidth()  - insets.left - insets.right  + 1) & ~1;

		if( isVert ) {
			extHold	= ((int) ((1.0f - holdNorm) * recentExt) + 1) & ~1;
			extPeak	= ((int) ((1.0f - peakNorm) * recentExt) + 1) & ~1;
			extRMS	= ((int) ((1.0f - rmsNorm)  * recentExt) + 1) & ~1;
	
			if( (extPeak != oldExtPeak) || (extRMS != oldExtRMS) || (extHold != oldExtHold) ) {
				final int minY, maxY;
			
				if( holdPainted ) {
					minY = Math.min( extHold, oldExtHold );
					if( rmsPainted ) {
						maxY = Math.max( Math.max( extPeak, oldExtPeak ), Math.max( extRMS, oldExtRMS )) + 2;
					} else {
						maxY = Math.max( extPeak, oldExtPeak );
					}
				} else {
					if( rmsPainted ) {
						minY = Math.min( Math.min( extPeak, oldExtPeak ), Math.min( extRMS, oldExtRMS ));
						maxY = Math.max( Math.max( extPeak, oldExtPeak ), Math.max( extRMS, oldExtRMS )) + 2;
					} else {
						minY = Math.min( extPeak, oldExtPeak );
						maxY = Math.max( extPeak, oldExtPeak );
					}
				}
		
				repaint( insets.left, insets.top + minY, getWidth() - insets.left - insets.right, maxY - minY );
			}
			
		} else {	// isHoriz
			extHold	= ((int) (holdNorm * recentExt) + 1) & ~1;
			extPeak	= ((int) (peakNorm * recentExt) + 1) & ~1;
			extRMS	= ((int) (rmsNorm  * recentExt) + 1) & ~1;

			if( (extPeak != oldExtPeak) || (extRMS != oldExtRMS) || (extHold != oldExtHold) ) {
				final int minY, maxY;
			
				if( holdPainted ) {
					maxY = Math.max( extHold, oldExtHold );
					if( rmsPainted ) {
						minY = Math.min( Math.min( extPeak, oldExtPeak ), Math.min( extRMS, oldExtRMS )) - 2;
					} else {
						minY = Math.min( extPeak, oldExtPeak );
					}
				} else {
					if( rmsPainted ) {
						maxY = Math.max( Math.max( extPeak, oldExtPeak ), Math.max( extRMS, oldExtRMS ));
						minY = Math.min( Math.min( extPeak, oldExtPeak ), Math.min( extRMS, oldExtRMS )) - 2;
					} else {
						maxY = Math.max( extPeak, oldExtPeak );
						minY = Math.min( extPeak, oldExtPeak );
					}
				}
		
				repaint( insets.left + minY, insets.top, maxY - minY, getHeight() - insets.top - insets.bottom );
			}
		}
	}
	
	/**
	 *	Updates the meter. This will call the component's paint
	 *	method to visually reflect the peak amplitude. Call this method
	 *	regularly for a steady animated meter. The RMS value is
	 *	not changed, so this method is appropriate when having RMS
	 *	painting turned off.
	 *
	 *	@param	peak	peak amplitude (linear) between zero and one.
	 *
	 *	@synchronization	this method is thread safe
	 */
	public void setPeak( float peak )
	{
		setPeakAndRMS( peak, this.rms );
	}

	private void recalcPaint()
	{
		int[]			pix;
		int				rgb;
		final float[]	hsbTop		= new float[ 3 ];
		final float[]	hsbBot		= new float[ 3 ];
		float			w1, w2;
		final float		w3			= 1.0f / (recentExt - 2);
		final int		extStep;
		final int		sizeFact;
		final int		sizeOff;

		if( isVert ) {
			extStep		= size;
			sizeFact	= 1;
			sizeOff		= 0;
		} else {
			extStep		= -1;
			sizeFact	= -recentExt;
			sizeOff		= recentExt * size - 1;
		}

		if( imgPeak != null ) {
			imgPeak.flush();
			imgPeak = null;
		}
		if( imgRMS != null ) {
			imgRMS.flush();
			imgRMS = null;
		}
		if( imgBg == null ) {
			if( isVert ) {
				imgBg = new BufferedImage( size, 2, BufferedImage.TYPE_INT_ARGB );
				imgBg.setRGB( 0, 0, size, 2, bgPixelsV, 0, size );
				pntBg = new TexturePaint( imgBg, new Rectangle( 0, 0, size, 2 ));
			} else {
				imgBg = new BufferedImage( 2, size, BufferedImage.TYPE_INT_ARGB );
				imgBg.setRGB( 0, 0, 2, size, bgPixelsH, 0, 2 );
				pntBg = new TexturePaint( imgBg, new Rectangle( 0, 0, 2, size ));
			}
		}

		pix = new int[ size * recentExt ];
		for( int x = 0; x < size; x++ ) {
			rgb = rmsTopColor[ x ];
			Color.RGBtoHSB( (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, hsbTop );
			rgb = rmsBotColor[ x ];
			Color.RGBtoHSB( (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, hsbBot );
			for( int y = 0, off = x * sizeFact + sizeOff; y < recentExt; y += 2, off += extStep << 1 ) {
				w2					= (float) y * w3;
				w1					= 1.0f - w2;
				rgb					= Color.HSBtoRGB( hsbTop[0] * w1 + hsbBot[0] * w2,
													  hsbTop[1] * w1 + hsbBot[1] * w2,
													  hsbTop[2] * w1 + hsbBot[2] * w2 );
				pix[ off ]			= rgb | 0xFF000000;
				pix[ off+extStep ]	= 0xFF000000;
			}
		}
		if( isVert ) {
			imgRMS = new BufferedImage( size, recentExt, BufferedImage.TYPE_INT_ARGB );
			imgRMS.setRGB( 0, 0, size, recentExt, pix, 0, size );
		} else {
			imgRMS = new BufferedImage( recentExt, size, BufferedImage.TYPE_INT_ARGB );
			imgRMS.setRGB( 0, 0, recentExt, size, pix, 0, recentExt );
		}

		pix = new int[ size * recentExt ];
		for( int x = 0; x < size; x++ ) {
			rgb = peakTopColor[ x ];
			Color.RGBtoHSB( (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, hsbTop );
			rgb = peakBotColor[ x ];
			Color.RGBtoHSB( (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, hsbBot );
			for( int y = 0, off = x * sizeFact + sizeOff; y < recentExt; y += 2, off += extStep << 1 ) {
				w2					= (float) y * w3;
				w1					= 1.0f - w2;
				rgb					= Color.HSBtoRGB( hsbTop[0] * w1 + hsbBot[0] * w2,
													  hsbTop[1] * w1 + hsbBot[1] * w2,
													  hsbTop[2] * w1 + hsbBot[2] * w2 );
				pix[ off ]			= rgb | 0xFF000000;
				pix[ off+extStep ]	= 0xFF000000;
			}
		}
		if( isVert ) {
			imgPeak = new BufferedImage( size, recentExt, BufferedImage.TYPE_INT_ARGB );
			imgPeak.setRGB( 0, 0, size, recentExt, pix, 0, size );
		} else {
			imgPeak = new BufferedImage( recentExt, size, BufferedImage.TYPE_INT_ARGB );
			imgPeak.setRGB( 0, 0, recentExt, size, pix, 0, recentExt );
		}
	}
	
	public void paintComponent( Graphics g )
	{
		super.paintComponent( g );
		
		final Graphics2D		g2		= (Graphics2D) g;
		final AffineTransform	atOrig	= g2.getTransform();
		final int				ext		= isVert ? (getHeight() - insets.top  - insets.bottom + 1) & ~1 :
												   (getWidth()  - insets.left - insets.right  + 1) & ~1;
		
		if( ext != recentExt ) {
			recentExt = ext;
			recalcPaint();
			if( isVert ) {
				extHold		= ((int) ((1.0f - holdNorm) * ext) + 1) & ~1;
				extPeak		= ((int) ((1.0f - peakNorm) * ext) + 1) & ~1;
				extRMS		= ((int) ((1.0f - rmsNorm)  * ext) + 1) & ~1;
			} else {
				extHold		= ((int) (holdNorm * recentExt) + 1) & ~1;
				extPeak		= ((int) (peakNorm * recentExt) + 1) & ~1;
				extRMS		= ((int) (rmsNorm  * recentExt) + 1) & ~1;
			}
		}
		
		g2.setColor( Color.black );
		g2.fillRect( 0, 0, getWidth(), getHeight() );

		g2.translate( insets.left, insets.top );

		g2.setPaint( pntBg );
		if( isVert ) {
			if( rmsPainted ) {
				g2.fillRect( 0, 0, size, extRMS + 1 );
				if( holdPainted ) g2.drawImage( imgPeak, 0, extHold, size, extHold + 1, 0, extHold, size, extHold + 1, this );
				g2.drawImage( imgPeak, 0, extPeak, size, extRMS, 0, extPeak, size, extRMS, this );
				g2.drawImage( imgRMS,  0, extRMS + 2, size, ext, 0, extRMS + 2, size, ext, this );
			} else {
				g2.fillRect( 0, 0, size, extPeak );
				if( holdPainted ) g2.drawImage( imgPeak, 0, extHold, size, extHold + 1, 0, extHold, size, extHold + 1, this );
				g2.drawImage( imgPeak, 0, extPeak, size, ext, 0, extPeak, size, ext, this );
			}
		} else {
			if( rmsPainted ) {
				g2.fillRect( extRMS + 1, 0, ext, size );
				if( holdPainted ) g2.drawImage( imgPeak, extHold - 1, 0, extHold, size, extHold - 1, 0, extHold, size, this );
				g2.drawImage( imgPeak, extRMS, 0, extPeak, size, extRMS, 0, extPeak, size, this );
				g2.drawImage( imgRMS, 0, 0, extRMS - 2, size, 0, 0, extRMS - 2, size, this );
			} else {
				g2.fillRect( extPeak, 0, ext, size );
				if( holdPainted ) g2.drawImage( imgPeak, extHold - 1, 0, extHold, size, extHold - 1, 0, extHold, size, this );
				g2.drawImage( imgPeak, 1, 0, extPeak, size, 1, 0, extPeak, size, this );
			}
		}
		
		g2.setTransform( atOrig );
	}
}