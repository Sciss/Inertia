///*
// *  PrefNumberField.java
// *  Meloncillo
// *
// *  Copyright (c) 2004-2005 Hanns Holger Rutz. All rights reserved.
// *
// *	This software is free software; you can redistribute it and/or
// *	modify it under the terms of the GNU General Public License
// *	as published by the Free Software Foundation; either
// *	version 2, june 1991 of the License, or (at your option) any later version.
// *
// *	This software is distributed in the hope that it will be useful,
// *	but WITHOUT ANY WARRANTY; without even the implied warranty of
// *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
// *	General Public License for more details.
// *
// *	You should have received a copy of the GNU General Public
// *	License (gpl.txt) along with this software; if not, write to the Free Software
// *	Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
// *
// *
// *	For further information, please contact Hanns Holger Rutz at
// *	contact@sciss.de
// *
// *
// *  Changelog:
// *		07-Aug-05	copied from de.sciss.meloncillo.gui.PrefNumberField
// */
//
//package de.sciss.inertia.gui;
//
//import java.awt.*;
//import java.awt.event.*;
//import java.beans.*;
//import java.util.*;
//import java.util.prefs.*;
//import javax.swing.*;
//
//// INERTIA
////import de.sciss.meloncillo.*;
////import de.sciss.meloncillo.math.*;
////import de.sciss.meloncillo.util.*;
//import de.sciss.inertia.math.*;
//
//import de.sciss.app.*;
//import de.sciss.gui.*;
//
///**
// *  Equips a NumberField with
// *  preference storing / recalling capabilities.
// *  We decided not to override setNumber().
// *  Thus, there are two ways
// *  to alter the gadget state, either by invoking
// *  the setNumberAndDispatch() method (DON'T USE setNumber()
// *  because it doesn't fire events) or by
// *  changing the associated preferences.
// *  When a preference change occurs, the
// *  setNumberAndDispatch() method is called, allowing
// *  clients to add NumberListeners to the
// *  gadget in case they don't want to deal
// *  with preferences. However, when possible
// *  it is recommended to use PreferenceChangeListener
// *  mechanisms.
// *
// *  @author		Hanns Holger Rutz
// *  @version	0.66, 14-Aug-04
// *
// *  @see		java.util.prefs.PreferenceChangeListener
// */
//public class PrefNumberField
//extends NumberField
//implements  DynamicListening, PreferenceChangeListener,
//			LaterInvocationManager.Listener, PreferenceEntrySync
//{
//	private boolean listening				= false;
//	private Preferences prefs				= null;
//	private String key						= null;
//	private final LaterInvocationManager lim= new LaterInvocationManager( this );
//	private NumberListener listener;
//
//	private Number defaultValue				= null;
//
//	/**
//	 *  Constructs a new <code>PrefNumberField</code>.
//	 *
//	 *  @param  flags		type of number field, usually zero
//	 *  @param  space		NumberSpace to use for the number formatting
//	 *  @param  unitLabel   text label to display right to the text field
//	 *						or null
//	 *  @synchronization	Like any other Swing component,
//	 *						the constructor is to be called
//	 *						from the event thread.
//	 */
//	public PrefNumberField( int flags, NumberSpace space, String unitLabel )
//	{
//		super( flags, space, unitLabel );
//
//		new DynamicAncestorAdapter( this ).addTo( this );
//		listener = new NumberListener() {
//			public void numberChanged( NumberEvent e )
//			{
//				if( EventManager.DEBUG_EVENTS ) System.err.println( "@numb numberChanged : "+key+" --> "+e.getNumber()+" ; node = "+(prefs != null ? prefs.name() : "null" ));
//				updatePrefs( e.getNumber() );
//			}
//		};
//		this.addNumberListener( listener );
//	}
//
////	public void setNumber( Number number )
////	{
////		if( EventManager.DEBUG_EVENTS ) System.err.println( "@numb setNumber : "+key+" --> "+number );
////		super.setNumber( number );
////		updatePrefs( number );
////	}
//
//	private void updatePrefs( Number guiNumber )
//	{
//		if( prefs != null ) {
//			Number prefsNumber;
//			if( getSpace().isInteger() ) {
//							// default value mustn't be guiNumber.doubleValue()
//				prefsNumber	= new Long( prefs.getLong( key, guiNumber.longValue() + 1 ));
//				if( EventManager.DEBUG_EVENTS ) System.err.println( "@numb updatePrefs : "+this.key+"; old = "+prefsNumber+" --> "+guiNumber );
//				if( !guiNumber.equals( prefsNumber )) {
//					prefs.putLong( key, guiNumber.longValue() );
//				}
//			} else {
//				prefsNumber	= new Double( prefs.getDouble( key, guiNumber.doubleValue() + 1.0 ));
//				if( EventManager.DEBUG_EVENTS ) System.err.println( "@numb updatePrefs : "+this.key+"; old = "+prefsNumber+" --> "+guiNumber );
//				if( !guiNumber.equals( prefsNumber )) {
//					prefs.putDouble( key, guiNumber.doubleValue() );
//				}
//			}
//		}
//	}
//
//	/**
//	 *  Enable Preferences synchronization.
//	 *  This method is not thread safe and
//	 *  must be called from the event thread.
//	 *  When a preference change is received,
//	 *  the number is updated in the GUI and
//	 *  the NumberField dispatches a NumberEvent.
//	 *  Likewise, if the user adjusts the number
//	 *  in the GUI, the preference will be
//	 *  updated. The same is true, if you
//	 *  call setNumber.
//	 *
//	 *  @param  prefs   the preferences node in which
//	 *					the value is stored, or null
//	 *					to disable prefs sync.
//	 *  @param  key		the key used to store and recall
//	 *					prefs. the value is the number
//	 *					converted to a string.
//	 */
//	public void setPreferences( Preferences prefs, String key )
//	{
//		if( this.prefs == null ) {
//			defaultValue = getNumber();
//		}
//		if( listening ) {
//			stopListening();
//			this.prefs  = prefs;
//			this.key	= key;
//			startListening();
//		} else {
//			this.prefs  = prefs;
//			this.key	= key;
//		}
//	}
//
//	public Preferences getPreferenceNode() { return prefs; }
//	public String getPreferenceKey() { return key; }
//
//	public void startListening()
//	{
//		if( prefs != null ) {
//			prefs.addPreferenceChangeListener( this );
//			listening	= true;
//			laterInvocation( new PreferenceChangeEvent( prefs, key, prefs.get( key, null ) ));
//		}
//	}
//
//	public void stopListening()
//	{
//		if( prefs != null ) {
//			prefs.removePreferenceChangeListener( this );
//			listening = false;
//		}
//	}
//
//	// o instanceof PreferenceChangeEvent
//	public void laterInvocation( Object o )
//	{
//		String prefsValue   = ((PreferenceChangeEvent) o).getNewValue();
//		if( prefsValue == null ) {
//			if( defaultValue != null ) updatePrefs( defaultValue );
//			return;
//		}
//		Number prefsNumber;
//		Number guiNumber	= getNumber();
//
//		try {
//			if( getSpace().isInteger() ) {
//				prefsNumber	= new Long( prefsValue );
//			} else {
//				prefsNumber	= new Double( prefsValue );
//			}
//		}
//		catch( NumberFormatException e1 ) {
//			prefsNumber		= guiNumber;
//		}
//
////System.err.println( "lim : "+prefsNumber );
//		if( !prefsNumber.equals( guiNumber )) {
//			// thow we filter out events when preferences effectively
//			// remain unchanged, it's more clean and produces less
//			// overhead to temporarily remove our NumberListener
//			// so we don't produce potential loops
//			this.removeNumberListener( listener );
//			if( EventManager.DEBUG_EVENTS ) System.err.println( "@numb setNumberAndDispatchEvent" );
//			setNumberAndDispatchEvent( prefsNumber );
//			this.addNumberListener( listener );
//		}
//	}
//
//	public void preferenceChange( PreferenceChangeEvent e )
//	{
////System.err.println( "preferenceChange : "+e.getKey()+" = "+e.getNewValue() );
//		if( e.getKey().equals( key )) {
//			if( EventManager.DEBUG_EVENTS ) System.err.println( "@numb preferenceChange : "+key+" --> "+e.getNewValue() );
//			lim.queue( e );
//		}
//	}
//}