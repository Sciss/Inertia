/*
 *  MarkerAxis.java
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
 *		12-Aug-05	created from de.sciss.eisenkraut.timeline.MarkerAxis
 */

package de.sciss.inertia.timeline;

import java.awt.*;
import java.awt.event.*;
import java.awt.font.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.text.*;
import java.util.*;
import java.util.prefs.*;
import javax.swing.*;
import javax.swing.undo.*;

// INERTIA
//import de.sciss.eisenkraut.*;
//import de.sciss.eisenkraut.edit.*;
//import de.sciss.eisenkraut.gui.*;
//import de.sciss.eisenkraut.io.*;
//import de.sciss.eisenkraut.session.*;
//import de.sciss.eisenkraut.util.*;
import de.sciss.util.LockManager;
import de.sciss.inertia.edit.BasicSyncCompoundEdit;
import de.sciss.inertia.edit.SyncCompoundEdit;
import de.sciss.inertia.io.MarkerManager;
import de.sciss.inertia.timeline.Timeline;
import de.sciss.app.Document;

import de.sciss.app.AbstractApplication;
import de.sciss.app.DynamicAncestorAdapter;
import de.sciss.app.DynamicListening;
import de.sciss.app.DynamicPrefChangeManager;
import de.sciss.app.LaterInvocationManager;

import de.sciss.gui.HelpGlassPane;

import de.sciss.io.AudioFile;
import de.sciss.io.AudioFileDescr;
import de.sciss.io.Marker;
import de.sciss.io.Region;
import de.sciss.io.Span;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.5, 03-Aug-05
 *
 *	@todo		uses TimelineListener to
 *				not miss document changes. should use 
 *				a document change listener!
 *
 *	@todo		marker sortierung sollte zentral von sesion o.ae. vorgenommen
 *				werden sobald neues file geladen wird!
 *
 *	@todo		recalcDisplay shouldn't try to lock, visibleSpan has
 *				been saved in timelineScrolled already, only need to
 *				updated once more in startListening
 */
