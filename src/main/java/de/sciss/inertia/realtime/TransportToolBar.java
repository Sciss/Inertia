/*
 *  TransportToolBar.java
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
 *  Change log:
 *		07-Aug-05	copied from de.sciss.eisenkraut.realtime.TransportToolBar
 */

package de.sciss.inertia.realtime;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.text.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;

// INERTIA
//import de.sciss.eisenkraut.*;
//import de.sciss.eisenkraut.edit.*;
//import de.sciss.eisenkraut.gui.*;
//import de.sciss.eisenkraut.session.*;
//import de.sciss.eisenkraut.timeline.*;
//import de.sciss.eisenkraut.util.*;
import de.sciss.inertia.gui.*;
import de.sciss.inertia.session.*;
import de.sciss.inertia.timeline.*;
import de.sciss.util.LockManager;
import de.sciss.app.Document;

import de.sciss.app.AbstractApplication;
import de.sciss.app.DynamicAncestorAdapter;
import de.sciss.app.DynamicListening;

import de.sciss.gui.DoClickAction;
import de.sciss.gui.GUIUtil;
import de.sciss.gui.HelpGlassPane;
import de.sciss.gui.KeyedAction;

import de.sciss.io.Span;

/**
 *	A GUI component showing
 *	basic transport gadgets. This class
 *	invokes the appropriate methods in the
 *	<code>Transport</code> class when these
 *	gadgets are clicked.
 *	<p><pre>
 *	Keyb.shortcuts :	space or numpad-0 : play / stop
 *						G : go to time
 *						shift + (alt) + space : play half or double speed
 *						numpad 1 / 2 : rewind / fast forward
 *	</pre>
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.5, 02-Aug-05
 *
 *	@todo		cueing sometimes uses an obsolete start position.
 *				idea: cue speed changes with zoom level
 *
 *	@todo		when palette is opened when transport is running(?)
 *				realtime listener is not registered (only after timeline change)
 */
public class TransportToolBar
extends Box
implements  TimelineListener, MultiTransport.Listener, RealtimeConsumer,
			DynamicListening
{
	private final MultiTransport	transport;
	private final Timeline			timeline;
	private final Document			doc;
	private final LockManager		lm;
	private final int				doors;
    
	private final JButton			ggPlay, ggStop;
	private final JToggleButton		ggLoop;
	private final actionLoopClass	actionLoop;

	private final JToolBar			toolBar;
	private final TimeLabel			lbTime;
	private static final Font		fntMono			= new Font( "monospaced", Font.PLAIN, 12 );
	
	private int						rate;
	private int						customGroup		= 3;

	private static final MessageFormat   msgFormat =
		new MessageFormat( "{0,number,integer}:{1,number,00.000}", Locale.US );		// XXX US locale

	// forward / rewind cueing
	private boolean					isCueing		= false;
	private int						cueStep;
	private final javax.swing.Timer	cueTimer;
	private long					cuePos;
	
	private final JComboBox			ggChannel;

	/**
	 *	Creates a new transport palette. Other classes
	 *	may wish to add custom gadgets using <code>addButton</code>
	 *	afterwards.
	 *
	 *	@param	doc		Session Session
	 */
	public TransportToolBar( MultiTransport transport, final Timeline timeline, final LockManager lm,
							 final int doors, final Document doc )
	{
		super( BoxLayout.X_AXIS );
		
		this.transport	= transport;
		this.timeline	= timeline;
		this.lm			= lm;
		this.doors		= doors;
		this.doc		= doc;
		rate			= timeline.getRate();

		final AbstractAction	actionPlay, actionStop, actionGoToTime;
		final JButton			ggFFwd, ggRewind;
		final ListLabel	channelRenderer;
		final InputMap			imap		= this.getInputMap( JComponent.WHEN_IN_FOCUSED_WINDOW );
		final ActionMap			amap		= this.getActionMap();

// INERTIA
//		toolBar			= new ToolBar( root, ToolBar.HORIZONTAL );
toolBar	= new JToolBar( JToolBar.HORIZONTAL );
toolBar.setFloatable( false );

        ggRewind		= new JButton();
		GraphicsUtil.setToolIcons( ggRewind, GraphicsUtil.createToolIcons( GraphicsUtil.ICON_REWIND ));
		ggRewind.addChangeListener( new CueListener( ggRewind, -100 ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_NUMPAD1, 0, false ), "startrwd" );
		amap.put( "startrwd", new actionCueClass( ggRewind, true ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_NUMPAD1, 0, true ), "stoprwd" );
		amap.put( "stoprwd", new actionCueClass( ggRewind, false ));

		actionStop		= new actionStopClass();
        ggStop			= new JButton( actionStop );
		GraphicsUtil.setToolIcons( ggStop, GraphicsUtil.createToolIcons( GraphicsUtil.ICON_STOP ));

		actionPlay		= new actionPlayClass();
        ggPlay			= new JButton( actionPlay );
		GraphicsUtil.setToolIcons( ggPlay, GraphicsUtil.createToolIcons( GraphicsUtil.ICON_PLAY ));

		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_SPACE, 0 ), "playstop" );
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_SPACE, KeyEvent.SHIFT_MASK ), "playstop" );
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_SPACE, KeyEvent.SHIFT_MASK | KeyEvent.ALT_MASK ), "playstop" );
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_NUMPAD0, 0 ), "playstop" );
		amap.put( "playstop", new actionTogglePlayStopClass() );

        ggFFwd			= new JButton();
		GraphicsUtil.setToolIcons( ggFFwd, GraphicsUtil.createToolIcons( GraphicsUtil.ICON_FASTFORWARD ));
		ggFFwd.addChangeListener( new CueListener( ggFFwd, 100 ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_NUMPAD2, 0, false ), "startfwd" );
		amap.put( "startfwd", new actionCueClass( ggFFwd, true ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_NUMPAD2, 0, true ), "stopfwd" );
		amap.put( "stopfwd", new actionCueClass( ggFFwd, false ));

		actionLoop		= new actionLoopClass();
		ggLoop			= new JToggleButton( actionLoop );
		GraphicsUtil.setToolIcons( ggLoop, GraphicsUtil.createToolIcons( GraphicsUtil.ICON_LOOP ));
		GUIUtil.createKeyAction( ggLoop, KeyStroke.getKeyStroke( KeyEvent.VK_DIVIDE, 0));
		
		ggChannel		= new JComboBox();
		channelRenderer	= new ListLabel();
