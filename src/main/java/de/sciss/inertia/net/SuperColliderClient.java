/*
 *  SuperColliderClient.java
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
 *		11-Aug-05	created from de.sciss.eisenkraut.net.SuperColliderClient
 */

package de.sciss.inertia.net;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.util.prefs.*;
import javax.swing.*;
import javax.swing.event.*;

// INERTIA
//import de.sciss.eisenkraut.*;
//import de.sciss.eisenkraut.gui.*;
//import de.sciss.eisenkraut.io.*;
//import de.sciss.eisenkraut.realtime.*;
//import de.sciss.eisenkraut.session.*;
//import de.sciss.eisenkraut.util.*;
import de.sciss.inertia.Main;
import de.sciss.inertia.util.PrefsUtil;
import de.sciss.inertia.session.*;
import de.sciss.inertia.io.RoutingConfig;

import de.sciss.app.AbstractApplication;
import de.sciss.app.Application;
import de.sciss.app.DocumentHandler;
import de.sciss.app.DynamicPrefChangeManager;
import de.sciss.app.LaterInvocationManager;

import de.sciss.net.*;

import de.sciss.gui.GUIUtil;
import de.sciss.gui.LogTextArea;
import de.sciss.gui.PrefComboBox;

import de.sciss.io.Span;

import de.sciss.jcollider.*;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.2, 03-Oct-05
 */
