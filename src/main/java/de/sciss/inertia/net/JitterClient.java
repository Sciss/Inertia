//
//  JitterClient.java
//  Inertia
//
//  Created by Admin on 12.08.05.
//  Copyright 2005 __MyCompanyName__. All rights reserved.
//

package de.sciss.inertia.net;

import de.sciss.app.AbstractApplication;
import de.sciss.app.BasicEvent;
import de.sciss.app.DocumentListener;
import de.sciss.app.EventManager;
import de.sciss.inertia.realtime.MultiTransport;
import de.sciss.inertia.session.DocumentFrame;
import de.sciss.inertia.session.Session;
import de.sciss.inertia.timeline.TimelineEvent;
import de.sciss.inertia.timeline.TimelineListener;
import de.sciss.inertia.util.PrefsUtil;
import de.sciss.io.Marker;
import de.sciss.net.OSCListener;
import de.sciss.net.OSCMessage;
import de.sciss.net.OSCReceiver;
import de.sciss.net.OSCTransmitter;
import de.sciss.util.MapManager;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;
import java.util.Collections;
import java.util.HashMap;

/**
 *	Communicates with the video patch
 *	(Max/Jitter) through OSC.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.28, 04-Nov-05
 */
public class JitterClient
implements OSCListener, DocumentListener, MapManager.Listener, EventManager.Processor
{
	private DatagramChannel			dch						= null;
	private OSCReceiver				rcv						= null;
	private OSCTransmitter			trns					= null;
	private InetSocketAddress		jitterAddr;
	
	private static final String		OSC_INERTIA				= "/inertia";
	private static final String		OSC_MAIN				= OSC_INERTIA + "/main";
	private final String[]			OSC_CHANNELS;
	private static final String		CMD_WELCOME				= "welcome";
	private static final String		CMD_PLAY				= "play";
	private static final String		CMD_STOP				= "stop";
	private static final String		CMD_SEEK				= "seek";

	private final de.sciss.app.DocumentHandler	docHandler;
	
	private final java.util.Map		mapDocsToMarkerLists	= new HashMap();
	private final Object			sync					= new Object();
	
	public final int				COUP_SLAVE				= 0;
	public final int				COUP_MASTER				= 1;
	public final int				COUP_NONE				= 2;
		
	private int						coupling				= COUP_SLAVE;
	
	private	EventManager			elm						= null;
	
	private final JitterClient		enc_this				= this;

	public JitterClient( InetSocketAddress jitterAddr )
	{
		docHandler		= AbstractApplication.getApplication().getDocumentHandler();
		
		OSC_CHANNELS	= new String[ Session.NUM_MOVIES ];
		for( int ch = 0; ch < OSC_CHANNELS.length; ch++ ) {
			OSC_CHANNELS[ ch ] = OSC_INERTIA + "/" + (char) (ch + 65);
//System.err.println( "channel "+(ch+1)+" : osc address "+OSC_CHANNELS[ ch ]);
		}

		getJitterAddr();
	
		try {
			dch	= DatagramChannel.open();
			dch.configureBlocking( true );
//			dch.socket().bind( new InetSocketAddress( "127.0.0.1", 0 ));
//			dch.socket().bind( new InetSocketAddress( InetAddress.getLocalHost(), 0 ));
//			rcv	= new OSCReceiver( dch, null );	// otudp write uses separate unknown socket ;-(
			rcv	= OSCReceiver.newUsing ( dch );	// otudp write uses separate unknown socket ;-(
			rcv.addOSCListener( this );
			rcv.startListening();
//			trns = new OSCTransmitter( dch );
			trns = OSCTransmitter.newUsing ( dch );

			docHandler.addDocumentListener( this );
		}
		catch( IOException e1 ) {
			System.err.println( "Failed to create JitterClient :\n"+
				e1.getClass().getName() + " : " + e1.getLocalizedMessage() );
		}
	}
	
	public void addListener( Listener l )
	{
		synchronized( this ) {
			if( elm == null ) {
				elm = new EventManager( this );
			}
		}
		elm.addListener( l );
	}
	
	public void removeListener( Listener l )
	{
		if( elm != null ) elm.removeListener( l );
	}

	public void processEvent( BasicEvent e )
	{
		Listener listener;
		
		for( int i = 0; i < elm.countListeners(); i++ ) {
			listener = (Listener) elm.getListener( i );
			switch( e.getID() ) {
			case Event.PLAY:
				listener.moviePlay( (Event) e );
				break;
			default:
				assert false : e.getID();
			}
		} // for( i = 0; i < elm.countListeners(); i++ )
	}

	private void getJitterAddr()
	{
		final String	val;
		final int		idx;
		int				port;
		String			hostName;

		val			= AbstractApplication.getApplication().getUserPrefs().node( PrefsUtil.NODE_VIDEO ).
						get( PrefsUtil.KEY_JITTEROSC, "" );
		idx			= val.indexOf( ':' );
		port		= 57111;
		hostName	= "127.0.0.1";
		try {
			if( idx >= 0 ) {
				hostName	= val.substring( 0, idx );
				port		= Integer.parseInt( val.substring( idx + 1 ));
			}
		}
		catch( NumberFormatException e1 ) {
			System.err.println( e1.getClass().getName() + " : " + e1.getLocalizedMessage() );
		}
		
		jitterAddr		= new InetSocketAddress( hostName, port );
	}

	public void dumpOSC( int mode )
	{
		if( rcv != null ) rcv.dumpOSC( mode, System.err );
		if( trns != null ) trns.dumpOSC( mode, System.err );
	}

	private String getResourceString( String key )
	{
		return AbstractApplication.getApplication().getResourceString( key );
	}

	public void setCoupling( int mode )
	{
		this.coupling = mode;
	}

	public void handshake()
	{
		if( dch == null ) {
			System.err.println( "! no datagram channel for jitter client !" );
			return;
		}
		
		getJitterAddr();	// if there are prefs changes, we update here
	
		// berkeley 'otudp read' provides no means
		// of querying the sender's address, so we
		// provide it directly
		System.out.println( getResourceString( "jitSendingHandshake" ) + " : " + jitterAddr.getHostName() + ":" +
							jitterAddr.getPort() );
//							+ " ; we are " + dch.socket().getLocalAddress().getHostAddress() +
//							":" + dch.socket().getLocalPort() );
		
		final OSCMessage msg = new OSCMessage( OSC_MAIN, new Object[] { CMD_WELCOME,
			dch.socket().getLocalAddress().getHostAddress(), new Integer( dch.socket().getLocalPort() )});
						
		try {
			if( trns != null ) trns.send( msg, jitterAddr );
		}
		catch( IOException e1 ) {
			System.err.println( e1.getClass().getName() + " : " + e1.getLocalizedMessage() );
		}
	}

	public void quit()
	{
		try {
			if( rcv != null ) rcv.stopListening();
			if( dch != null ) dch.close();
		}
		catch( IOException e1 ) {
			System.err.println( e1.getClass().getName() + " : " + e1.getLocalizedMessage() );
		}
	}
	
	private void rebuildMaps()
	{
		Session			doc;
		java.util.List	coll;

		synchronized( sync ) {
			mapDocsToMarkerLists.clear();
						
			for( int i = 0; i < docHandler.getDocumentCount(); i++ ) {
				doc		= (Session) docHandler.getDocument( i );
				coll	= doc.markers.getAllMarkers();
				Collections.sort( coll, Marker.nameComparator );
				mapDocsToMarkerLists.put( doc, coll );
			}
		}
	}	
	
// --------- OSCListener interface ---------

	public void messageReceived( OSCMessage msg, SocketAddress sender, long time )
	{
		String		cmd		= msg.getName();
		Session		doc;
		int			ch;

		try {
			synchronized( sync ) {
				for( ch = 0; ch < OSC_CHANNELS.length; ch++ ) {
					if( cmd.equals( OSC_CHANNELS[ ch ])) break;
				}
				if( (coupling == COUP_SLAVE) && (ch < OSC_CHANNELS.length) && (msg.getArgCount() >= 1) ) {
					cmd = msg.getArg( 0 ).toString();
					// play <(string)movieName> <(number)startTimeSecs> <(number)rateScale> <volume>
					if( cmd.equals( CMD_PLAY ) && msg.getArgCount() >= 4 ) {
						final String	movieName	= msg.getArg( 1 ).toString();
						final double	startTime	= ((Number) msg.getArg( 2 )).doubleValue();
						final float		rateScale	= ((Number) msg.getArg( 3 )).floatValue();
						final float		volume		= msg.getArgCount() == 4 ? 1.0f : ((Number) msg.getArg( 4 )).floatValue();
						int				markIdx;
						java.util.List	collMarkers;
						long			pos;
						
						for( int i = 0; i < docHandler.getDocumentCount(); i++ ) {	// XXX needs sync
							doc			= (Session) docHandler.getDocument( i );
							if( !doc.bird.attemptShared( Session.DOOR_TIME, 250 )) continue;
							try {
								pos			= (long) (doc.timeline.getRate() * startTime + 0.5);
								collMarkers	= (java.util.List) mapDocsToMarkerLists.get( doc );
								markIdx		= Collections.binarySearch( collMarkers, movieName, Marker.nameComparator );
								if( (markIdx >= 0) && (markIdx < collMarkers.size()) ) {
									pos	   += ((Marker) collMarkers.get( markIdx )).pos;
								}
								if( pos >= 0 && pos < doc.timeline.getLength() ) {
//									doc.timeline.setPosition( this, pos );
doc.layers.setVolume( ch, volume );
									doc.getTransport().goPosition( ch, pos, rateScale );
									if( elm != null ) {
										elm.dispatchEvent( new Event( enc_this, Event.PLAY, System.currentTimeMillis(), ch, movieName, startTime, rateScale ));
									}
								}
							}
							finally {
								doc.bird.releaseShared( Session.DOOR_TIME );
							}
						}
					// stop
					} else if( cmd.equals( CMD_STOP )) {
						for( int i = 0; i < docHandler.getDocumentCount(); i++ ) {	// XXX needs sync
							doc = (Session) docHandler.getDocument( i );
							doc.getTransport().goStop( ch );
						}
					}
				} else if( cmd.equals( OSC_MAIN )) {
					if( (msg.getArgCount() >= 1) && msg.getArg( 0 ).equals( CMD_WELCOME )) {
						System.out.println( getResourceString( "jitReceivedHandshake" ) );
					}
				}
			} // synchronized( sync )
		}
		catch( ClassCastException e1 ) {	// if osc arg types are casted wrong
			System.err.println( e1.getClass().getName() + " : " + e1.getLocalizedMessage() );
		}
	}

// ---------------- MapManager.Listener interface (from Session) ---------------- 

	public void mapChanged( MapManager.Event e )
	{
//		if( e.getPropertyNames().contains( Session.MAP_KEY_SCREEN )) {
//			rebuildMaps();
//		}
	}
		
	public void mapOwnerModified( MapManager.Event e ) {}

// ---------------- DocumentListener interface ---------------- 

	public void documentAdded( de.sciss.app.DocumentEvent e )
	{
		final Session doc = (Session) e.getDocument();
	
		doc.getMap().addListener( this );
		new TransportFollower( doc );
		rebuildMaps();
	}
	
	public void documentRemoved( de.sciss.app.DocumentEvent e )
	{
		((Session) e.getDocument()).getMap().removeListener( this );
		rebuildMaps();
	}

	public void documentFocussed( de.sciss.app.DocumentEvent e ) {}
	
// ---------------- internal classes ---------------- 

	private class TransportFollower
	implements MultiTransport.Listener, TimelineListener
	{
		final Session	doc;
	
		private TransportFollower( Session doc )
		{
			this.doc	= doc;
			doc.getTransport().addListener( this );
			doc.timeline.addTimelineListener( this );
//			oscCmd		= OSC_INERTIA + "/" + (String) doc.getMap().getValueAvecDefault( Session.MAP_KEY_SCREEN, "A" );
		}

		// sync: attempts shared on DOOR_TIME
		private void sendPlaySeekCmd( int ch, long pos, float rateScale, boolean play )
		{
			final int			markIdx;
			final double		startTime;
			final Marker		mark;
			final OSCMessage	msg;

			if( !doc.bird.attemptShared( Session.DOOR_TIME, 250 )) return;
			try {
				if( !play ) pos	= doc.timeline.getPosition();
				markIdx			= doc.markers.indexOf( pos + 1, false );
				if( markIdx < 0 ) return;
				mark			= doc.markers.getMarker( markIdx );
				startTime		= (double) (pos - mark.pos) / doc.timeline.getRate();
				msg				= new OSCMessage( OSC_CHANNELS[ ch ],
					play ?
						new Object[] { CMD_PLAY, mark.name, new Double( startTime ), new Float( rateScale )}
					:	new Object[] { CMD_SEEK, mark.name, new Double( startTime )}
				);
				if( trns != null ) trns.send( msg, jitterAddr );
			}
			catch( IOException e1 ) {
				System.err.println( e1.getClass().getName() + " : " + e1.getLocalizedMessage() );
			}
			finally {
				doc.bird.releaseShared( Session.DOOR_TIME );
			}
		}

		public void transportStop( MultiTransport transport, int ch, long pos )
		{
			if( coupling == COUP_MASTER ) {
				final OSCMessage msg = new OSCMessage( OSC_CHANNELS[ ch ], new Object[] { CMD_STOP });
				try {
					if( trns != null ) trns.send( msg, jitterAddr );
				}
				catch( IOException e1 ) {
					System.err.println( e1.getClass().getName() + " : " + e1.getLocalizedMessage() );
				}
			}
		}
		
		public void transportPosition( MultiTransport transport, int ch, long pos, float rateScale )
		{
			transportStop( transport, ch, pos );
			transportPlay( transport, ch, pos, rateScale );
		}
		
		public void transportPlay( MultiTransport transport, int ch, long pos, float rateScale )
		{
			if( coupling == COUP_MASTER ) {
				sendPlaySeekCmd( ch, pos, rateScale, true );
			}
		}

		public void transportQuit( MultiTransport transport ) {}

		public void timelinePositioned( TimelineEvent e )
		{
			if( coupling == COUP_MASTER ) {
				final DocumentFrame f = doc.getFrame();
				if( f != null ) {
					final int ch = f.getActiveChannel();
					if( !doc.getTransport().isRunning( ch )) {
						sendPlaySeekCmd( ch, 0, 0.0f, false );
					}
				}
			}
		}

		public void timelineChanged( TimelineEvent e ) {}
		public void timelineScrolled( TimelineEvent e ) {}
		public void timelineSelected( TimelineEvent e ) {}
	}
	
	public static class Event
	extends BasicEvent
	{
		public static final int	PLAY	= 0;
		
		private final int		ch;
		private final String	movieName;
		private final double	movieTime;
		private final float		movieRate;
		
		private Event( Object source, int ID, long when, int ch, String movieName, double movieTime, float movieRate )
		{
			super( source, ID, when );
			this.ch			= ch;
			this.movieName	= movieName;
			this.movieTime	= movieTime;
			this.movieRate	= movieRate;
		}
		
		public int    getChannel()   { return ch; }
		public String getMovieName() { return movieName; }
		public double getMovieTime() { return movieTime; }
		public float  getMovieRate() { return movieRate; }
		
		public boolean incorporate( BasicEvent e ) { return false; }
	}
	
	public interface Listener
	{
		public void moviePlay( Event e );
	}
}