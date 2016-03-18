/*
 *  SuperColliderPlayer.java
 *  Inertia
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
 *		11-Aug-05	created from de.sciss.eisenkraut.net.SuperColliderPlayer
 */

package de.sciss.inertia.net;

import de.sciss.app.AbstractApplication;
import de.sciss.app.BasicEvent;
import de.sciss.app.EventManager;
import de.sciss.inertia.io.RoutingConfig;
import de.sciss.inertia.math.MathUtil;
import de.sciss.inertia.realtime.MultiTransport;
import de.sciss.inertia.realtime.RealtimeConsumer;
import de.sciss.inertia.realtime.RealtimeConsumerRequest;
import de.sciss.inertia.realtime.RealtimeContext;
import de.sciss.inertia.session.Atom;
import de.sciss.inertia.session.LayerManager;
import de.sciss.inertia.session.Session;
import de.sciss.inertia.session.Track;
import de.sciss.jcollider.*;
import de.sciss.net.OSCBundle;
import de.sciss.net.OSCListener;
import de.sciss.net.OSCMessage;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

// INERTIA
//import de.sciss.eisenkraut.*;
//import de.sciss.eisenkraut.gui.*;
//import de.sciss.eisenkraut.io.*;
//import de.sciss.eisenkraut.realtime.*;
//import de.sciss.eisenkraut.session.*;
//import de.sciss.eisenkraut.util.*;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.27, 09-Oct-05
 */