public class SuperColliderClient
implements Constants, ServerListener, de.sciss.app.DocumentListener
{
	private ServerOptions			so;
	private Server					server			= null;
	private NodeWatcher				nw				= null;
	private final Preferences		audioPrefs;

	private static final int		DEFAULT_PORT	= 57109;	// our default server udp port

	private final java.util.List	collListeners	= new ArrayList();
	
	private final java.util.Map		mapDocsToPlayers = new HashMap();
	
	private RoutingConfig			oCfg			= null;
	
	private int						dumpMode		= kDumpOff;
	private float					volume			= 1.0f;

	public SuperColliderClient( Main root )
	{
		final Application app	= AbstractApplication.getApplication();

		audioPrefs				= app.getUserPrefs().node( PrefsUtil.NODE_AUDIO );
		so						= new ServerOptions();
		
		new DynamicPrefChangeManager( audioPrefs, new String[] { PrefsUtil.KEY_OUTPUTCONFIG },
			new LaterInvocationManager.Listener() {
				public void laterInvocation( Object o )
				{
					createOutputConfig();
					for( Iterator iter = mapDocsToPlayers.values().iterator(); iter.hasNext(); ) {
						((SuperColliderPlayer) iter.next()).setOutputConfig( oCfg, volume );
					}
				}
			}
		).startListening();	// fires change and hence createOutputConfig()

		root.getDocumentHandler().addDocumentListener( this );
	}
	
	public ServerOptions getServerOptions()
	{
		return so;
	}
	
	public SuperColliderPlayer getPlayerForDocument( Session doc )
	{
		return (SuperColliderPlayer) mapDocsToPlayers.get( doc );
	}
	
	public void addListener( ServerListener l )
	{
		synchronized( collListeners ) {
			collListeners.add( l );
		}
//		if( server != null ) {
//			server.addListener( l );
//		}
	}

	public void removeListener( ServerListener l )
	{
		synchronized( collListeners ) {
			collListeners.remove( l );
		}
//		if( server != null ) {
//			server.removeListener( l );
//		}
	}
	
	public void setVolume( float volume )
	{
		this.volume	= volume;
		if( (server != null) && server.isRunning() ) {
			try {
				server.getDefaultGroup().set( "volume", volume );	// all dem subgroups geddid
			}
			catch( IOException e1 ) {
				printError( "setVolume", e1 );
			}
		}
	}
	
	public void dumpOSC( int mode )
	{
		this.dumpMode	= mode;
	
		if( (server != null) && (server.getDumpMode() != mode) ) {
			try {
				server.dumpIncomingOSC( mode );
				server.dumpOSC( mode );
			}
			catch( IOException e1 ) {
				printError( "dumpOSC", e1 );
			}
		}
	}
	
	private String getResourceString( String key )
	{
		return AbstractApplication.getApplication().getResourceString( key );
	}

	public Server getServer()
	{
		return server;
	}

	public NodeWatcher getNodeWatcher()
	{
		return nw;
	}

	public Server.Status getStatus()
	{
		if( server != null && server.isRunning() ) {
			return server.getStatus();
		} else {
			return null;
		}
	}

	public void quit()
	{
		Server.quitAll();
	}

	public void stop()
	{
		if( (server != null) && (server.isRunning() || server.isBooting()) ) {
			try {
				server.quitAndWait();
			}
			catch( IOException e1 ) {
				printError( "stop", e1 );
			}
		}
	}

	public boolean boot()
	{
		if( (server != null) && (server.isRunning() || server.isBooting()) ) return false;
	
		String	val;
		int		idx, serverPort;
	
		so.setSampleRate( audioPrefs.getDouble( PrefsUtil.KEY_AUDIORATE, so.getSampleRate() ));
		so.setNumInputBusChannels( audioPrefs.getInt( PrefsUtil.KEY_AUDIOINPUTS, so.getNumInputBusChannels() ));
		so.setNumOutputBusChannels( audioPrefs.getInt( PrefsUtil.KEY_AUDIOOUTPUTS, so.getNumInputBusChannels() ));
		so.setDevice( audioPrefs.get( PrefsUtil.KEY_AUDIODEVICE, so.getDevice() ));
		so.setLoadDefs( false );

		// udp-port-number
		val					= audioPrefs.get( PrefsUtil.KEY_SUPERCOLLIDEROSC, "" );
		idx					= val.indexOf( ':' );
		serverPort			= DEFAULT_PORT;
		try {
			if( idx >= 0 ) serverPort = Integer.parseInt( val.substring( idx + 1 ));
		}
		catch( NumberFormatException e1 ) {
			printError( "boot", e1 );
		}
		
		val		= audioPrefs.get( PrefsUtil.KEY_SUPERCOLLIDERAPP, null );
		if( val == null ) {
			System.err.println( getResourceString( "errSCSynthAppNotFound" ));
			return false;
		}
		Server.setProgram( val );

		if( server != null ) {
			server.dispose();	// removes listeners as well
			server = null;
		}
		if( nw != null ) {
			nw.dispose();
			nw = null;
		}

		try {
			// loopback is sufficient here
			server	= new Server( AbstractApplication.getApplication().getName() + " Server",
								  new InetSocketAddress( "127.0.0.1", serverPort ), so );
//			for( int i = 0; i < collListeners.size(); i++ ) {
//				server.addListener( (ServerListener) collListeners.get( i ));
//			}
			server.addListener( this );
			if( dumpMode != kDumpOff ) dumpOSC( dumpMode );
			nw		= new NodeWatcher( server );
//nw.VERBOSE	= true;
			nw.start();
			server.boot();
			return true;
		}
		catch( IOException e1 ) {
			printError( "boot", e1 );
		}
		return false;
	}
	
	private void createOutputConfig()
	{
		final String cfgName	= audioPrefs.get( PrefsUtil.KEY_OUTPUTCONFIG, null );

		oCfg	= null;

		try {
			if( cfgName != null && audioPrefs.node( PrefsUtil.NODE_OUTPUTCONFIGS ).nodeExists( cfgName )) {
				oCfg	= new RoutingConfig( audioPrefs.node( PrefsUtil.NODE_OUTPUTCONFIGS ).node( cfgName ));
			}
		}
		catch( BackingStoreException e1 ) {
			printError( "createOutputConfig", e1 );
		}
	}
	
	private static void printError( String name, Throwable t )
	{
		System.err.println( name + " : " + t.getClass().getName() + " : " + t.getLocalizedMessage() );
	}

	public Action getDebugLoadDefsAction()
	{
		return new actionLoadDefsClass();
	}

// ------------- ServerListener interface -------------

	public void serverAction( ServerEvent e )
	{
		if( server == null ) return;
		
		switch( e.getID() ) {
		case ServerEvent.STOPPED:
			for( Iterator iter = mapDocsToPlayers.values().iterator(); iter.hasNext(); ) {
				((SuperColliderPlayer) iter.next()).dispose();
				iter.remove();
			}
			nw.clear();
			break;
			
		case ServerEvent.RUNNING:	// XXX sync
			nw.register( server.getDefaultGroup() );
			try {
				if( !server.sendMsgSync( new OSCMessage( "/d_loadDir", new Object[] {
					new File( "synthdefs" ).getAbsolutePath() }), 4.0f )) {
				
					System.err.println( getResourceString( "errOSCTimeOut" ) + " : /d_loadDir" );
					return;
				}
				
				final DocumentHandler dh = AbstractApplication.getApplication().getDocumentHandler();
				Session doc;
				for( int i = 0; i < dh.getDocumentCount(); i++ ) {
					doc = (Session) dh.getDocument( i );
					if( !mapDocsToPlayers.containsKey( doc )) {
						mapDocsToPlayers.put( doc, new SuperColliderPlayer( doc, server, nw, oCfg, volume ));
					}
				}
			}
			catch( IOException e1 ) {
				printError( "ServerEvent.RUNNING", e1 );
			}
			break;
			
		default:
			break;
		}

		synchronized( collListeners ) {
			for( int i = 0; i < collListeners.size(); i++ ) {
				((ServerListener) collListeners.get( i )).serverAction( e );
			}
		}
	}

// ---------------- DocumentListener interface ---------------- 

	public void documentAdded( de.sciss.app.DocumentEvent e )
	{
		if( (server != null) && server.isRunning() ) {
			try {
				mapDocsToPlayers.put( e.getDocument(),
					new SuperColliderPlayer( (Session) e.getDocument(), server, nw, oCfg, volume ));
			}
			catch( IOException e1 ) {
				printError( "new SuperColliderPlayer", e1 );
			}
		}
	}
	
	public void documentRemoved( de.sciss.app.DocumentEvent e )
	{
		final SuperColliderPlayer play = (SuperColliderPlayer) mapDocsToPlayers.remove( e.getDocument() );

		if( play != null ) {
			play.dispose();
		}
	}

	public void documentFocussed( de.sciss.app.DocumentEvent e ) {}

// -------------- internal classes --------------

	private class actionLoadDefsClass
	extends AbstractAction
	{
		public actionLoadDefsClass()
		{
			super( "Reload Synth Defs" );
		}

		public void actionPerformed( ActionEvent e )
		{
			if( server != null ) {
				try {
					server.sendMsg( new OSCMessage( "/d_loadDir", new Object[] {
						new File( "synthdefs" ).getAbsolutePath() }));
				}
				catch( IOException e1 ) {
					printError( getValue( NAME ).toString(), e1 );
				}
			}
		}
	}
}
