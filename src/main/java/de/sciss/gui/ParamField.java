/*
 *  PathField.java
 *  de.sciss.gui package
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
 *		16-Sep-05	created
 */

package de.sciss.gui;

import java.awt.*;
import java.awt.event.*;
import java.beans.*;
import java.text.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;

import de.sciss.app.BasicEvent;
import de.sciss.app.EventManager;

import de.sciss.util.DefaultUnitTranslator;
import de.sciss.util.Param;
import de.sciss.util.ParamSpace;

/**
 *  @version	0.25, 17-Sep-05
 */
public class ParamField
extends JPanel
implements PropertyChangeListener, EventManager.Processor
{
	private final Jog						ggJog;
	private final NumberField				ggNumber;
	private final UnitLabel					lbUnit;
	private final UnitViewFactory			uvf;
	private ParamSpace.Translator			ut;

	protected final java.util.List			collSpaces		= new ArrayList();
	private ParamSpace						currentSpace	= null;

	private EventManager					elm				= null;	// lazy creation

	public ParamField()
	{
		this( new DefaultUnitTranslator() );
	}

	public ParamField( final ParamSpace.Translator ut )
	{
		super();
		
		uvf				= new DefaultUnitViewFactory();
		this.ut			= ut;
		
		final GridBagLayout			lay		= new GridBagLayout();
		final GridBagConstraints	con		= new GridBagConstraints();

		setLayout( lay );
		con.anchor		= GridBagConstraints.WEST;
		con.fill		= GridBagConstraints.HORIZONTAL;

		ggJog			= new Jog();
		ggNumber		= new NumberField();
		lbUnit			= new UnitLabel();
		
		ggJog.addListener( new NumberListener() {
			public void numberChanged( NumberEvent e )
			{
				if( currentSpace != null ) {
					final double	inc		= e.getNumber().doubleValue() * currentSpace.inc;
					final Number	num		= ggNumber.getNumber();
					final Number	newNum;
					boolean			changed;
					
					if( currentSpace.isInteger() ) {
						newNum	= new Long( (long) currentSpace.fitValue( num.longValue() + inc ));
					} else {
						newNum	= new Double( currentSpace.fitValue( num.doubleValue() + inc ));
					}
					
					changed	= !newNum.equals( num );
					if( changed ) {
						ggNumber.setNumber( newNum );
					}
					if( changed || !e.isAdjusting() ) {
						fireValueChanged( e.isAdjusting() );
					}
				}
			}
		});
		
		ggNumber.addListener( new NumberListener() {
			public void numberChanged( NumberEvent e )
			{
				fireValueChanged( e.isAdjusting() );
			}
		});
		
		lbUnit.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent e )
			{
				selectSpace( lbUnit.getSelectedIndex() );
				
				fireSpaceChanged();
				fireValueChanged( false );
			}
		});

		con.gridwidth	= 1;
		con.gridheight	= 1;
		con.gridx		= 1;
		con.gridy		= 1;
		con.weightx		= 0.0;
		con.weighty		= 0.0;
		lay.setConstraints( ggJog, con );
		ggJog.setBorder( new EmptyBorder( 0, 2, 0, 2 ));
		add( ggJog );

		con.gridx++;
		con.weightx		= 1.0;
		lay.setConstraints( ggNumber, con );
		add( ggNumber );

		con.gridx++;
		con.weightx		= 0.0;
		con.gridwidth	= GridBagConstraints.REMAINDER;
		lay.setConstraints( lbUnit, con );
		lbUnit.setBorder( new EmptyBorder( 0, 4, 0, 0 ));
		add( lbUnit );

		this.addPropertyChangeListener( "font", this );
		this.addPropertyChangeListener( "enabled", this );
	}
	
	public void focusNumber()
	{
		ggNumber.requestFocus();
	}
	
	public void addSpace( ParamSpace spc )
	{
		collSpaces.add( spc );
		final Object view = uvf.createView( spc.unit );
		
		if( view instanceof Icon ) {
			lbUnit.addUnit( (Icon) view );
		} else {
			lbUnit.addUnit( view.toString() );
		}
		
		if( collSpaces.size() == 1 ) {
			currentSpace = spc;
			ggNumber.setSpace( spc );
		}
	}
	
	public Param getValue()
	{
		return new Param( ggNumber.getNumber().doubleValue(),
						  currentSpace == null ? ParamSpace.NONE : currentSpace.unit );
	}
	
	public void setValue( Param newValue )
	{
		final Number oldNum		= ggNumber.getNumber();
		final Param newParam	= ut.translate( newValue, currentSpace );
		final Number newNum;

		if( currentSpace.isInteger() ) {
			newNum				= new Long( (long) newParam.val );
		} else {
			newNum				= new Double( newParam.val );
		}
		if( !newNum.equals( oldNum )) ggNumber.setNumber( newNum );
	}

	public ParamSpace getSpace()
	{
		return currentSpace;
	}

	public void setSpace( ParamSpace newSpace )
	{
		for( int i = 0; i < collSpaces.size(); i++ ) {
			if( newSpace == collSpaces.get( i )) {	// rely on references here
				selectSpace( i );
				return;
			}
		}
		throw new IllegalArgumentException( "Illegal space switch "+newSpace );
	}

	public ParamSpace.Translator getTranslator()
	{
		return ut;
	}

	public void setTranslator( ParamSpace.Translator ut )
	{
		this.ut	= ut;
	}

	private void selectSpace( int selectedIdx )
	{
		if( selectedIdx >= 0 && selectedIdx < collSpaces.size() ) {

			final ParamSpace oldSpace = currentSpace;
			currentSpace			= (ParamSpace) collSpaces.get( selectedIdx );

			final Number oldNum		= ggNumber.getNumber();
			final Param oldParam	= new Param( oldNum == null ?
				(oldSpace == null ? 0.0 : oldSpace.reset) : oldNum.doubleValue(),
				oldSpace == null ? ParamSpace.NONE : oldSpace.unit );
			final Param newParam	= ut.translate( oldParam, currentSpace );
			final Number newNum;

			if( currentSpace.isInteger() ) {
				newNum				= new Long( (long) newParam.val );
			} else {
				newNum				= new Double( newParam.val );
			}

			ggNumber.setSpace( currentSpace );
			ggNumber.setFlags( currentSpace.unit & ParamSpace.SPECIAL_MASK );
			if( !newNum.equals( oldNum )) ggNumber.setNumber( newNum );

			if( selectedIdx != lbUnit.getSelectedIndex() ) {
				lbUnit.setSelectedIndex( selectedIdx );
			}

		} else {
			currentSpace = null;
		}
	}

	// --- listener registration ---
	
	/**
	 *  Register a <code>NumberListener</code>
	 *  which will be informed about changes of
	 *  the gadgets content.
	 *
	 *  @param  listener	the <code>NumberListener</code> to register
	 */
	public void addListener( ParamField.Listener listener )
	{
		synchronized( this ) {
			if( elm == null ) {
				elm = new EventManager( this );
			}
			elm.addListener( listener );
		}
	}

	/**
	 *  Unregister a <code>NumberListener</code>
	 *  from receiving number change events.
	 *
	 *  @param  listener	the <code>NumberListener</code> to unregister
	 */
	public void removeListener( ParamField.Listener listener )
	{
		if( elm != null ) elm.removeListener( listener );
	}

	public void processEvent( BasicEvent e )
	{
		ParamField.Listener listener;
		
		for( int i = 0; i < elm.countListeners(); i++ ) {
			listener = (ParamField.Listener) elm.getListener( i );
			switch( e.getID() ) {
			case ParamField.Event.VALUE:
				listener.paramValueChanged( (ParamField.Event) e );
				break;
			case ParamField.Event.SPACE:
				listener.paramSpaceChanged( (ParamField.Event) e );
				break;
			default:
				assert false : e.getID();
			}
		} // for( i = 0; i < elm.countListeners(); i++ )
	}

	protected void fireValueChanged( boolean adjusting )
	{
		if( elm != null ) {
			elm.dispatchEvent( new ParamField.Event( this, ParamField.Event.VALUE, System.currentTimeMillis(),
				getValue(), getSpace(), getTranslator(), adjusting ));
		}
	}

	protected void fireSpaceChanged()
	{
		if( elm != null ) {
			elm.dispatchEvent( new ParamField.Event( this, ParamField.Event.SPACE, System.currentTimeMillis(),
				getValue(), getSpace(), getTranslator(), false ));
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
			final Font fnt = this.getFont();
			ggNumber.setFont( fnt );
			lbUnit.setFont( fnt );
		} else if( e.getPropertyName().equals( "enabled" )) {
			final boolean enabled = this.isEnabled();
			ggJog.setEnabled( enabled );
			ggNumber.setEnabled( enabled );
			lbUnit.setEnabled( enabled );
		}
	}

