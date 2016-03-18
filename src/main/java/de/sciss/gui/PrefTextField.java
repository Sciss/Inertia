/*
 *  PrefTextField.java
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
 *		11-Aug-05	copied from de.sciss.eisenkraut.gui.PrefTextField
 */

package de.sciss.gui;

import java.awt.event.*;
import java.util.prefs.*;
import javax.swing.*;

import de.sciss.app.DynamicAncestorAdapter;
import de.sciss.app.DynamicListening;
import de.sciss.app.EventManager;
import de.sciss.app.LaterInvocationManager;
import de.sciss.app.PreferenceEntrySync;

/**
 *  Equips a normal JTextField with
 *  preference storing / recalling capabilities.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.27, 25-Sep-05
 */
public class PrefTextField
extends JTextField
implements  DynamicListening, PreferenceChangeListener,
			LaterInvocationManager.Listener, PreferenceEntrySync
{
	private static final long serialVersionUID = 0x050925L;

	private boolean listening				= false;
	private Preferences prefs				= null;
	private String key						= null;
	private final LaterInvocationManager lim= new LaterInvocationManager( this );
	private ActionListener listener;
	
	private String defaultValue				= null;

	/**
	 *  Creates a new empty <code>PrefTextField</code>
	 *  with no preferences initially set
	 */
	public PrefTextField()
	{
		super();
		init();
	}

	/**
	 *  Creates a new <code>PrefTextField</code>
	 *  with no preferences initially set
	 *
	 *  @param  text	the initial gadget's content
	 */
	public PrefTextField( String text )
	{
		super( text );
		init();
	}

	/**
	 *  Creates a new <code>PrefTextField</code>
	 *  with no preferences initially set
	 *
	 *  @param  text	the initial gadget's content
	 *  @param  columns number of columns for the text field
	 *					(affects preferred layout size)
	 */
	public PrefTextField( String text, int columns )
	{
		super( text, columns );
		init();
	}

	/**
	 *  Creates a new empty <code>PrefTextField</code>
	 *  with no preferences initially set
	 *
	 *  @param  columns number of columns for the text field
	 *					(affects preferred layout size)
	 */
	public PrefTextField( int columns )
	{
		super( columns );
		init();
	}

	private void init()
	{
		new DynamicAncestorAdapter( this ).addTo( this );
		listener = new ActionListener() {
			public void actionPerformed( ActionEvent e )
			{
				if( EventManager.DEBUG_EVENTS ) System.err.println( "@text actionPerformed : "+key+" --> "+getText() );
				updatePrefs( getText() );
			}
		};
	}
	
	private void updatePrefs( String guiValue )
	{
		if( (prefs != null) && (key != null) ) {
			String prefsValue = prefs.get( key, null );
			if( EventManager.DEBUG_EVENTS ) System.err.println( "@text updatePrefs : "+this.key+"; old = "+prefsValue+" --> "+guiValue );
			if( (prefsValue == null && guiValue != null) ||
				(prefsValue != null && guiValue == null) ||
				(prefsValue != null && guiValue != null && !prefsValue.equals( guiValue ))) {

				prefs.put( key, guiValue );
			}
		}
	}

	public void setPreferenceNode( Preferences prefs )
	{
		setPreferences( prefs, this.key );
	}

	public void setPreferenceKey( String key )
	{
		setPreferences( this.prefs, key );
	}

	public void setPreferences( Preferences prefs, String key )
	{
		if( (this.prefs == null) || (this.key == null) ) {
			defaultValue = getText();
		}
		if( listening ) {
			stopListening();
			this.prefs  = prefs;
			this.key	= key;
			startListening();
		} else {
			this.prefs  = prefs;
			this.key	= key;
		}
	}

	public Preferences getPreferenceNode() { return prefs; }
	public String getPreferenceKey() { return key; }

	public void startListening()
	{
		if( prefs != null ) {
			prefs.addPreferenceChangeListener( this );
			this.addActionListener( listener );
			listening	= true;
			if( key != null ) {
				laterInvocation( new PreferenceChangeEvent( prefs, key, prefs.get( key, null )));
			}
		}
	}

	public void stopListening()
	{
		if( prefs != null ) {
			prefs.removePreferenceChangeListener( this );
			this.removeActionListener( listener );
			listening = false;
		}
	}
	
	// o instanceof PreferenceChangeEvent
	public void laterInvocation( Object o )
	{
		String prefsValue   = ((PreferenceChangeEvent) o).getNewValue();
		if( prefsValue == null ) {
			if( defaultValue != null ) updatePrefs( defaultValue );
			return;
		}
		String guiValue		= getText();

		if( guiValue == null || (guiValue != null && !prefsValue.equals( guiValue ))) {

			// though we filter out events when preferences effectively
			// remain unchanged, it's more clean and produces less
			// overhead to temporarily remove our ActionListener
			// so we don't produce potential loops
			this.removeActionListener( listener );
			if( EventManager.DEBUG_EVENTS ) System.err.println( "@text setText" );
			setText( prefsValue );
			fireActionPerformed();
			this.addActionListener( listener );
		}
	}
	
	public void preferenceChange( PreferenceChangeEvent e )
	{
		if( e.getKey().equals( key )) {
			if( EventManager.DEBUG_EVENTS ) System.err.println( "@text preferenceChange : "+key+" --> "+e.getNewValue() );
			lim.queue( e );
		}
	}
}
