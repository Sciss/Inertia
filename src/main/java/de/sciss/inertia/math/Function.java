/*
 *  Function.java
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
 *		07-Aug-05	copied from de.sciss.meloncillo.math.Function
 */

package de.sciss.inertia.math;

import java.util.*;

/**
 *  A collection of function generators
 *  for commonly used waves, e.g. triangle,
 *  sine, noise, DC. Their application
 *  is targeted towards sense data, and the original
 *  bandlimited wave generators have been replaced
 *  by "simple" aliased generators, since the
 *  gibbs effect for bandlimited functions is
 *  rather unwanted in this application.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.66, 22-Aug-04
 */
public abstract class Function
{
	/**
	 *  The last queried amplitude
	 */
	protected double  amplitude;
	/**
	 *  The last custom function parameter
	 */
	protected double  parameter;
	/**
	 *  The last queried bipolar setting
	 */
	protected boolean bipolar;

	private Function() {}

	/**
	 *  Initializes the basic parameters of a
	 *  function. This is necessary because
	 *  in Meloncillo, the functions are created
	 *  using the newInstance() method
	 *  (constructor without parameters) and not
	 *  by calling the class's constructor directly.
	 *  This is kind of a constructor replacement.
	 *
	 *  @param  amplitude   peak-peak.
	 *  @param  parameter   basic parameter of the
	 *						particular function.
	 *  @param  bipolar		whether zero is lowest
	 *						function value (<code>false</code>)
	 *						or function oscillates around zero (<code>true</code>).
	 */
	public void init( double amplitude, double parameter, boolean bipolar )
	{
		this.amplitude  = amplitude;
		this.parameter  = parameter;
		this.bipolar	= bipolar;
	}

	/**
	 *  Evaluates the function along a given time span
	 *
	 *  @param  buf		where to store the evaluation results
	 *  @param  off		frame offset in buf
	 *  @param  len		number of frames to calculate
	 *  @param  phase   phase of period functions (radians)
	 *  @param  freq	frequency of period functions (normalized)
	 */
	public abstract void eval( float[] buf, int off, int len, double phase, double freq );
	
	/**
	 *  A constant function, i. e. f(x) = 1.0 * amplitude
	 */
	public static class Constant
	extends Function
	{
		public Constant() {}
	
		/**
		 *  Phase and frequency are not used
		 */
		public void eval( float[] buf, int off, int len, double phase, double freq )
		{
			int		i;
			float   c = (float) amplitude;
			
			for( i = 0; i < len; i++ ) {
				buf[ off++ ] = c;
			}
		}
	}

	/**
	 *  A noise function with power spectrum proportional to 1 / f^beta
	 *	see <A HREF="http://astronomy.swin.edu.au/~pbourke/fractals/noise/">
	 *  astronomy.swin.edu.au/~pbourke/fractals/noise</A> for details.
	 *	the code is based on the source file gennoise.c on this site
	 *  which is placed in the public domain (thanks to paul bourke).
	 *  <p>
	 *  A beta value of 0 describes white noise, while beta = 1 represents pink noise,
	 *  and beta = 2 represents brownian noise. Values much higher than
	 *  this can be used and will create beautiful slowly moving random waves.
	 *  <p>
	 *  beta is passed as the 'parameter' value in init()!
	 */
	public static class Noise
	extends Function
	{
		private static Random rand	= new Random( System.currentTimeMillis() );
	
		public Noise() {}
	
		/**
		 *  Phase and frequency are not used
		 */
		public void eval( float[] buf, int off, int len, double phase, double freq )
		{
			int		i, j, fftLength;
			float   mul, add, f1, maxAmp;
			float[] fftBuf;
			double  fftMag, fftPhase;
			double  minusBetaH  = -parameter/2;
			
			for( fftLength = 2; fftLength < len; fftLength <<= 1 );
			
			fftBuf  = new float[ fftLength + 2 ];
			
			for( i = 2, j = 2; i <= fftLength; j++ ) {
				fftMag		= Math.pow( j, minusBetaH ) * rand.nextGaussian();
				fftPhase	= MathUtil.PI2 * rand.nextDouble();
				fftBuf[i++] = (float) (fftMag * Math.cos( fftPhase ));
				fftBuf[i++] = (float) (fftMag * Math.sin( fftPhase ));
			}
			fftBuf[ fftLength - 1 ] = 0.0f;

			Fourier.realTransform( fftBuf, fftLength, Fourier.INVERSE );

			// measure maximum amplitude
			for( i = 0, maxAmp = 0.0f; i < len; i++ ) {
				f1 = Math.abs( fftBuf[i] );
				if( f1 > maxAmp ) {
					maxAmp = f1;
				}
			}
			mul = (float) (amplitude / maxAmp);

			if( bipolar ) {
				for( i = 0; i < len; i++ ) {
					buf[ off++ ] = mul * fftBuf[i];
				}
			} else {
				for( i = 0; i < len; i++ ) {
					buf[ off++ ] = mul * Math.abs( fftBuf[i] );
				}
			}
		}
	}