//		channelRenderer.setPreferredSize( 128, 32 );
		ggChannel.setRenderer( channelRenderer );
		for( int ch = 0; ch < transport.getNumChannels(); ch++ ) {
			ggChannel.addItem( new Integer( ch ));
			channelRenderer.addItem( new ColorIcon( (float) ch / transport.getNumChannels() ),
									 String.valueOf( (char) (ch + 65) ));
		}
		
		ggRewind.setFocusable( false );
		ggStop.setFocusable( false );
		ggPlay.setFocusable( false );
		ggFFwd.setFocusable( false );
		ggLoop.setFocusable( false );
		ggChannel.setFocusable( false );
		toolBar.add( ggRewind );
		toolBar.add( ggStop );
		toolBar.add( ggPlay );
		toolBar.add( ggFFwd );
		toolBar.add( ggLoop );
		toolBar.add( ggChannel );
        HelpGlassPane.setHelp( toolBar, "TransportTools" );
        
		actionGoToTime  = new actionGoToTimeClass();
		lbTime			= new TimeLabel();
        HelpGlassPane.setHelp( lbTime, "TransportPosition" );
        lbTime.setBorder( new EmptyBorder( 1, 24, 1, 8 ));
		lbTime.setCursor( new Cursor( Cursor.HAND_CURSOR ));
		lbTime.setForeground( Color.black );
		lbTime.addMouseListener( new MouseAdapter() {
			public void mouseClicked( MouseEvent e )
			{
				actionGoToTime.actionPerformed( null );
				lbTime.black();
			}
			
			public void mouseEntered( MouseEvent e )
			{
				lbTime.blue();
			}

			public void mouseExited( MouseEvent e )
			{
				lbTime.black();
			}
		});
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_G, 0 ), "gototime" );
		amap.put( "gototime", actionGoToTime );
		
		this.add( toolBar );
		this.add( lbTime );
		
		// --- Listener ---
		new DynamicAncestorAdapter( this ).addTo( this );

		cueTimer = new javax.swing.Timer( 25, new ActionListener() {
			public void actionPerformed( ActionEvent e )
			{
				if( !lm.attemptExclusive( doors, 200 )) return;
				try {
					cuePos = Math.max( 0, Math.min( timeline.getLength(), cuePos + (cueStep * rate) / 1000 ));
// INERTIA
//					doc.getUndoManager().addEdit( new EditSetTimelinePosition( this, doc, cuePos ));
timeline.setPosition( this, cuePos );
				}
				finally {
					lm.releaseExclusive( doors );
				}
			}
		});

		timeline.addTimelineListener( this );
		transport.addListener( this );
	}
	
	public void addActiveChannelListener( ActionListener l )
	{
		ggChannel.addActionListener( l );
	}
	
	/**
	 *	Causes the timeline position label
	 *	to blink red to indicate a dropout error
	 */
	public void blink()
	{
		lbTime.blink();
	}

	public int getActiveChannel()
	{
		return ggChannel.getSelectedIndex();
	}

