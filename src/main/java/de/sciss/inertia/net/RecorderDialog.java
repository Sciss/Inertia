//
//  RecorderDialog.java
//  Inertia
//
//  Created by Hanns Holger Rutz on 06.10.05.
//  Copyright 2005 __MyCompanyName__. All rights reserved.
//

package de.sciss.inertia.net;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.prefs.*;
import javax.swing.*;
import javax.swing.event.*;

import de.sciss.inertia.*;
import de.sciss.inertia.gui.*;
import de.sciss.inertia.io.*;
import de.sciss.inertia.util.*;

import de.sciss.app.*;
import de.sciss.gui.*;
import de.sciss.io.*;
import de.sciss.jcollider.*;
import de.sciss.net.*;

/**
 *	@version	0.26	08-Oct-05
 */
public class RecorderDialog
extends BasicPalette
implements ServerListener, NodeListener, Constants, JitterClient.Listener
{
	private static final int DISKBUF_SIZE		= 32768;	// buffer size in frames

	private final Preferences				audioPrefs;
	private final PrefComboBox				ggRecordConfig;
	private RoutingConfig					rCfg;
	private float							volume				= 1.0f;
	private Server							server				= null;
	private final java.util.List			collMeters			= new ArrayList();
	private final JPanel					meterPanel;
	private float[]							meterValues			= new float[ 0 ];
	private final actionRecordClass			actionRecord;
	private final actionStopClass			actionStop;
	private final actionAbortClass			actionAbort;
	private final actionStoreJitterCmdClass	actionStoreJitterCmd;
	private Context							ct					= null;
	private final javax.swing.Timer			meterTimer;
	private final RecorderDialog			enc_this			= this;
	private final Main						root;
	private final OSCListener				cSetNRespBody;
	private NodeWatcher						nw;
	private final PrefPathField				ggAudioFile;
	private final PrefPathField				ggCollFile;
	private final AudioFileFormatPane		affp;
	private final TimeoutTimer				timeoutTimer		= new TimeoutTimer( 4000 );
	private boolean							isRecording			= false;
	private long							recAbsOffset;
	private final java.util.List			collJitSync			= new ArrayList();

	public RecorderDialog( Main root )
	{
		super( "Recorder" );

		this.root	= root;

		final Application			app				= AbstractApplication.getApplication();
		final SpringPanel			afPane;
		final Container				cp				= getContentPane();
		final JPanel				butPane;
		final WindowAdapter			winListener;

		audioPrefs	= app.getUserPrefs().node( PrefsUtil.NODE_AUDIO );
		
		afPane		= new SpringPanel();
		ggAudioFile = new PrefPathField( PathField.TYPE_OUTPUTFILE, "Choose Record Output Audio File" );
		affp		= new AudioFileFormatPane( AudioFileFormatPane.FORMAT | AudioFileFormatPane.ENCODING );
		affp.automaticFileSuffix( ggAudioFile );
		afPane.gridAdd( new JLabel( "Audio File", JLabel.RIGHT ), 0, 0 );
		afPane.gridAdd( ggAudioFile, 1, 0 );
		afPane.gridAdd( new JLabel( getResourceString( "labelFormat" ), JLabel.RIGHT ), 0, 1 );
		afPane.gridAdd( affp, 1, 1 );
		afPane.gridAdd( new JLabel( "Record Source", JLabel.RIGHT ), 0, 2 );
		ggRecordConfig	= new PrefComboBox();
		afPane.gridAdd( ggRecordConfig, 1, 2 );
		ggCollFile	= new PrefPathField( PathField.TYPE_OUTPUTFILE, "Choose Record Output .Coll File" );
		afPane.gridAdd( new JLabel( "Max Coll File", JLabel.RIGHT ), 0, 3 );
		afPane.gridAdd( ggCollFile, 1, 3 );
		
		ggAudioFile.setPreferences( audioPrefs, "rec-audiofile" );
		ggCollFile.setPreferences( audioPrefs, "rec-collfile" );
		
		refillConfigs();
		ggRecordConfig.setPreferences( audioPrefs, PrefsUtil.KEY_RECORDCONFIG );
		afPane.makeCompactGrid( false );

		butPane				= new JPanel( new FlowLayout( FlowLayout.TRAILING ));
		actionRecord		= new actionRecordClass();
		actionStop			= new actionStopClass();
		actionAbort			= new actionAbortClass();
		actionStoreJitterCmd= new actionStoreJitterCmdClass();
//butPane.add( new JButton( ));
		butPane.add( new JButton( actionAbort ));
		butPane.add( new JButton( actionRecord ));
		butPane.add( new JButton( actionStop ));
		
		meterPanel	= new JPanel();
		meterPanel.setLayout( new BoxLayout( meterPanel, BoxLayout.Y_AXIS ));
//meterPanel.setLayout( new BoxLayout( meterPanel, BoxLayout.X_AXIS ));	// XXX
		
		cp.setLayout( new BorderLayout() );
		cp.add( afPane, BorderLayout.NORTH );
		cp.add( meterPanel, BorderLayout.CENTER );
		cp.add( butPane, BorderLayout.SOUTH );

		GUIUtil.setDeepFont( cp, GraphicsUtil.smallGUIFont );

		// ---- listeners -----
		
		winListener = new WindowAdapter() {
			public void windowClosing( WindowEvent e ) {
				disposeRecorder();
			}
		};
		this.addWindowListener( winListener );
		
		root.superCollider.addListener( this );
		root.jitter.addListener( this );

		new DynamicAncestorAdapter( new DynamicPrefChangeManager( audioPrefs, new String[]
			{ PrefsUtil.KEY_RECORDCONFIG },
			new LaterInvocationManager.Listener() {
				public void laterInvocation( Object o )
				{
					if( server != null ) createRecordConfig();
				}
			}
		)).addTo( getRootPane() );
		
		cSetNRespBody = new OSCListener() {
			public void messageReceived( OSCMessage msg, SocketAddress sender, long time )
			{
				final int		busIndex	= ((Number) msg.getArg( 0 )).intValue();
				final Context	ct			= enc_this.ct;	// quasi sync
				
				if( (ct == null) || (busIndex != ct.busMeter.getIndex()) ) return;

//				final long		now			= System.currentTimeMillis();
				
				try {
					final int numValues = ((Number) msg.getArg( 1 )).intValue();
					if( numValues != meterValues.length ) {
						meterValues = new float[ numValues ];
					}
						
					for( int i = 0; i < meterValues.length; i++ ) {
						meterValues[ i ] = ((Number) msg.getArg( i + 2 )).floatValue();
					}
						
					meterUpdate();
				}
//				catch( IOException e1 ) {
//					printError( "Receive /c_setn", e1 );
//				}
				catch( ClassCastException e2 ) {
					printError( "Receive /c_setn", e2 );
				}
			}
		};

		// ---- meters -----

		meterTimer	= new javax.swing.Timer( 33, new ActionListener() {
			public void actionPerformed( ActionEvent e )
			{
				try {
					if( (server != null) && (ct != null) ) server.sendMsg( ct.meterBangMsg );
				}
				catch( IOException e1 ) {}
			}
		});
		
		setDefaultCloseOperation( DO_NOTHING_ON_CLOSE );
		root.addComponent( Main.COMP_RECORDER, this );

        HelpGlassPane.setHelp( getRootPane(), "Recorder" );

		server	= root.superCollider.getServer();
		nw		= root.superCollider.getNodeWatcher();
		nw.addListener( this );

		init( root );
	}

	private void meterUpdate()
	{
		synchronized( collMeters ) {
			final int numMeters = Math.min( collMeters.size(), meterValues.length >> 1 );
			for( int i = 0, j = 0; i < numMeters; i++ ) {
				((LevelMeter) collMeters.get( i )).setPeakAndRMS( meterValues[ j++ ], meterValues[ j++ ]);
			}
		}
	}
	
	private void createRecordConfig()
	{
		final String cfgName	= audioPrefs.get( PrefsUtil.KEY_RECORDCONFIG, null );

		RoutingConfig rCfg		= null;

		try {
			if( cfgName != null && audioPrefs.node( PrefsUtil.NODE_OUTPUTCONFIGS ).nodeExists( cfgName )) {
				rCfg	= new RoutingConfig( audioPrefs.node( PrefsUtil.NODE_OUTPUTCONFIGS ).node( cfgName ));
			}
		}
		catch( BackingStoreException e1 ) {
			printError( "createRecordConfig", e1 );
		}
		
		this.rCfg	= rCfg;
		this.volume	= volume;

		if( server.isRunning() ) {
			rebuildSynths();
			actionRecord.setEnabled( true );
		}
	}
	
	private void disposeAll()
	{
		meterTimer.stop();
		meterPanel.removeAll();
		synchronized( collMeters ) {
			collMeters.clear();
		}
		meterPanel.revalidate();
		disposeContext();
	}
	
	private void rebuildSynths()
	{
		Synth		synth;
		LevelMeter	meter;
	
		try {
			disposeAll();
			
			if( rCfg == null ) return;
			
			ct = new Context( rCfg.numChannels );

			final OSCBundle	bndl	= new OSCBundle( 0.0 );

			for( int ch = 0; ch < rCfg.numChannels; ch++ ) {
				bndl.addPacket( ct.synthsRoute[ ch ].newMsg( ct.grpRoot, new String[] {
					"i_aInBus",		    "i_aOutBus"       }, new float[] {
					rCfg.mapping[ ch ], ct.busInternal.getIndex() + ch }, kAddToHead ));
				nw.register( ct.synthsRoute[ ch ]);
				bndl.addPacket( ct.synthsMeter[ ch ].newMsg( ct.grpRoot, new String[] {
					"i_aInBus",					   "i_kOutBus" }, new float[] {
					ct.busInternal.getIndex() + ch, ct.busMeter.getIndex() + (ch << 1) }, kAddToTail ));
				nw.register( ct.synthsMeter[ ch ]);
				meter = new LevelMeter( LevelMeter.HORIZONTAL );
//				meter.setHoldDuration( -1 );
//meter.setRMSPainted( false );
				synchronized( collMeters ) {
					collMeters.add( meter );
				}
				meterPanel.add( meter );
			}
pack();
//			meterPanel.revalidate();

			server.sendBundle( bndl );
			if( !server.sync( 4.0f )) {
				printTimeOutMsg();
			}

			ct.cSetNResp.add();
			meterTimer.start();
		}
		catch( IOException e1 ) {
			printError( "rebuildSynths", e1 );
		}
	}

	private void printTimeOutMsg()
	{
		Server.getPrintStream().println( AbstractApplication.getApplication().getResourceString( "errOSCTimeOut" ));
	}

	private void disposeRecorder()
	{
		setVisible( false );
		disposeAll();
		root.superCollider.removeListener( this );
		root.jitter.removeListener( this );
		dispose();	// JFrame
		root.addComponent( Main.COMP_RECORDER, null );
	}

	private void disposeContext()
	{
		if( ct != null ) {
			try {
				ct.dispose();
			}
			catch( IOException e1 ) {
				printError( "disposeContext", e1 );
			}
			ct = null;
		}
	}

	public void refillConfigs()
	{
		try {
			final String[] cfgNames = audioPrefs.node( PrefsUtil.NODE_OUTPUTCONFIGS ).childrenNames();
			ggRecordConfig.removeAllItems();
			for( int i = 0; i < cfgNames.length; i++ ) {
				ggRecordConfig.addItem( cfgNames[ i ]);
			}
		}
		catch( BackingStoreException e1 ) {
			printError( "refillConfigs", e1 );
		}
	}
	
	private static void printError( String name, Throwable t )
	{
		System.err.println( name + " : " + t.getClass().getName() + " : " + t.getLocalizedMessage() );
	}

	private String getResourceString( String key )
	{
		return AbstractApplication.getApplication().getResourceString( key );
	}
	
	private void serverStarted()
	{
//		try {
			createRecordConfig();
//		}
//		catch( IOException e1 ) {
//			printError( "Recorder synth init", e1 );
//		}
	}
	
	private void serverStopped()
	{
		disposeAll();
	
//		LevelMeter meter;
//	
//		synchronized( collMeters ) {
//			for( int i = 0; i < collMeters.size(); i++ ) {
//				meter = (LevelMeter) collMeters.get( i );
//				meter.setPeakAndRMS( 0.0f, 0.0f );
//				meter.clearHold();
//			}
//		}
//		
		actionRecord.setEnabled( false );
		actionStop.setEnabled( false );
		actionAbort.setEnabled( false );
	}
	
// ------------- JitterClient.Listener interface -------------

	public void moviePlay( JitterClient.Event e )
	{
		if( isRecording ) {
			collJitSync.add( e );
		}
	}

// ------------- ServerListener interface -------------

	public void serverAction( ServerEvent e )
	{
//		if( server == null ) return;
	
		switch( e.getID() ) {
		case ServerEvent.STOPPED:
			serverStopped();
			break;
			
		case ServerEvent.RUNNING:	// XXX sync
			server	= e.getServer();
			nw		= root.superCollider.getNodeWatcher();
			nw.addListener( this );
			serverStarted();
			break;
			
		default:
			break;
		}
	}
	
// ------------- NodeListener interface -------------

	public void nodeAction( NodeEvent e )
	{
		if( (ct == null) || (e.getNode() != ct.synthDiskOut) ) return;
	
		switch( e.getID() ) {
		case NodeEvent.GO:
			collJitSync.clear();
			recAbsOffset	= e.getWhen();
			isRecording		= true;
			timeoutTimer.stop();
			actionStop.setEnabled( true );
			actionAbort.setEnabled( true );
			break;

		case NodeEvent.END:
			isRecording	= false;
			timeoutTimer.stop();
			actionRecord.setEnabled( true );
			ggAudioFile.setPath( ggAudioFile.getPath() );	// turns blue, XXX actually we should do a variation on the name
System.err.println( "Got "+collJitSync.size()+" movie seeks during record" );
actionStoreJitterCmd.actionPerformed( null );
			break;

		default:
			break;
		}
	}

// ------------- internal classes -------------

	private class Context
	{
		private OSCResponderNode	cSetNResp	= null;
		private Group				grpRoot		= null;
		private final Synth			synthDiskOut;	// one multi-channel disk out synth
		private final Synth[]		synthsRoute;	// for each config channel one route
		private final Synth[]		synthsMeter;	// for each config channel a meter control
		private Buffer				bufDisk		= null;
		private Bus					busInternal	= null;
		private Bus					busMeter	= null;		// control rate, two channels per channel
			
		private final OSCMessage	meterBangMsg;	// /c_getn for the meter values
	
		/*
		 *	@throws	IOException	if the server ran out of busses
		 *						or buffers
		 */
		private Context( int numConfigChannels )
		throws IOException
		{
			try {
	//			this.numInputChannels	= numInputChannels;
				synthDiskOut			= Synth.basicNew( "inertia-sfrec" + numConfigChannels, server );
				synthsRoute				= new Synth[ numConfigChannels ];
				synthsMeter				= new Synth[ numConfigChannels ];
				for( int i = 0; i < numConfigChannels; i++ ) {
					synthsRoute[ i ]	= Synth.basicNew( "inertia-route1", server );
					synthsMeter[ i ]	= Synth.basicNew( "inertia-recmeter", server );
				}
//				bufDisk					= new Buffer( server, DISKBUF_SIZE, numConfigChannels );
				bufDisk					= Buffer.alloc( server, DISKBUF_SIZE, numConfigChannels );
				busInternal				= Bus.audio( server, numConfigChannels );
				busMeter				= Bus.control( server, numConfigChannels << 1 );

				if( (bufDisk == null) || (busInternal == null) || (busMeter == null) ) {
					throw new IOException( "Server ran out of buffers or busses!" );
				}

				meterBangMsg			= new OSCMessage( "/c_getn", new Object[] {
					new Integer( busMeter.getIndex() ), new Integer( busMeter.getNumChannels() )});

				cSetNResp				= new OSCResponderNode( server.getAddr(), "/c_setn", cSetNRespBody );
				grpRoot					= new Group( server.getDefaultGroup(), kAddToTail );
				grpRoot.setName( "Recorder" );
				nw.register( grpRoot );
			}
			catch( IOException e1 ) {
				dispose();
				throw e1;
			}
		}

		private void dispose()
		throws IOException
		{
			IOException e11 = null;
		
			if( cSetNResp != null ) {
				try {
					cSetNResp.remove();
					cSetNResp = null;
				}
				catch( IOException e1 ) {
					e11 = e1;
				}
			}
			if( bufDisk != null ) {
				try {
					bufDisk.free();
				}
				catch( IOException e1 ) {
					e11 = e1;
				}
			}
			if( busInternal != null ) busInternal.free();
//			busPan.free();
			if( busMeter != null ) busMeter.free();
			if( grpRoot != null ) grpRoot.free();
			if( e11 != null ) throw e11;
		}
	}
	
	private class actionStoreJitterCmdClass
	extends AbstractAction
	{
		private actionStoreJitterCmdClass()
		{
			super( "Store Jitter" );
		}
		
		public void actionPerformed( ActionEvent ae )
		{
			if( collJitSync.isEmpty() ) {
				System.err.println( "Jitter Sync List is empty!" );
				return;
			}
			
			// XXX ok, a hard coded presumption for now : 8 fps film speed
			// , equal to 125 milliseconds frame duration
			
			try {
				JitterClient.Event		e;
				int						maxLayNum	= 4;	// XXX
				JitterClient.Event[]	current		= new JitterClient.Event[maxLayNum];
				long					now			= recAbsOffset;
				int						frame;
				final File				f			= ggCollFile.getPath(); // new File( dir, file );
				if( f.exists() ) f.delete();
				final RandomAccessFile	raf			= new RandomAccessFile( f, "rw" );
				int						collCounter	= 0;
				
				for( int i = 0; i < maxLayNum; i++ ) {
					raf.writeBytes( String.valueOf( collCounter++ ) + ", 0 read black.mov;\n" );
				}

				// prompt text file name
				for( int i = 0; i < collJitSync.size(); i++ ) {
					e = (JitterClient.Event) collJitSync.get( i );
					while( now < e.getWhen() ) {
						for( int j = maxLayNum - 1; j >= 0; j-- ) {	// backwards for mr. max
							if( current[ j ] == null ) {
								raf.writeBytes( String.valueOf( collCounter++ ) + ", " +String.valueOf( j ) +
									" frame 0;\n" );	// schwarz zu beginn
							} else {
								frame = (int) ((now - current[ j ].getWhen() + (current[ j ].getMovieTime() * 1000)) / 125);	// ignores movie rate for now XXX
								raf.writeBytes( String.valueOf( collCounter++ ) + ", " + String.valueOf( j ) +
									" frame " + String.valueOf( frame ) + ";\n" );
							}
						}
						now += 125;
					}
					if( e.getChannel() >= maxLayNum ) {
						maxLayNum = e.getChannel() + 1;
						JitterClient.Event[] oldSchnucki = current;
						current = new JitterClient.Event[ maxLayNum ];
						System.arraycopy( oldSchnucki, 0, current, 0, oldSchnucki.length );
					}
					current[ e.getChannel() ] = e;
					raf.writeBytes( String.valueOf( collCounter++ ) + ", " + String.valueOf( e.getChannel() ) +
						" read " + e.getMovieName() + ";\n" );
				}
				
				raf.close();
			}
			catch( IOException e1 ) {
				printError( "Saving Jitter Sync File", e1 );
			}
		}
	}

	private class actionRecordClass
	extends AbstractAction
	{
		private actionRecordClass()
		{
			super( "Record" );
			setEnabled( false );
		}
		
		public void actionPerformed( ActionEvent e )
		{
			if( (server == null) || !server.isRunning() || (ct == null) ) return;
			
			final OSCBundle		bndl	= new OSCBundle();
			final OSCMessage	msgWrite;
			
			try {
//				server.dumpOutgoingOSC( kDumpBoth );
				bndl.addPacket( ct.bufDisk.closeMsg() );
				msgWrite = ct.bufDisk.writeMsg( ggAudioFile.getPath().getAbsolutePath(), affp.getFormatString(),
								affp.getEncodingString(), 0, 0, true );
				bndl.addPacket( msgWrite );
				if( server.sendBundleSync( bndl, msgWrite.getName(), 4 )) {
					nw.register( ct.synthDiskOut );
					server.sendMsg( ct.synthDiskOut.newMsg( ct.grpRoot, new String[] { "i_aInBus", "i_aOutBuf" },
								new float[] { ct.busInternal.getIndex(), ct.bufDisk.getBufNum() }, kAddToTail ));
					timeoutTimer.stop();
					timeoutTimer.setMessage( "Failed to initialize recording synth" );
					timeoutTimer.setActions( new Action[] { actionRecord });
					timeoutTimer.start();
					timeoutTimer.enable( false );
				} else {
					System.err.println( "Failed to initialize recording buffer" );
				}
			}
			catch( IOException e1 ) {
				printError( getValue( NAME ).toString(), e1 );
			}
		}
	}

	private class actionStopClass
	extends AbstractAction
	{
		private actionStopClass()
		{
			super( "Stop" );
			setEnabled( false );
		}

		public void actionPerformed( ActionEvent e )
		{
			if( (server == null) || !server.isRunning() || (ct == null) ) return;
			
			final OSCBundle bndl = new OSCBundle();

			try {
				bndl.addPacket( ct.synthDiskOut.freeMsg() );
				bndl.addPacket( ct.bufDisk.closeMsg() );
				server.sendBundle( bndl );
				timeoutTimer.stop();
				timeoutTimer.setMessage( "Failed to stop recording synth" );
				timeoutTimer.setActions( new Action[] { actionStop, actionAbort });
				timeoutTimer.start();
				timeoutTimer.enable( false );
			}
			catch( IOException e1 ) {
				printError( getValue( NAME ).toString(), e1 );
			}
		}
	}

	private class actionAbortClass
	extends AbstractAction
	{
		private actionAbortClass()
		{
			super( "Abort" );
			setEnabled( false );
		}

		public void actionPerformed( ActionEvent e )
		{
			actionStop.actionPerformed( e );
			if( !ggAudioFile.getPath().delete() ) {
				System.err.println( "Couldn't delete file : "+ggAudioFile.getPath() );
			}
		}
	}
	
	private static class TimeoutTimer
	extends javax.swing.Timer
	implements ActionListener
	{
		private String		errorMsg;
		private Action[]	actions;
	
		private TimeoutTimer( int timeOutMillis )
		{
			super( timeOutMillis, null );
			addActionListener( this );
			setRepeats( false );
		}
		
		private void setMessage( String errorMsg )
		{
			this.errorMsg = errorMsg;
		}
		
		private void setActions( Action[] actions )
		{
			this.actions	= actions;
		}
		
		public void actionPerformed( ActionEvent e )
		{
			System.err.println( errorMsg );
			enable( true );
		}
		
		private void enable( boolean onOff )
		{
			for( int i = 0; i < actions.length; i++ ) {
				actions[ i ].setEnabled( onOff );
			}
		}
	}
}