public class MarkerAxis
extends JComponent
implements	TimelineListener, MouseListener, MouseMotionListener,
			DynamicListening, MarkerManager.Listener // , LaterInvocationManager.Listener
{
    private final MarkerManager	markers;
    private final Timeline		timeline;
    private final Document		doc;
    private final LockManager	lm;
    private final int			doors;

	private Marker				dragMark		= null;
	private Marker				dragLastMark	= null;
	private boolean				dragStarted		= false;
	private int					dragStartX		= 0;
	  
	private static final Font	fntLabel		= new Font( "Helvetica", Font.ITALIC, 10 );

	private String[]			markLabels		= new String[0];
	private int[]				markFlagPos		= new int[0];
	private int					numMarkers		= 0;
	private int					numRegions		= 0;	// XXX not yet used
	private final GeneralPath   shpFlags		= new GeneralPath();
	private int					recentWidth		= -1;
	private boolean				doRecalc		= true;
	private Span				visibleSpan		= new Span();
	private double				scale			= 1.0;

	private static final int[] pntBarGradientPixels = { 0xFFB8B8B8, 0xFFC0C0C0, 0xFFC8C8C8, 0xFFD3D3D3,
														0xFFDBDBDB, 0xFFE4E4E4, 0xFFEBEBEB, 0xFFF1F1F1,
														0xFFF6F6F6, 0xFFFAFAFA, 0xFFFBFBFB, 0xFFFCFCFC,
														0xFFF9F9F9, 0xFFF4F4F4, 0xFFEFEFEF };
	private static final int barExtent = pntBarGradientPixels.length;

	private static final int[] pntMarkGradientPixels ={ 0xFF5B8581, 0xFF618A86, 0xFF5D8682, 0xFF59827E,
														0xFF537D79, 0xFF4F7975, 0xFF4B7470, 0xFF47716D,
														0xFF446E6A, 0xFF426B67, 0xFF406965, 0xFF3F6965,
														0xFF3F6864 };	// , 0xFF5B8581

//	private static final Paint	pntMarkStick= new Color( 0x31, 0x50, 0x4D, 0xC0 );
	private static final Paint	pntMarkStick= new Color( 0x31, 0x50, 0x4D, 0x7F );
	private static final Stroke	strkStick	= new BasicStroke( 1.0f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_BEVEL,
		1.0f, new float[] { 4.0f, 4.0f }, 0.0f );

	private static final int markExtent = pntMarkGradientPixels.length;
	private final Paint  pntBackground;
	private final Paint  pntMarkFlag;

	/**
	 *  Constructs a new object for
	 *  displaying the timeline ruler
	 *
	 *  @param  doc		session Session
	 */
	public MarkerAxis( MarkerManager markers, Timeline timeline, de.sciss.app.Document doc, LockManager lm, int doors )
	{
		super();
        
        this.markers	= markers;
        this.timeline	= timeline;
        this.doc		= doc;
        this.lm			= lm;
		this.doors		= doors;
		
		BufferedImage img;
		
		setMaximumSize( new Dimension( getMaximumSize().width, barExtent ));
		setMinimumSize( new Dimension( getMinimumSize().width, barExtent ));
		setPreferredSize( new Dimension( getPreferredSize().width, barExtent ));

		img			= new BufferedImage( 1, barExtent, BufferedImage.TYPE_INT_ARGB );
		img.setRGB( 0, 0, 1, barExtent, pntBarGradientPixels, 0, 1 );
		pntBackground = new TexturePaint( img, new Rectangle( 0, 0, 1, barExtent ));
		img			= new BufferedImage( 1, markExtent, BufferedImage.TYPE_INT_ARGB );
		img.setRGB( 0, 0, 1, markExtent, pntMarkGradientPixels, 0, 1 );
		pntMarkFlag	= new TexturePaint( img, new Rectangle( 0, 0, 1, markExtent ));

		setOpaque( true );

		// --- Listener ---
        new DynamicAncestorAdapter( this ).addTo( this );
//		new DynamicAncestorAdapter( new DynamicPrefChangeManager(
//			AbstractApplication.getApplication().getUserPrefs(), new String[] { PrefsUtil.KEY_TIMEUNITS }, this
//		)).addTo( this );
		this.addMouseListener( this );
		this.addMouseMotionListener( this );

		// ------
        HelpGlassPane.setHelp( this, "MarkerAxis" );
	}
	
	private String getResourceString( String key )
	{
		return( AbstractApplication.getApplication().getResourceString( key ));
	}
	
	// sync: attempts shared on timeline
	private void recalcDisplay( FontMetrics fm )
	{
		java.util.List	collMarkers;
		long			start, stop;
		Marker			mark;

		shpFlags.reset();
		numMarkers	= 0;
		numRegions	= 0;
		
		if( !lm.attemptShared( doors, 250 )) return;
		try {
			visibleSpan = timeline.getVisibleSpan();	// so we don't have to do that after startListening
			start		= visibleSpan.getStart();
			stop		= visibleSpan.getStop();
			scale		= (double) recentWidth / (stop - start);
			
			collMarkers	= markers.getMarkers( visibleSpan, true, false );
			numMarkers	= collMarkers.size();
			if( (numMarkers > markLabels.length) || (numMarkers < (markLabels.length >> 1)) ) {
				markLabels		= new String[ numMarkers * 3 / 2 ];		// 'decent growing and shrinking'
				markFlagPos		= new int[ markLabels.length ];
			}
			
			for( int i = 0; i < numMarkers; i++ ) {
				mark				= (Marker) collMarkers.get( i );
				markLabels[ i ]		= mark.name;
				markFlagPos[ i ]	= (int) (((mark.pos - start) * scale) + 0.5);
				shpFlags.append( new Rectangle( markFlagPos[ i ], 1, fm.stringWidth( mark.name ) + 8, markExtent ), false );
			}

//			coll	= (java.util.List) afd.getProperty( AudioFileDescr.KEY_REGIONS );
//			if( coll != regions ) {
//				regions		= coll;
//				regionIdx	= 0;
//			}
//			if( (regions != null) && !regions.isEmpty() ) {
//			
//			}
			doRecalc	= false;
		}
		finally {
			lm.releaseShared( doors );
		}
	}

	public void paintComponent( Graphics g )
	{
		super.paintComponent( g );
		
        final Graphics2D	g2	= (Graphics2D) g;
		final FontMetrics	fm	= g2.getFontMetrics();

		final int			y;

		if( doRecalc || (recentWidth != getWidth()) ) {
			recentWidth = getWidth();
			recalcDisplay( fm );
		}

		g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF );

		g2.setPaint( pntBackground );
		g2.fillRect( 0, 0, recentWidth, barExtent );

		g2.setPaint( pntMarkFlag );
		g2.fill( shpFlags );

		g2.setColor( Color.white );
		g2.setFont( fntLabel );

		g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );

		y   = fm.getAscent(); // markExtent - 2 - fm.getMaxDescent();
		for( int i = 0; i < numMarkers; i++ ) {
			g2.drawString( markLabels[i], markFlagPos[i] + 4 , y );
		}
    }

	public void paintFlagSticks( Graphics2D g2, Rectangle bounds )
	{
		if( doRecalc ) {
			recalcDisplay( g2.getFontMetrics() );	// XXX nicht ganz sauber (anderer graphics-context!)
		}
	
		int				i, x;
		final Stroke	strkOrig	= g2.getStroke();
	
		g2.setPaint( pntMarkStick );
		g2.setStroke( strkStick );
		for( i = 0; i < numMarkers; i++ ) {
			g2.drawLine( markFlagPos[i], bounds.y, markFlagPos[i], bounds.y + bounds.height );
		}
		g2.setStroke( strkOrig );
	}

	private void triggerRedisplay()
	{
		doRecalc	= true;
		repaint();
	}
  
	public void addMarker( long pos )
	{
		final SyncCompoundEdit	ce;
	
		if( !lm.attemptExclusive( doors, 250 )) return;
		try {
			pos		= Math.max( 0, Math.min( timeline.getLength(), pos ));
			ce		= new BasicSyncCompoundEdit( lm, doors,
							getResourceString( "editAddMarker" ));
			markers.addMarker( this, new Marker( pos, "Mark" ), ce );
			ce.end();
			doc.getUndoManager().addEdit( ce );
		}
		finally {
			lm.releaseExclusive( doors );
		}
	}
	
	private void removeMarkerLeftTo( long pos )
	{
		final SyncCompoundEdit	ce;
		final int				idx;
	
		if( !lm.attemptExclusive( doors, 250 )) return;
		try {
			pos		= Math.max( 0, Math.min( timeline.getLength(), pos ));
			idx		= markers.indexOf( pos, false );
			if( idx == -1 ) return;
			
			ce		= new BasicSyncCompoundEdit( lm, doors,
							getResourceString( "editDeleteMarker" ));
			markers.removeMarkers( this, idx, idx, ce );
			ce.end();
			doc.getUndoManager().addEdit( ce );
		}
		finally {
			lm.releaseExclusive( doors );
		}
	}
	
	private void renameMarkerLeftTo( long pos )
	{
		final SyncCompoundEdit	ce;
		final Marker			mark;
		final String			result;
		int						idx;
	
		if( !lm.attemptShared( doors, 250 )) return;
		try {
			pos		= Math.max( 0, Math.min( timeline.getLength(), pos ));
			idx		= markers.indexOf( pos, false );
			if( idx == -1 ) return;
			mark	= markers.getMarker( idx );
			if( mark == null ) return;
		}
		finally {
			lm.releaseShared( doors );
		}

		result	= JOptionPane.showInputDialog( this, getResourceString( "inputDlgRenameMarker" ), mark.name );
		if( result == null ) return;
			
		if( !lm.attemptExclusive( doors, 250 )) return;
		try {
			idx		= markers.indexOf( mark );
			if( idx == -1 ) {
				System.err.println( "Lost marker! " + mark.name );
				return;
			}
			ce		= new BasicSyncCompoundEdit( lm, doors,
							getResourceString( "editRenameMarker" ));
			markers.removeMarkers( this, idx, idx, ce );
			markers.addMarker( this, new Marker( mark.pos, result ), ce );
			ce.end();
			doc.getUndoManager().addEdit( ce );
		}
		finally {
			lm.releaseExclusive( doors );
		}
	}

	private Marker getMarkerLeftTo( long pos )
	{
		final int	idx;
	
		if( !lm.attemptShared( doors, 250 )) return null;
		try {
			pos		= Math.max( 0, Math.min( timeline.getLength(), pos ));
			idx		= markers.indexOf( pos, false );
			if( idx == -1 ) return null;
			return markers.getMarker( idx );
		}
		finally {
			lm.releaseShared( doors );
		}
	}