public class SuperColliderPlayer
implements	de.sciss.jcollider.Constants,
			MultiTransport.Listener, RealtimeConsumer, LayerManager.Listener,
			EventManager.Processor  //	SessionCollection.Listener
{
	public static boolean			RAZ_DEBUG		= false;

	private static final int		DISKBUF_SIZE	= 32768;	// buffer size in frames
	private static final int		DISKBUF_SIZE_H	= DISKBUF_SIZE >> 1;
	private static final int		GROUP_ROOT		= 0;

	private final Server			server;
	private final Session			doc;
	private final NodeWatcher		nw;

	private final MultiTransport	transport;
	
	private RoutingConfig			oCfg;
	private Context					ct				= null;
	
	private float					volume;
	private double					serverRate;
	private double					sourceRate;
	private double					srcFactor		= 1.0;
//	private long					playOffset		= -1;

	private final Group				grpRoot;
	private final Group[]			grpInPre;
	private final Group[]			grpFilter;
	private final Group[]			grpInPost;
	private final Group[]			grpAccum;
	private final Group				grpOutput;
	private final int[]				syncLayer;				// array index = movie quadrant ; element = layer ID
	private final Synth[]			synthsFilter;
	
// INERTIA
//	private final Synth				synthPhasor;
//	private final Bus				busPhasor;
	
	private final int				numInputChannels;		// XXX static for now
	
	private final OSCResponderNode	trigResp, nEndResp;
	
//	private int						rate;

	// allowed overlap in milliseconds for atoms
	// not to be considered to stand in the shadow
	private static final int		OVERLAP			= 500;
	private static final int		DURCHGEWUNKEN	= OVERLAP << 2;
	
	// atoms must be at least this span [secs] in the future to be launched
	// ; this avoid clicks due to soundfile players being started to early
	// and their buffers not yet properly filled
	private static final double		LATENCY_SAFETY	= 0.1;
	// seconds to calculate in advance
	// ; this span increases for top most layers
	// so there content will tend to be known before
	// the content of lower layers, hence allowing to
	// shadow them for density limitation / transparency maintainance
	// ; this cannot work for loop jump backs, but maybe
	// that's even better so coz it introduces an element
	// of non-order
	private static final double[]	FUTURE_SPAN		= { 8.0, 6.0, 4.0, 2.0 };
	private static final int		TICK_RATE		= 2; // 1;		// must be >= 2 * (1/FUTURE_SPAN)
	
	private final float[]			rateScale;
	private final long[]			absOffset;		// millis
	private final double[]			relOffset;		// seconds
	private final long[]			estLoopDur;		// estimated loop duration (millis)
	
	// this map is sync'ed through synchronized( mapSync )
	// one coll for each layer
	// map: (Integer) nodeID of soundfile player --> RunningAtom
	private final java.util.Map		mapRunningAtoms	= new HashMap();
	private final Object			mapSync			= new Object();
	private final java.util.List[]	collRunningAtoms;
	
	// listeners can watch running atoms being created or destroyed
	private EventManager			elmRAZ			= null; // lazy creation

	// mobile
	private Mobile					mobile			= null;
	
	private final Random			rnd				= new Random( System.currentTimeMillis() );
	
	private final float[]			volumes;

	public SuperColliderPlayer( Session doc, final Server server, NodeWatcher nw,
								RoutingConfig oCfg, float volume )
	throws IOException
	{
		this.server			= server;
		this.doc			= doc;
		this.nw				= nw;
		transport			= doc.getTransport();
		
		assert transport.getNumChannels() == doc.layers.getNumLayers() : "transport channels";
		
		rateScale			= new float[ doc.layers.getNumLayers() ];
		absOffset			= new long[ doc.layers.getNumLayers() ];
		relOffset			= new double[ doc.layers.getNumLayers() ];
		volumes				= new float[ doc.layers.getNumLayers() ];
		estLoopDur			= new long[ doc.layers.getNumLayers() ];
		for( int ch = 0; ch < estLoopDur.length; ch++ ) {
			estLoopDur[ ch ] = 30000;	// XXX initial schnucki
		}
		for( int ch = 0; ch < rateScale.length; ch++ ) {
			rateScale[ ch ]	= 1.0f;
		}
		for( int ch = 0; ch < volumes.length; ch++ ) {
			volumes[ ch ]	= 1.0f;
		}
		synthsFilter		= new Synth[ doc.layers.getNumLayers() ];
		syncLayer			= new int[ doc.layers.getNumLayers() ];
		for( int ch = 0; ch < syncLayer.length; ch++ ) {
			syncLayer[ ch ]	= ch;
		}
		
		collRunningAtoms	= new java.util.List[ doc.layers.getNumLayers() ];
		for( int ch = 0; ch < collRunningAtoms.length; ch++ ) {
			collRunningAtoms[ ch ] = new ArrayList();
		}
		
		final OSCBundle	bndl	= new OSCBundle();
		String			prefix;

// INERTIA
//		numInputChannels	= doc.getMTE().getChannels();	// XXX sync?
numInputChannels = 2;
		sourceRate			= doc.timeline.getRate();		// XXX sync?
		serverRate			= server.getSampleRate();
		
		grpRoot				= Group.basicNew( server );
		grpRoot.setName( "Root_" + doc.getName().substring( 0, Math.min( 6, doc.getName().length() )));
		nw.register( grpRoot );
		bndl.addPacket( grpRoot.newMsg( server.getDefaultGroup() ));
// INERTIA
//		grpRoot.run( false );
		// groups are created front to tail here!
		grpInPre			= new Group[ doc.layers.getNumLayers() ];
		grpFilter			= new Group[ doc.layers.getNumLayers() ];
		grpInPost			= new Group[ doc.layers.getNumLayers() ];
		grpAccum			= new Group[ doc.layers.getNumLayers() ];
		for( int ch = 0; ch < grpAccum.length; ch++ ) {
			prefix			= String.valueOf( (char) (ch + 65) ) + "_";
			grpInPre[ ch ]	= Group.basicNew( server );
			grpInPre[ ch ].setName( prefix + "In" );
			nw.register( grpInPre[ ch ]);
			bndl.addPacket( grpInPre[ ch ].newMsg( grpRoot, kAddToTail ));
			grpFilter[ ch ] = Group.basicNew( server );
			grpFilter[ ch ].setName( prefix + "Fl" );
			nw.register( grpFilter[ ch ]);
			bndl.addPacket( grpFilter[ ch ].newMsg( grpRoot, kAddToTail ));
			grpInPost[ ch ]	= Group.basicNew( server );
			grpInPost[ ch ].setName( prefix + "In" );
			nw.register( grpInPost[ ch ]);
			bndl.addPacket( grpInPost[ ch ].newMsg( grpRoot, kAddToTail ));
			grpAccum[ ch ]	= Group.basicNew( server );
			grpAccum[ ch ].setName( prefix + "Ac" );
			nw.register( grpAccum[ ch ]);
			bndl.addPacket( grpAccum[ ch ].newMsg( grpRoot, kAddToTail ));
		}
		grpOutput			= Group.basicNew( server );
		grpOutput.setName( "Out" );
		nw.register( grpOutput );
		bndl.addPacket( grpOutput.newMsg( grpRoot, kAddToTail ));
// INTERTIA
//		synthPhasor			= Synth.basicNew( "eisen-phasor", server );
//		busPhasor			= Bus.audio( server );
		trigResp			= new OSCResponderNode( server /* .getAddr() */, "/tr", new OSCListener() {
			public void messageReceived( OSCMessage msg, SocketAddress sender, long time )
			{
				final Object		nodeID	= msg.getArg( 0 );
				final int			clock	= ((Number) msg.getArg( 2 )).intValue() + 1;
				final long			pos;
				final RunningAtom	run;

				try {
					synchronized( mapSync ) {
						run	= (RunningAtom) mapRunningAtoms.get( msg.getArg( 0 ));
						if( run == null ) return;

						pos	 = run.fileStartFrame + (clock * DISKBUF_SIZE_H);
					
						server.sendMsg( run.bufDisk.readMsg( run.filePath, pos, DISKBUF_SIZE_H, (clock & 1) == 0 ? 0 : DISKBUF_SIZE_H ));
					}
				}
				catch( IOException e1 ) {
					System.err.println( "messageReceived : " + e1.getClass().getName() + " : " + e1.getLocalizedMessage() );
				}
				catch( ClassCastException e2 ) {
					System.err.println( "messageReceived : " + e2.getClass().getName() + " : " + e2.getLocalizedMessage() );
				}
			}
		});
		
		// UUU XXX can be put in a NodeWatcher listener
		nEndResp			= new OSCResponderNode( server /* .getAddr() */, "/n_end", new OSCListener() {
			public void messageReceived( OSCMessage msg, SocketAddress sender, long time )
			{
				try {
					synchronized( mapSync ) {
						final RunningAtom run = (RunningAtom) mapRunningAtoms.remove( msg.getArg( 0 ));
						if( run != null ) {
							collRunningAtoms[ run.layer ].remove( run );
							dispatchAtomDeath( run );
							run.bufDisk.free();
						}
					}
				}
				catch( IOException e1 ) {
					System.err.println( "messageReceived : " + e1.getClass().getName() + " : " + e1.getLocalizedMessage() );
				}
			}
		});
		
		trigResp.add();
		nEndResp.add();
		
		server.sendBundle( bndl );
		
//		updateSRC();
		setOutputConfig( oCfg, volume );

		transport.addListener( this );
		transport.addRealtimeConsumer( this );
		doc.layers.addListener( this );
	}
	
	public Server getServer()
	{
		return server;
	}
	
	public Group getRootNode()
	{
		return grpRoot;
	}
	
	public int getNumInputChannels()
	{
		return numInputChannels;
	}
	
	public void makeMonitor( Node node, int layer, String busCtrlName, boolean preFilter )
	throws IOException
	{
		nw.register( node );
		node.moveToTail( preFilter ? grpInPre[ layer ] : grpInPost[ layer ]);
		if( ct != null ) {
			node.set( busCtrlName, ct.bussesFilter[ layer ].getIndex() );
		}
	}
	
	public void dispose()
	{
		stopMobile();
	
		if( server.isRunning() ) {
			try {
				trigResp.remove();
				nEndResp.remove();
				disposeContext();
				grpRoot.free();
			}
			catch( IOException e1 ) {
				System.err.println( "dispose : " + e1.getClass().getName() + " : " + e1.getLocalizedMessage() );
			}
		}
		transport.removeRealtimeConsumer( this );
		transport.removeListener( this );
		doc.layers.removeListener( this );
		if( elmRAZ != null ) elmRAZ.dispose();
	}
	
	// sync : attempts shared on DOOR_TRACKS
	public void setOutputConfig( RoutingConfig oCfg, float volume )
	{
		this.oCfg	= oCfg;
		this.volume	= volume;

		if( server.isRunning() ) {
			final int		channels	= transport.getNumChannels();
			final boolean[]	wasRunning	= new boolean[ channels ];
			final float[]	rate		= new float[ channels ];
			final long[]	pos			= new long[ channels ];
			
			for( int ch = 0; ch < channels; ch++ ) {
				wasRunning[ ch ]	= transport.isRunning( ch );
				if( wasRunning[ ch ]) {
					transport.stopAndWait( ch );
					rate[ ch ]		= transport.getRateScale( ch );
					pos[ ch ]		= transport.getPosition( ch );
				}
			}

			if( !doc.bird.attemptShared( Session.DOOR_TRACKS, 500 )) return;
			try {
				rebuildSynths();
			}
			finally {
				doc.bird.releaseShared( Session.DOOR_TRACKS );
			}
			
			for( int ch = 0; ch < channels; ch++ ) {
				if( wasRunning[ ch ]) {
					transport.goPlay( ch, pos[ ch ], rate[ ch ]);
				}
			}
		}
	}

	private void disposeContext()
	{
		if( ct != null ) {
			ct.busAccum.free();
			ct.busInternal.free();
			for( int i = 0; i < ct.bussesFilter.length; i++ ) {
				ct.bussesFilter[ i ].free();
			}
			ct.busPan.free();
			ct = null;
		}
	}
	
	// note: includes call to addChannelMuteMessages
	// sync: caller must have shared on tracks! (no?)
	private void rebuildSynths()
	{
		Synth synth;
	
		synchronized( this ) {
			try {
				grpRoot.deepFree();
				
				disposeContext();
				
				if( oCfg == null ) return;
				
				ct = new Context( numInputChannels, oCfg.numChannels, doc.layers.getNumLayers() );

				final float		orient	= -oCfg.startAngle/360 * oCfg.numChannels;
				final OSCBundle	bndl	= new OSCBundle( 0.0 );

// INERTIA
//				bndl.addPacket( ct.bufDisk.allocMsg() );

				for( int ch = 0; ch < oCfg.numChannels; ch++ ) {
					if( oCfg.mapping[ ch ] < server.getOptions().getNumOutputBusChannels() ) {
						nw.register( ct.synthsRoute[ ch ]);
						bndl.addPacket( ct.synthsRoute[ ch ].newMsg( grpOutput, new String[] {
							"i_aInBus",				   "i_aOutBus"       }, new float[] {
							ct.busPan.getIndex() + ch, oCfg.mapping[ ch ]}));
					}
				}
				for( int ch = 0; ch < ct.numInputChannels; ch++ ) {
					nw.register( ct.synthsPan[ ch ]);
					bndl.addPacket( ct.synthsPan[ ch ].newMsg( grpOutput, new String[] {
						"i_aInBus",					   "i_aOutBus",		     "volume", "orient" }, new float[] {
						ct.busInternal.getIndex() + ch, ct.busPan.getIndex(), volume,   orient  }));
				}

				addChannelPanMessages( bndl );
// INERTIA
//				addChannelMuteMessages( bndl );

				// oki doki ; no let's set up accum and route schnucki
				// Accum :	add the (potentially filtered) atom to the internal bus
				//			and the accum bus
//				synth = Synth.basicNew( "inertia-route" + ct.numInputChannels, server );
//				bndl.addPacket( synth.newMsg( grpOutput, new String[] {
//					"i_aInBus",				 "i_aOutBus" }, new float[] {
//					ct.bussesFilter.getIndex(), ct.busInternal.getIndex() }));
				for( int ch = 0; ch < grpAccum.length; ch++ ) {
					synth = Synth.basicNew( "inertia-accum" + ct.numInputChannels, server );
					nw.register( synth );
					bndl.addPacket( synth.newMsg( grpAccum[ ch ], new String[] {
						"i_aInBus",					   "i_aOutBus",				  "i_aAccumBus" }, new float[] {
						ct.bussesFilter[ ch ].getIndex(), ct.busInternal.getIndex(), ct.busAccum.getIndex() }));
				}

				server.sendBundle( bndl );
				if( !server.sync( 4.0f )) {
					printTimeOutMsg();
				}
			}
			catch( IOException e1 ) {
				System.err.println( "rebuildSynths : " + e1.getClass().getName() + " : " + e1.getLocalizedMessage() );
			}
		}
	}
	
// INERTIA
//	private void addBufferReadMessages( OSCBundle bndl, TrackList tl, int numFrames, int offset )
//	{
//		TrackSpan	ts;
//		int			len;
//	
//		for( int i = 0; i <  tl.size(); i++ ) {
//			ts			= tl.get( i );
//			len			= (int) Math.min( numFrames, ts.span.getLength() );
//			if( len > 0 ) {
//				bndl.addPacket( ct.bufDisk.readMsg( ts.f.getFile().getAbsolutePath(), ts.offset, len, offset ));
//				offset     += len;
//				numFrames  -= len;
//			}
//		}
//		if( numFrames > 0 ) {	// zero the rest
//			bndl.addPacket( ct.bufDisk.fillMsg(
//				offset * ct.bufDisk.getNumChannels(), numFrames * ct.bufDisk.getNumChannels(), 0.0f ));
//		}
//	}

	// sync : attempts shared on DOOR_TRACKS
	private void addChannelMuteMessages( OSCBundle bndl )
	{
// INERTIA
//		Object	o;
//		boolean	muted;
//
//		if( oCfg == null ) return;
//	
//		if( !doc.bird.attemptShared( Session.DOOR_TRACKS, 250 )) return;
//		try {
//			if( doc.tracks.size() != ct.numInputChannels ) {
//				Server.getPrintStream().println( "Input channel mismatch!" );
//				return;
//			}
//			for( int ch = 0; ch < ct.numInputChannels; ch++ ) {
//				o = doc.tracks.get( ch ).getMap().getValue( SessionObject.MAP_KEY_FLAGS );
//				if( (o != null) && (o instanceof Number) ) {
//					muted = (((Number) o).intValue() & (SessionObject.FLAGS_MUTE | SessionObject.FLAGS_VIRTUALMUTE)) != 0;
//				} else {
//					muted = false;
//				}
//				bndl.addPacket( ct.synthsPan[ ch ].runMsg( !muted ));
//			}
//		}
//		finally {
//			doc.bird.releaseShared( Session.DOOR_TRACKS );
//		}
	}

	// sync : attempts shared on DOOR_TRACKS
	private void addChannelPanMessages( OSCBundle bndl )
	{
		if( oCfg == null ) return;

		float		pos, width;
		
		for( int ch = 0; ch < ct.numInputChannels; ch++ ) {
			pos		= (((float) ch - 0.5f) * 2) / ct.numInputChannels;
			width	= 2.0f;
			bndl.addPacket( ct.synthsPan[ ch ].setMsg(
				new String[] { "pos", "width" }, new float[] { pos, width }));
		}
		
// INERTIA
//		Object		o;
//		MapManager	map;
//		float		pos, width;
//		
//		if( oCfg == null ) return;
//	
//		if( !doc.bird.attemptShared( Session.DOOR_TRACKS, 250 )) return;
//		try {
//			if( doc.tracks.size() != ct.numInputChannels ) {
//				Server.getPrintStream().println( "Input channel mismatch!" );
//				return;
//			}
//			for( int ch = 0; ch < ct.numInputChannels; ch++ ) {
//				map	= doc.tracks.get( ch ).getMap();
//				o	= map.getValue( Track.MAP_KEY_PANAZIMUTH );
//				if( (o != null) && (o instanceof Number) ) {
//					pos	= ((Number) o).floatValue() / 180;
//					pos	= pos < 0.0f ? 2.0f - ((-pos) % 2.0f) : pos % 2.0f;
//				} else {
//					pos	= 0.0f;
//				}
//				o	= map.getValue( Track.MAP_KEY_PANSPREAD );
//				if( (o != null) && (o instanceof Number) ) {
//					width		= ((Number) o).floatValue();
//					if( width <= 0.0f ) {
//						width	= Math.max( 1.0f, width + 2.0f );
//					} else {
//						width	= Math.min( 1.0f, width ) * (oCfg.numChannels - 2) + 2.0f;
//					}
//				} else {
//					width	= 2.0f;
//				}
//				bndl.addPacket( ct.synthsPan[ ch ].setMsg(
//					new String[] { "pos", "width" }, new float[] { pos, width }));
//			}
//		}
//		finally {
//			doc.bird.releaseShared( Session.DOOR_TRACKS );
//		}
	}

	private void printTimeOutMsg()
	{
		Server.getPrintStream().println( AbstractApplication.getApplication().getResourceString( "errOSCTimeOut" ));
	}

//	private void updateSRC()
//	{
//		srcFactor	= sourceRate / serverRate;
//// INERTIA
////		doc.getFrame().setSRCEnabled( srcFactor != 1.0 );
//	}

	/**
	 *	Sets the shadowing filter for a given layer.
	 *
	 *	@param	layer	one of the layers, cannot be the topmost layer, i.e. must
	 *					be greater than zero
	 *	@param	when	server action time (OSCBundle time) or zero to execute immediately
	 *	@param	name	the filter name which is used to construct a synthdef name
	 *					<code>&quot;inertia-layer&lt;name&gt;&lt;numberOfChannels&gt;&quot;</code>
	 *					; if <code>null</code> the current filter is removed
	 */
	public void setFilter( int layer, long when, String name )
	{
		if( oCfg == null ) return;
		if( layer < 1 || layer >= synthsFilter.length ) {
			System.err.println( "setFilter : illegal layer idx " + layer );
			return;
		}
	
		OSCBundle bndl = when == 0 ? new OSCBundle() : new OSCBundle( when );
	
		if( synthsFilter[ layer ] != null ) {
			bndl.addPacket( synthsFilter[ layer ].freeMsg() );
			synthsFilter[ layer ] = null;
		}
		
		if( name != null ) {
			synthsFilter[ layer ] = Synth.basicNew( "inertia-layer" + name + ct.numInputChannels, server );
			nw.register( synthsFilter[ layer ]);
			bndl.addPacket( synthsFilter[ layer ].newMsg( grpFilter[ layer ], new String[] {
				"i_aBusA",						  "i_aBusB" }, new float[] {
				ct.bussesFilter[ layer ].getIndex(), ct.busAccum.getIndex() }));
		}
		try {
			server.sendBundle( bndl );
		}
		catch( IOException e1 ) {
			System.err.println( "setFilter : " + e1.getClass().getName() + " : " + e1.getLocalizedMessage() );
		}
	}
	
	public void startMobile()
	{
//System.err.println( "startMobile" );
		if( mobile == null ) {
			mobile = new Mobile();
		} else {
			mobile.restart();
		}
	}
	
	public void stopMobile()
	{
//System.err.println( "stopMobile" );
		if( mobile != null ) {
			mobile.stopMobile();
			mobile = null;
		}
	}
	
//	/**
//	 *	Switches the movie/sound sources
//	 *	of two layers. I.e., when movieX plays
//	 *	to layerA, and movieY plays to layerB,
//	 *	calling this method will make movieX
//	 *	play to layerB, and movieY play to layerA.
//	 *
//	 *	@param	layerA	the first layer of the switch operation
//	 *	@param	layerB	the second layer of the switch operation
//	 */
//	public void switchLayers( int layerA, int layerB )
//	{
//		int movieX = -1, movieY = -1;
//		
//		for( int layer = 0; layer < syncLayer.length; layer++ ) {
//			if( syncLayer[ layer ] == layerA ) {
//				movieX = layer;
//			} else if( syncLayer[ layer ] == layerB ) {
//				movieY = layer;
//			}
//		}
//		if( movieX < 0 || movieY < 0 ) {
//			System.err.println( "switchLayers : can't find layers " + layerA +", " + layerB );
//			return;
//		}
//		
//		syncLayer[ movieX ] = layerB;
//		syncLayer[ movieY ] = layerA;
//	}

	private void requestRaz( int ch, long pos, boolean incRad )
	{
		final int		layer		= syncLayer[ ch ];
		final double	startSec	= (double) pos / sourceRate + LATENCY_SAFETY;
		final double	stopSec		= startSec + FUTURE_SPAN[ layer ] * rateScale[ ch ];
		final double	addToRA		= (double) absOffset[ ch ] / 1000 - relOffset[ ch ];

		Track			t;
		java.util.List	collRaz;
		Atom.Realized	ra;
		RunningAtom		run, run2;
		long			nodeStartTime, nodeStopTime;	// millis abs system time
		long			fileStartFrame;
		Double			d;
		double			decibels;
		
		if( !doc.bird.attemptShared( Session.DOOR_TRACKS + Session.DOOR_MOLECULES, 250 )) {
if( RAZ_DEBUG ) System.err.println( "attemptShared failed" );
			return;
		}
		try {
			synchronized( mapSync ) {
				for( int i = 0; i < doc.tracks.size(); i++ ) {
					t = (Track) doc.tracks.get( i );

					if( incRad ) {
if( RAZ_DEBUG ) System.err.println( "incRad for ch "+ch );
						t.increaseRadiation( ch );
					}
					collRaz = t.getRealizedAtoms( ch, startSec, stopSec );
if( RAZ_DEBUG ) System.err.println( "getRealizedAtoms "+startSec+" ... " +stopSec+" for ch "+ch+" : "+collRaz.size()+" atoms." );

//if( ch == 3 && collRaz.size() > 0 ) System.err.println( "reqRaz "+collRaz.size() );
razierKlinge:		for( int j = 0; j < collRaz.size(); j++ ) {
						ra				= (Atom.Realized) collRaz.get( j );
						d				= (Double) ra.events.get( Atom.PROB_VOLUME );
						decibels		= d == null ? 0.0 : d.doubleValue();
						if( decibels < -36 ) {
if( RAZ_DEBUG ) System.err.println( "quasi mute" );
							continue;	// quasi mute
						}

						nodeStartTime	= (long) ((ra.startTime + addToRA) * 1000);
						nodeStopTime	= (long) ((ra.stopTime + addToRA) * 1000);
						fileStartFrame	= (long) (ra.fileStart * server.getSampleRate());
						run				= new RunningAtom( ra, layer, nodeStartTime, nodeStopTime, fileStartFrame );
						if( (nodeStopTime - nodeStartTime) > (long) (DURCHGEWUNKEN * rnd.nextFloat()) ) {
							for( int k = 0; k < layer; k++ ) {	// check for shadows
								for( int m = 0; m < collRunningAtoms[ k ].size(); m++ ) {
									run2 = (RunningAtom) collRunningAtoms[ k ].get( m );
									// so don't be afraid to get caught in the shadow of love...
									if( (run2.nodeStopTime - run2.nodeStartTime) > (long) (DURCHGEWUNKEN * rnd.nextFloat()) ) {
										if( nodeStartTime <= run2.nodeStartTime ) {
											if( nodeStopTime > run2.nodeStartTime + OVERLAP ) {
												if( rnd.nextBoolean() ) {
													// the hour is late and you know that i wait for no one...
													dispatchAtomShadow( run );
												} else {
													playAtom( ch, run, (float) MathUtil.dBToLinear( decibels ), true );
													dispatchAtomFilter( run );
												}
												continue razierKlinge;
											}
										} else if( run2.nodeStopTime - OVERLAP > nodeStartTime ) {
											if( rnd.nextBoolean() ) {
												dispatchAtomShadow( run );
											} else {
												playAtom( ch, run, (float) MathUtil.dBToLinear( decibels ), true );
												dispatchAtomFilter( run );
											}
											continue razierKlinge;
										}
									}
								}
							}
						}
						// ok, we're on stage. but where the fuck is the crowd??
						playAtom( ch, run, (float) MathUtil.dBToLinear( decibels ), false );
						dispatchAtomLight( run );
					}
				}
			}
		}
		finally {
			doc.bird.releaseShared( Session.DOOR_TRACKS + Session.DOOR_MOLECULES );
		}
	}
	// sync : call with sync on mapSync
	private void playAtom( int ch, RunningAtom run, float volume, boolean filtered )
//	throws IOException
	{
		final Context		ct			= this.ct;
		final Atom.Realized	ra			= run.ra;
	
		if( (ra.file == null) || (ct == null) ) return;
	
		final Synth		synthSFPlay;
		final int		interpolation;
		final float		realRate;
				
		final OSCBundle	bndl1;
		
		final float		time1;
		final float		time2;
		final float		time3;
		
		Double			d;
		Buffer			bufDisk			= null;

volume *= volumes[ ch ];
		
		synthSFPlay		= Synth.basicNew( "inertia-sfplay" + numInputChannels, server );
		bndl1			= new OSCBundle( run.nodeStartTime );
		d				= (Double) ra.events.get( Atom.PROB_PITCH );
		realRate		= (float) (rateScale[ ch ] * (d == null ? 1.0 : MathUtil.pitchToRate( d.doubleValue(), 12 )));
		interpolation	= realRate == 1.0 ? 1 : 4;
		time1			= (float) ra.fadeIn; // / realRate;
		time2			= (float) (ra.stopTime - ra.startTime - ra.fadeOut - ra.fadeIn); // / realRate;
		time3			= (float) ra.fadeOut; // / realRate;

		try {
			bufDisk		= Buffer.readNoUpdate( server, run.filePath, run.fileStartFrame, DISKBUF_SIZE );
			run.bufDisk	= bufDisk;
			nw.register( synthSFPlay );
			bndl1.addPacket( synthSFPlay.newMsg( filtered ? grpInPre[ run.layer ] : grpInPost[ run.layer ], new String[] {
				"i_aInBuf",			 "i_aOutBus",							 "i_gain", "rate",   "i_interpolation", "i_time1", "i_time2", "i_time3" }, new float[] {
				bufDisk.getBufNum(), ct.bussesFilter[ run.layer ].getIndex(), volume,   realRate, interpolation,     time1,     time2,     time3     }));

			server.sendBundle( bndl1 );

			mapRunningAtoms.put( new Integer( synthSFPlay.getNodeID() ), run );
			collRunningAtoms[ run.layer ].add( run );
		}
		catch( IOException e1 ) {
			System.err.println( "playAtom : " + e1.getClass().getName() + " : " + e1.getLocalizedMessage() );
			if( bufDisk != null ) {
				try {
					bufDisk.free();
				}
				catch( IOException e2 ) {}
			}
		}
	}

	private void dispatchAtomLight( RunningAtom run )
	{
		if( elmRAZ != null ) {
			elmRAZ.dispatchEvent( new RunningAtomEvent( this, RunningAtomEvent.LIGHT, System.currentTimeMillis(), run ));
		}
	}

	private void dispatchAtomShadow( RunningAtom run )
	{
		if( elmRAZ != null ) {
			elmRAZ.dispatchEvent( new RunningAtomEvent( this, RunningAtomEvent.SHADOW, System.currentTimeMillis(), run ));
		}
	}

	private void dispatchAtomFilter( RunningAtom run )
	{
		if( elmRAZ != null ) {
			elmRAZ.dispatchEvent( new RunningAtomEvent( this, RunningAtomEvent.FILTER, System.currentTimeMillis(), run ));
		}
	}

	private void dispatchAtomDeath( RunningAtom run )
	{
		if( elmRAZ != null ) {
			elmRAZ.dispatchEvent( new RunningAtomEvent( this, RunningAtomEvent.DEATH, System.currentTimeMillis(), run ));
		}
	}

	public void addListener( RunningAtomListener listener )
	{
		synchronized( this ) {
			if( elmRAZ == null ) {
				elmRAZ = new EventManager( this );
			}
			elmRAZ.addListener( listener );
		}
	}

	public void removeListener( RunningAtomListener listener )
	{
		if( elmRAZ != null ) elmRAZ.removeListener( listener );
	}

// ------------- LayerManager.Listener interface -------------

	public void layersSwitched( LayerManager.Event e )
	{
		syncLayer[ e.getManager().getMovieForLayer( e.getFirstLayer() )]	= e.getFirstLayer();
		syncLayer[ e.getManager().getMovieForLayer( e.getSecondLayer() )]	= e.getSecondLayer();
	}
	
	public void layersFiltered( LayerManager.Event e )
	{
		setFilter( e.getFirstLayer(), 0, (String) e.getParam() );
	}

// ------------- TimelineListener interface -------------

//	public void timelinePositioned( TimelineEvent e ) {}
//
//	public void timelineChanged( TimelineEvent e ) {}	// handled by realtime host
//	
//	public void timelineScrolled( TimelineEvent e ) {}
//	public void timelineSelected( TimelineEvent e ) {}

// ------------- RealtimeConsumer interface -------------

	public RealtimeConsumerRequest createRequest( RealtimeContext ct )
	{
		final RealtimeConsumerRequest request = new RealtimeConsumerRequest( this, ct );

		request.notifyTickStep  = RealtimeConsumerRequest.approximateStep( ct, TICK_RATE );
		request.notifyTicks		= true;
		request.notifyOffhand	= false;

		if( this.sourceRate	   != ct.getSourceRate() ) {
			this.sourceRate		= ct.getSourceRate();
//			updateSRC();
		}

		return request;
	}
	
	public void realtimeTick( RealtimeContext ct, int ch, long currentPos )
	{
		requestRaz( ch, currentPos, false );
	}
	
//	public void offhandTick( RealtimeContext ct, long currentPos ) {}

// ------------- TransportListener interface -------------

	// XXX sync
	public void transportStop( MultiTransport transport, int ch, long pos )
	{
	}
	
	// XXX sync
	public void transportPosition( MultiTransport transport, int ch, long pos, float rateScale )
	{
		transportStop( transport, ch, pos );
		transportPlay( transport, ch, pos, rateScale );
	}
	
	public void transportPlay( MultiTransport transport, int ch, long pos, float rateScale )
	{
		final long now			= System.currentTimeMillis();
	
		this.rateScale[ ch ]	= rateScale;
		estLoopDur[ ch ]		= now - absOffset[ ch ];
		absOffset[ ch ]			= now;
		relOffset[ ch ]			= (double) pos / sourceRate;
		volumes[ ch ]			= doc.layers.getVolume( ch );
		requestRaz( ch, pos, true );
	}
	
	public void transportQuit( MultiTransport transport ) {}

// -------------- SessioCollection.Listener classes --------------
		
//	public void sessionObjectMapChanged( SessionCollection.Event e ) {}
//	public void sessionCollectionChanged( SessionCollection.Event e ) {} // XXX should react (well realtime host does)
//	public void sessionObjectChanged( SessionCollection.Event e ) {}

// -------------- EventManager.Processor interface --------------

	public void processEvent( BasicEvent e )
	{
		RunningAtomListener listener;
		RunningAtom			run;
		
		for( int i = 0; i < elmRAZ.countListeners(); i++ ) {
			listener	= (RunningAtomListener) elmRAZ.getListener( i );
			run			= ((RunningAtomEvent) e).run;
			switch( e.getID() ) {
			case RunningAtomEvent.LIGHT:
				listener.atomInTheLight( run );
				break;
			case RunningAtomEvent.SHADOW:
				listener.atomInTheShadow( run );
				break;
			case RunningAtomEvent.FILTER:
				listener.atomInTheFilter( run );
				break;
			case RunningAtomEvent.DEATH:
				listener.atomInTheVoid( run );
				break;
			default:
				assert false : e.getID();
			}
		}
	}

// -------------- internal classes --------------
	
	public static class RunningAtom
	{
		public final Atom.Realized	ra;
		private Buffer				bufDisk;
		private final String		filePath;
		private final long			fileStartFrame;
		public final long			nodeStartTime, nodeStopTime;
		public final int			layer;
		
		private RunningAtom( Atom.Realized ra, int layer, long nodeStartTime,
							 long nodeStopTime, long fileStartFrame )
		{
			this.ra				= ra;
//			this.bufDisk		= bufDisk;
			filePath			= ra.file.getAbsolutePath();
			this.fileStartFrame	= fileStartFrame;
			this.nodeStartTime	= nodeStartTime;
			this.nodeStopTime	= nodeStopTime;
			this.layer			= layer;
		}
	}

	private class Context
	{
		private final Synth[]	synthsPan;
		private final Synth[]	synthsRoute;
		private final Bus		busAccum;
		private final Bus[]		bussesFilter;
		private final Bus		busInternal;
		private final Bus		busPan;
		
		private final int		numInputChannels;
	
		private Context( int numInputChannels, int numConfigOutputs, int numLayers )
		{
			this.numInputChannels	= numInputChannels;
		
			synthsPan				= new Synth[ numInputChannels ];
			for( int i = 0; i < numInputChannels; i++ ) {
				synthsPan[ i ]		= Synth.basicNew( "inertia-pan" + numConfigOutputs, server );
			}
			synthsRoute				= new Synth[ numConfigOutputs ];
			for( int i = 0; i < numConfigOutputs; i++ ) {
				synthsRoute[ i ]	= Synth.basicNew( "inertia-route1", server );
			}
			busAccum				= Bus.audio( server, numInputChannels );
			bussesFilter			= new Bus[ numLayers ];
			for( int i = 0; i < numLayers; i++ ) {
				bussesFilter[ i ]	= Bus.audio( server, numInputChannels );
			}
			busInternal				= Bus.audio( server, numConfigOutputs );
			busPan					= Bus.audio( server, numConfigOutputs );
		}
	}
	
	private static class RunningAtomEvent
	extends BasicEvent
	{
		private static final int LIGHT	= 0;
		private static final int SHADOW	= 1;
		private static final int FILTER	= 2;
		private static final int DEATH	= 3;
		
		private final RunningAtom	run;
	
		private RunningAtomEvent( Object source, int ID, long when, RunningAtom run )
		{
			super( source, ID, when );
			
			this.run = run;
		}

		public boolean incorporate( BasicEvent oldEvent )
		{
			return false;
		}
	}
	
	public interface RunningAtomListener
	{
		public void atomInTheLight( RunningAtom run );
		public void atomInTheShadow( RunningAtom run );
		public void atomInTheFilter( RunningAtom run );
		public void atomInTheVoid( RunningAtom run );
	}
	
	private class Mobile
	implements ActionListener
	{
		private static final int		MIN_DELAY	= 5000;
		private static final int		MAX_DELAY	= 60000;
		private static final int		MAX_STEP	= 10000;
	
		private javax.swing.Timer		timer;
		private final Random			rnd;
		private int						delay;
		private int						lastSwitch	= -1;
	
		private Mobile()
		{
			super();
		
			rnd		= new Random( System.currentTimeMillis() );
			delay	= (int) (expo() * (MAX_DELAY - MIN_DELAY) + MIN_DELAY);
			timer	= new javax.swing.Timer( 0, this );
			timer.setRepeats( false );
			reschedule();
		}
	
		public void stopMobile()
		{
			timer.stop();
		}
		
		public void restart()
		{
			timer.restart();
		}
		
		public void actionPerformed( ActionEvent schnucki3000 )
		{
//System.err.println( "boing" );
			int layer = rnd.nextInt( syncLayer.length - 1 );
			if( layer == lastSwitch ) {
				layer += rnd.nextInt( 2 ) == 0 ? -1 : 1;
				if( layer < 0 ) layer = syncLayer.length - 2;
				else if( layer == syncLayer.length - 1 ) layer--;
			}
			doc.layers.switchLayers( this, layer, layer + 1 );
			lastSwitch = layer;
		
			reschedule();
		}
		
		public double expo()
		{
			final double doubleYouBushiThatSonOfABitch = rnd.nextDouble();
			return doubleYouBushiThatSonOfABitch * doubleYouBushiThatSonOfABitch;
		}
		
		
		public void reschedule()
		{
			final int schnucki6000 = (int) (expo() * (rnd.nextBoolean() ? MAX_STEP : -MAX_STEP));
			delay				= Math.max( MIN_DELAY, Math.min( MAX_DELAY, delay + schnucki6000 ));
//			final long	target	= System.currentTimeMillis() + delay;
//System.err.println( "Scheduled for "+((float) delay/1000) );
			timer.setInitialDelay( delay ); // target );
			timer.restart();
		}
	}
}