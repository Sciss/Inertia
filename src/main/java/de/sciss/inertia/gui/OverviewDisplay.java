//
//  OverviewDisplay.java
//  Inertia
//
//  Created by Hanns Holger Rutz on 07.08.05.
//  Copyright 2005 __MyCompanyName__. All rights reserved.
//

package de.sciss.inertia.gui;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.undo.CompoundEdit;

import de.sciss.inertia.edit.*;
import de.sciss.inertia.realtime.*;
import de.sciss.inertia.session.*;
import de.sciss.inertia.timeline.Timeline;
import de.sciss.inertia.timeline.TimelineEvent;
import de.sciss.inertia.timeline.TimelineListener;

import de.sciss.app.AbstractApplication;
import de.sciss.app.DynamicAncestorAdapter;
import de.sciss.app.DynamicListening;

import de.sciss.gui.GUIUtil;

import de.sciss.io.AudioFile;
import de.sciss.io.AudioFileDescr;
import de.sciss.io.Span;

import de.sciss.net.*;

import de.sciss.util.MapManager;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.31, 04-Dec-05
 *
 *	@todo		redrawImage() slows down incredible if going to the end of the
 *				timeline, can be binarySearch or the coll ops
 */
public class OverviewDisplay
extends JComponent
implements DynamicListening, TimelineListener, ToolActionListener // RealtimeConsumer, TransportListener
{
	private final Session	doc;
	private final Track		t;

	private static final Shape shpCrossHair;
	private static final Color colrMolec	= new Color( 0x00, 0x00, 0xFF, 0x40 );
	private static final Color colrMolecSel	= new Color( 0x00, 0x00, 0xC0, 0x80 );
	private static final Color colrMolecDrag= new Color( 0x40, 0x40, 0x40, 0x80 );
	private static final Color colrAtom		= new Color( 0x60, 0x00, 0x80, 0x40 );
	private static final Color colrAtomB	= new Color( 0x60, 0x00, 0x80, 0x80 );
	private static final Color colrAtomSel	= new Color( 0x40, 0x00, 0x60, 0x80 );
	private static final Color colrAtomBSel	= new Color( 0x40, 0x00, 0x60, 0xC0 );
	private static final Color colrMolecLab	= new Color( 0x00, 0x00, 0xFF, 0x80 );
	private static final Color colrAtomLab	= new Color( 0x60, 0x00, 0x80, 0x80 );
	private static final Color colrMuted	= new Color( 0x60, 0x60, 0x60, 0x60 );
	private static final Color colrMutedSel	= new Color( 0x60, 0x60, 0x60, 0xC0 );

	private static final Paint pntProbZone;
	private static final Paint pntProbZoneSel;
	private static final Paint pntProbZoneM;
	private static final Paint pntProbZoneSelM;
	
	private static final Stroke	strkMolec	= new BasicStroke( 2.0f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_BEVEL,
		1.0f, new float[] { 4.0f, 4.0f }, 0.0f );
	private static final Stroke	strkAtom	= new BasicStroke( 2.0f );
	
	private static final Font	fntMolec	= new Font( "Helvetica", Font.ITALIC, 10 );

	private long lastTimelinePos		= 0;

	private int			verticalProbIdx	= 0;
	private static int	timeProbIdx		= 0;

	private boolean		recalcImage = true;
	private Image		image		= null;
	private int			recentWidth, recentHeight;

	private static final int[]	pntBgAquaPixels = { 0xFFF0F0F0, 0xFFF0F0F0, 0xFFF0F0F0, 0xFFF0F0F0,
													0xFFF0F0F0, 0xFFF0F0F0, 0xFFF0F0F0, 0xFFF0F0F0,
													0xFFECECEC, 0xFFECECEC, 0xFFECECEC, 0xFFECECEC,
													0xFFECECEC, 0xFFECECEC, 0xFFECECEC, 0xFFECECEC };
	private static final Paint	pntBackground;

	private static final int[] pntProbZonePixels ={ 0x40600080, 0x40600080, 0x40600080, 0x40600080,
													0x40600080, 0x40600080, 0x40600080, 0x40600080,
													0x00000000, 0x00000000, 0x00000000, 0x00000000,
													0x00000000, 0x00000000, 0x00000000, 0x00000000 };
	private static final int[] pntProbZoneSelPixels={0x80400060,0x80400060, 0x80400060, 0x80400060,
													0x80400060, 0x80400060, 0x80400060, 0x80400060,
													0x00000000, 0x00000000, 0x00000000, 0x00000000,
													0x00000000, 0x00000000, 0x00000000, 0x00000000 };
	private static final int[] pntProbZoneMPixels ={ 0x60606060, 0x60606060, 0x60606060, 0x60606060,
													0x60606060, 0x60606060, 0x60606060, 0x60606060,
													0x00000000, 0x00000000, 0x00000000, 0x00000000,
													0x00000000, 0x00000000, 0x00000000, 0x00000000 };
	private static final int[] pntProbZoneSelMPixels={0x606060C0, 0x80400060, 0x606060C0, 0x606060C0,
													0x606060C0, 0x606060C0, 0x606060C0, 0x606060C0,
													0x00000000, 0x00000000, 0x00000000, 0x00000000,
													0x00000000, 0x00000000, 0x00000000, 0x00000000 };

	// timeline mirroir
	private int		rate		= 1;
	private Span	visibleSpan	= new Span();
	private long	timelineLen;

	// --- tools ---
	
	private			AbstractTool			activeTool				= null;
	private final	MoleculePencilTool		pencilTool;
	private			boolean					moleculesEditable		= false; // depends on zoom level!
	private final	java.util.List			collMolecRects			= new ArrayList();

	private final DocumentFrame.TimelineViewport onTop;

	static {
	// --- Images ---
		BufferedImage img;
		
		img = new BufferedImage( 4, 4, BufferedImage.TYPE_INT_ARGB );
		img.setRGB( 0, 0, 4, 4, pntBgAquaPixels, 0, 4 );
		pntBackground = new TexturePaint( img, new Rectangle( 0, 0, 4, 4 ));

		img = new BufferedImage( 4, 4, BufferedImage.TYPE_INT_ARGB );
		img.setRGB( 0, 0, 4, 4, pntProbZonePixels, 0, 4 );
		pntProbZone = new TexturePaint( img, new Rectangle( 0, 0, 4, 4 ));

		img = new BufferedImage( 4, 4, BufferedImage.TYPE_INT_ARGB );
		img.setRGB( 0, 0, 4, 4, pntProbZoneSelPixels, 0, 4 );
		pntProbZoneSel = new TexturePaint( img, new Rectangle( 0, 0, 4, 4 ));

		img = new BufferedImage( 4, 4, BufferedImage.TYPE_INT_ARGB );
		img.setRGB( 0, 0, 4, 4, pntProbZoneMPixels, 0, 4 );
		pntProbZoneM = new TexturePaint( img, new Rectangle( 0, 0, 4, 4 ));

		img = new BufferedImage( 4, 4, BufferedImage.TYPE_INT_ARGB );
		img.setRGB( 0, 0, 4, 4, pntProbZoneSelMPixels, 0, 4 );
		pntProbZoneSelM = new TexturePaint( img, new Rectangle( 0, 0, 4, 4 ));

	// --- Shapes ---
		shpCrossHair	= new Area( new Rectangle2D.Float( -5f, -0.5f, 10f, 1f ));
		((Area) shpCrossHair).add( new Area( new Rectangle2D.Float( -0.5f, -5f, 1f, 10f )));
		
		for( int i = 0; i < Atom.PROB_ALL.length; i++ ) {
			if( Atom.PROB_ALL[ i ].equals( Atom.PROB_TIME )) {
				timeProbIdx = i;
				break;
			}
		}
	}

	public OverviewDisplay( Session doc, Track t, DocumentFrame.TimelineViewport onTop )
	{
		super();

		this.doc	= doc;
		this.t		= t;
		this.onTop	= onTop;
		
		// ------ listeners ------
		
		t.molecules.addListener( new SessionCollection.Listener() {
			public void sessionCollectionChanged( SessionCollection.Event e )
			{
				triggerRedisplay();
			}
		  
			public void sessionObjectMapChanged( SessionCollection.Event e )
			{
				triggerRedisplay();
			}
			
			public void sessionObjectChanged( SessionCollection.Event e )
			{
				triggerRedisplay();
			}
		});

		t.selectedMolecules.addListener( new SessionCollection.Listener() {
			public void sessionCollectionChanged( SessionCollection.Event e )
			{
				triggerRedisplay();
			}
		  
			public void sessionObjectMapChanged( SessionCollection.Event e ) {}
			public void sessionObjectChanged( SessionCollection.Event e ) {}
		});

		t.selectedAtoms.addListener( new SessionCollection.Listener() {
			public void sessionCollectionChanged( SessionCollection.Event e )
			{
				triggerRedisplay();
			}
		  
			public void sessionObjectMapChanged( SessionCollection.Event e ) {}
			public void sessionObjectChanged( SessionCollection.Event e ) {}
		});

		new DynamicAncestorAdapter( this ).addTo( this );

		// ---- Tools ----
		pencilTool	= new MoleculePencilTool();


		setTransferHandler( new AFRTransferHandler() );
		
		setFocusable( true );
	}
	
	public Track getTrack()
	{
		return t;
	}

	public void setVerticalProbability( String name )
	{
		for( int i = 0; i < Atom.PROB_ALL.length; i++ ) {
			if( name.equals( Atom.PROB_ALL[ i ])) {
				verticalProbIdx = i;
				recalcImage = true;
				repaint();
				return;
			}
		}
		System.err.println( "prob not found : "+name );
	}
		
	private String getResourceString( String key )
	{
		return AbstractApplication.getApplication().getResourceString( key );
	}

	private void recreateImage()
	{
		if( image != null ) image.flush();
		image = createImage( recentWidth, recentHeight );
	}
	
	/*
	 *	@sync	call only in event thread
	 */
	private void redrawImage()
	{
		if( image == null ) return;
		if( !doc.bird.attemptShared( Session.DOOR_MOLECULES, 250 )) return;
		try {
			final double			scale, startSec, stopSec, dur;
			final boolean			paintAtoms;
			final Graphics2D		g2;
			final java.util.List	collVisi;
			int						zx, zy, zy2, zw, zh, zh2, dx;
			float					zhf;
			Molecule				molec;
			Atom					a;
			Probability				hProb;
			boolean					selected, muted, frozen;
			MolecRect				mr	= null;
			Object					o;
			
			g2	= (Graphics2D) image.getGraphics();
			g2.setPaint( pntBackground );
			g2.fillRect( 0, 0, recentWidth, recentHeight );	// 'aqua'

			// need not sync on visibleSpan because
			// it will only be updated in the event thread
			// ; same for the session collections !
			startSec				= (double) visibleSpan.getStart() / rate;
			stopSec					= (double) visibleSpan.getStop() / rate;
			dur						= stopSec - startSec;
			scale					= (double) recentWidth / dur;
			paintAtoms				= dur < 150;	// zoom level <= 2 1/2 minutes
			this.moleculesEditable	= (dur < 600) && (activeTool != null);	// zoom level <= ten minutes
			
			collVisi	= t.getMolecules( startSec, stopSec );
	//System.err.println( "collVisi.size() " + collVisi.size() );
			this.collMolecRects.clear();

			g2.setFont( fntMolec );

			for( int i = 0; i < collVisi.size(); i++ ) {
				molec	= (Molecule) collVisi.get( i );
	//System.err.println( "  " + i + " : " + molec.getName() + " -> " + molec.getStart() + " ... " + molec.getStop() );
				zx		= (int) ((molec.getStart() - startSec) * scale + 0.5) - 2;
				zy		= (int) (molec.getY() * recentHeight + 1.5);
				zw		= (int) (molec.getLength() * scale + 0.5) + 4;
				zh		= (int) (molec.getHeight() * recentHeight - 1.5);
				
				g2.setStroke( strkMolec );
				
				o		= molec.getMap().getValue( SessionObject.MAP_KEY_FLAGS );
				if( o != null ) {
					muted	= (((Number) o).intValue() & (SessionObject.FLAGS_MUTE | SessionObject.FLAGS_VIRTUALMUTE)) != 0;
				} else {
					muted	= false;
				}

				if( moleculesEditable ) {
					selected	= t.selectedMolecules.contains( molec );
					mr			= new MolecRect( molec, zx, zy, zw, zh, selected );
					collMolecRects.add( mr );
					if( paintAtoms ) {
						mr.atomRects	= new ArrayList( molec.atoms.size() );
					}
				} else {
					selected	= false;
				}
				
				o		= molec.getMap().getValue( Molecule.MAP_KEY_FREEZE );
				if( o != null ) {
					frozen	= ((Boolean) o).booleanValue();
				} else {
					frozen	= false;
				}
				
				g2.setColor( muted ? (selected ? colrMutedSel : colrMuted) : (selected ? colrMolecSel : colrMolec) );
				g2.drawRect( zx, zy, zw - 1, zh - 1 );
	//			g2.fillRect( zx, zy, zw, zh );
				g2.setColor( muted ? (selected ? colrMutedSel : colrMuted) : (selected ? colrMolecSel : colrMolecLab) );
				g2.drawString( molec.getName(), zx + zw + 4, zy + 7 );
				if( frozen ) {
					g2.setColor( Color.blue );
					g2.drawString( "\u2744", zx - 10, zy + 7 );
				}


				if( !paintAtoms ) continue;
				
				g2.setStroke( strkAtom );
							
				zhf		= (float) (zh - 6) / molec.atoms.size();
				zh2		= (int) zhf;
				
				for( int j = 0; j < molec.atoms.size(); j++ ) {
					a		= (Atom) molec.atoms.get( j );
					hProb	= (Probability) a.probabilities.findByName( Atom.PROB_TIME );
					if( hProb == null ) continue;
					
	//				vProb	= a.probabilities.findByName( vProb );
					zx		= (int) ((hProb.getMin() - startSec) * scale + 0.5) + 1;
					zy2		= (int) (j * zhf) + zy + 3;
					zw		= (int) ((a.getFileStop() - a.getFileStart()) * scale + 0.5) - 2;
					dx		= (int) ((hProb.getMax() - hProb.getMin()) * scale + 0.5);
					
					if( (mr != null) && (mr.atomRects != null) ) {
						selected	= t.selectedAtoms.contains( a );
						mr.atomRects.add( new AtomRect( a, mr.molec, zx, zy2, zw + dx, zh2, selected ));
					} else {
						selected	= false;
					}

					g2.setColor( muted ? (selected ? colrMutedSel : colrMuted) : (selected ? colrAtomBSel : colrAtomB) );
					g2.drawRect( zx, zy2, zw + dx - 1, zh2 - 1 );
					if( zw > dx ) {
						g2.setColor( muted ? (selected ? colrMutedSel : colrMuted) : (selected ? colrAtomSel : colrAtom) );
						g2.fillRect( zx + dx, zy2 + 2, zw - dx, zh2 - 4 );
					}
					g2.setPaint( muted ? (selected ? pntProbZoneSelM : pntProbZoneM ) : (selected ? pntProbZoneSel : pntProbZone) );
					g2.fillRect( zx + 2, zy2 + 2, Math.min( zw, dx ) - 2, zh2 - 4 );
					g2.fillRect( zx + zw, zy2 + 2, dx - 2, zh2 - 4 );
					
					g2.setColor( muted ? (selected ? colrMutedSel : colrMuted) : (selected ? colrAtomBSel : colrAtomLab) );
					g2.drawString( a.getName(), zx + dx + 4, zy2 + 10 );
				}
			}

			recalcImage = false;
			g2.dispose();
		}
		finally {
			doc.bird.releaseShared( Session.DOOR_MOLECULES );
		}
	}

	public void paintComponent( Graphics g )
	{
		final Graphics2D g2 = (Graphics2D) g;

//		int				x, y;
		final int		w, h;
//		RealizedAtom	ra;
//		AtomEvent		hEvent, vEvent;
		
		w = getWidth();
		h = getHeight();
	
		if( (recentWidth != w) || (recentHeight != h) ) {
			recentWidth		= w;
			recentHeight	= h;
			recreateImage();
			recalcImage		= true;
		}
		if( recalcImage ) {
			redrawImage();
		}
		if( image != null ) g.drawImage( image, 0, 0, this );

		final Point delta = SwingUtilities.convertPoint( onTop, 0, 0, this );
		g2.translate( delta.x, delta.y );
		onTop.paintGAGA( g2 );
		g2.translate( -delta.x, -delta.y );

		if( activeTool != null ) activeTool.paintOnTop( g2 );
	}
	
	private void triggerRedisplay()
	{
		recalcImage	= true;
		repaint();
//		getParent().repaint();
	}
	
// ---------------- ToolListener interface ---------------- 
 
	public void toolChanged( ToolActionEvent e )
	{
		if( activeTool != null ) {
			activeTool.toolDismissed( this );
			activeTool = null;
			triggerRedisplay();
		}
		
		if( e.getToolAction().getID() == ToolAction.PENCIL ) {
			activeTool	= pencilTool;
			this.setCursor( e.getToolAction().getDefaultCursor() );
			activeTool.toolAcquired( this );
			triggerRedisplay();
		} else {
			this.setCursor( null );
		}
	}

// ---------------- DynamicListening interface ---------------- 

    public void startListening()
    {
		doc.timeline.addTimelineListener( this );

		if( doc.bird.attemptShared( Session.DOOR_TIME, 250 )) {
			try {
				visibleSpan = doc.timeline.getVisibleSpan();
				rate		= doc.timeline.getRate();
				timelineLen	= doc.timeline.getLength();
				triggerRedisplay();
			}
			finally {
				doc.bird.releaseShared( Session.DOOR_TIME );
			}
		}

//		markers.addListener( this );
    }

    public void stopListening()
    {
//		markers.removeListener( this );
        doc.timeline.removeTimelineListener( this );
    }

// ---------------- TimelineListener interface ---------------- 
  
   	public void timelineSelected( TimelineEvent e ) {}
	public void timelinePositioned( TimelineEvent e ) {}

	public void timelineChanged( TimelineEvent e )
	{
		if( !doc.bird.attemptShared( Session.DOOR_TIME, 250 )) return;
		try {
			timelineLen	= doc.timeline.getLength();
			rate		= doc.timeline.getRate();
			triggerRedisplay();
		}
		finally {
			doc.bird.releaseShared( Session.DOOR_TIME );
		}
	}

   	public void timelineScrolled( TimelineEvent e )
    {
		if( !doc.bird.attemptShared( Session.DOOR_TIME, 250 )) return;
		try {
			visibleSpan = doc.timeline.getVisibleSpan();
//			scale		= (double) getWidth() / visibleSpan.getLength();
			
			triggerRedisplay();
		}
		finally {
			doc.bird.releaseShared( Session.DOOR_TIME );
		}
    }

// ---------------- RealtimeConsumer interface ---------------- 

//	/**
//	 *  Requests 30 fps notification (no data block requests).
//	 *  This is used to update the timeline position during transport
//	 *  playback.
//	 */
//	public RealtimeConsumerRequest createRequest( RealtimeContext context )
//	{
//		RealtimeConsumerRequest request = new RealtimeConsumerRequest( this, context );
//		// 30 fps is visually fluent
//		request.notifyTickStep  = RealtimeConsumerRequest.approximateStep( context, 30 );
//		request.notifyTicks		= true;
//		request.notifyOffhand	= true;
//		return request;
//	}
//	
//	public void realtimeTick( RealtimeContext context, long timelinePos )
//	{
//		long			len		= doc.timeline.getLength();
//		int				i, j;
//		float			f;
//		RealizedAtom	ra;
//		
//		synchronized( collRealAtoms ) {
//			
//			f = (float) lastTimelinePos / (float) len;
////System.err.println( "f = "+f );
//			
//			for( i = 0; i < collRealAtoms.size(); i++ ) {
//				ra = (RealizedAtom) collRealAtoms.get( i );
//				if( ra.dead ) continue;
//				if( ra.events[ timeProbIdx ].value >= f ) break;
//			}
//
//			lastTimelinePos = timelinePos;
//			
//			f = (float) timelinePos / (float) len;
//
//			for( j = i; j < collRealAtoms.size(); j++ ) {
//				ra = (RealizedAtom) collRealAtoms.get( j );
//				if( ra.dead ) continue;
//				if( ra.events[ timeProbIdx ].value > f ) break;
//			}
//
//			if( i < j ) {
//				while( i < j ) {
//					ra = (RealizedAtom) collRealAtoms.get( --j );
//					sendAtom( ra );
//					ra.realize();
//					ra.dead = true;
//				}
//				repaint();
//			}
//		}
//	}
//
//	public void offhandTick( RealtimeContext context, long timelinePos )
//	{
//		lastTimelinePos = timelinePos;
//	}
	
// ---------- TransportListener interface ----------

//	public void transportStop( Transport transport, long pos )
//	{
//	
//	}
//
//	public void transportPosition( Transport transport, long pos, float rate )
//	{
//		transportStop( transport, pos );
//		transportPlay( transport, pos, rate );
//	}
//
//	public void transportPlay( Transport transport, long pos, float rate )
//	{
//		synchronized( collRealAtoms ) {
//			for( int i = 0; i < collRealAtoms.size(); i++ ) {
//				((RealizedAtom) collRealAtoms.get( i )).dead = false;
//			}
//			Collections.sort( collRealAtoms );
//		}
//		lastTimelinePos = pos;
//		repaint();
//	}
//
//	public void transportQuit( Transport transport ) {}

// ---------- internal classes ----------

// -------------- internal classes --------------

	private class AFRTransferHandler
	extends TransferHandler
	{
		private AFRTransferHandler() {}

		public int getSourceActions( JComponent c )
		{
			return NONE;
		}
		
		public boolean importData( JComponent c, Transferable t )
		{
			String				s;
			StringTokenizer		strTok;
			final boolean		createdMolec;
			final Molecule		molec;			
			final Atom			a;
			final Probability	prob;
			final MapManager	map;
			final Track			track	= getTrack(); // (Track) doc.selectedTracks.get( 0 );
			final CompoundEdit	ce;
		
			try {
				if( t.isDataFlavorSupported( DataFlavor.stringFlavor )) {
					strTok	= new StringTokenizer( t.getTransferData( DataFlavor.stringFlavor ).toString(), File.pathSeparator );
					if( strTok.countTokens() == 3 ) {
						final File f = new File( strTok.nextToken() );
						final long startFrame	= Long.parseLong( strTok.nextToken() );
						final long stopFrame	= Long.parseLong( strTok.nextToken() );
						final AudioFile af		= AudioFile.openAsRead( f );
						final AudioFileDescr afd = af.getDescr();
						af.close();
						
						if( afd.channels != 2 ) {
							System.err.println( "wrong # of channels. need 2, drag source got "+afd.channels );
							return false;
						}
						a		= new Atom();
						a.createDefaultProbs( doc );
						prob	= a.getTime();
						map		= a.getMap();
						map.putValue( null, Atom.MAP_KEY_AFSTART, new Double( (double) startFrame / afd.rate ));
						map.putValue( null, Atom.MAP_KEY_AFSTOP, new Double( (double) stopFrame / afd.rate ));
						map.putValue( null, Atom.MAP_KEY_AUDIOFILE, f );

						if( !doc.bird.attemptExclusive( Session.DOOR_MOLECULES, 250 )) return false;
						try {
							if( track.selectedMolecules.size() == 1 ) {
								molec = (Molecule) track.selectedMolecules.get( 0 );
								prob.setMin( this, molec.getStart() );
								prob.setMax( this, prob.getMin() );
								createdMolec = false;
							} else {
								s = SessionCollection.createUniqueName( Session.SO_NAME_PTRN,
									new Object[] { new Integer( 1 ), Track.MOLECULE_NAME_PREFIX, "" }, track.molecules.getAll() );
								molec = new Molecule();
								molec.setName( s );
								prob.setMin( this, (double) doc.timeline.getPosition() / doc.timeline.getRate() );
								prob.setMax( this, prob.getMin() );
								createdMolec = true;
							}
							s = SessionCollection.createUniqueName( Session.SO_NAME_PTRN,
									new Object[] { new Integer( 1 ), Track.ATOM_NAME_PREFIX, "" }, molec.atoms.getAll() );
							a.setName( s );
							ce = new BasicSyncCompoundEdit( doc.bird, Session.DOOR_MOLECULES,
								getResourceString( "editAddAtoms" ));
							if( createdMolec ) {
								ce.addEdit( new EditAddSessionObject( this, track.molecules,
									molec, track.molecules.size(), doc.bird, Session.DOOR_MOLECULES ));
								ce.addEdit( new EditAddSessionObject( this, track.selectedMolecules,
									molec, track.selectedMolecules.size(), doc.bird, Session.DOOR_MOLECULES ));
							}
							ce.addEdit( new EditAddSessionObject( this, molec.atoms,
								a, molec.atoms.size(), doc.bird, Session.DOOR_MOLECULES ));
							ce.addEdit( new EditAddSessionObject( this, track.selectedAtoms,
								a, track.selectedAtoms.size(), doc.bird, Session.DOOR_MOLECULES ));
							ce.end();
							doc.getUndoManager().addEdit( ce );
						}
						finally {
							doc.bird.releaseExclusive( Session.DOOR_MOLECULES );
						}
						return true;
					}
				}
			}
			catch( UnsupportedFlavorException e1 ) {}
			catch( IOException e2 ) {
				System.err.println( e2.getClass().getName() + " : " + e2.getLocalizedMessage() );
			}
			catch( NumberFormatException e3 ) {
				System.err.println( e3.getClass().getName() + " : " + e3.getLocalizedMessage() );
			}

			return false;
		}

		public boolean canImport( JComponent c, DataFlavor[] flavors )
		{
			for( int i = 0; i < flavors.length; i++ ) {
				if( flavors[i].equals( DataFlavor.stringFlavor )) return true;
			}
			return false;
		}
		
//			for( int i = 0; i < flavors.length; i++ ) {
//System.err.println( "can import ? "+flavors[i] );
//				if( flavors[i].equals( AudioFileRegion.flavor )) return true;
//			}
//System.err.println( "nope" );
//			return false;
//		}
	}

	private static final int DRAG_NONE		= 0;
	// re molec:
	private static final int DRAG_MOVE		= 1;
	private static final int DRAG_HMOVE		= 2;
	private static final int DRAG_VMOVE		= 3;
	private static final int DRAG_HSTART	= 4;
	private static final int DRAG_HSTOP		= 5;
	private static final int DRAG_VSTART	= 6;
	private static final int DRAG_VSTOP		= 7;
	// re atom:
	private static final int DRAG_AMOVE		= 8;
	private static final int DRAG_AHMOVE	= 9;
	private static final int DRAG_AVMOVE	= 10;
	private static final int DRAG_AHSTART	= 11;
	private static final int DRAG_AHSTOP	= 12;
	private static final int DRAG_AFMOVE	= 13;	// file region

	private static String	incDecProb		= null;
	private static double	incDecStep;
	private static double	incDecMin;
	private static double	incDecMax;

	private class MoleculePencilTool
	extends AbstractTool
	implements KeyListener
	{
		private boolean shiftDrag, ctrlDrag, dragStarted = false;
		private int startX, dragX, startY, dragY;
		private int dragType	= DRAG_NONE;
		private final java.util.List dragMolecs	= new ArrayList();
		private final java.util.List dragAtoms	= new ArrayList();
	
		/**
		 *  Invokes the <code>AbstractTool</code>'s method
		 *  and additionally installs a <code>KeyListener</code> on
		 *  the <code>Component</code>.
		 */
		public void toolAcquired( Component c )
		{
			super.toolAcquired( c );
			c.addKeyListener( this );
		}

		/**
		 *  Completes or cancels the tool gesture
		 *  befoer invoking the <code>AbstractTool</code>'s method.
		 *  Finally removes the <code>KeyListener</code> from
		 *  the <code>Component</code>.
		 */
		public void toolDismissed( Component c )
		{
			dragType	= DRAG_NONE;
			dragStarted	= false;
//			finishGesture( false );
		
			super.toolDismissed( c );
			c.removeKeyListener( this );
		}

		public void keyPressed( KeyEvent e )
		{
			Rectangle2D		dndCurrentRect;
			CompoundEdit	ce;
			Atom			a;
			Probability		prob;
			boolean			consume	= true;
		
			switch( e.getKeyCode() ) {
//			case KeyEvent.VK_ENTER:  // complete
//				if( dndState == DND_CTRL || dndState == DND_VELOCITY ) {
//					finishGesture( true );
//					if( e.isControlDown() && canConcatenate() ) {   // anschluss-gesture
//						if( !initConcatenation( dndCtrlPoints )) return;	// failed
//						dndState			= DND_INITDRAG;
//						dndRecentRect		= null;
//						dndCtrlPoints[1]	= dndCtrlPoints[0];
//						dndBasicShape		= createBasicShape( dndCtrlPoints );
//						dndCtrlPointsShape  = calcCtrlPointsShape( dndCtrlPoints, 2 );
//						dndCurrentRect		= dndBasicShape.getBounds2D().createUnion( dndCtrlPointsShape.getBounds2D() );
//						efficientRepaint( dndRecentRect, dndCurrentRect );
//						dndRecentRect		= dndCurrentRect;
//					}
//				}
//				break;
//				

			case KeyEvent.VK_A:	// inc-dec volume
				incDecProb	= Atom.PROB_VOLUME;
				incDecStep	= 1.0;
				incDecMin	= -96.0;
				incDecMax	= 96.0;
				break;
				
			case KeyEvent.VK_P:	// inc-dec pitch
				incDecProb	= Atom.PROB_PITCH;
				incDecStep	= 1.0;
				incDecMin	= -96.0;
				incDecMax	= 96.0;
				break;

			case KeyEvent.VK_T:	// inc-dec time
				incDecProb	= Atom.PROB_TIME;
				incDecStep	= 0.1;
				incDecMin	= 0.0;
				incDecMax	= (double) timelineLen / rate;
				break;

			case KeyEvent.VK_OPEN_BRACKET:	// dec
			case KeyEvent.VK_CLOSE_BRACKET:	// inc
				if( incDecProb == null ) break;
			
				final double delta	= e.getKeyCode() == KeyEvent.VK_CLOSE_BRACKET ? incDecStep : -incDecStep;

				ce	= new BasicSyncCompoundEdit( doc.bird, Session.DOOR_MOLECULES, getResourceString( "editAdjustProbSpan" ));

				if( !doc.bird.attemptExclusive( Session.DOOR_MOLECULES, 250 )) return;
				try {
					final java.util.List collAtomsSel	= t.selectedAtoms.getAll();
//System.err.println( "prob "+incDecProb+" --> "+collAtomsSel.size()+"; step "+delta );
					for( int i = 0; i < collAtomsSel.size(); i++ ) {
						a		= (Atom) collAtomsSel.get( i );
						prob	= (Probability) a.probabilities.findByName( incDecProb );
						if( prob != null ) {
							prob.move( this, delta, incDecMin, incDecMax, ce, doc.bird, Session.DOOR_MOLECULES );
						}
					}
					ce.end();
					if( ce.isSignificant() ) {
						doc.getUndoManager().addEdit( ce );
					}
				}
				finally {
					doc.bird.releaseExclusive( Session.DOOR_MOLECULES );
				}
				incDecProb	= null;
				break;

			case KeyEvent.VK_Y:	// split at timeline pos
				if( !doc.bird.attemptExclusive( Session.DOOR_MOLECULES | Session.DOOR_TIME, 250 )) return;
				try {
					final java.util.List	collMolecsSel	= t.selectedMolecules.getAll();
					final java.util.List	collMolecsNamed	= t.molecules.getAll();
					final java.util.List	collMolecsNew	= new ArrayList( collMolecsSel.size() );
					final java.util.List	collNewSelAtoms	= new ArrayList();
					final double			pos				= (double) doc.timeline.getPosition() / rate;
					final double			maxt			= (double) timelineLen / rate - 1.0; // -1 willkuerlich, um "verschwinden" des molekuels zu vermeiden
					Molecule				molec, molec2;
					String					s;
					Object					o;
					double					time1, time2, fadeLen;
					java.util.List			coll;

					ce	= new BasicSyncCompoundEdit( doc.bird, Session.DOOR_MOLECULES, getResourceString( "editSplitMolecules" ));
					
					for( int i = collMolecsSel.size() - 1; i >= 0; i-- ) {
						molec	= (Molecule) collMolecsSel.get( i );
						if( (molec.getStart() < pos) && (molec.getStop() > pos) ) {
							molec2	= molec.duplicate();
							s = SessionCollection.createUniqueName( Session.SO_NAME_PTRN,
									new Object[] { new Integer( 1 ), Track.MOLECULE_NAME_PREFIX, "" }, collMolecsNamed );
							molec2.setName( s );
							collMolecsNamed.add( molec2 );
							collMolecsNew.add( molec2 );
							for( int j = molec.atoms.size() - 1; j >= 0; j-- ) {
								a		= (Atom) molec.atoms.get( j );
								time1	= a.getTimeStart();
								time2	= time1 + a.getFileLength();
								if( time1 > pos ) {	// completely moved to new molec
									coll = new ArrayList( 1 );
									coll.add( a );
									ce.addEdit( new EditRemoveSessionObjects( this,
										t.selectedAtoms, coll, doc.bird, Session.DOOR_MOLECULES ));
									ce.addEdit( new EditRemoveSessionObjects( this,
										molec.atoms, coll, doc.bird, Session.DOOR_MOLECULES ));
									collNewSelAtoms.add( molec2.atoms.get( j ));
								} else if( time2 <= pos ) {	// completely left in old molec
									molec2.atoms.remove( this, molec2.atoms.get( j ));
								} else {	// splitted
									fadeLen	= 0.0;
									o		= a.getMap().getValue( Atom.MAP_KEY_FADEIN );
									if( o != null ) {
										fadeLen	+= ((Number) o).doubleValue();
									}
									o		= a.getMap().getValue( Atom.MAP_KEY_FADEOUT );
									if( o != null ) {
										fadeLen	+= ((Number) o).doubleValue();
									}
									fadeLen /= 2;
									ce.addEdit( new EditPutMapValue( this, doc.bird, Session.DOOR_MOLECULES,
										a.getMap(), Atom.MAP_KEY_AFSTOP, new Double( a.getFileStart() + pos - time1 + fadeLen )));
									ce.addEdit( new EditPutMapValue( this, doc.bird, Session.DOOR_MOLECULES,
										a.getMap(), Atom.MAP_KEY_FADEOUT, new Double( fadeLen )));
									a	= (Atom) molec2.atoms.get( j );
									collNewSelAtoms.add( a );
									a.setFileStart( this, a.getFileStart() + pos - time1 - fadeLen );
									a.getMap().putValue( this, Atom.MAP_KEY_FADEIN, new Double( fadeLen ));
									prob = (Probability) a.probabilities.findByName( Atom.PROB_TIME );
									prob.move( this, pos - time1 - fadeLen, 0.0, maxt, ce, doc.bird, Session.DOOR_MOLECULES );
								}
							}
						} else {
							collMolecsSel.remove( i );
						}

						if( !collMolecsNew.isEmpty() ) {
							ce.addEdit( new EditRemoveSessionObjects( this,
								t.selectedMolecules, collMolecsSel, doc.bird, Session.DOOR_MOLECULES ));
							ce.addEdit( new EditRemoveSessionObjects( this,
								t.selectedAtoms, t.selectedAtoms.getAll(), doc.bird, Session.DOOR_MOLECULES ));
							ce.addEdit( new EditAddSessionObjects( this,
								t.molecules, collMolecsNew, doc.bird, Session.DOOR_MOLECULES ));
							ce.addEdit( new EditAddSessionObjects( this,
								t.selectedMolecules, collMolecsNew, doc.bird, Session.DOOR_MOLECULES ));
							ce.addEdit( new EditAddSessionObjects( this,
								t.selectedAtoms, collNewSelAtoms, doc.bird, Session.DOOR_MOLECULES ));
							ce.end();
						}
						
						if( ce.isSignificant() ) doc.getUndoManager().addEdit( ce );
					}
				}
				finally {
					doc.bird.releaseExclusive( Session.DOOR_MOLECULES | Session.DOOR_TIME );
				}
				break;
				
			case KeyEvent.VK_D:	// duplicate
				if( !doc.bird.attemptExclusive( Session.DOOR_MOLECULES, 250 )) return;
				try {
					if( t.selectedMolecules.size() == 1 ) {
						final Molecule			molec			= (Molecule) t.selectedMolecules.get( 0 );
						final java.util.List	collAtomsSel	= molec.atoms.getAll();
						final java.util.List	collAtomsNamed	= molec.atoms.getAll();
						final java.util.List	collAtomsNew	= new ArrayList( collAtomsSel.size() );
						String					s;
						
						ce = new BasicSyncCompoundEdit( doc.bird, Session.DOOR_MOLECULES, getResourceString( "editAddAtoms" ));
						
						collAtomsSel.retainAll( t.selectedAtoms.getAll() );
						
						for( int i = 0; i < collAtomsSel.size(); i++ ) {
							a = ((Atom) collAtomsSel.get( i )).duplicate();
							s = SessionCollection.createUniqueName( Session.SO_NAME_PTRN,
									new Object[] { new Integer( 1 ), Track.ATOM_NAME_PREFIX, "" }, collAtomsNamed );
							a.setName( s );
							collAtomsNamed.add( a );
							collAtomsNew.add( a );
						}
						
						if( !collAtomsNew.isEmpty() ) {
							ce.addEdit( new EditRemoveSessionObjects( this,
								t.selectedAtoms, collAtomsSel, doc.bird, Session.DOOR_MOLECULES ));
							ce.addEdit( new EditAddSessionObjects( this,
								molec.atoms, collAtomsNew, doc.bird, Session.DOOR_MOLECULES ));
							ce.addEdit( new EditAddSessionObjects( this,
								t.selectedAtoms, collAtomsNew, doc.bird, Session.DOOR_MOLECULES ));
							ce.end();
							doc.getUndoManager().addEdit( ce );
						}
					}
				}
				finally {
					doc.bird.releaseExclusive( Session.DOOR_MOLECULES );
				}
				break;
				
			case KeyEvent.VK_L:	// align to timeline pos
				ce	= new BasicSyncCompoundEdit( doc.bird, Session.DOOR_MOLECULES, getResourceString( "editAdjustProbSpan" ));

				if( !doc.bird.attemptExclusive( Session.DOOR_MOLECULES, 250 )) return;
				try {
					final java.util.List collAtomsSel	= t.selectedAtoms.getAll();
					for( int i = 0; i < collAtomsSel.size(); i++ ) {
						a		= (Atom) collAtomsSel.get( i );
						prob	= (Probability) a.probabilities.findByName( Atom.PROB_TIME );
						if( prob != null ) {
							prob.move( this, (double) doc.timeline.getPosition() / rate - prob.getMin(), 0.0, (double) timelineLen / rate,
									   ce, doc.bird, Session.DOOR_MOLECULES );
						}
					}
					ce.end();
					if( ce.isSignificant() ) {
						doc.getUndoManager().addEdit( ce );
					}
				}
				finally {
					doc.bird.releaseExclusive( Session.DOOR_MOLECULES );
				}
				break;
				
			case KeyEvent.VK_F:	// fuse selected molecs
				if( !doc.bird.attemptExclusive( Session.DOOR_MOLECULES, 250 )) return;
				try {
					if( t.selectedMolecules.size() > 1 ) {
						final java.util.List	collMolecs		= t.selectedMolecules.getAll();
						final Molecule			molec			= (Molecule) collMolecs.remove( 0 );
						final java.util.List	collAtomsSel	= t.selectedAtoms.getAll(); // molec.atoms.getAll();
						final java.util.List	collAtomsNamed	= molec.atoms.getAll();
						final java.util.List	collAtomsNew	= new ArrayList();
						Molecule				molec2;
						String					s;
						java.util.List			collAtomsXeno, collAtomsXenoSel;

						ce = new BasicSyncCompoundEdit( doc.bird, Session.DOOR_MOLECULES, getResourceString( "editFuseMolecules" ));

						for( int i = 0; i < collMolecs.size(); i++ ) {
							molec2			= (Molecule) collMolecs.get( i );
							collAtomsXeno	= molec2.atoms.getAll();
							for( int j = 0; j < collAtomsXeno.size(); j++ ) {
								a = ((Atom) collAtomsXeno.get( j )).duplicate();
								s = SessionCollection.createUniqueName( Session.SO_NAME_PTRN,
										new Object[] { new Integer( 1 ), Track.ATOM_NAME_PREFIX, "" }, collAtomsNamed );
								a.setName( s );
								collAtomsNamed.add( a );
								collAtomsNew.add( a );
							}

							if( !collAtomsXeno.isEmpty() ) {
								ce.addEdit( new EditRemoveSessionObjects( this,
									t.selectedAtoms, collAtomsXeno, doc.bird, Session.DOOR_MOLECULES ));
							}

							collAtomsXenoSel = new ArrayList( collAtomsXeno );
							collAtomsXenoSel.retainAll( collAtomsSel );

							if( !collAtomsXenoSel.isEmpty() ) {
								ce.addEdit( new EditRemoveSessionObjects( this,
									t.selectedAtoms, collAtomsXenoSel, doc.bird, Session.DOOR_MOLECULES ));
							}
							if( !collAtomsXeno.isEmpty() ) {
								ce.addEdit( new EditRemoveSessionObjects( this,
									molec2.atoms, collAtomsXeno, doc.bird, Session.DOOR_MOLECULES ));
							}
						}
						
						if( !collMolecs.isEmpty() ) {
							ce.addEdit( new EditRemoveSessionObjects( this,
								t.selectedMolecules, collMolecs, doc.bird, Session.DOOR_MOLECULES ));
							ce.addEdit( new EditRemoveSessionObjects( this,
								t.molecules, collMolecs, doc.bird, Session.DOOR_MOLECULES ));
						}
						
						if( !collAtomsNew.isEmpty() ) {
							ce.addEdit( new EditAddSessionObjects( this,
								molec.atoms, collAtomsNew, doc.bird, Session.DOOR_MOLECULES ));
							ce.addEdit( new EditAddSessionObjects( this,
								t.selectedAtoms, collAtomsNew, doc.bird, Session.DOOR_MOLECULES ));
						}

						ce.end();
						if( ce.isSignificant() ) {
							doc.getUndoManager().addEdit( ce );
						}
					}
				}
				finally {
					doc.bird.releaseExclusive( Session.DOOR_MOLECULES );
				}
				break;
				
			case KeyEvent.VK_ESCAPE: // abort
				if( dragType != DRAG_NONE ) {
					dragType	= DRAG_NONE;
					dragStarted	= false;
					repaint();
				}
				break;
				
			default:
				consume	= false;
				break;
			}
			
			if( consume ) e.consume();
		}

		/**
		 *  Does nothing since we only track keyPressed events.
		 */
		public void keyReleased( KeyEvent e ) {}

		/**
		 *  Does nothing since we only track keyPressed events.
		 */
		public void keyTyped( KeyEvent e ) {}

		public void paintOnTop( Graphics2D g2 )
		{
			if( !dragStarted ) return;
			
			final int	dx = dragX - startX;
			final int	dy = dragY - startY;
			MolecRect	mr;
			AtomRect	ar;
			int			dxl, dyl;

			g2.setStroke( strkMolec );
			g2.setColor( colrMolecDrag );
			
			switch( dragType ) {
			case DRAG_HMOVE:		
				for( int i = 0; i < dragMolecs.size(); i++ ) {
					mr = (MolecRect) dragMolecs.get( i );
					g2.drawRect( mr.x + dx, mr.y, mr.w - 1, mr.h - 1 );
				}
				break;
				
			case DRAG_HSTART:		
				for( int i = 0; i < dragMolecs.size(); i++ ) {
					mr = (MolecRect) dragMolecs.get( i );
					dxl = Math.min( dx, mr.w - 1 );
					g2.drawRect( mr.x + dxl, mr.y, mr.w - 1 - dxl, mr.h - 1 );
				}
				break;
				
			case DRAG_HSTOP:		
				for( int i = 0; i < dragMolecs.size(); i++ ) {
					mr = (MolecRect) dragMolecs.get( i );
					dxl = Math.max( dx, 1 - mr.w );
					g2.drawRect( mr.x, mr.y, mr.w - 1 + dxl, mr.h - 1 );
				}
				break;
				
			case DRAG_VMOVE:
				for( int i = 0; i < dragMolecs.size(); i++ ) {
					mr = (MolecRect) dragMolecs.get( i );
					g2.drawRect( mr.x, mr.y + dy, mr.w - 1, mr.h - 1 );
				}
				break;

			case DRAG_VSTART:		
				for( int i = 0; i < dragMolecs.size(); i++ ) {
					mr = (MolecRect) dragMolecs.get( i );
					dyl = Math.min( dy, mr.h - 1 );
					g2.drawRect( mr.x, mr.y + dyl, mr.w - 1, mr.h - 1 - dyl );
				}
				break;
				
			case DRAG_VSTOP:		
				for( int i = 0; i < dragMolecs.size(); i++ ) {
					mr = (MolecRect) dragMolecs.get( i );
					dyl = Math.max( dy, 1 - mr.h );
					g2.drawRect( mr.x, mr.y, mr.w - 1, mr.h - 1 + dyl );
				}
				break;

			case DRAG_AHMOVE:		
			case DRAG_AFMOVE:		
				for( int i = 0; i < dragAtoms.size(); i++ ) {
					ar = (AtomRect) dragAtoms.get( i );
					g2.drawRect( ar.x + dx, ar.y, ar.w - 1, ar.h - 1 );
				}
				break;
				
			case DRAG_AHSTART:		
				for( int i = 0; i < dragAtoms.size(); i++ ) {
					ar = (AtomRect) dragAtoms.get( i );
					dxl = Math.min( dx, ar.w - 1 );
					g2.drawRect( ar.x + dxl, ar.y, ar.w - 1 - dxl, ar.h - 1 );
				}
				break;
				
			case DRAG_AHSTOP:		
				for( int i = 0; i < dragAtoms.size(); i++ ) {
					ar = (AtomRect) dragAtoms.get( i );
					dxl = Math.max( dx, 1 - ar.w );
					g2.drawRect( ar.x, ar.y, ar.w - 1 + dxl, ar.h - 1 );
				}
				break;
				
			default:
				break;
			}
		}
		
		private MolecRect detectMolec( MouseEvent e )
		{
			MolecRect mr;
		
			for( int i = 0; i < collMolecRects.size(); i++ ) {
				mr = (MolecRect) collMolecRects.get( i );
				if( (mr.x - 1 <= e.getX()) && (mr.y - 1 <= e.getY()) &&
					(mr.x + mr.w >= e.getX()) && (mr.y + mr.h >= e.getY()) ) return mr;
			}
			return null;
		}
		
		private AtomRect detectAtom( MolecRect mr, MouseEvent e )
		{
			AtomRect ar;
			
			if( mr.atomRects == null ) return null;
		
			for( int i = 0; i < mr.atomRects.size(); i++ ) {
				ar = (AtomRect) mr.atomRects.get( i );
				if( (ar.x - 1 <= e.getX()) && (ar.y - 1 <= e.getY()) &&
					(ar.x + ar.w >= e.getX()) && (ar.y + ar.h >= e.getY()) ) return ar;
			}
			return null;
		}
		
		public void mousePressed( MouseEvent e )
		{
			e.getComponent().requestFocus();

			if( !doc.bird.attemptExclusive( Session.DOOR_MOLECULES, 250 )) return;
			try {
				MolecRect		mr = detectMolec( e );
				MolecRect		mr2;
				AtomRect		ar;
				java.util.List	collAtomRects;
				CompoundEdit	ce;
				java.util.List	coll;
			
				if( mr == null) {
					dragType	= DRAG_NONE;
					
					if( e.isMetaDown() ) {
						// nothin
					} else if( e.isAltDown() ) {
						// nothin
					} else if( e.isShiftDown() ) {
						// nothin
					} else {
						deselectAllMolecs();
					}
				} else {
					ar = detectAtom( mr, e );
				
					if( e.isMetaDown() ) {							// send to back
						collMolecRects.remove( mr );
						collMolecRects.add( mr );
					} else if( e.isAltDown() ) {					// delete
						if( (ar == null) || (mr.molec.atoms.size() <= 1) ) {
							ce = new BasicSyncCompoundEdit( doc.bird, Session.DOOR_MOLECULES,
								getResourceString( "editRemoveMolecs" ));
							collMolecRects.remove( mr );
							coll = new ArrayList( 1 );
							coll.add( mr.molec );
							ce.addEdit( new EditRemoveSessionObjects( this,
								t.selectedAtoms, t.selectedAtoms.getAll(), doc.bird, Session.DOOR_MOLECULES ));
							ce.addEdit( new EditRemoveSessionObjects( this,
								mr.molec.atoms, mr.molec.atoms.getAll(), doc.bird, Session.DOOR_MOLECULES ));
							ce.addEdit( new EditRemoveSessionObjects( this,
								t.selectedMolecules, coll, doc.bird, Session.DOOR_MOLECULES ));
							ce.addEdit( new EditRemoveSessionObjects( this,
								t.molecules, coll, doc.bird, Session.DOOR_MOLECULES ));
							ce.end();
							doc.getUndoManager().addEdit( ce );
							
						} else {
							ce = new BasicSyncCompoundEdit( doc.bird, Session.DOOR_MOLECULES,
								getResourceString( "editRemoveAtoms" ));
							coll = new ArrayList( 1 );
							coll.add( ar.a );
							mr.atomRects.remove( ar );
							ce.addEdit( new EditRemoveSessionObjects( this,
								t.selectedAtoms, coll, doc.bird, Session.DOOR_MOLECULES ));
							ce.addEdit( new EditRemoveSessionObjects( this,
								mr.molec.atoms, coll, doc.bird, Session.DOOR_MOLECULES ));
							ce.end();
							doc.getUndoManager().addEdit( ce );
						}
						
					} if( e.isShiftDown() ) {
						if( e.isControlDown() ) {					// move atom file region
							if( ar != null ) {
								dragType		= DRAG_AFMOVE;
							}
						} else {									// add/remove selection
							if( mr.selected ) {						// remove from selection
								t.selectedMolecules.remove( this, mr.molec );
								mr.selected = false;
							} else {								// add to selection
								t.selectedMolecules.add( this, mr.molec );
								mr.selected = true;
							}
						}
					} else {										// single selection, drag init
						if( e.isControlDown() ) {					// drag atoms
							if( ar != null ) {
								if( Math.abs( e.getX() - ar.x ) <= 3 ) {
									dragType	= DRAG_AHSTART;
								} else if( Math.abs( e.getX() - ar.x - ar.w ) <= 4 ) {
									dragType	= DRAG_AHSTOP;
								} else {
									dragType	= DRAG_AMOVE;
								}
							}
						} else {									// drag molecs
							if( Math.abs( e.getX() - mr.x ) <= 3 ) {
								dragType	= DRAG_HSTART;
							} else if( Math.abs( e.getY() - mr.y ) <= 3 ) {
								dragType	= DRAG_VSTART;
							} else if( Math.abs( e.getX() - mr.x - mr.w ) <= 4 ) {
								dragType	= DRAG_HSTOP;
							} else if( Math.abs( e.getY() - mr.y - mr.h ) <= 4 ) {
								dragType	= DRAG_VSTOP;
							} else {
								dragType	= DRAG_MOVE;
							}
						}
						if( !mr.selected ) {						// replace selection
							deselectAllMolecs();
							t.selectedMolecules.add( this, mr.molec );
							mr.selected = true;
							if( ar != null ) {
								t.selectedAtoms.add( this, ar.a );
								ar.selected = true;
							}
						} else {
testAtomSelection:			if( ar != null ) {
								if( t.selectedAtoms.size() > 1 ) {
									deselectAllAtoms();
								} else if( t.selectedAtoms.size() == 1 ) {
									if( t.selectedAtoms.get( 0 ) == ar.a ) break testAtomSelection;
									deselectAllAtoms();
								}
								t.selectedAtoms.add( this, ar.a );
								ar.selected = true;
							}
						}
					}
				}
				
				if( dragType != DRAG_NONE ) {
					dragStarted = false;
					startX		= e.getX();
					startY		= e.getY();
					dragMolecs.clear();
					dragAtoms.clear();
					if( dragType < DRAG_AMOVE ) {
						for( int i = 0; i < collMolecRects.size(); i++ ) {
							mr = (MolecRect) collMolecRects.get( i );
							if( mr.selected ) dragMolecs.add( mr );
						}
						if( dragMolecs.isEmpty() ) {
							dragType	= DRAG_NONE;
						}
					} else {
						for( int i = 0; i < collMolecRects.size(); i++ ) {
							mr = (MolecRect) collMolecRects.get( i );
							if( mr.selected ) {
								collAtomRects = mr.atomRects;
								for( int j = 0; j < collAtomRects.size(); j++ ) {
									ar = (AtomRect) collAtomRects.get( j );
									if( ar.selected ) dragAtoms.add( ar );
								}
							}
						}
						if( dragAtoms.isEmpty() ) {
							dragType	= DRAG_NONE;
						}
					}
					repaint();
				}
			}
			finally {
				doc.bird.releaseExclusive( Session.DOOR_MOLECULES );
			}
		}

		private void deselectAllMolecs()
		{
			t.selectedMolecules.clear( this );
			t.selectedAtoms.clear( this );

			MolecRect mr;
		
			for( int i = 0; i < collMolecRects.size(); i++ ) {
				mr = (MolecRect) collMolecRects.get( i );
				mr.selected = false;
				if( mr.atomRects != null ) {
					for( int j = 0; j < mr.atomRects.size(); j++ ) {
						((AtomRect) mr.atomRects.get( j )).selected = false;
					}
				}
			}
		}

		private void deselectAllAtoms()
		{
			t.selectedAtoms.clear( this );

			MolecRect mr;
		
			for( int i = 0; i < collMolecRects.size(); i++ ) {
				mr = (MolecRect) collMolecRects.get( i );
				if( mr.atomRects != null ) {
					for( int j = 0; j < mr.atomRects.size(); j++ ) {
						((AtomRect) mr.atomRects.get( j )).selected = false;
					}
				}
			}
		}

		public void mouseDragged( MouseEvent e )
		{
			if( dragType != DRAG_NONE ) {
				if( !dragStarted ) {
					if( Math.abs( e.getX() - startX ) > 4 ) {
						if( dragType == DRAG_MOVE ) {
							dragType = DRAG_HMOVE;
						} else if( dragType == DRAG_AMOVE ) {
							dragType = DRAG_AHMOVE;
						}
						dragStarted = true;
					} else if( Math.abs( e.getY() - startY ) > 4 ) {
						if( dragType == DRAG_MOVE ) dragType = DRAG_VMOVE;
						dragStarted = true;
					}
				}
				dragX = e.getX();
				dragY = e.getY();
				repaint();
			}
		}
		
		public void mouseReleased( MouseEvent e )
		{
			if( dragStarted ) {
				if( doc.bird.attemptExclusive( Session.DOOR_MOLECULES, 250 )) {
					try {
						final double			dx		= (double) (dragX - startX) / recentWidth;
						final double			dy		= (double) (dragY - startY) / recentHeight;
						final double			dt		= (visibleSpan.getLength() / rate) * dx;
						final double			maxt	= (double) timelineLen / rate - 1.0; // -1 willkuerlich, um "verschwinden" des molekuels zu vermeiden
						double					d;
						Molecule				molec, molec2;
						Atom					a;
						Probability				prob;
						String					s;
						final SyncCompoundEdit	ce		= new BasicSyncCompoundEdit( doc.bird, Session.DOOR_MOLECULES,
															getResourceString( "editDragObjects" ));

						switch( dragType ) {
						case DRAG_HMOVE:
							if( e.isAltDown() ) {		// duplicate
								final java.util.List	collMolecsSel	= t.selectedMolecules.getAll();
								final java.util.List	collMolecsNamed	= t.molecules.getAll();
								final java.util.List	collMolecsNew	= new ArrayList( dragMolecs.size() );
								final java.util.List	collNewSelAtoms	= new ArrayList();
								for( int i = 0; i < dragMolecs.size(); i++ ) {
									molec	= ((MolecRect) dragMolecs.get( i )).molec;
									molec2	= molec.duplicate();
									s		= SessionCollection.createUniqueName( Session.SO_NAME_PTRN,
										new Object[] { new Integer( 1 ), Track.MOLECULE_NAME_PREFIX, "" }, collMolecsNamed );
									molec2.setName( s );
									collMolecsNamed.add( molec2 );
									collMolecsNew.add( molec2 );
									for( int j = 0; j < molec.atoms.size(); j++ ) {
										a		= (Atom) molec.atoms.get( j );
										prob	= (Probability) a.probabilities.findByName( Atom.PROB_TIME );
										if( prob != null ) {
											prob.move( this, dt, 0.0, maxt, ce, doc.bird, Session.DOOR_MOLECULES );
										}
										collNewSelAtoms.add( a );
									}
								}
								ce.addEdit( new EditRemoveSessionObjects( this,
									t.selectedMolecules, collMolecsSel, doc.bird, Session.DOOR_MOLECULES ));
								ce.addEdit( new EditRemoveSessionObjects( this,
									t.selectedAtoms, t.selectedAtoms.getAll(), doc.bird, Session.DOOR_MOLECULES ));
								ce.addEdit( new EditAddSessionObjects( this,
									t.molecules, collMolecsNew, doc.bird, Session.DOOR_MOLECULES ));
								ce.addEdit( new EditAddSessionObjects( this,
									t.selectedMolecules, collMolecsNew, doc.bird, Session.DOOR_MOLECULES ));
								ce.addEdit( new EditAddSessionObjects( this,
									t.selectedAtoms, collNewSelAtoms, doc.bird, Session.DOOR_MOLECULES ));
								break;
							}
							// THRU
						case DRAG_HSTART:
						case DRAG_HSTOP:
							for( int i = 0; i < dragMolecs.size(); i++ ) {
								molec = ((MolecRect) dragMolecs.get( i )).molec;
								for( int j = 0; j < molec.atoms.size(); j++ ) {
									a		= (Atom) molec.atoms.get( j );
									prob	= (Probability) a.probabilities.findByName( Atom.PROB_TIME );
									if( prob != null ) {
										switch( dragType ) {
										case DRAG_HMOVE:
											prob.move( this, dt, 0.0, maxt, ce, doc.bird, Session.DOOR_MOLECULES );
											break;
										case DRAG_HSTART:
											prob.shiftStart( this, dt, 0.0, maxt, ce, doc.bird, Session.DOOR_MOLECULES );
											break;
										case DRAG_HSTOP:
											prob.shiftStop( this, dt, 0.0, maxt, ce, doc.bird, Session.DOOR_MOLECULES );
											break;
										default:
											assert false : dragType;
											break;
										}
									}
								}
							}
							break;

						case DRAG_VMOVE:
						case DRAG_VSTART:
						case DRAG_VSTOP:
							for( int i = 0; i < dragMolecs.size(); i++ ) {
								molec = ((MolecRect) dragMolecs.get( i )).molec;
								switch( dragType ) {
								case DRAG_VMOVE:
									molec.moveVertical( this, dy, 0.0, 1.0, ce, doc.bird, Session.DOOR_MOLECULES );
									break;
								case DRAG_VSTART:
									molec.shiftVerticalStart( this, dy, 0.0, 1.0, ce, doc.bird, Session.DOOR_MOLECULES );
									break;
								case DRAG_VSTOP:
									molec.shiftVerticalStop( this, dy, 0.0, 1.0, ce, doc.bird, Session.DOOR_MOLECULES );
									break;
								default:
									assert false : dragType;
									break;
								}
							}
							break;

						case DRAG_AHMOVE:
						case DRAG_AHSTART:
						case DRAG_AHSTOP:
							for( int i = 0; i < dragAtoms.size(); i++ ) {
								a		= ((AtomRect) dragAtoms.get( i )).a;
								prob	= (Probability) a.probabilities.findByName( Atom.PROB_TIME );
								if( prob != null ) {
									switch( dragType ) {
									case DRAG_AHMOVE:
										prob.move( this, dt, 0.0, maxt, ce, doc.bird, Session.DOOR_MOLECULES );
										break;
									case DRAG_AHSTART:
										prob.shiftStart( this, dt, 0.0, maxt, ce, doc.bird, Session.DOOR_MOLECULES );
										break;
									case DRAG_AHSTOP:
										prob.shiftStop( this, dt, 0.0, maxt, ce, doc.bird, Session.DOOR_MOLECULES );
										break;
									default:
										assert false : dragType;
										break;
									}
								}
							}
							break;

						case DRAG_AFMOVE:
							for( int i = 0; i < dragAtoms.size(); i++ ) {
								a = ((AtomRect) dragAtoms.get( i )).a;
								d = a.getFileLength();
								a.setFileStart( this, Math.max( 0.0, a.getFileStart() - dt ));
								a.setFileStop( this, a.getFileStart() + d );
							}
							break;

						default:
							System.err.println( "drag not implemented : "+dragType );
							break;
						}
						
						ce.end();
						if( ce.isSignificant() ) {
							doc.getUndoManager().addEdit( ce );
						}
					}
					finally {
						doc.bird.releaseExclusive( Session.DOOR_MOLECULES );
					}
				}
				dragStarted = false;
				dragType	= DRAG_NONE;
				repaint();
			} else {
				dragType	= DRAG_NONE;
			}
		}

		public void mouseClicked( MouseEvent e )
		{
//			if( (e.getClickCount() == 2) && !e.isMetaDown() && !transport.isRunning() ) {
//				transport.goPlay( 1.0f );
//			}
		}

		// on Mac, Ctrl+Click is interpreted as
		// popup trigger by the system which means
		// no successive mouseDragged calls are made,
		// instead mouseMoved is called ...
		public void mouseMoved( MouseEvent e )
		{
			mouseDragged( e );
//			System.err.println( "mouseMoved "+e.getX()+", "+e.getY() );
		}

		public void mouseEntered( MouseEvent e ) {
//			System.err.println( "mouseEntered" );
		}
		
		public void mouseExited( MouseEvent e ) {
//			System.err.println( "mouseExited" );
		}
	}
	
	private static class MolecRect
	{
		private final Molecule		molec;
		private final int			x, y, w, h;
		private boolean				selected;
		private java.util.List		atomRects	= null;
		
		private MolecRect( Molecule molec, int x, int y, int w, int h, boolean selected )
		{
			this.molec		= molec;
			this.x			= x;
			this.y			= y;
			this.w			= w;
			this.h			= h;
			this.selected	= selected;
		}
	}

	private static class AtomRect
	{
		private final Atom		a;
		private final int		x, y, w, h;
		private boolean			selected;
		private final Molecule	molec;
		
		private AtomRect( Atom a, Molecule molec, int x, int y, int w, int h, boolean selected )
		{
			this.a			= a;
			this.molec		= molec;
			this.x			= x;
			this.y			= y;
			this.w			= w;
			this.h			= h;
			this.selected	= selected;
		}
	}
}
