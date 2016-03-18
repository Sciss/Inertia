/*
 *  NumberSpace.java
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
 *		07-Aug-05	copied from de.sciss.meloncillo.math.NumberSpace
 */

package de.sciss.inertia.math;

/**
 *  A number space
 *  describes a field of possible
 *  numeric values with a minimum
 *  and maximum (which can be infinity),
 *  a central value (usually zero)
 *  a quantization size which can be
 *  used to describe integers or to
 *  limit the numeric resolution.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.6, 04-Aug-04
 */
public class NumberSpace
{
	/**
	 *  Minimum allowed value
	 *  or Double.NEGATIVE_INFINITY
	 */
	public final double min;
	/**
	 *  Maximum allowed value
	 *  or Double.POSITIVE_INFINITY
	 */
	public final double max;
	/**
	 *  Quantization of values
	 *  or zero
	 */
	public final double quant;
	/**
	 *  Reset value, i.e.
	 *  a kind of default value.
	 */
	public final double reset;
	/**
	 *  Default increment.
	 *  Decrement is likewise -inc.
	 */
	public final double inc;
	
	/**
	 *  Ready-made NumberField for
	 *  double values, without boundaries.
	 */
	public static NumberSpace   genericDoubleSpace  = new NumberSpace(
		Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0.0, 0.0, 1.0 );
	/**
	 *  Ready-made NumberField for
	 *  integer values, without boundaries.
	 */
	public static NumberSpace   genericIntSpace  = new NumberSpace(
		Integer.MIN_VALUE, Integer.MAX_VALUE, 1.0, 0.0, 1.0 );

	private final boolean isInteger;

	/**
	 *  Creates a new <code>NumberSpace</code>
	 *  with the given values.
	 *
	 *  @param  min		minimum allowed value or a special value like Double.NEGATIVE_INFINITY
	 *					or Integer.MIN_VALUE
	 *  @param  max		maximum allowed value or a special value like Float.POSITIVE_INFINITY
	 *					or Long.MAX_VALUE
	 *  @param  quant   coarsity for each allowed value. E.g. if quant is 0.1, then a
	 *					value of 0.12 becomes 0.1 if you call fitValue. If quant is 0.0,
	 *					no quantization is used. If quant is integer, the number space is
	 *					marked integer and calling isInteger returns true.
	 *  @param  reset   central value for initializations of unknown values. Usually zero.
	 *  @param  inc		default increment in GUI elements.
	 */
	public NumberSpace( double min, double max, double quant, double reset, double inc )
	{
		this.min	= min;
		this.max	= max;
		this.quant	= quant;
		this.reset	= reset;
		this.inc	= inc;
		
		isInteger   = quant > 0.0 && (quant % 1.0) == 0.0;
	}

	/**
	 *  Creates a new NumberSpace
	 *  with the given values. Reset is
	 *  zero or on of min/max, 
	 *  increment is max( 1, quant )
	 */
	public NumberSpace( double min, double max, double quant )
	{
		this( min, max, quant, NumberSpace.fitValue( 0.0, min, max, quant ),
			  NumberSpace.fitValue( Math.max( 1.0, quant ), min, max, quant ));
	}
	
	/**
	 *  States if the NumberSpace's quant
	 *  is integer (and not zero).
	 *
	 *  @return true if the quantization is integer and
	 *			hence all valid values are integers
	 */
	public boolean isInteger()
	{
		return isInteger;
	}
	
	public boolean equals( Object o )
	{
		if( o instanceof NumberSpace ) {
			NumberSpace space2 = (NumberSpace) o;
			return( (this.min == space2.min) && (this.max == space2.max) &&
					(this.quant == space2.quant) && (this.reset == space2.reset) &&
					(this.inc == space2.inc) );
		}
		return false;
	}
	
	/**
	 *  Utility method for creating a generic integer space
	 *  for a given minimum and maximum value. Quant will
	 *  be set to 1.0.
	 */
	public static NumberSpace createIntSpace( int min, int max )
	{
		return new NumberSpace( min, max, 1.0 );
	}
	
	/**
	 *  Validates a value for this number space. First,
	 *  it is quantized (rounded) if necessary. Then it
	 *  is limited to the minimum and maximum value.
	 *
	 *  @param  value   a value to validate
	 *  @return the input value possibly quantisized and limited
	 *			the space's bounds.
	 */
	public double fitValue( double value )
	{
		if( quant > 0.0 ) {
			value = Math.round( value / quant ) * quant;
		}
		return Math.min( max, Math.max( min, value ));
	}

	/**
	 *  Validates a value for an ad-hoc number space. First,
	 *  it is quantized (rounded) if necessary. Then it
	 *  is limited to the minimum and maximum value.
	 *
	 *  @param  value   a value to validate
	 *  @param  min		the minimum limitation
	 *  @param  max		the maximum limitation
	 *  @param  quant	the quantization to apply
	 *  @return the input value possibly quantisized and limited
	 *			the described space's bounds.
	 */
	public static double fitValue( double value, double min, double max, double quant )
	{
		if( quant > 0.0 ) {
			value = Math.round( value / quant ) * quant;
		}
		return Math.min( max, Math.max( min, value ));
	}
}