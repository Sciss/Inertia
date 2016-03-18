/*
 *  NumberField.java
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
 *		07-Aug-05	copied from de.sciss.meloncillo.gui.NumberField
 */

package de.sciss.inertia.gui;

import java.awt.*;
import java.awt.event.*;
import java.beans.*;
import java.text.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;

// INERTIA
//import de.sciss.meloncillo.*;
//import de.sciss.meloncillo.math.*;
//import de.sciss.meloncillo.util.*;
import de.sciss.inertia.math.*;

import de.sciss.app.*;

import de.sciss.gui.TimeFormat;

/**
 *  A NumberField is basically a <code>JPanel</code>
 *  holding a <code>JTextField</code> whose content
 *  is limited to decimal numbers. The
 *  idea is somewhat similar to FScape's
 *  <code>ParamField</code>, but we try to avoid the
 *  conceptual drawbacks made there.
 *  <p>
 *  Number formatting is accomplished by using
 *  a <code>NumberFormat</code> object whose configuration
 *  is determinated by a <code>NumberSpace</code> given
 *  to the constructor.
 *  <p>
 *  Clients can listen to user edits by registering
 *  a <code>NumberListener</code>.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.75, 14-Jul-05
 */
public class NumberField
extends JPanel
implements EventManager.Processor, PropertyChangeListener
{
	/**
	 *  Constructor flag : Format the values
	 *  as minutes:seconds
	 */
	public static final int FLAG_MINSEC	= 0x01;

	private NumberSpace			space;
	private final JTextField	ggText;
	private JLabel				lbUnit;
	private Number				value;
	private NumberFormat		numberFormat;
	private TimeFormat			timeFormat;
	private int					flags;

	private final EventManager elm = new EventManager( this );

	/**
	 *  Create a new <code>NumberField</code> for
	 *  a given space. the initial value of the 
	 *  <code>NumberField</code> is taken
	 *  from <code>space.reset</code>. The number of
	 *  displayed integers and decimals is calculated 
	 *  by evaluating the space's <code>min</code>,
	 *  <code>max</code> and <code>quant</code>
	 *  fields. If the <code>quant</code> field is integer, no
	 *  decimals are displayed. User adjustments of
	 *  the number are automatically trimmed to the
	 *  space's <code>min</code> and <code>max</code> and
	 *  quantisized to its <code>quant</code> field
	 *
	 *  @param  flags		<code>0</code> for a normal field,
	 *						<code>FLAG_MINSEC</code> to
	 *						use minutes:seconds formatting.
	 *  @param  space		the space describing
	 *						the boundaries of the
	 *						allowed numbers.
	 *  @param  unitLabel   optional Label which will
	 *						be displayed to the right of
	 *						of the text box. use <code>null</code>
	 *						if you don't want a label.
	 */
	public NumberField( int flags, NumberSpace space, String unitLabel )
	{
		super();
		
		ggText 	= new JTextField();

		setSpace( flags, space );
		
		value	= space.isInteger() ? (Number) new Long( (long) (space.reset + 0.5) ) :
									  (Number) new Double( space.reset );
		
		GridBagLayout lay		= new GridBagLayout();
		GridBagConstraints con	= new GridBagConstraints();

		setLayout( lay );
		con.anchor		= GridBagConstraints.WEST;
		con.fill		= GridBagConstraints.HORIZONTAL;

		con.gridwidth	= 1;
		con.gridheight	= 1;
		con.gridx		= 1;
		con.gridy		= 1;
		con.weightx		= 1.0;
		con.weighty		= 0.0;
		lay.setConstraints( ggText, con );
		ggText.addFocusListener( new FocusAdapter() {
			public void focusLost( FocusEvent e )
			{
				if( validateGUI() ) dispatchChange();
			}
		});
		ggText.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent e )
			{
				validateGUI();
				dispatchChange();
			}
		});
		add( ggText );

		if( unitLabel != null ) {
			lbUnit			= new JLabel( unitLabel );
			con.gridx		= 2;
			con.weightx		= 0.0;
			con.gridwidth	= GridBagConstraints.REMAINDER;
			lay.setConstraints( lbUnit, con );
			lbUnit.setBorder( new EmptyBorder( 0, 4, 0, 0 ));
			add( lbUnit );
//			updatePreferredSize();
		}

		// MacOS X has a bug with
		// the caret position when the
		// font isn't set explicitly for sub containers
		this.addPropertyChangeListener( "font", this );
		// otherwise text field doesn't get disabled
		this.addPropertyChangeListener( "enabled", this );

		valueToTextField();
	}
	
	/**
	 *  Return the number currently displayed
	 *
	 *  @return		the number displayed in the gadget.
	 *				if the <code>NumberSpace</space> is
	 *				integer, a <code>Long</code> is returned,
	 *				otherwise a <code>Double</code>.
	 *
	 *  @see		de.sciss.meloncillo.math.NumberSpace#isInteger()
	 *  @warning	if the number was set using the <code>setNumber</code>
	 *				method, it is possible that the returned object
	 *				is neither <code>Long</code> nor <code>Double</code>.
	 *				Thus never cast it to a subclass of Number, but rather
	 *				use an appropriate translation like <code>intValue()</code>
	 *				or <code>doubleValue()</code>!
	 */
	public Number getNumber()
	{
		return value;
	}

	/**
	 *  Set the gadget contents
	 *  to a new number. No event
	 *  is fired. Though the number is formatted
	 *  according to the space's settings
	 *  (e.g. number of decimals), its value
	 *  is not altered, even if it exceeds the
	 *  space's bounds.
	 *
	 *  @param  value   the new number to display
	 */
	public void setNumber( Number value )
	{
		this.value  = value;
		valueToTextField();
	}

	/**
	 *  Returns the used number space.
	 *
	 *  @return		the <code>NumberSpace</code> that was used
	 *				to construct the <code>NumberField</code>.
	 */
	public NumberSpace getSpace()
	{
		return space;
	}

	// sync : must be called in the event thread!!
	public void setSpace( int flags, NumberSpace space )
	{
		this.flags		= flags;
		this.space		= space;
		
		double  d;
		int		i;
		String  s;

		if( space.quant > 0.0 ) {
			if( space.isInteger() ) {
				numberFormat	= NumberFormat.getIntegerInstance( Locale.US );
			} else {
				numberFormat	= NumberFormat.getInstance( Locale.US );
				numberFormat.setMinimumFractionDigits( 1 );
				numberFormat.setMaximumFractionDigits( 8 );
				numberFormat.setMinimumIntegerDigits( 1 );
				numberFormat.setMaximumIntegerDigits( 1 );
				d				= space.quant % 1.0;
				s				= numberFormat.format( new Double( d ));
				i				= s.length() - 2;
				numberFormat.setMaximumFractionDigits( i );
				numberFormat.setMinimumFractionDigits( 1 );
			}
		} else {
			numberFormat	= NumberFormat.getInstance( Locale.US );
			numberFormat.setMaximumFractionDigits( 10 );
			numberFormat.setMinimumFractionDigits( 1 );
		}
		if( (flags & FLAG_MINSEC) != 0 ) {
			timeFormat  = new TimeFormat( 0, null, null, numberFormat.getMaximumFractionDigits(), Locale.US );
			numberFormat.setMinimumIntegerDigits( 2 );
			numberFormat.setMaximumIntegerDigits( 2 );
			numberFormat.setMinimumFractionDigits( numberFormat.getMaximumFractionDigits() );
			i			= 5;
		} else {
			timeFormat  = null;
			if( Double.isInfinite( space.min ) || Double.isInfinite( space.max )) {
				numberFormat.setMaximumIntegerDigits( 8 );
			} else {
				d   = Math.max( 1.0, Math.abs( space.min ));
				i	= (int) (Math.log( d ) / MathUtil.LN10) + 1;
				d   = Math.max( 1.0, Math.abs( space.max ));
				i	= Math.max( i, (int) (Math.log( d ) / MathUtil.LN10) + 1 );
				numberFormat.setMaximumIntegerDigits( i );
			}
//			msgFormat = new MessageFormat( "{0}" );
//			msgFormat.setFormatByArgumentIndex( 0, numberFormat );
			i = 1;
		}
		i += Math.min( 4, numberFormat.getMaximumFractionDigits() ) + numberFormat.getMaximumIntegerDigits();

		numberFormat.setGroupingUsed( false );

		ggText.setColumns( i );
//		updatePreferredSize();
	}
	