//	/**
//	 *	Adds a new button to the transport palette
//	 *
//	 *	@param	b	the button to add
//	 */
//	public void addButton( AbstractButton b )
//	{
//		if( b instanceof JToggleButton ) {
//			toolBar.addToggleButton( (JToggleButton) b, customGroup );
//			customGroup++;
//		} else {
//			toolBar.addButton( b );
//		}
//	}

// ---------------- DynamicListening interface ---------------- 

    public void startListening()
    {
		transport.addRealtimeConsumer( this );
//		updateTimeLabel();
    }

    public void stopListening()
    {
		transport.removeRealtimeConsumer( this );
    }
    
// ---------------- RealtimeConsumer interface ---------------- 

	/**
	 *  Requests 15 fps notification (no data block requests).
	 *  This is used to update the timeline position label during transport
	 *  playback.
	 */
	public RealtimeConsumerRequest createRequest( RealtimeContext context )
	{
		RealtimeConsumerRequest request = new RealtimeConsumerRequest( this, context );
		// 15 fps is enough for text update
		request.notifyTickStep  = RealtimeConsumerRequest.approximateStep( context, 15 );
		request.notifyTicks		= true;
		request.notifyOffhand	= true;
		return request;
	}
	
//	public void realtimeTick( RealtimeContext context, RealtimeProducer.Source source, long currentPos )
	public void realtimeTick( RealtimeContext context, int ch, long currentPos )
	{
		if( ch == getActiveChannel() ) {
			lbTime.wahrnehmungsApparillo( (int) ((double) (currentPos * 1000) / context.getSourceRate() + 0.5 ));
		}
	}

//	public void offhandTick( RealtimeContext context, RealtimeProducer.Source source, long currentPos )
//	public void offhandTick( RealtimeContext context, long currentPos )
//	{
//		lbTime.wahrnehmungsApparillo( (int) ((double) (currentPos * 1000) / context.getSourceRate() + 0.5 ));
//		if( !isCueing ) cuePos = currentPos;
//	}

//	public void realtimeBlock( RealtimeContext context, RealtimeProducer.Source source, boolean even ) {}
	public void realtimeBlock( RealtimeContext context, boolean even ) {}

// ---------------- TimelineListener interface ---------------- 

	public void timelineSelected( TimelineEvent e )
	{
		if( ggLoop.isSelected() ) {
			actionLoop.updateLoop();
		}
    }

	public void timelineChanged( TimelineEvent e )
	{
		try {
			lm.waitShared( doors );
			rate = timeline.getRate();
		}
		finally {
			lm.releaseShared( doors );
		}
	}
	
    public void timelineScrolled( TimelineEvent e ) {}

	public void timelinePositioned( TimelineEvent e )
	{
		if( !lm.attemptShared( doors, 250 )) return;
		try {
			final long	currentPos	= timeline.getPosition();
			final int	currentRate	= timeline.getRate();
			lbTime.wahrnehmungsApparillo( (int) ((double) (currentPos * 1000) / currentRate + 0.5 ));
			if( !isCueing ) cuePos = currentPos;
		}
		finally {
			lm.releaseShared( doors );
		}
	}