// ---------------- LaterInvocationManager.Listener interface ---------------- 

		// called by DynamicPrefChangeManager ; o = PreferenceChangeEvent
//		public void laterInvocation( Object o )
//		{
//			final PreferenceChangeEvent	pce = (PreferenceChangeEvent) o;
//			final String				key = pce.getKey();
//			
//			if( key.equals( PrefsUtil.KEY_TIMEUNITS )) {
//				int timeUnits = pce.getNode().getInt( key, 0 );
//				setFlags( timeUnits == 0 ? 0 : TIMEFORMAT );
//				recalcDisplay();
//			}
//		}

// ---------------- DynamicListening interface ---------------- 

    public void startListening()
    {
		timeline.addTimelineListener( this );
//		doc.tracks.addListener( this );
		markers.addListener( this );
		triggerRedisplay();
    }

    public void stopListening()
    {
		markers.removeListener( this );
// 		doc.tracks.removeListener( this );
        timeline.removeTimelineListener( this );
    }

// ---------------- MarkerManager.Listener interface ---------------- 

	public void markersModified( MarkerManager.Event e )
	{
		if( e.getAffectedSpan().touches( visibleSpan )) {
			triggerRedisplay();
		}
	}

// ---------------- MouseListener interface ---------------- 
// we're listening to the ourselves

	public void mouseEntered( MouseEvent e )
	{
//		if( isEnabled() ) dispatchMouseMove( e );
	}
	
	public void mouseExited( MouseEvent e ) {}

	public void mousePressed( MouseEvent e )
    {
		final long pos = (long) (e.getX() / scale + visibleSpan.getStart() + 0.5);
	
		if( shpFlags.contains( e.getPoint() )) {
			if( e.isAltDown() ) {					// delete marker
				removeMarkerLeftTo( pos + 1 );
			} else if( e.getClickCount() == 2 ) {	// rename
				renameMarkerLeftTo( pos + 1 );
			} else {								// start drag
				dragMark	= getMarkerLeftTo( pos + 1 );
				dragLastMark= dragMark;
				dragStarted	= false;
				dragStartX	= e.getX();
			}
			
		} else if( !e.isAltDown() && (e.getClickCount() == 2) ) {		// insert marker
			addMarker( pos );
		}
	}

	public void mouseReleased( MouseEvent e )
	{
		int					idx;
		SyncCompoundEdit	ce;
	
		if( dragLastMark != null ) {
			if( !lm.attemptExclusive( doors, 250 )) return;
			try {
				// ok this is tricky and totally stupid, have to replace it some day XXX
				idx			= markers.indexOf( dragLastMark );
				if( idx >= 0 ) {
					markers.removeMarkers( this, idx, idx, null );	// remove temporary marker
				} else {
					System.err.println( "! marker lost : " + dragLastMark.name );
				}
				markers.addMarker( this, dragMark, null );			// restore original marker for undoable edit!
				ce	= new BasicSyncCompoundEdit( lm, doors, getResourceString( "editMoveMarker" ));
				markers.replaceMarker( this, dragMark, dragLastMark, ce );
				ce.end();
				doc.getUndoManager().addEdit( ce );
			}
			finally {
				lm.releaseExclusive( doors );
			}
		}
		
		dragStarted		= false;
		dragMark		= null;
		dragLastMark	= null;
	}
	
	public void mouseClicked( MouseEvent e ) {}