//	private void updatePreferredSize()
//	{
//		final Dimension d = ggText.getPreferredSize();
//		if( lbUnit != null ) {
//			final Dimension d2	= lbUnit.getPreferredSize();
//			setPreferredSize( new Dimension( d.width + d2.width, Math.max( d.height, d2.height )));
//		} else {
//			setPreferredSize( d );
//		}
//	}

	/**
	 *  Set the gadget contents
	 *  to a new number and
	 *  dispatch a <code>NumberEvent</code> to
	 *  registered Listeners.
	 *
	 *  @param  value   the new number to display
	 *					and dispatch
	 */
	public void setNumberAndDispatchEvent( Number value )
	{
		setNumber( value );
		elm.dispatchEvent( new NumberEvent( this, NumberEvent.CHANGED, System.currentTimeMillis(), value ));
	}
	
	// --- listener registration ---
	
	/**
	 *  Register a <code>NumberListener</code>
	 *  which will be informed about changes of
	 *  the gadgets content.
	 *
	 *  @param  listener	the <code>NumberListener</code> to register
	 *  @see	de.sciss.meloncillo.util.EventManager#addListener( Object )
	 */
	public void addNumberListener( NumberListener listener )
	{
		elm.addListener( listener );
	}

	/**
	 *  Unregister a <code>NumberListener</code>
	 *  from receiving number change events.
	 *
	 *  @param  listener	the <code>NumberListener</code> to unregister
	 *  @see	de.sciss.meloncillo.util.EventManager#removeListener( Object )
	 */
	public void removeNumberListener( NumberListener listener )
	{
		elm.removeListener( listener );
	}

	public void processEvent( BasicEvent e )
	{
		NumberListener listener;
		int i;
		
		for( i = 0; i < elm.countListeners(); i++ ) {
			listener = (NumberListener) elm.getListener( i );
			switch( e.getID() ) {
			case NumberEvent.CHANGED:
				listener.numberChanged( (NumberEvent) e );
				break;
			default:
				assert false : e.getID();
			}
		} // for( i = 0; i < elm.countListeners(); i++ )
	}

	private void dispatchChange()
	{
		elm.dispatchEvent( new NumberEvent( this, NumberEvent.CHANGED, System.currentTimeMillis(), value ));
	}

	// returns true if the value was modified due fitting
	private boolean validateGUI()
	{
		Number  newValue = value;
		boolean fitted;
		long	n1, n2;
		double  d1, d2;
		
		try {
			if( (flags & FLAG_MINSEC) == 0 ) {
				newValue = numberFormat.parse( ggText.getText() );
			} else {
				newValue = timeFormat.parseTime( ggText.getText() );
			}
		}
		catch( ParseException e1 ) {
			valueToTextField();
			return false;
		}
		
		if( space.isInteger() ) {
			n1			= value.longValue();
//			n2			= (long) (space.fitValue( newValue.longValue() ) + 0.5);
			n2			= (long) space.fitValue( newValue.longValue() );
			fitted		= n1 != n2;
			if( fitted ) {
				value   = new Long( n2 );
			}
		} else {
			d1			= value.doubleValue();
			d2			= space.fitValue( newValue.doubleValue() );
			fitted		= d1 != d2;
			if( fitted ) {
				value   = new Double( d2 );
			}
		}
		valueToTextField();
		return fitted;
	}

	private void valueToTextField()
	{
		if( (flags & FLAG_MINSEC) == 0 ) {
			ggText.setText( numberFormat.format( value ));
		} else {
			ggText.setText( timeFormat.formatTime( value ));
		}
	}

// ------------------- PropertyChangeListener interface -------------------

	/**
	 *  Forwards <code>Font</code> property
	 *  changes to the child gadgets
	 */
	public void propertyChange( PropertyChangeEvent e )
	{
		if( e.getPropertyName().equals( "font" )) {
			Font fnt = this.getFont();
			ggText.setFont( fnt );
			if( lbUnit != null ) lbUnit.setFont( fnt );
		} else if( e.getPropertyName().equals( "enabled" )) {
			ggText.setEnabled( this.isEnabled() );
		}
	}
} // class NumberField
