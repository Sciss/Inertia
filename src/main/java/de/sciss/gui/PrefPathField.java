/*
 *  PrefPathField.java
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
 *		20-May-05	created from de.sciss.meloncillo.gui.PrefPathField
 */

package de.sciss.gui;

import java.io.File;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;

import de.sciss.app.DynamicAncestorAdapter;
import de.sciss.app.DynamicListening;
import de.sciss.app.LaterInvocationManager;
import de.sciss.app.PreferenceEntrySync;

/**
 *  Subclass of PathField
 *  that enables automatic
 *  Preferences association
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.27, 25-Sep-05
 */
public class PrefPathField
extends PathField
implements  DynamicListening, PreferenceChangeListener,
			LaterInvocationManager.Listener, PreferenceEntrySync
{
	private static final long serialVersionUID = 0x050925L;

	private boolean listening				= false;
	private Preferences prefs				= null;
	private String key						= null;
	private final LaterInvocationManager lim= new LaterInvocationManager( this );

	private PathListener listener;
	private File defaultValue				= null;

	/**
	 *  Constructs a new <code>PrefPathField</code>.
	 *
	 *  @param  type		type of path field, e.g. TYPE_INPUTFILE.
	 *  @param  dlgTxt		text for the file chooser dialog or null
	 *  @synchronization	Like any other Swing component,
	 *						the constructor is to be called
	 *						from the event thread.
	 */
	public PrefPathField( int type, String dlgTxt )
	{
		super( type, dlgTxt );
		
		new DynamicAncestorAdapter( this ).addTo( this );
		listener = new PathListener() {
			public void pathChanged( PathEvent e )
			{
				updatePrefs( e.getPath() );
			}
		};
	}
	
	public void setPath( File f )
	{
		super.setPath( f );
		updatePrefs( f );
	}
	
	private void updatePrefs( File guiPath )
	{
		if( (prefs != null) && (key != null) ) {
			String oldValue	= prefs.get( key, "" );
			File prefsPath  = new File( oldValue );
			if( !guiPath.equals( prefsPath )) {
				prefs.put( key, guiPath.getPath() );
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

	/**
	 *  Enable Preferences synchronization.
	 *  This method is not thread safe and
	 *  must be called from the event thread.
	 *  When a preference change is received,
	 *  the path is updated in the GUi and
	 *  the PathField dispatches a PathEvent.
	 *  Likewise, if the user adjusts the path
	 *  in the GUI, the preference will be
	 *  updated. The same is true, if you
	 *  call setPath.
	 *  
	 *  @param  prefs   the preferences node in which
	 *					the value is stored, or null
	 *					to disable prefs sync.
	 *  @param  key		the key used to store and recall
	 *					prefs. the value is the path
	 *					converted to a string.
	 */
	public void setPreferences( Preferences prefs, String key )
	{
		if( (this.prefs == null) || (this.key != null) ) {
			defaultValue = getPath();
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
//System.err.println( "startListening ("+key+"). adding listener" );				
			prefs.addPreferenceChangeListener( this );
			this.addPathListener( listener );
			listening		= true;
			if( key != null ) {
				laterInvocation( new PreferenceChangeEvent( prefs, key, prefs.get( key, null ) ));
			}
		}
	}

	public void stopListening()
	{
		if( prefs != null ) {
			prefs.removePreferenceChangeListener( this );
			this.removePathListener( listener );
//System.err.println( "stopListening ("+key+"). removed listener" );				
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
		File prefsPath		= new File( prefsValue );
	
//System.err.println( "lim : "+prefsPath );
		if( !prefsPath.equals( getPath() )) {
			setPathAndDispatchEvent( prefsPath );
		}
	}
	
	public void preferenceChange( PreferenceChangeEvent e )
	{
//System.err.println( "preferenceChange : "+e.getKey()+" = "+e.getNewValue() );
		if( e.getKey().equals( key )) {
			lim.queue( e );
		}
	}
}