// ---------------- MouseMotionListener interface ---------------- 
// we're listening to ourselves

    public void mouseMoved( MouseEvent e ) {}

	public void mouseDragged( MouseEvent e )
	{
		if( dragMark == null ) return;

		if( !dragStarted ) {
			if( Math.abs( e.getX() - dragStartX ) < 5 ) return;
			dragStarted = true;
		}

		Marker newMark;
	
		long pos = (long) ((e.getX() - dragStartX) / scale + dragMark.pos + 0.5);

		if( !lm.attemptExclusive( doors, 250 )) return;
		try {
			pos			= Math.max( 0, Math.min( timeline.getLength(), pos ));
			newMark		= new Marker( pos, dragMark.name );
			markers.replaceMarker( this, dragLastMark, newMark, null );
			dragLastMark= newMark;
		}
		finally {
			lm.releaseExclusive( doors );
		}
	}

// ---------------- TimelineListener interface ---------------- 
  
   	public void timelineSelected( TimelineEvent e ) {}
	public void timelinePositioned( TimelineEvent e ) {}

	public void timelineChanged( TimelineEvent e )
	{
		triggerRedisplay();
	}

   	public void timelineScrolled( TimelineEvent e )
    {
		try {
			lm.waitShared( doors );
			visibleSpan = timeline.getVisibleSpan();
			scale		= (double) getWidth() / visibleSpan.getLength();
			
			triggerRedisplay();
		}
		finally {
			lm.releaseShared( doors );
		}
    }
}