// ---------------- TransportListener interface ---------------- 

	public void transportStop( MultiTransport transport, int ch, long pos )
	{
		if( ch == getActiveChannel() ) {
			ggPlay.setSelected( false );
			if( isCueing ) {
				cuePos = pos;
				cueTimer.restart();
			}
		}
	}
	
	public void transportPlay( MultiTransport transport, int ch, long pos, float rate )
	{
		if( ch == getActiveChannel() ) {
			ggPlay.setSelected( true );
			if( cueTimer.isRunning() ) cueTimer.stop();
		}
	}
	
	public void transportQuit( MultiTransport transport )
	{
		if( cueTimer.isRunning() ) cueTimer.stop();
	}
	
	public void transportPosition( MultiTransport transport, int ch, long pos, float rate ) {}

// ---------------- time label class ---------------- 

	private class TimeLabel
	extends JComponent
	{
		private byte[]		wahrnehmung		= {
			0x20, 0x20, 0x020, 0x3A, 0x20, 0x20, 0x2E, 0x20, 0x20, 0x20
		};
		private Dimension   preferredSize   = new Dimension( 100, 14 ); // XXX Test
		private Color		colr;
		private long		resetWhen;
	
		private TimeLabel()
		{
			super();

			setFont( fntMono );
		}
		
		private void blink()
		{
			colr		= Color.red;
			resetWhen   = System.currentTimeMillis() + 150;
			repaint();
		}
		
		private void blue()
		{
			colr		= Color.blue;
			repaint();
		}
		
		private void black()
		{
			colr		= Color.black;
			repaint();
		}
		
		public Dimension getPreferredSize()
		{
			return preferredSize;
		}
		
		private void wahrnehmungsApparillo( int millis )
		{
			int mins, secs, msecs;
		
			secs	= millis / 1000;
			mins	= secs / 60;
			msecs   = millis - secs * 1000;
			secs   -= mins * 60;

			wahrnehmung[ 9 ] = (byte) ((msecs % 10) + 0x30);
			wahrnehmung[ 8 ] = (byte) (((msecs/10) % 10) + 0x30);
			wahrnehmung[ 7 ] = (byte) (((msecs/100) % 10 ) + 0x30);
			wahrnehmung[ 5 ] = (byte) ((secs % 10 ) + 0x30);
			wahrnehmung[ 4 ] = (byte) (((secs/10) % 10 ) + 0x30);
			wahrnehmung[ 2 ] = (byte) ((mins % 10 ) + 0x30);
			wahrnehmung[ 1 ] = (byte) (mins >= 10 ? ((mins/10) % 10 ) + 0x30 : 0x20);
			wahrnehmung[ 0 ] = (byte) (mins >= 100 ? ((mins/100) % 10 ) + 0x30 : 0x20);
			
			repaint();
		}
		
		public void paintComponent( Graphics g )
		{
			super.paintComponent( g );
			
			if( colr == Color.red && System.currentTimeMillis() >= resetWhen ) {
				colr = Color.black;
			}
			g.setColor( colr );
			g.drawBytes( wahrnehmung, 0, 10, 0, 10 );
		}
	}

// ---------------- actions ---------------- 

	private class actionGoToTimeClass