// ------------------- internal classes / interfaces -------------------

	public interface UnitViewFactory
	{
		public Object createView( int unit );
	}
	
	public static class Event
	extends BasicEvent
	{
	// --- ID values ---
		/**
		 *  returned by getID() : the param value changed
		 */
		public static final int VALUE	= 0;
		/**
		 *  returned by getID() : the param space was switched
		 */
		public static final int SPACE	= 1;

		private final Param					value;
		private final ParamSpace			space;
		private final ParamSpace.Translator	ut;
		private final boolean				adjusting;

		public Event( Object source, int ID, long when, Param value, ParamSpace space,
					  ParamSpace.Translator ut, boolean adjusting )
		{
			super( source, ID, when );
		
			this.value			= value;
			this.space			= space;
			this.ut				= ut;
			this.adjusting		= adjusting;
		}
		
		public boolean isAdjusting()
		{
			return adjusting;
		}
		
		public Param getValue()
		{
			return value;
		}

		public Param getTranslatedValue( ParamSpace newSpace )
		{
			return ut.translate( value, newSpace );
		}

		public ParamSpace getSpace()
		{
			return space;
		}

		public boolean incorporate( BasicEvent oldEvent )
		{
			if( oldEvent instanceof ParamField.Event &&
				this.getSource() == oldEvent.getSource() &&
				this.getID() == oldEvent.getID() ) {
				
				return true;

			} else return false;
		}
	}
	
	public interface Listener
	extends EventListener
	{
		public void paramValueChanged( ParamField.Event e );
		public void paramSpaceChanged( ParamField.Event e );
	}
}