	/**
	 *  A sine function, i. e. f[i] = amplitude * sin( freq * i + phase )
	 */
	public static class Sine
	extends Function
	{
		public Sine() {}
	
		public void eval( float[] buf, int off, int len, double phase, double freq )
		{
			int		i;
			double  d1;
			
			if( bipolar ) {
				for( i = 0; i < len; i++ ) {
					buf[ off++ ] = (float) (amplitude * Math.sin( freq * i + phase ));
				}
			} else {
				d1 = amplitude/2;
				for( i = 0; i < len; i++ ) {
					buf[ off++ ] = (float) (d1 * (Math.sin( freq * i + phase ) + 1.0));
				}
			}
		}
	}

	/**
	 *  A falling sawtooth
	 */
	public static class FallingSaw
	extends Function
	{
		public FallingSaw() {}

		public void eval( float[] buf, int off, int len, double phase, double freq )
		{
			int		i, j;
			double  normAmp, normFreq, normPhase, add, norm;
			
			normAmp		= -2 * amplitude / (bipolar ? 1 : 2);
			add			= amplitude;
//			normFreq	= freq / 2; // / MathUtil.PI2;
			for( i = 1, normPhase = phase; normPhase < 0.0; i++ ) {
				normPhase = phase + i * MathUtil.PI2;
			}
			norm		= 0.5 / Math.PI;

			for( i = 0, j = off; i < len; i++ ) {
				buf[ j++ ] = (float) (normAmp * (((freq * i + normPhase) * norm) % 1.0) + add);
			}
		}
	}

	/**
	 *  A rising sawtooth
	 */
	public static class RisingSaw
	extends Function
	{
		public RisingSaw() {}

		public void eval( float[] buf, int off, int len, double phase, double freq )
		{
			int		i, j;
			double  normAmp, normFreq, normPhase, add, norm;
			
			normAmp		= 2 * amplitude / (bipolar ? 1 : 2);
			add			= bipolar ? -amplitude : 0.0;
//			normFreq	= freq / 2; // / MathUtil.PI2;
			for( i = 1, normPhase = phase; normPhase < 0.0; i++ ) {
				normPhase = phase + i * MathUtil.PI2;
			}
			norm		= 0.5 / Math.PI;

			for( i = 0, j = off; i < len; i++ ) {
				buf[ j++ ] = (float) (normAmp * (((freq * i + normPhase) * norm) % 1.0) + add);
			}
		}
	}

	/**
	 *  A square wave
	 */
	public static class Square
	extends Function
	{
		public Square() {}
	
		public void eval( float[] buf, int off, int len, double phase, double freq )
		{
			int		i, j;
			float   val1, val2;
			double  normPhase;
			
			val1	= (float) amplitude;
			if( bipolar ) {
				val2	= (float) -amplitude;
			} else {
				val2	= 0.0f;
			}

			for( i = 1, normPhase = phase; normPhase < 0.0; i++ ) {
				normPhase = phase + i * MathUtil.PI2;
			}
			
			for( i = 0, j = off; i < len; i++ ) {
				if( (freq * i + normPhase) % MathUtil.PI2 < Math.PI ) {
					buf[ j++ ] = val1;
				} else {
					buf[ j++ ] = val2;
				}
			}
		}
	}

	/**
	 *  A triangle wave
	 */
	public static class Triangle
	extends Function
	{
		public Triangle() {}
	
		public void eval( float[] buf, int off, int len, double phase, double freq )
		{
			int		i, j;
			double  normAmp, normFreq, normPhase, add, norm;
			
			if( bipolar ) {
				normAmp	= 4 * amplitude;
				add		= -amplitude;
			} else {
				normAmp	= 2 * amplitude;
				add		= 0.0;
			}
//			normFreq	= freq / 2; // / MathUtil.PI2;
			for( i = 1, phase += 1.5 * Math.PI, normPhase = phase; normPhase < 0.0; i++ ) {
				normPhase = phase + i * MathUtil.PI2;
			}
			norm		= 0.5 / Math.PI;

			for( i = 0, j = off; i < len; i++ ) {
				buf[ j++ ] = (float) (normAmp * Math.abs( (((freq * i + normPhase) * norm) % 1.0) - 0.5 ) + add);
			}
		}
	}
}