//	extends KeyedAction
	extends AbstractAction
	{
		private int defaultValue = 0;   // millisecs
	
//		private actionGoToTimeClass( KeyStroke stroke )
//		{
//			super( stroke );
//		}
		
//		protected void validActionPerformed( ActionEvent e )
		public void actionPerformed( ActionEvent e )
		{
			String			result;
			int				min;
			Object[]		msgArgs		= new Object[2];
			Object[]		resultArgs;

			min			= defaultValue / 60000;
			msgArgs[0]  = new Integer( min );
			msgArgs[1]  = new Double( (double) (defaultValue % 60000) / 1000 );

			result  = JOptionPane.showInputDialog( null, AbstractApplication.getApplication().getResourceString( "inputDlgGoToTime" ),
												   msgFormat.format( msgArgs ));

			if( result == null || !lm.attemptExclusive( doors, 1000 )) return;
			try {
				resultArgs		= msgFormat.parse( result );
				if( !(resultArgs[0] instanceof Number) || !(resultArgs[1] instanceof Number) ) return;
				defaultValue	= (int) ((((Number) resultArgs[1]).doubleValue() + 60 *
										  ((Number) resultArgs[0]).intValue()) * 1000 + 0.5);  // millisecs
				timeline.setPosition( this, Math.max( 0, Math.min( timeline.getLength(),
										  (long) ((double) defaultValue / 1000 * timeline.getRate() + 0.5 ))));
			}
			catch( ParseException e1 ) {
				System.err.println( e1.getLocalizedMessage() );
			}
			finally {
				lm.releaseExclusive( doors );
			}
        }
	} // class actionGoToTimeClass

	private class actionPlayClass
	extends AbstractAction
	{
		private actionPlayClass()
		{
			super();
		}
		
		public void actionPerformed( ActionEvent e )
		{
			if( !lm.attemptShared( doors, 200 )) return;
			try {
				long pos = timeline.getPosition();
				if( pos == timeline.getLength() ) {
					pos = 0;
				}
				transport.goPlay( getActiveChannel(), pos,
								 (e.getModifiers() & ActionEvent.SHIFT_MASK) == 0 ? 1.0f :
								  ((e.getModifiers() & ActionEvent.ALT_MASK) == 0 ? 0.5f : 2.0f) );
			}
			finally {
				lm.releaseShared( doors );
			}
        }
	} // class actionPlayClass

	private class actionStopClass
	extends AbstractAction
	{
		private actionStopClass()
		{
			super();
		}
		
		public void actionPerformed( ActionEvent e )
		{
			transport.goStop( getActiveChannel() );
        }
	} // class actionStopClass
	
	private class actionTogglePlayStopClass
//	extends KeyedAction
	extends AbstractAction
	{
//		private actionTogglePlayStopClass( KeyStroke stroke )
//		{
//			super( stroke );
//		}

//		protected void validActionPerformed( ActionEvent e )
		public void actionPerformed( ActionEvent e )
		{
			if( transport.isRunning( getActiveChannel() )) {
				ggStop.doClick();
			} else {
				ggPlay.doClick();
			}
		}
	} // class actionTogglePlayStop

	private static class actionCueClass
	extends AbstractAction
	{
		private final boolean			onOff;
		private final AbstractButton	b;
	
		private actionCueClass( AbstractButton b, boolean onOff )
		{
			this.onOff	= onOff;
			this.b		= b;
		}
		
		public void actionPerformed( ActionEvent e )
		{
			final ButtonModel bm = b.getModel();
			if( bm.isPressed() != onOff ) bm.setPressed( onOff );
			if( bm.isArmed()   != onOff ) bm.setArmed(   onOff );
		}
	} // class actionCueClass
		
	private class actionLoopClass
	extends AbstractAction
	{	
		private actionLoopClass()
		{
			super();
		}

		public void actionPerformed( ActionEvent e )
		{
			if( ((AbstractButton) e.getSource()).isSelected() ) {
				if( lm.attemptShared( doors, 200 )) {
					try {
						updateLoop();
					}
					finally {
						lm.releaseShared( doors );
					}
				} else {
					((AbstractButton) e.getSource()).setSelected( false );
				}
			} else {
				transport.setLoop( getActiveChannel(), null );
			}
        }
		
		private void updateLoop()
		{
			Span span;

			try {
				lm.waitShared( doors );
				span = timeline.getSelectionSpan();
				transport.setLoop( getActiveChannel(), span.isEmpty() ? null : span );
			}
			finally {
				lm.releaseShared( doors );
			}
		}
	} // class actionLoopClass
	
	private class CueListener
	implements ChangeListener
	{
		private final ButtonModel	bm;
		private boolean				transportWasRunning	= false;
		private final int			step;
	
		// step = in millisecs, > 0 = fwd, < = rwd
		private CueListener( AbstractButton b, int step )
		{
			bm			= b.getModel();
			this.step	= step;
		}

		public void stateChanged( ChangeEvent e )
		{
			if( isCueing && !bm.isArmed() ) {
				isCueing	= false;
				cueTimer.stop();
				if( transportWasRunning ) {
					transport.goPlay( getActiveChannel(), timeline.getPosition(), 1.0f );
				}
			} else if( !isCueing && bm.isArmed() ) {
				transportWasRunning = transport.isRunning( getActiveChannel() );
				cueStep		= step;
				isCueing	= true;
				if( transportWasRunning ) {
					transport.goStop( getActiveChannel() );
				} else {
					cueTimer.restart();
				}
			}
		}
	}
}