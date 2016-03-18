/*
 *  DocumentFrame.java
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
 *		07-Aug-05	created
 */

package de.sciss.inertia.session;

import de.sciss.app.*;
import de.sciss.gui.GUIUtil;
import de.sciss.gui.MenuAction;
import de.sciss.gui.NumberField;
import de.sciss.gui.ProgressBar;
import de.sciss.inertia.Main;
import de.sciss.inertia.edit.*;
import de.sciss.inertia.gui.*;
import de.sciss.inertia.gui.SessionCollectionTable;
import de.sciss.inertia.io.MarkerManager;
import de.sciss.inertia.realtime.*;
import de.sciss.inertia.timeline.*;
import de.sciss.inertia.timeline.ChannelRowHeader;
import de.sciss.inertia.util.PrefsUtil;
import de.sciss.io.IOUtil;
import de.sciss.io.Marker;
import de.sciss.io.Region;
import de.sciss.io.Span;
import de.sciss.util.*;
import org.w3c.dom.DOMException;
import org.w3c.dom.Element;

import javax.swing.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.31, 04-Dec-05
 *
 *	@todo	import markers : does not support white space in columns
 *			(even if enclosed in quotation marks)
 *			; undo produces error when timeline was empty before
 */
public class DocumentFrame
extends BasicFrame
implements ProgressComponent, TimelineListener, RealtimeConsumer, ToolActionListener
{
	private final JLabel					lbProgress;
	private final ProgressBar				pb;
	private final TimelineViewport			vpTrackPanel;
//	private final InertiaPanel				ip;

	private final Main						root;
	private final Session					doc;
	
	private final DocumentFrame				enc_this	= this;

//	private final LaterInvocationManager	lim;

	private Span							timelineSel;
	private Span							timelineVis;
	private final long[]					timelinePos;

    private final TimelineScroll			scroll;

	private final MultiTransport			transport;
	private final Timeline					timeline;

	private final WindowAdapter				winListener;

	private final actionCloseClass			actionClose;
	private final actionSaveClass			actionSave;
	private final actionImportMarkersClass	actionImportMarkers;
	private final actionSaveAsClass			actionSaveAs;
	private final actionSaveAsClass			actionSaveCopy;
//	private final actionCutClass			actionCut;
//	private final actionCopyClass			actionCopy;
	private final actionPasteClass			actionPaste;
//	private final actionDeleteClass			actionDelete;
	private final actionSelectAllClass		actionSelectAll;
	private final actionTrimToSelectionClass actionTrimToSelection;
	private final actionInsertSilenceClass	actionInsertSilence;
	private final actionMissingLinksClass	actionMissingLinks;
	private final actionCompactAudioClass	actionCompactAudio;
	
	private final actionSpanWidthClass		actionIncWidth;
	private final actionSpanWidthClass		actionDecWidth;

    private final TimelineAxis				timeAxis;
    private final MarkerAxis				markAxis;

	private final java.util.List			collChannelHeaders		= new ArrayList();
	private final java.util.List			collChannelRulers		= new ArrayList();
	private final JPanel					ggTrackPanel;
	private final JPanel					ggTrackRowHeaderPanel;
	private final java.util.List			collOverviews			= new ArrayList();
	
	// basicframe class prefs
	private static final String				KEY_TRACK_DIVIDER		= "layerdivider";
	private static final String				KEY_MOLECULE_DIVIDER	= "moleculedivider";
	private final JSplitPane				splitMolecule;
	private final JSplitPane				splitTrack;

	// --- tools ---
	
	private final   Map						tools					= new HashMap();
	private			AbstractTool			activeTool				= null;
	private final	TimelinePointerTool		pointerTool;
	
	private final TransportToolBar			transTB;
	private int								activeChannel			= 0;

	private final Color[]					colrPosition;

	public DocumentFrame( final Main root, final Session doc )
	{
		super( "" );
	
		this.doc			= doc;
		this.root			= root;
		timeline			= doc.timeline;
		timelinePos			= new long[ doc.layers.getNumLayers() ];
		timelinePos[ activeChannel ] = timeline.getPosition();
		timelineSel			= timeline.getSelectionSpan();
		timelineVis			= timeline.getVisibleSpan();

		Color opaque;
		colrPosition		= new Color[ timelinePos.length ];
		for( int ch = 0; ch < colrPosition.length; ch++ ) {
			opaque = Color.getHSBColor( (float) ch / colrPosition.length, 0.75f, 0.75f );
			colrPosition[ ch ] = new Color( opaque.getRed(), opaque.getGreen(), opaque.getBlue(), 0x4F );
		}

		final Application				app				= AbstractApplication.getApplication();
		final Container					cp				= getContentPane();
		final JPanel					panelMain		= new JPanel( new BorderLayout() );
		final JPanel					panelTab		= new JPanel();
		final JComboBox					ggVerticalView	= new JComboBox();
		final JScrollPane				ggScrollPane	= new JScrollPane();

// UUU
//		final AtomTable					atomTable		= new AtomTable( doc );
		final ProbabilityTableTable		probTableTable	= new ProbabilityTableTable( doc );
		final JRootPane					rp				= getRootPane();
		final InputMap					imap			= rp.getInputMap( JComponent.WHEN_IN_FOCUSED_WINDOW );
		final ActionMap					amap			= rp.getActionMap();
		final int						menuShortcut	= Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
		final JPanel					ggAxisPanel;
		final TimelineToolBar			timeTB			= new TimelineToolBar();
		final Box						topPane			= Box.createHorizontalBox();
		final Box						bottomPane		= Box.createHorizontalBox();
		final SessionCollectionTable activeTracksTable;

		transport		= doc.getTransport();
        scroll			= new TimelineScroll( doc );
		transTB			= new TransportToolBar( transport, doc.timeline, doc.bird, Session.DOOR_TIME, doc );
		splitMolecule	= new JSplitPane();
		splitTrack		= new JSplitPane();
		
		lbProgress		= new JLabel();
		pb				= new ProgressBar();

		topPane.add( timeTB );
		topPane.add( transTB );
		topPane.add( Box.createHorizontalGlue() );
		topPane.add( lbProgress );
		topPane.add( pb );

		activeTracksTable	= new ActiveTracksTable( doc );

		markAxis	= new MarkerAxis( doc.markers, doc.timeline, doc, doc.bird, Session.DOOR_TIME );
		timeAxis	= new TimelineAxis( doc.timeline, doc.bird, Session.DOOR_TIME, doc );
		ggAxisPanel	= new JPanel();
		ggAxisPanel.setLayout( new BoxLayout( ggAxisPanel, BoxLayout.Y_AXIS ));
		ggAxisPanel.add( timeAxis );
		ggAxisPanel.add( markAxis );
		ggTrackPanel= new JPanel( new SpringLayout() );

		timeAxis.setFlags( Axis.TIMEFORMAT );

// UUU
//		atomTable.setFocusable( false );
		probTableTable.setFocusable( false );
		activeTracksTable.setFocusable( false );

		panelTab.setLayout( new BoxLayout( panelTab, BoxLayout.Y_AXIS ));
// UUU
//		panelTab.add( atomTable );
		panelTab.add( probTableTable );

		for( int i = 0; i < Atom.PROB_ALL.length; i++ ) {
			if( Atom.PROB_ALL[ i ] != Atom.PROB_TIME ) ggVerticalView.addItem( Atom.PROB_ALL[ i ]);
		}
		ggVerticalView.setFocusable( false );
// UUU
//		ggVerticalView.addActionListener( new ActionListener() {
//			public void actionPerformed( ActionEvent e ) 
//			{
//				ip.setVerticalProbability( ggVerticalView.getSelectedItem().toString() );
//			}
//		});

		ggTrackRowHeaderPanel = new JPanel();
		ggTrackRowHeaderPanel.setLayout( new SpringLayout() );
		vpTrackPanel	= new TimelineViewport();
		vpTrackPanel.setView( ggTrackPanel );
		ggScrollPane.setViewport( vpTrackPanel );
		ggScrollPane.setColumnHeaderView( ggAxisPanel );
		ggScrollPane.setRowHeaderView( ggTrackRowHeaderPanel );

//		bottomPane.add( ggVerticalView );
bottomPane.add( new LayerPanel( root, doc ));
		bottomPane.add( Box.createHorizontalGlue() );

		panelMain.add( ggScrollPane, BorderLayout.CENTER );
		panelMain.add( scroll, BorderLayout.SOUTH );

		splitTrack.setLeftComponent( activeTracksTable );
		splitTrack.setRightComponent( panelMain );
		splitTrack.setOneTouchExpandable( true );
		splitMolecule.setLeftComponent( splitTrack );
		splitMolecule.setRightComponent( panelTab );
		splitMolecule.setOneTouchExpandable( true );

		cp.setLayout( new BorderLayout() );
		cp.add( topPane, BorderLayout.NORTH );
		cp.add( splitMolecule, BorderLayout.CENTER );
		cp.add( bottomPane, BorderLayout.SOUTH );

		// ---- actions ----

		actionClose			= new actionCloseClass();
		actionImportMarkers	= new actionImportMarkersClass();
		actionSave			= new actionSaveClass();
		actionSaveAs		= new actionSaveAsClass( false );
		actionSaveCopy		= new actionSaveAsClass( true );
//		actionCut			= new actionCutClass();
//		actionCopy			= new actionCopyClass();
		actionPaste			= new actionPasteClass();
//		actionDelete		= new actionDeleteClass();
		actionSelectAll		= new actionSelectAllClass();
		actionTrimToSelection=new actionTrimToSelectionClass();
		actionInsertSilence	= new actionInsertSilenceClass();
		actionMissingLinks	= new actionMissingLinksClass();
		actionCompactAudio	= new actionCompactAudioClass();

		actionIncWidth		= new actionSpanWidthClass( 2.0f );
		actionDecWidth		= new actionSpanWidthClass( 0.5f );

		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_LEFT, KeyEvent.CTRL_MASK ), "incw" );
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_OPEN_BRACKET, menuShortcut ), "incw" );
		amap.put( "incw", actionIncWidth );
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_RIGHT, KeyEvent.CTRL_MASK ), "decw" );
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_CLOSE_BRACKET, menuShortcut ), "decw" );
		amap.put( "decw", actionDecWidth );
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_ENTER, 0 ), "retn" );
		amap.put( "retn", new actionScrollClass( SCROLL_SESSION_START ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_LEFT, 0 ), "left" );
		amap.put( "left", new actionScrollClass( SCROLL_SELECTION_START ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_RIGHT, 0 ), "right" );
		amap.put( "right", new actionScrollClass( SCROLL_SELECTION_STOP ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_F, KeyEvent.ALT_MASK ), "fit" );
		amap.put( "fit", new actionScrollClass( SCROLL_FIT_TO_SELECTION ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_A, KeyEvent.ALT_MASK ), "entire" );
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_LEFT, KeyEvent.META_MASK ), "entire" );
		amap.put( "entire", new actionScrollClass( SCROLL_ENTIRE_SESSION ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_ENTER, KeyEvent.SHIFT_MASK ), "seltobeg" );
		amap.put( "seltobeg", new actionSelectClass( SELECT_TO_SESSION_START ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_ENTER, KeyEvent.SHIFT_MASK + KeyEvent.ALT_MASK ), "seltoend" );
		amap.put( "seltoend", new actionSelectClass( SELECT_TO_SESSION_END ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_UP, 0 ), "postoselbeg" );
		amap.put( "postoselbeg", new actionSelToPosClass( 0.0f ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_DOWN, 0 ), "postoselend" );
		amap.put( "postoselend", new actionSelToPosClass( 1.0f ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_M, 0 ), "dropmark" );
		amap.put( "dropmark", new actionDropMarkerClass() );
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_TAB, 0 ), "selnextreg" );
		amap.put( "selnextreg", new actionSelectRegionClass( SELECT_NEXT_REGION ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_TAB, KeyEvent.ALT_MASK ), "selprevreg" );
		amap.put( "selprevreg", new actionSelectRegionClass( SELECT_PREV_REGION ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_TAB, KeyEvent.SHIFT_MASK ), "extnextreg" );
		amap.put( "extnextreg", new actionSelectRegionClass( EXTEND_NEXT_REGION ));
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_TAB, KeyEvent.ALT_MASK + KeyEvent.SHIFT_MASK ), "extprevreg" );
		amap.put( "extprevreg", new actionSelectRegionClass( EXTEND_PREV_REGION ));

		// --- Tools ---
		
		pointerTool = new TimelinePointerTool();
		tools.put( new Integer( ToolAction.POINTER ), pointerTool );
		tools.put( new Integer( ToolAction.ZOOM ), new TimelineZoomTool() );

		// ---- listeners ----

		new DynamicAncestorAdapter( new DynamicPrefChangeManager( app.getUserPrefs(), new String[] {
//			PrefsUtil.KEY_VIEWNULLLINIE, PrefsUtil.KEY_VIEWVERTICALRULERS, PrefsUtil.KEY_VIEWMARKERS },
			PrefsUtil.KEY_VIEWMARKERS },
			new LaterInvocationManager.Listener() {

			public void laterInvocation( Object o )
			{
				final PreferenceChangeEvent e			= (PreferenceChangeEvent) o;
				final String				key			= e.getKey();
//				OverviewDisplay				overview;
				
// INERTIA
//				if( key == PrefsUtil.KEY_VIEWNULLLINIE ) {
//					for( int i = 0; i < collOverviews.size(); i++ ) {
//						overview	= (OverviewDisplay) collOverviews.get( i );
//						overview.setNullLinie( e.getNode().getBoolean( e.getKey(), false ));
//					}
//				} else if( key == PrefsUtil.KEY_VIEWVERTICALRULERS ) {
if( key == PrefsUtil.KEY_VIEWVERTICALRULERS ) {
					final boolean visible = e.getNode().getBoolean( e.getKey(), false );
					for( int ch = 0; ch < collChannelRulers.size(); ch++ ) {
						((JComponent) collChannelRulers.get( ch )).setVisible( visible );
					}
					GUIUtil.makeCompactSpringGrid( ggTrackRowHeaderPanel, collChannelRulers.size(), 2, 0, 0, 1, 1 );
				} else if( key == PrefsUtil.KEY_VIEWMARKERS ) {
					final boolean visible = e.getNode().getBoolean( e.getKey(), false );
					markAxis.setVisible( visible );
				}
			}
		})).addTo( rp );

//		lim			= new LaterInvocationManager( new LaterInvocationManager.Listener() {
//			// o egal
//			public void laterInvocation( Object o )
//			{
//				vpTrackPanel.updatePositionAndRepaint();
//			}
//		});
		doc.timeline.addTimelineListener( this );
//		doc.tracks.addListener( this );
		transport.addRealtimeConsumer( this );

		doc.markers.addListener( new MarkerManager.Listener() {
			public void markersModified( MarkerManager.Event e )
			{
//				if( e.getSpan().touches( visibleSpan )) {
					ggTrackPanel.repaint();
//				}
			}
		});

		winListener = new WindowAdapter() {
			public void windowClosing( WindowEvent e ) {
				closeDocument( false );
			}
			
			public void windowGainedFocus( WindowEvent e )
			{
				root.getDocumentHandler().setActiveDocument( enc_this, doc );
			}
		};
		this.addWindowListener( winListener );
		this.addWindowFocusListener( winListener );
		
		splitMolecule.addPropertyChangeListener( "dividerLocation", new PropertyChangeListener() {
			public void propertyChange( PropertyChangeEvent e )
			{
				getClassPrefs().putInt( KEY_MOLECULE_DIVIDER, splitMolecule.getDividerLocation() );
			}
		});
		splitTrack.addPropertyChangeListener( "dividerLocation", new PropertyChangeListener() {
			public void propertyChange( PropertyChangeEvent e )
			{
				getClassPrefs().putInt( KEY_TRACK_DIVIDER, splitTrack.getDividerLocation() );
			}
		});
		
        doc.activeTracks.addListener( new SessionCollection.Listener() {
			public void sessionCollectionChanged( SessionCollection.Event e )
			{
				final java.util.List	coll		= e.getCollection();
				boolean					visible		= false;
				SessionObject			so;
				boolean					revalidate	= false;
			
				switch( e.getModificationType() ) {
				case SessionCollection.Event.ACTION_ADDED:
					visible	= true;
					// THRU
				case SessionCollection.Event.ACTION_REMOVED:
					if( !doc.bird.attemptShared( Session.DOOR_TRACKS, 250 )) return;
					try {
						for( int ch = 0; ch < doc.tracks.size(); ch++ ) {
							so		= doc.tracks.get( ch );
							if( coll.contains( so )) {
								((JComponent) collOverviews.get( ch )).setVisible( visible );
								((JComponent) collChannelHeaders.get( ch )).setVisible( visible );
								// XXX vertical rulaz
								revalidate = true;
							}
						}
						if( revalidate ) {
							revalidateView( doc.tracks.size() );
						}
					}
					finally {
						doc.bird.releaseShared( Session.DOOR_TRACKS );
					}
					break;
					
				default:
					break;
				}
			}
			
			public void sessionObjectChanged( SessionCollection.Event e ) {}
			public void sessionObjectMapChanged( SessionCollection.Event e ) {}
		});
		
		transTB.addActiveChannelListener( new ActionListener() {
			public void actionPerformed( ActionEvent e )
			{
				activeChannel = transTB.getActiveChannel();
				scroll.setActiveChannel( activeChannel );
			}
		});

		timeTB.addToolActionListener( this );

		this.setFocusTraversalKeysEnabled( false ); // we want the tab!
		setDefaultCloseOperation( DO_NOTHING_ON_CLOSE );

		documentUpdate();
		GUIUtil.setDeepFont( cp, null );

		init( root );
		setVisible( true );
		toFront();
	}
	
	public int getActiveChannel()
	{
		return activeChannel;
	}

	protected void restoreFromPrefs()
	{
		final int divMolec = getClassPrefs().getInt( KEY_MOLECULE_DIVIDER, -1 );
		final int divTrack = getClassPrefs().getInt( KEY_TRACK_DIVIDER, -1 );
		
		super.restoreFromPrefs();
		
		if( divMolec >= 0 ) splitMolecule.setDividerLocation( divMolec );
		if( divTrack >= 0 ) splitTrack.setDividerLocation( divTrack );
	}

	protected boolean alwaysPackSize()
	{
		return false;
	}

	protected Action replaceDummyAction( int ID, Action dummyAction )
	{
		switch( ID ) {
		case MenuFactory.MI_FILE_CLOSE:
			actionClose.mimic( dummyAction );
			actionClose.setEnabled( true );
			return actionClose;
			
		case MenuFactory.M_FILE_IMPORT:
		case MenuFactory.M_EDIT:
		case MenuFactory.M_TIMELINE:
			return new actionEnabledMenuClass( dummyAction );
			
		case MenuFactory.MI_FILE_IMPORTMARKERS:
			actionImportMarkers.mimic( dummyAction );
			actionImportMarkers.setEnabled( true );
			return actionImportMarkers;

		case MenuFactory.MI_FILE_SAVE:
			actionSave.mimic( dummyAction );
			return actionSave;
			
		case MenuFactory.MI_FILE_SAVEAS:
			actionSaveAs.mimic( dummyAction );
			actionSaveAs.setEnabled( true );
			return actionSaveAs;
			
		case MenuFactory.MI_FILE_SAVECOPY:
			actionSaveCopy.mimic( dummyAction );
			actionSaveCopy.setEnabled( true );
			return actionSaveCopy;
			
		case MenuFactory.MI_EDIT_UNDO:
			return doc.getUndoManager().getUndoAction();

		case MenuFactory.MI_EDIT_REDO:
			return doc.getUndoManager().getRedoAction();

//		case MenuFactory.MI_EDIT_CUT:
//			actionCut.mimic( dummyAction );
//			return actionCut;
//
//		case MenuFactory.MI_EDIT_COPY:
//			actionCopy.mimic( dummyAction );
//			return actionCopy;
//
		case MenuFactory.MI_EDIT_PASTE:
			actionPaste.mimic( dummyAction );
			actionPaste.setEnabled( true );
			return actionPaste;

//		case MenuFactory.MI_EDIT_CLEAR:
//			actionDelete.mimic( dummyAction );
//			actionDelete.putValue( Action.NAME, getResourceString( "menuDelete" ));
//			return actionDelete;
//
		case MenuFactory.MI_EDIT_SELECTALL:
			actionSelectAll.mimic( dummyAction );
			actionSelectAll.setEnabled( true );
			return actionSelectAll;

		case MenuFactory.MI_TIMELINE_INSERTSILENCE:
			actionInsertSilence.mimic( dummyAction );
			actionInsertSilence.setEnabled( true );
			return actionInsertSilence;

		case MenuFactory.MI_TIMELINE_TRIMTOSELECTION:
			actionTrimToSelection.mimic( dummyAction );
			return actionTrimToSelection;

		case MenuFactory.MI_OPERATION_MISSINGLINKS:
			actionMissingLinks.mimic( dummyAction );
			actionMissingLinks.setEnabled( true );
			return actionMissingLinks;

		case MenuFactory.MI_OPERATION_COMPACTAUDIO:
			actionCompactAudio.mimic( dummyAction );
			actionCompactAudio.setEnabled( true );
			return actionCompactAudio;

		default:
			return dummyAction;
		}
	}

	private String getResourceString( String key )
	{
		return AbstractApplication.getApplication().getResourceString( key );
	}

	/*
	 *  Checks if there are unsaved changes to
	 *  the session. If so, displays a confirmation
	 *  dialog. Invokes Save/Save As depending
	 *  on user selection.
	 *  
	 *  @param  parentComponent the component associated with
	 *							the proposed action, e.g. root
	 *  @param  actionName		name of the action that
	 *							threatens the session
	 *  @return					- true if the action should proceed,
	 *							- false if the action should be aborted
	 */
	private boolean confirmUnsaved( String actionName )
	{
		if( !doc.isDirty() ) return true;
		
		final de.sciss.app.Application	app		= AbstractApplication.getApplication();
		final String[]					options	= { getResourceString( "buttonSave" ),
													getResourceString( "buttonCancel" ),
													getResourceString( "buttonDontSave" ) };
		int								choice;
		ProcessingThread				proc;
		File							f		= (File) doc.getMap().getValue( Session.MAP_KEY_PATH );
		String							name;
	
		if( f == null ) {
			name = getResourceString( "frameUntitled" );
		} else {
			name = f.getName();
		}
		
		choice = JOptionPane.showOptionDialog( this, name + " :\n" + getResourceString( "optionDlgUnsaved" ),
											   actionName, JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null,
											   options, options[0] );
		switch( choice ) {
		case JOptionPane.CLOSED_OPTION:
		case 1:
			return false;
			
		case 2:
			return true;
			
		case 0:
			if( f == null ) {
				f = actionSaveAs.queryFile( false );
			}
			if( f != null ) {
				proc = actionSave.perform( f, false );
				if( proc != null ) {
					return proc.sync();
				}
			}
			return false;
			
		default:
			assert false : choice;
			return false;
		}
	}

	public boolean closeDocument( boolean force )
	{
		if( !force && !confirmUnsaved( getResourceString( "menuClose" ))) return false;
				
		this.removeWindowListener( winListener );
		this.removeWindowFocusListener( winListener );	// otherwise we'll try to set an obsolete active doc
		root.getDocumentHandler().removeDocument( this, doc );
		
		return true;
	}

	/**
	 *  Recreates the main frame's title bar
	 *  after a sessions name changed (clear/load/save as session)
	 */
	public void updateTitle()
	{
// INERTIA
//		final File	f	= doc.getAudioFileDescr().file;
final File f = (File) doc.getMap().getValue( Session.MAP_KEY_PATH );
		String		name;
		
		if( f == null ) {
			name = getResourceString( "frameUntitled" );
		} else {
			name = f.getName();
		}
		
		setTitle( AbstractApplication.getApplication().getName() + (doc.isDirty() ? " : \u2022" : " : " ) + name );
		actionSave.setEnabled( doc.isDirty() );
	}

	private void documentUpdate()
	{
		boolean					revalidate	= false;

		ChannelRowHeader chanHead;
		OverviewDisplay			overview;
		Track					t;
		int						oldChannels, newChannels;
		JComponent				chanRuler;
	
		try {
			doc.bird.waitShared( Session.DOOR_TRACKS );

// INERTIA
//			newChannels = doc.getAudioFileDescr().channels;
newChannels = doc.tracks.size();
			oldChannels = collOverviews.size();

			assert collChannelHeaders.size() == oldChannels : collChannelHeaders.size();

			// first kick out editors whose tracks have been removed
			for( int ch = 0; ch < oldChannels; ch++ ) {
				chanHead	= (ChannelRowHeader) collChannelHeaders.get( ch );
				t			= chanHead.getTrack();
				if( !doc.tracks.contains( t )) {
					revalidate	= true;
					overview	= (OverviewDisplay) collOverviews.remove( ch );
					chanHead	= (ChannelRowHeader) collChannelHeaders.remove( ch );
					chanRuler	= (JComponent) collChannelRulers.remove( ch );
//					chanHead.removeComponentListener( rowHeightListener );
					oldChannels--;
                    // XXX : dispose trnsEdit (e.g. free vectors, remove listeners!!)
					ggTrackPanel.remove( overview );
					ggTrackRowHeaderPanel.remove( chanHead );
					ggTrackRowHeaderPanel.remove( chanRuler );
					ch--;
				}
			}
			// next look for newly added transmitters and create editors for them
newLp:		for( int ch = 0; ch < newChannels; ch++ ) {
				t			= (Track) doc.tracks.get( ch );
				for( int ch2 = 0; ch2 < oldChannels; ch2++ ) {
					chanHead = (ChannelRowHeader) collChannelHeaders.get( ch );
					if( chanHead.getTrack() == t ) continue newLp;
				}
				
				revalidate = true;

//
//// XXX TEST
//double maxStart = (double) doc.timeline.getLength() / doc.timeline.getRate();
//if( maxStart > 0.0 ) {
//	Random rnd = new Random( System.currentTimeMillis() );
//	double maxDur = Math.min( 30.0, maxStart / 4 );
//	maxStart -= maxDur;
//	Molecule molec;
//	Atom a;
//	Probability prob;
//	java.util.List collMolec = new ArrayList();
//	MapManager map;
//
//	for( int i = 0; i < 1000; i++ ) {
//		molec	= new Molecule();
//		molec.setName( "M" + (i+1) );
//		a		= new Atom();
//		a.setName( "A" + (i+1) );
//		prob	= new Probability();
//		prob.setName( Atom.PROB_TIME );
//		prob.setMin( null, rnd.nextDouble() * maxStart );
//		prob.setMax( null, prob.getMin() + (rnd.nextDouble() * maxDur) );
//		a.probabilities.add( null, prob );
//		map = a.getMap();
//		map.putValue( null, Atom.MAP_KEY_AFSTART, new Double( 0.0 ));
//		map.putValue( null, Atom.MAP_KEY_AFSTOP, new Double( 2.0 ));
//		map.putValue( null, Atom.MAP_KEY_AUDIOFILE, new File( "/UserOrdner/Rutz/Wolkenpumpe/audio/Sphere1.aif" ));
//		molec.addAtom( null, a );
//	//System.err.println( molec.getName() + " : " + molec.getStart() + " ... " +molec.getStop() );
//		collMolec.add( molec );
//	}
//	t.addMolecules( null, collMolec );
//}

// INERTIA
//				overview = new OverviewDisplay();
overview = new OverviewDisplay( doc, t, vpTrackPanel );
				collOverviews.add( overview );
// INERTIA
//				chanHead = new ChannelRowHeader( root, doc, t );
chanHead = new ChannelRowHeader( doc, t );
				collChannelHeaders.add( chanHead );
				ggTrackPanel.add( overview, ch );
				ggTrackRowHeaderPanel.add( chanHead, ch << 1 );
				
Axis test = new Axis( Axis.VERTICAL, 0 );
test.setSpace( VectorSpace.createLinSpace( 0.0, 1.0, -100.0, 100.0, null, null, null, null ));
collChannelRulers.add( test );
				ggTrackRowHeaderPanel.add( test, (ch << 1) + 1 );

				initStrip( overview, test );
			}
		}
		finally {
			doc.bird.releaseShared( Session.DOOR_TRACKS );
		}

		if( revalidate ) {
			revalidateView( newChannels );
		}

// INERTIA
//		updateOverviews( false );
	}

	private void revalidateView( int numCh )
	{
		GUIUtil.makeCompactSpringGrid( ggTrackRowHeaderPanel, numCh, 2, 0, 0, 1, 1 ); // initX, initY, padX, padY
		GUIUtil.makeCompactSpringGrid( ggTrackPanel, numCh, 1, 0, 0, 0, 1 ); // initX, initY, padX, padY
		ggTrackRowHeaderPanel.revalidate();
		ggTrackPanel.revalidate();
	}

	private void initStrip( OverviewDisplay overview, Axis chanRuler )
	{
		Preferences prefs = AbstractApplication.getApplication().getUserPrefs();
	
// INERTIA
//		overview.setNullLinie( prefs.getBoolean( PrefsUtil.KEY_VIEWNULLLINIE, false ));
		chanRuler.setVisible( prefs.getBoolean( PrefsUtil.KEY_VIEWVERTICALRULERS, false ));
	}

// ---------------- ProgressComponent interface ---------------- 

	public Component getComponent()
	{
		return this;
	}
	
	public void resetProgression()
	{
		pb.reset();
	}
	
	public void setProgression( float p )
	{
		if( p >= 0 ) {
			pb.setProgression( p );
		} else {
			pb.setIndeterminate( true );
		}
	}
	
	public void	finishProgression( boolean success )
	{
		pb.finish( success );
	}
	
	public void setProgressionText( String text )
	{
		lbProgress.setText( text );
	}
	
	public void showMessage( int type, String text )
	{
		System.out.println( text );
	}
	
	public void displayError( Exception e, String processName )
	{
		GUIUtil.displayError( this, e, processName );
	}

 // ---------------- TimelineListener interface ---------------- 

	public void timelineSelected( TimelineEvent e )
    {
		final boolean wasEmpty = timelineSel.isEmpty();
		final boolean isEmpty;
	
		try {
			doc.bird.waitShared( Session.DOOR_TIME );
			timelineSel	= doc.timeline.getSelectionSpan();
		}
		finally {
			doc.bird.releaseShared( Session.DOOR_TIME );
		}

		vpTrackPanel.updateSelectionAndRepaint();
		isEmpty	= timelineSel.isEmpty();
// INERTIA
//		if( wasEmpty != isEmpty ) {
//			actionCut.setEnabled( !isEmpty );
//			actionCopy.setEnabled( !isEmpty );
//			actionDelete.setEnabled( !isEmpty );
//			actionTrimToSelection.setEnabled( !isEmpty );
//			actionProcess.setEnabled( !isEmpty );
//		}
    }
    
	// warning : don't call doc.setAudioFileDescr, it will restore the old markers!
	public void timelineChanged( TimelineEvent e )
    {
// INERTIA
//		if( !doc.bird.attemptExclusive( Session.DOOR_ALL, 250 )) return;
//		try {
//			final AudioFileDescr afd	= doc.getAudioFileDescr();
//			afd.rate					= doc.timeline.getRate();
//			afd.length					= doc.timeline.getLength();
//			ggAudioFileDescr.setText( afd.getFormat() );
//			updateOverviews( false );
//		}
//		finally {
//			doc.bird.releaseExclusive( Session.DOOR_ALL );
//		}
		vpTrackPanel.repaint(); // updateAndRepaint();
    }

	public void timelinePositioned( TimelineEvent e )
	{
		try {
			doc.bird.waitShared( Session.DOOR_TIME );
			timelinePos[ activeChannel ] = doc.timeline.getPosition();
		}
		finally {
			doc.bird.releaseShared( Session.DOOR_TIME );
		}
		
		vpTrackPanel.updatePositionAndRepaint( activeChannel );
		scroll.setPosition( activeChannel, timelinePos[ activeChannel ],
			0, pointerTool.validDrag ? TimelineScroll.TYPE_DRAG : TimelineScroll.TYPE_UNKNOWN );
	}

    public void timelineScrolled( TimelineEvent e )
    {
		try {
			doc.bird.waitShared( Session.DOOR_TIME );
			timelineVis	= doc.timeline.getVisibleSpan();
		}
		finally {
			doc.bird.releaseShared( Session.DOOR_TIME );
		}

// INERTIA
//		updateOverviews( false );
		vpTrackPanel.updateTransformsAndRepaint( false );
    }

// ---------------- RealtimeConsumer interface ---------------- 

	/**
	 *  Requests 30 fps notification (no data block requests).
	 *  This is used to update the timeline position during transport
	 *  playback.
	 */
	public RealtimeConsumerRequest createRequest( RealtimeContext context )
	{
		RealtimeConsumerRequest request = new RealtimeConsumerRequest( this, context );
		// 30 fps is visually fluent
		request.notifyTickStep  = RealtimeConsumerRequest.approximateStep( context, 30 );
		request.notifyTicks		= true;
		request.notifyOffhand	= true;
		return request;
	}
	
	public void realtimeTick( RealtimeContext context, int ch, long timelinePos )
	{
//		if( ch == activeChannel ) {
			this.timelinePos[ ch ] = timelinePos;
//			lim.queue( this );
//		}
		vpTrackPanel.updatePositionAndRepaint( ch );
		scroll.setPosition( ch, timelinePos, 50, TimelineScroll.TYPE_TRANSPORT );
	}

//	public void offhandTick( RealtimeContext context, long timelinePos )
//	{
//		this.timelinePos = timelinePos;
//		vpTrackPanel.updatePositionAndRepaint();
//
//		scroll.setPosition( timelinePos, 0, pointerTool.validDrag ?
//			TimelineScroll.TYPE_DRAG : TimelineScroll.TYPE_UNKNOWN );
//	}

// ---------------- ToolListener interface ---------------- 
 
	public void toolChanged( ToolActionEvent e )
	{
		Track				t;
		OverviewDisplay		od;
	
		if( activeTool != null ) {
			activeTool.toolDismissed( ggTrackPanel );
		}

		// forward event to all editors that implement ToolActionListener
		if( doc.bird.attemptShared( Session.DOOR_TRACKS, 250 )) {
			try {
				for( int i = 0; i < collOverviews.size(); i++ ) {
					od		= (OverviewDisplay) collOverviews.get( i );
					if( (od instanceof ToolActionListener) && doc.activeTracks.contains( od.getTrack() )) {
						((ToolActionListener) od).toolChanged( e );
					}
				}
			}
			finally {
				doc.bird.releaseShared( Session.DOOR_TRACKS );
			}
		}

		activeTool = (AbstractTool) tools.get( new Integer( e.getToolAction().getID() ));
		if( activeTool != null ) {
			ggTrackPanel.setCursor( e.getToolAction().getDefaultCursor() );
			activeTool.toolAcquired( ggTrackPanel );
		} else {
			ggTrackPanel.setCursor( null );
		}
	}

// ------------- action classes -------------

	private class actionEnabledMenuClass
	extends MenuAction
	{
		private actionEnabledMenuClass( Action dummy )
		{
			super();
			mimic( dummy );
			setEnabled( true );
		}
	
		public void actionPerformed( ActionEvent e ) {}
	}

	// action for the Save-Session menu item
	private class actionCloseClass
	extends MenuAction
	{
		public void actionPerformed( ActionEvent e )
		{
			closeDocument( false );
		}
	}

	private class actionImportMarkersClass
	extends MenuAction
	{
		public void actionPerformed( ActionEvent e )
		{
			final File					f			= queryFile();
			
			if( f == null ) return;

			final java.util.List		collLines	= new ArrayList();
			BufferedReader				br			= null;
			String						str;
			
			try {
				br	= new BufferedReader( new InputStreamReader( new FileInputStream( f )));
				while( br.ready() ) {
					str = br.readLine();
					if( str.length() > 0 ) collLines.add( str );
				}
			}
			catch( IOException e1 ) {
				GUIUtil.displayError( enc_this, e1, getValue( NAME ).toString() );
				return;
			}
			finally {
				if( br != null ) {
					try {
						br.close();
					}
					catch( IOException e1 ) {
						GUIUtil.displayError( enc_this, e1, getValue( NAME ).toString() );
						return;
					}
				}
			}
			
			if( collLines.isEmpty() ) {
				JOptionPane.showMessageDialog( enc_this, getResourceString( "errFileEmpty" ),
					getValue( NAME ).toString(), JOptionPane.ERROR_MESSAGE );
				return;
			}
			
			final int					numMarkers		= collLines.size();
			final Pattern				ptrn			= Pattern.compile( "\\s+" );
			final int					numColumns		= ptrn.split( ((String) collLines.get( 0 ))).length;
			
			if( numColumns == 0 ) {
				JOptionPane.showMessageDialog( enc_this, getResourceString( "errNoColumnsInTextFile" ),
					getValue( NAME ).toString(), JOptionPane.ERROR_MESSAGE );
				return;
			}
			
			final NumberSpace spcColumn		= NumberSpace.createIntSpace( 1, numColumns );
			final JOptionPane			dlg;
			final JPanel				msgPane;
			final JComboBox				ggFormat, ggMarkerType, ggUnits, ggColStopType;
			final NumberField			ggColName, ggColStart, ggColStop;
			final JLabel				lbColStart, lbColStop;
			final String[]				formatOptions	= { getResourceString( "formatMaxColl" )};
			final String[]				markerOptions	= { getResourceString( "labelMarkers" ),
															getResourceString( "labelRegions" )};
			final String[]				unitOptions		= { getResourceString( "labelSeconds" ),
															getResourceString( "labelSamples" )};
			final String[]				stopOptions		= { getResourceString( "labelColStop" ),
															getResourceString( "labelColLength" )};
			final String[]				truncOptions	= { getResourceString( "buttonTrunc" ),
															getResourceString( "buttonExpand" )};
			int							rows			= 0;
			int							result;

			ggFormat	= new JComboBox();
			for( int i = 0; i < formatOptions.length; i++ ) {
				ggFormat.addItem( formatOptions[ i ]);
			}
			ggMarkerType	= new JComboBox();
			for( int i = 0; i < markerOptions.length; i++ ) {
				ggMarkerType.addItem( markerOptions[ i ]);
			}
			ggColName	= new NumberField();
			ggColName.setSpace( spcColumn );
			lbColStart	= new JLabel( getResourceString( "labelColPos" ));
			ggColStart	= new NumberField();
			ggColStart.setSpace( spcColumn );
			ggColStopType = new JComboBox();
			for( int i = 0; i < stopOptions.length; i++ ) {
				ggColStopType.addItem( stopOptions[ i ]);
			}
			ggColStop	= new NumberField();
			ggColStop.setSpace( spcColumn );
			ggUnits	= new JComboBox();
			for( int i = 0; i < unitOptions.length; i++ ) {
				ggUnits.addItem( unitOptions[ i ]);
			}
			ggColStopType.setEnabled( false );
			ggColStop.setEnabled( false );
			
			ggMarkerType.addActionListener( new ActionListener() {
				public void actionPerformed( ActionEvent e )
				{
					final boolean isRegionType = ggMarkerType.getSelectedIndex() == 1;
					
					lbColStart.setText( getResourceString( isRegionType ?
						"labelColStart" : "labelColPos" ));
					ggColStopType.setEnabled( isRegionType );
					ggColStop.setEnabled( isRegionType );
				}
			});

			rows++;
			msgPane		= new JPanel( new SpringLayout() );
			msgPane.add( new JLabel( getResourceString( "labelTextFormat" )));
			msgPane.add( ggFormat );
			rows++;
			msgPane.add( new JLabel( getResourceString( "labelMarkerType" )));
			msgPane.add( ggMarkerType );
			rows++;
			msgPane.add( new JLabel( getResourceString( "labelColName" )));
			msgPane.add( ggColName );
			rows++;
			msgPane.add( lbColStart );
			msgPane.add( ggColStart );
			rows++;
			msgPane.add( ggColStopType );
			msgPane.add( ggColStop );
			rows++;
			msgPane.add( new JLabel( getResourceString( "labelTimeUnits" )));
			msgPane.add( ggUnits );

			GUIUtil.makeCompactSpringGrid( msgPane, rows, 2, 4, 2, 4, 2 );	// #row #col initx inity padx pady
			GUIUtil.setDeepFont( msgPane, null );

			result		= JOptionPane.showConfirmDialog( enc_this, msgPane, getValue( NAME ).toString(),
							JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
							null );

			if( result != JOptionPane.OK_OPTION ) return;

			// --------- ok, here we go ---------
			
			final boolean			isRegionType	= ggMarkerType.getSelectedIndex() == 1;
			final boolean			isSeconds		= ggUnits.getSelectedIndex() == 0;
			final boolean			isLength		= ggColStopType.getSelectedIndex() == 1;
			final java.util.List	collMarkers		= new ArrayList();
			final int				colName			= ggColName.getNumber().intValue() - 1;
			final int				colStart		= ggColStart.getNumber().intValue() - 1;
			final int				colStop			= ggColStop.getNumber().intValue() - 1;
			final int				minColNum		= Math.max( colName, Math.max( colStart, isRegionType ? colStop : 0 )) + 1;
			final double			rate			= isSeconds ? doc.timeline.getRate() : 1.0;
			final long				timelineLen		= doc.timeline.getLength();	// XXX sync
			final int				omitted;
			String					name;
			long					start, stop;
			String[]				columns;
			Marker					mark;
			Region					region;
			int						skipped			= 0;
			int						errorneous		= 0;
			int						illegal			= 0;
			long					maxStop			= 0;

			for( int i = 0; i < collLines.size(); i++ ) {
				columns	= ptrn.split( (String) collLines.get( i ));
				if( columns.length >= minColNum ) {
					try {
						name		= getStrippedColumn( columns, colName );
						start		= (long) (Double.parseDouble( getStrippedColumn( columns, colStart )) *
												rate + 0.5);
						if( isRegionType ) {
							stop	= (long) (Double.parseDouble( getStrippedColumn( columns, colStop )) *
												rate + 0.5);
							if( isLength ) stop += start;
							if( start >= 0 && stop >= start ) {
//System.err.println( "region '"+name+"' at "+start+" ... "+stop );
								collMarkers.add( new Region( new Span( start, stop ), name ));
								maxStop	= Math.max( maxStop, stop );
							} else {
								illegal++;
							}
						} else {
							if( start >= 0 ) {
//System.err.println( "marker '"+name+"' at "+start );
								collMarkers.add( new Marker( start, name ));
								maxStop	= Math.max( maxStop, start );
							} else {
								illegal++;
							}
						}
					}
					catch( NumberFormatException e1 ) {
						errorneous++;
					}
				} else {
					skipped++;
				}
			}
			
			omitted = skipped + errorneous + illegal;
			if( omitted > 0 ) {
				result = JOptionPane.showConfirmDialog( enc_this, new MessageFormat(
					getResourceString( "msgOmittedLines" ), Locale.US ).format( new Object[] {
						new Integer( omitted ), new Integer( collLines.size() ),
						new Integer( skipped ), new Integer( errorneous ), new Integer( illegal )}),
					getValue( NAME ).toString(), JOptionPane.YES_NO_OPTION,
					JOptionPane.WARNING_MESSAGE );
					
				if( result != JOptionPane.YES_OPTION ) return;
			}
			
			final SyncCompoundEdit	ce = new BasicSyncCompoundEdit( doc.bird, Session.DOOR_TIME, getValue( NAME ).toString() );

			if( maxStop > timelineLen ) {
				result = JOptionPane.showOptionDialog( enc_this, new MessageFormat(
					getResourceString( "msgMarkersExceedTimelineLength" ), Locale.US ).format( new Object[] {
						new Long( maxStop ), new Long( timelineLen )}),
					getValue( NAME ).toString(), JOptionPane.YES_NO_OPTION,
					JOptionPane.QUESTION_MESSAGE, null, truncOptions, truncOptions[1] );
					
				if( result == 0 ) {
					if( isRegionType ) {
						for( int i = collMarkers.size() - 1; i >= 0; i--  ) {
							region = (Region) collMarkers.get( i );
							if( region.span.getStop() > timelineLen ) collMarkers.remove( i );
						}
					} else {
						for( int i = collMarkers.size() - 1; i >= 0; i--  ) {
							mark = (Marker) collMarkers.get( i );
							if( mark.pos > timelineLen ) collMarkers.remove( i );
						}
					}
				} else {
					// XXX
					ce.addEdit( new EditSetTimelineLength( this, doc, maxStop + 1 ));	// XXX + 1 ?
				}
			}
			
			if( collMarkers.isEmpty() ) {
				JOptionPane.showMessageDialog( enc_this, getResourceString( "msgMarkerListEmpty" ),
					getValue( NAME ).toString(), JOptionPane.INFORMATION_MESSAGE );
				return;
			}
			
			if( isRegionType ) {
				System.err.println( "regions not yet supported!" );
			} else {
				doc.markers.addMarkers( this, collMarkers, ce );
			}
			ce.end();
			doc.getUndoManager().addEdit( ce );
		}
		
		private String getStrippedColumn( String[] columns, int col )
		{
			final String str = columns[ col ];
			if( ((col == 0) && str.endsWith( "," )) ||
				((col == columns.length - 1) && str.endsWith( ";" )) ) {
				return str.substring( 0, str.length() - 1 );
			} else {
				return str;
			}
		}

		private File queryFile()
		{
			FileDialog  fDlg;
			String		strFile, strDir;
			File		f;
			int			i;
			
			final Frame frame = enc_this;

			fDlg	= new FileDialog( frame,
				AbstractApplication.getApplication().getResourceString( "fileDlgSelectText" ),
				FileDialog.LOAD );
			fDlg.setVisible( true ); // show();
			strDir	= fDlg.getDirectory();
			strFile	= fDlg.getFile();
			
			if( strFile == null ) return null;   // means the dialog was cancelled

			return new File( strDir, strFile );
		}
	}

	// action for the Save-Session menu item
	private class actionSaveClass
	extends MenuAction
	implements RunnableProcessing
	{
		/**
		 *  Saves a document. If the file
		 *  wasn't saved before, a file chooser
		 *  is shown before.
		 */
		public void actionPerformed( ActionEvent e )
		{
			File f	= (File) doc.getMap().getValue( Session.MAP_KEY_PATH );
		
			if( f == null ) {
				f = actionSaveAs.queryFile( false );
			}
			if( f != null ) perform( f, false );
		}
		
		/**
		 *  Save the session to the given file.
		 *  Transport is stopped before, if it was running.
		 *  On success, undo history is purged and
		 *  <code>setModified</code> and <code>updateTitle</code>
		 *  are called, and the file is added to
		 *  the Open-Recent menu.
		 *
		 *  @param  docFile		the file denoting
		 *						the session's name. note that
		 *						<code>Session</code> will create
		 *						a folder of this name and store the actual
		 *						session data in a file of the same name
		 *						plus .XML suffix inside this folder
		 *  @synchronization	this method is to be called in the event thread
		 */
		protected ProcessingThread perform( File docFile, boolean asCopy )
		{
			doc.getTransport().stopAllAndWait();
			Object[] args	= new Object[3];
			args[0]			= docFile;
			args[1]			= doc;
			args[2]			= new Boolean( asCopy );
			if( !asCopy ) doc.getUndoManager().discardAllEdits();
			return( new ProcessingThread( this, doc.getFrame(), doc.bird, getValue( NAME ).toString(), args, Session.DOOR_ALL ));
		}

		public boolean run( ProcessingThread context, Object argument )
		{
			final Object[]					args			= (Object[]) argument;
			final File						f				= (File) args[0];
			final Session					doc				= (Session) args[1];
			final boolean					asCopy			= ((Boolean) args[2]).booleanValue();
			final File						dir				= f.getParentFile();
			final org.w3c.dom.Document		domDoc;
			final Map						options			= new HashMap();
			final de.sciss.app.Application	app				= AbstractApplication.getApplication();
			final DOMSource					domSrc;
			final StreamResult				streamResult;
			final DocumentBuilderFactory	builderFactory;
			final DocumentBuilder			builder;
			final TransformerFactory		transformerFactory;
			final Transformer				transformer;
			
			Element							childNode;
			File							tempDir			= null;
			boolean							success			= false;
		
			builderFactory		= DocumentBuilderFactory.newInstance();
			builderFactory.setValidating( true );
			transformerFactory  = TransformerFactory.newInstance();

			context.setProgression( -1f );

			try {
				builder		= builderFactory.newDocumentBuilder();
				transformer = transformerFactory.newTransformer();
				builder.setEntityResolver( doc );
				domDoc		= builder.newDocument();
				childNode   = domDoc.createElement( Session.XML_ROOT );
				domDoc.appendChild( childNode );
				options.put( XMLRepresentation.KEY_BASEPATH, dir );
				doc.getMap().putValue( this, Session.MAP_KEY_PATH, f );
				doc.setName( f.getName() );
				
				if( dir.exists() ) {
					tempDir = new File( dir.getAbsolutePath() + ".tmp" );
					if( tempDir.exists() ) {
						IOUtil.deleteAll( tempDir );
					}
					if( !dir.renameTo( tempDir )) {
						throw new IOException( tempDir.getAbsolutePath() + " : " +
											   IOUtil.getResourceString( "errMakeDir" ));
					}
				}
				if( !dir.mkdirs() ) {
					throw new IOException( dir.getAbsolutePath() + " : " +
										   IOUtil.getResourceString( "errMakeDir" ));
				}
				
				doc.toXML( domDoc, childNode, options );
				domSrc			= new DOMSource( domDoc );
				streamResult	= new StreamResult( f );
				transformer.setOutputProperty( OutputKeys.DOCTYPE_SYSTEM, Session.SESSION_DTD );
				transformer.setOutputProperty( OutputKeys.INDENT, app.getUserPrefs().getBoolean( PrefsUtil.KEY_SESSIONINDENT, false ) ?
					"no" : "yes" );
				transformer.setOutputProperty( OutputKeys.OMIT_XML_DECLARATION, app.getUserPrefs().getBoolean( PrefsUtil.KEY_SESSIONNOXMLHEADER, false ) ?
					"no" : "yes" );

				transformer.transform( domSrc, streamResult );
// INERTIA
//				MRJAdapter.setFileCreatorAndType( f, app.getMacOSCreator(), Session.MACOS_FILE_TYPE );
				
				doc.getUndoManager().discardAllEdits();

				if( tempDir != null && tempDir.exists() ) {
					IOUtil.deleteAll( tempDir );
				}

				context.setProgression( 1.0f );
				success = true;
			}
			catch( ParserConfigurationException e1 ) {
				context.setException( e1 );
			}
			catch( TransformerConfigurationException e2 ) {
				context.setException( e2 );
			}
			catch( TransformerException e3 ) {
				context.setException( e3 );
			}
			catch( IOException e4 ) {
				context.setException( e4 );
			}
			catch( DOMException e5 ) {
				context.setException( e5 );
			}

			return success;
		} // run

		public void finished( ProcessingThread context, Object argument, boolean success )
		{
// INERTIA
//			final MainFrame mf = (MainFrame) root.getComponent( Main.COMP_MAIN );

			if( success ) {
				Object[]		args	= (Object[]) argument;
				final File		docFile	= (File) args[0];
				final Session	doc		= (Session) args[1];
				final boolean	asCopy	= ((Boolean) args[2]).booleanValue();
				if( !asCopy ) {
					root.menuFactory.addRecent( docFile );
// INERTIA
//					doc.setAudioFileDescr( afd );
doc.getMap().putValue( this, Session.MAP_KEY_PATH, docFile );
				}
			} else {
				File tempDir = new File( ((File) argument).getParentFile().getAbsolutePath() + ".tmp" );
				if( tempDir.exists() ) {
					JOptionPane.showMessageDialog( null,
						AbstractApplication.getApplication().getResourceString( "warnOldSessionDir" )+ " :\n"+
						tempDir.getAbsolutePath(), getValue( Action.NAME ).toString(),
						JOptionPane.WARNING_MESSAGE );
				}
			}
// INERTIA
//			mf.updateTitle();
updateTitle();
		}
	}

	// action for the Save-Session-As menu item
	private class actionSaveAsClass
	extends MenuAction
	{
		private final boolean asCopy;
	
		private actionSaveAsClass( boolean asCopy )
		{
			this.asCopy	= asCopy;
		}

		/*
		 *  Query a file name from the user and save the document
		 */
		public void actionPerformed( ActionEvent e )
		{
			File f = queryFile( asCopy );
			if( f != null ) actionSave.perform( f, asCopy );
		}
		
		/**
		 *  Open a file chooser so the user
		 *  can select a new output file for the session.
		 *
		 *  @return the chosen <coded>File</code> or <code>null</code>
		 *			if the dialog was cancelled.
		 */
		protected File queryFile( boolean asCopy)
		{
			FileDialog  fDlg;
			String		strFile, strDir;
			File		f;
			int			i;
// INERTIA
//			Frame		frame = (Frame) root.getComponent( Main.COMP_MAIN );
Frame frame = enc_this;

			fDlg	= new FileDialog( frame,
				AbstractApplication.getApplication().getResourceString( "fileDlgSave" ),
				FileDialog.SAVE );
			f		= (File) doc.getMap().getValue( Session.MAP_KEY_PATH );
			if( f != null ) f = f.getParentFile();	// use session folder instead of XML file
			if( f != null ) {
				strDir  = f.getParent();
				strFile = f.getName();
				if( asCopy ) {
					strFile	= strFile + " " + getResourceString( "fileDlgCopy" );
				}
				if( strDir != null ) fDlg.setDirectory( strDir );
				fDlg.setFile( strFile );
			}
			fDlg.setVisible( true ); // show();
			strDir	= fDlg.getDirectory();
			strFile	= fDlg.getFile();
			
			if( strFile == null ) return null;   // means the dialog was cancelled

			i = strFile.lastIndexOf( "." );
			strFile = i > 0 ? strFile.substring( 0, i ) : strFile;
			f = new File( new File( strDir, strFile ), strFile + Session.FILE_EXTENSION );
			return f;
		}
	}

	private class actionPasteClass
	extends MenuAction
//	implements RunnableProcessing
	{
		public void actionPerformed( ActionEvent e )
		{
			perform( getValue( NAME ).toString() );
		}
		
		protected void perform( String name )
		{
				Transferable	t;
//			TrackList		tl;
//
//			try {
				t = Main.clipboard.getContents( this );
System.err.println( "gettin' contents..." );
				if( t == null ) return;

System.err.println( "yes there are contents!" );
DataFlavor[] df = t.getTransferDataFlavors();

for( int i = 0; i < df.length; i++ ) {
	System.err.println( "  " + i + " : getDefaultRepresentationClassAsString() = " + df[i].getDefaultRepresentationClassAsString()+
		"; getHumanPresentableName() = "+df[i].getHumanPresentableName() +
		"; getMimeType() = "+df[i].getMimeType()+
		"; getPrimaryType() = "+df[i].getPrimaryType()+
		"; getSubType() = "+df[i].getSubType() +
		"; getRepresentationClass() "+df[i].getRepresentationClass() );
}
				
//				if( !t.isDataFlavorSupported( TrackList.trackListFlavor )) return;
//				tl		= (TrackList) t.getTransferData( TrackList.trackListFlavor );
//			}
//			catch( IOException e11 ) {
//				System.err.println( e11.getLocalizedMessage() );
//				return;
//			}
//			catch( UnsupportedFlavorException e12 ) {
//				System.err.println( e12.getLocalizedMessage() );
//				return;
//			}
//
//			new ProcessingThread( this, doc.getFrame(), root, doc.bird, name, tl, Session.DOOR_ALL );
		}
		
//		/**
//		 *  This method is called by ProcessingThread
//		 */
//		public boolean run( ProcessingThread context, Object argument )
//		{
//			long							position, docLength, pasteLength, start;
//			TrackList						tl		= (TrackList) argument;
//			MultirateTrackEditor			mte;
//			SyncCompoundEdit				edit;
////			SyncCompoundSessionObjectsEdit	edit;
//			Span							oldSelSpan, newSelSpan, span;
//			long[]							trnsLen;
//			boolean							success		= false;
//			BlendContext					bc;
//
////			edit		= new BasicSyncCompoundEdit( doc.bird, Session.DOOR_ALL, context.getName() );
//			edit		= new SyncCompoundSessionObjEdit( this, doc.bird, Session.DOOR_ALL, doc.tracks.getAll(), // XXX getAll
//														  Track.OWNER_WAVE, null, null, context.getName() );
//			position	= timelinePos; // doc.timeline.getPosition();
//			oldSelSpan	= timelineSel; // doc.timeline.getSelectionSpan();
//			docLength	= doc.timeline.getLength();
//			pasteLength	= tl.getSpan().getLength();
//			bc			= createBlendContext(
//				Math.min( pasteLength, Math.min( position, docLength - position )) / 2 );
//
//			try {
//				if( !oldSelSpan.isEmpty() ) { // deselect
//					edit.addEdit( new EditSetTimelineSelection( this, doc, new Span() ));
////							position = oldSelSpan.getStart();
//				}
//				newSelSpan	= new Span( position, position );
//				mte			= doc.getMTE();
//				mte.insert( position, tl, bc, edit, context, 0.0f, 1.0f ); // insert clipboard content
//				newSelSpan	= new Span( newSelSpan.getStart(),
//										Math.max( newSelSpan.getStop(),
//										newSelSpan.getStart() + pasteLength ));
//				if( pasteLength != 0 ) {	// adjust timeline
//					edit.addEdit( new EditSetTimelineLength( this, doc, docLength + pasteLength ));
//					doc.markers.insertSpan( this, new Span( position, position + pasteLength ), edit );
//				}
//				if( !newSelSpan.isEmpty() ) {
//					edit.addEdit( new EditSetTimelineSelection( this, doc, newSelSpan ));
//				}
//				edit.end();
//				doc.getUndoManager().addEdit( edit );
//				success = true;
//			}
//			catch( IOException e1 ) {
//				edit.cancel();
//				context.setException( e1 );
//			}
//
////			context.setProgression( 1.0f );	// XXX
//			
//			return success;
//		}
//
//		public void finished( ProcessingThread context, Object argument, boolean success ) {}
	} // class actionPasteClass

	private class actionSelectAllClass
	extends MenuAction
	{
		public void actionPerformed( ActionEvent e )
		{
			Span			span;
// INERTIA
//			UndoableEdit	edit;
		
			if( !doc.bird.attemptExclusive( Session.DOOR_TIME, 250 )) return;
			try {
				span	= new Span( 0, doc.timeline.getLength() );
// INERTIA
//				edit	= new EditSetTimelineSelection( this, doc, span );
//				doc.getUndoManager().addEdit( edit );
doc.timeline.setSelectionSpan( this, span );
			}
			finally {
				doc.bird.releaseExclusive( Session.DOOR_TIME );
			}
		}
	}

	private class actionTrimToSelectionClass
	extends MenuAction
	{
		// performs inplace (no runnable processing) coz it's always fast
		public void actionPerformed( ActionEvent e )
		{
			Span					selSpan, deleteBefore, deleteAfter;
			SyncCompoundEdit		edit;
// INERTIA
//			MultirateTrackEditor	mte;
		
			if( !doc.bird.attemptShared( Session.DOOR_ALL, 500 )) return;
			try {
				selSpan			= doc.timeline.getSelectionSpan();
				if( selSpan.isEmpty() ) return;
				deleteBefore	= new Span( 0, selSpan.getStart() );
				deleteAfter		= new Span( selSpan.getStop(), doc.timeline.getLength() );
// INERTIA
//				edit			= new SyncCompoundSessionObjEdit( this, doc.bird, Session.DOOR_ALL,
//										doc.tracks.getAll(), Track.OWNER_WAVE, null, null,	 // XXX getAll
//										getValue( Action.NAME ).toString() );
edit = new BasicSyncCompoundEdit( doc.bird, Session.DOOR_ALL, getValue( Action.NAME ).toString() );
// INERTIA
//				mte				= doc.getMTE();
//				try {
					if( !deleteAfter.isEmpty() ) {
						edit.addEdit( new EditRemoveTimeSpan( this, doc, deleteAfter ));
// INERTIA
//						mte.remove( deleteAfter, edit );
					}
					if( !deleteBefore.isEmpty() ) {
						edit.addEdit( new EditRemoveTimeSpan( this, doc, deleteBefore ));
// INERTIA
//						mte.remove( deleteAfter, edit );
					}
					edit.end(); // fires doc.tc.modified()
					doc.getUndoManager().addEdit( edit );
// INERTIA
//				}
//				catch( IOException e1 ) {
//					edit.cancel();
//					GUIUtil.displayError( null, e1, getValue( Action.NAME ).toString() );
//				}
			}
			finally {
				doc.bird.releaseShared( Session.DOOR_ALL );
			}
		}
	} // class actionTrimToSelectionClass

	private class actionInsertSilenceClass
	extends MenuAction
	implements RunnableProcessing
	{
		private Number duration = new Double( 1.0 );   // seconds

		public void actionPerformed( ActionEvent e )
		{
			final JPanel		msgPane;
			final NumberField	ggDuration;
			final int			result;
			
			msgPane			= new JPanel( new SpringLayout() );
			ggDuration		= new NumberField();	// XXX unit label
			ggDuration.setSpace( NumberSpace.genericDoubleSpace );
			ggDuration.setNumber( duration );
			msgPane.add( ggDuration );
			
			GUIUtil.makeCompactSpringGrid( msgPane, 1, 1, 4, 2, 4, 2 );	// #row #col initx inity padx pady

			result = JOptionPane.showOptionDialog( null, msgPane, getValue( NAME ).toString(),
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, null, null );

			if( result == JOptionPane.OK_OPTION ) {
				duration = ggDuration.getNumber();
				if( duration.doubleValue() > 0.0 ) {
					new ProcessingThread( this, doc.getFrame(), doc.bird,
										  getValue( NAME ).toString(), new Object[]
										  { doc, duration }, Session.DOOR_ALL );
				}
			}
		}

		/**
		 *  This method is called by ProcessingThread
		 */
		public boolean run( ProcessingThread context, Object argument )
		{
			long					position, docLength;
			final Object[]			args		= (Object[]) argument;
			final Session			doc			= (Session) args[0];
			final Number			duration	= (Number) args[1];
			final long				pasteLength	= (long) (doc.timeline.getRate() * duration.doubleValue() + 0.5);
			SyncCompoundEdit		edit;
			Span					selSpan;
// INERTIA
//			MultirateTrackEditor	mte;
//			BlendContext			bc;
//			BlendSpan				bs;
//			float[][]				buf;
			int						len;
			boolean					success		= false;

// INERTIA
//			edit		= new SyncCompoundSessionObjEdit( this, doc.bird, Session.DOOR_ALL, doc.tracks.getAll(),  // XXX getAll
//								Track.OWNER_WAVE, null, null, context.getName() );
edit = new BasicSyncCompoundEdit( doc.bird, Session.DOOR_ALL, context.getName() );
			position	= doc.timeline.getPosition();
			selSpan		= doc.timeline.getSelectionSpan();
			docLength	= doc.timeline.getLength();
// INERTIA
//			bc			= BlendingAction.createBlendContext(
//				AbstractApplication.getApplication().getUserPrefs().node( BlendingAction.DEFAULT_NODE ),
//				doc.timeline.getRate(), Math.min( pasteLength, Math.min( position, docLength - position )) / 2 );

// INERTIA
//			try {
				if( !selSpan.isEmpty() ) { // deselect
					edit.addEdit( new EditSetTimelineSelection( this, doc, new Span() ));
				}
				selSpan		= new Span( position, position + pasteLength );

// INERTIA
//				mte			= doc.getMTE();
//				// automatically filled with zeroes
//				buf			= new float[ mte.getChannels() ][ (int) Math.min( 8192, pasteLength )];
//
//				bs			= mte.beginInsert( selSpan, bc, edit );
//				for( long off = 0; off < pasteLength; ) {
//					len		= (int) Math.min( pasteLength - off, 8192 );
//					mte.continueWrite( bs, buf, 0, len );
//					off     += len;
//					context.setProgression( (float) off / (float) pasteLength );
//				}
//				mte.finishWrite( bs, edit );

				if( pasteLength != 0 ) {	// adjust timeline
					edit.addEdit( new EditSetTimelineLength( this, doc, docLength + pasteLength ));
					doc.markers.insertSpan( this, selSpan, edit );
				}
				if( !selSpan.isEmpty() ) {
					edit.addEdit( new EditSetTimelineSelection( this, doc, selSpan ));
				}
				edit.end();
				doc.getUndoManager().addEdit( edit );
				success = true;
// INERTIA
//			}
//			catch( IOException e1 ) {
//				edit.cancel();
//				context.setException( e1 );
//			}
			
			return success;
		}

		public void finished( ProcessingThread context, Object argument, boolean success ) {}
	} // class actionInsertSilenceClass

	private class actionMissingLinksClass
	extends MenuAction
	{
		public void actionPerformed( ActionEvent e )
		{
			new MissingLinksDialog( doc );
		}
	}

	private class actionCompactAudioClass
	extends MenuAction
	{
		public void actionPerformed( ActionEvent e )
		{
			new CompactAudioDialog( doc );
		}
	}

	/**
	 *  Increase or decrease the width
	 *  of the visible time span
	 */
	private class actionSpanWidthClass
	extends AbstractAction
	{
		private final float factor;
		
		/**
		 *  @param  factor  factors > 1 increase the span width (zoom out)
		 *					factors < 1 decrease (zoom in).
		 *					special value 0.0 means zoom to sample level
		 */
		private actionSpanWidthClass( float factor )
		{
			super();
			this.factor = factor;
		}
		
		public void actionPerformed( ActionEvent e )
		{
			perform();
		}
		
		public void perform()
		{
			long			pos, visiLen, start, stop;
// INERTIA
//			UndoableEdit	edit;
			Span			visiSpan;
			
			if( !doc.bird.attemptExclusive( Session.DOOR_TIME, 250 )) return;
			try {
				visiSpan	= timelineVis;
				visiLen		= visiSpan.getLength();
				pos			= timelinePos[ activeChannel ]; // doc.timeline.getPosition();
				if( factor == 0.0f ) {				// to sample level
					start	= Math.max( 0, pos - (ggTrackPanel.getWidth() >> 1) );
					stop	= Math.min( doc.timeline.getLength(), start + ggTrackPanel.getWidth() );
				} else if( factor < 1.0f ) {		// zoom in
					if( visiLen < 4 ) return;
					// if timeline pos visible -> try to keep it's relative position constant
					if( visiSpan.contains( pos )) {
						start	= pos - (long) ((pos - visiSpan.getStart()) * factor + 0.5f);
						stop    = start + (long) (visiLen * factor + 0.5f);
					// if timeline pos before visible span, zoom left hand
					} else if( visiSpan.getStart() > pos ) {
						start	= visiSpan.getStart();
						stop    = start + (long) (visiLen * factor + 0.5f);
					// if timeline pos after visible span, zoom right hand
					} else {
						stop	= visiSpan.getStop();
						start   = stop - (long) (visiLen * factor + 0.5f);
					}
				} else {			// zoom out
					start   = Math.max( 0, visiSpan.getStart() - (long) (visiLen * factor/4 + 0.5f) );
					stop    = Math.min( doc.timeline.getLength(), start + (long) (visiLen * factor + 0.5f) );
				}
				visiSpan	= new Span( start, stop );
				if( !visiSpan.isEmpty() ) {
// INERTIA
//					edit	= new EditSetTimelineScroll( this, doc, new Span( start, stop ));
//					doc.getUndoManager().addEdit( edit );
timeline.setVisibleSpan( this, new Span( start, stop ));
				}
			}
			finally {
				doc.bird.releaseExclusive( Session.DOOR_TIME );
			}
		}
	} // class actionSpanWidthClass

	private static final int SCROLL_SESSION_START	= 0;
	private static final int SCROLL_SELECTION_START	= 1;
	private static final int SCROLL_SELECTION_STOP	= 2;
	private static final int SCROLL_FIT_TO_SELECTION= 3;
	private static final int SCROLL_ENTIRE_SESSION	= 4;

	private class actionScrollClass
	extends AbstractAction
	{
		private final int mode;
	
		private actionScrollClass( int mode )
		{
			super();
			
			this.mode = mode;
		}
	
		public void actionPerformed( ActionEvent e )
		{
// INERTIA
//			UndoableEdit	edit	= null;
			Span			selSpan, newSpan;
			long			start, stop;
		
			if( mode == SCROLL_SESSION_START && transport.isRunning( activeChannel )) {
				transport.stopAndWait( activeChannel );
			}
			if( !doc.bird.attemptExclusive( Session.DOOR_TIME, 250 )) return;
			try {
				selSpan		= timelineSel; // doc.timeline.getSelectionSpan();
				
				switch( mode ) {
				case SCROLL_SESSION_START:
					if( timelinePos[ activeChannel ] != 0 ) {
// INERTIA
//						edit	= new EditSetTimelinePosition( this, doc, 0 );
timeline.setPosition( this, 0 );
						if( !timelineVis.contains( 0 )) {
// INERTIA
//							final CompoundEdit ce	= new BasicSyncCompoundEdit( doc.bird, Session.DOOR_TIME );
//							ce.addEdit( edit );
							newSpan	= new Span( 0, timelineVis.getLength() );
// INERTIA
//							ce.addEdit( new EditSetTimelineScroll( this, doc, newSpan ));
//							ce.end();
//							edit	= ce;
timeline.setVisibleSpan( this, newSpan );
						}
					}
					break;
					
				case SCROLL_SELECTION_START:
					if( selSpan.isEmpty() ) selSpan = new Span( timelinePos[ activeChannel ], timelinePos[ activeChannel ]);
					if( timelineVis.contains( selSpan.getStart() )) {
						start = Math.max( 0, selSpan.getStart() - (timelineVis.getLength() >> 1) );
					} else {
						start = Math.max( 0, selSpan.getStart() - (timelineVis.getLength() >> 3) );
					}
					stop	= Math.min( doc.timeline.getLength(), start + timelineVis.getLength() );
					newSpan	= new Span( start, stop );
					if( !timelineVis.equals( newSpan ) && !newSpan.isEmpty() ) {
// INERTIA
//						edit	= new EditSetTimelineScroll( this, doc, newSpan );
timeline.setVisibleSpan( this, newSpan );
					}
					break;

				case SCROLL_SELECTION_STOP:
					if( selSpan.isEmpty() ) selSpan = new Span( timelinePos[ activeChannel ], timelinePos[ activeChannel ]);
					if( timelineVis.contains( selSpan.getStop() )) {
						stop = Math.min( doc.timeline.getLength(), selSpan.getStop() + (timelineVis.getLength() >> 1) );
					} else {
						stop = Math.min( doc.timeline.getLength(), selSpan.getStop() + (timelineVis.getLength() >> 3) );
					}
					start	= Math.max( 0, stop - timelineVis.getLength() );
					newSpan	= new Span( start, stop );
					if( !timelineVis.equals( newSpan ) && !newSpan.isEmpty() ) {
// INERTIA
//						edit	= new EditSetTimelineScroll( this, doc, newSpan );
timeline.setVisibleSpan( this, newSpan );
					}
					break;

				case SCROLL_FIT_TO_SELECTION:
					newSpan		= selSpan;
					if( !timelineVis.equals( newSpan ) && !newSpan.isEmpty() ) {
//						edit	= new EditSetTimelineScroll( this, doc, newSpan );
timeline.setVisibleSpan( this, newSpan );
					}
					break;

				case SCROLL_ENTIRE_SESSION:
					newSpan		= new Span( 0, doc.timeline.getLength() );
					if( !timelineVis.equals( newSpan ) && !newSpan.isEmpty() ) {
// INERTIA
//						edit	= new EditSetTimelineScroll( this, doc, newSpan );
timeline.setVisibleSpan( this, newSpan );
					}
					break;

				default:
					assert false : mode;
					break;
				}
// INERTIA
//				if( edit != null ) doc.getUndoManager().addEdit( edit );
			}
			finally {
				doc.bird.releaseExclusive( Session.DOOR_TIME );
			}
		}
	} // class actionScrollClass
	
	private static final int SELECT_TO_SESSION_START	= 0;
	private static final int SELECT_TO_SESSION_END		= 1;

	private class actionSelectClass
	extends AbstractAction
	{
		private final int mode;
	
		private actionSelectClass( int mode )
		{
			super();
			
			this.mode = mode;
		}
	
		public void actionPerformed( ActionEvent e )
		{
			Span			selSpan, newSpan = null;
		
			if( !doc.bird.attemptExclusive( Session.DOOR_TIME, 250 )) return;
			try {
				selSpan		= timelineSel; // doc.timeline.getSelectionSpan();
				if( selSpan.isEmpty() ) {
					selSpan	= new Span( timelinePos[ activeChannel ], timelinePos[ activeChannel ]);
				}
				
				switch( mode ) {
				case SELECT_TO_SESSION_START:
					if( selSpan.getStop() > 0 ){
						newSpan = new Span( 0, selSpan.getStop() );
					}
					break;

				case SELECT_TO_SESSION_END:
					if( selSpan.getStart() < doc.timeline.getLength() ){
						newSpan = new Span( selSpan.getStart(), doc.timeline.getLength() );
					}
					break;

				default:
					assert false : mode;
					break;
				}
				if( newSpan != null && !newSpan.equals( selSpan )) {
// INERTIA
//					doc.getUndoManager().addEdit( new EditSetTimelineSelection( this, doc, newSpan ));
timeline.setSelectionSpan( this, newSpan );
				}
			}
			finally {
				doc.bird.releaseExclusive( Session.DOOR_TIME );
			}
		}
	} // class actionSelectClass
	
	private class actionSelToPosClass
	extends AbstractAction
	{
		private final float weight;
	
		private actionSelToPosClass( float weight )
		{
			super();
			
			this.weight = weight;
		}
	
		public void actionPerformed( ActionEvent e )
		{
			Span			selSpan;
// INERTIA
//			CompoundEdit	edit;
			long			pos;
		
			if( !doc.bird.attemptExclusive( Session.DOOR_TIME, 250 )) return;
			try {
				selSpan		= timelineSel; // doc.timeline.getSelectionSpan();
				if( selSpan.isEmpty() ) return;
				
// INERTIA
//				edit	= new BasicSyncCompoundEdit( doc.bird, Session.DOOR_TIME );
//				edit.addEdit( new EditSetTimelineSelection( this, doc, new Span() ));
timeline.setSelectionSpan( this, new Span() );
				pos		= (long) (selSpan.getStart() + selSpan.getLength() * weight + 0.5);
// INERTIA
//				edit.addEdit( new EditSetTimelinePosition( this, doc, pos ));
//				edit.end();
//				doc.getUndoManager().addEdit( edit );
timeline.setPosition( this, pos );
			}
			finally {
				doc.bird.releaseExclusive( Session.DOOR_TIME );
			}
		}
	} // class actionSelToPosClass

	private static final int SELECT_NEXT_REGION	= 0;
	private static final int SELECT_PREV_REGION	= 1;
	private static final int EXTEND_NEXT_REGION	= 2;
	private static final int EXTEND_PREV_REGION	= 3;

	private class actionSelectRegionClass
	extends AbstractAction
	{
		private final int mode;
	
		private actionSelectRegionClass( int mode )
		{
			super();
			
			this.mode = mode;
		}
	
		public void actionPerformed( ActionEvent e )
		{
			Span			selSpan;
// INERTIA
//			UndoableEdit	edit;
			long			pos, start, stop;
			boolean			b;
			Marker			mark;
			int				idx;
			
			if( !markAxis.isVisible() ) return;
		
			if( !doc.bird.attemptExclusive( Session.DOOR_TIME, 250 )) return;
			try {
				selSpan		= timelineSel; // doc.timeline.getSelectionSpan();
				if( selSpan.isEmpty() ) selSpan = new Span( timelinePos[ activeChannel ], timelinePos[ activeChannel ]);
				
				start		= selSpan.getStart();
				stop		= selSpan.getStop();
				
				switch( mode ) {
				case SELECT_NEXT_REGION:
				case EXTEND_NEXT_REGION:
					idx		= doc.markers.indexOf( stop, true );
					if(	idx == -1 ) {
						stop	= doc.timeline.getLength();
					} else {
						do {
							b		= false;
							mark	= doc.markers.getMarker( idx );
							if( mark.pos == stop ) {
								if( ++idx == doc.markers.getNumMarkers() ) {
									stop = doc.timeline.getLength();
								} else {
									b	= true;
								}
							} else {
								stop	= mark.pos;
							}
						} while( b );
					}
					
					if( mode == SELECT_NEXT_REGION ) {
						idx		= doc.markers.indexOf( stop, false );
						if( idx == -1 ) {
							start	= 0;
						} else {
							mark	= doc.markers.getMarker( idx );
							start	= mark.pos;
						}
					}
					break;

				case SELECT_PREV_REGION:
				case EXTEND_PREV_REGION:
					idx		= doc.markers.indexOf( start, false );
					if(	idx == -1 ) {
						start	= 0;
					} else {
						do {
							b		= false;
							mark	= doc.markers.getMarker( idx );
							if( mark.pos == start ) {
								if( --idx <= 0 ) {
									start = 0;
								} else {
									b	= true;
								}
							} else {
								start	= mark.pos;
							}
						} while( b );
					}
					
					if( mode == SELECT_PREV_REGION ) {
						idx		= doc.markers.indexOf( start + 1, true );
						if( idx == -1 ) {
							stop	= doc.timeline.getLength();
						} else {
							mark	= doc.markers.getMarker( idx );
							stop	= mark.pos;
						}
					}
					break;

				default:
					assert false : mode;
					break;
				}
				
				if( (start == selSpan.getStart()) && (stop == selSpan.getStop()) ) return;
				
// INERTIA
//				edit	= new EditSetTimelineSelection( this, doc, new Span( start, stop ));
//				doc.getUndoManager().addEdit( edit );
doc.timeline.setSelectionSpan( this, new Span( start, stop ));
			}
			finally {
				doc.bird.releaseExclusive( Session.DOOR_TIME );
			}
		}
	} // class actionSelectRegionClass
		
	private class actionDropMarkerClass
	extends AbstractAction
	{
		public void actionPerformed( ActionEvent e )
		{
			if( markAxis.isVisible() ) {
				markAxis.addMarker( timelinePos[ activeChannel ]);
			}
		}
	} // class actionDropMarkerClass

// ---------------- the viewport component---------------- 

	public class TimelineViewport
	extends JViewport
	{
		private static final long serialVersionUID = 0x050929L;

		// --- painting ---
		private final Color colrSelection		= GraphicsUtil.colrSelection;
		private final Color colrSelection2		= new Color( 0xB0, 0xB0, 0xB0, 0x3F );  // selected timeline span over unselected trns
//		private final Color colrPosition		= new Color( 0xFF, 0x00, 0x00, 0x4F );
		private final Color colrZoom			= new Color( 0xA0, 0xA0, 0xA0, 0x7F );
//		private final Color colrPosition		= Color.red;
		private Rectangle   recentRect			= new Rectangle();
		private final int[]	position			= new int[ timelinePos.length ];
		private final Rectangle[] positionRect	= new Rectangle[ timelinePos.length ];
		private final ArrayList	selections		= new ArrayList();
		private final ArrayList	selectionColors	= new ArrayList();
		private Rectangle	selectionRect		= new Rectangle();
		
		private Rectangle   updateRect			= new Rectangle();
		private Rectangle   zoomRect			= null;
		private float[]		dash				= { 3.0f, 5.0f };

		private float		scale;

		private final Stroke[] zoomStroke			= {
			new BasicStroke( 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER, 1.0f, dash, 0.0f ),
			new BasicStroke( 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER, 1.0f, dash, 4.0f ),
			new BasicStroke( 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER, 1.0f, dash, 6.0f ),
		};
		private int			zoomStrokeIdx		= 0;
	
		public TimelineViewport()
		{
			super();
//			setScrollMode( BACKINGSTORE_SCROLL_MODE );
			for( int ch = 0; ch < position.length; ch++ ) {
				position[ ch ] = -1;
				positionRect[ ch ] = new Rectangle();
			}
		}


//		public void paint( Graphics g )
//		{
//			super.paint( g );
	
		/**
		 *  See paint( Graphics ) for details
		 */
//		public void paintChildren( Graphics g )
//		{
//			super.paintChildren( g );
//		protected void paintComponent( Graphics g )
//		{
//			super.paintComponent( g );
//			
//			paintGAGA( g );
//		}
		
		public void paintGAGA( Graphics g )
		{
			final Graphics2D	g2			= (Graphics2D) g;
			Rectangle			r;

			r = getViewRect();
			if( !recentRect.equals( r )) {
				recalcTransforms( r );
			}

			for( int i = 0; i < selections.size(); i++ ) {
				r = (Rectangle) selections.get( i );
				g2.setColor( (Color) selectionColors.get( i ));
				g2.fillRect( selectionRect.x, r.y - recentRect.y, selectionRect.width, r.height );
			}
			
			if( markAxis.isVisible() ) {
				markAxis.paintFlagSticks( g2, recentRect );
			}
			
			for( int ch = 0; ch < position.length; ch++ ) {
				g2.setColor( colrPosition[ ch ]);
				g2.drawLine( position[ ch ], 0, position[ ch ], recentRect.height );
			}

			if( zoomRect != null ) {
				g2.setColor( colrZoom );
				g2.setStroke( zoomStroke[ zoomStrokeIdx ]);
				g2.drawRect( zoomRect.x, zoomRect.y, zoomRect.width, zoomRect.height );
			}
		}

		public void setZoomRect( Rectangle r )
		{
			zoomRect		= r;
			zoomStrokeIdx	= (zoomStrokeIdx + 1) % zoomStroke.length;
			repaint();
		}

		/**
		 *  Custom painting. This is really tricky... It seems that JViewport
		 *  saves its new backing store image, at the end of the paint() call.
		 *  Therefore, we first call super.paint() which ensures that our
		 *  song position line isn't painted in the backing store image. This is
		 *  because paintChildren() is hell slow so for realtime playback update
		 *  of the song position line we call paintDirty() directly and instead
		 *  of calling paintChildren() we redraw the dirty part of the
		 *  backing store image. To speed things up, the paining of the selected
		 *  areas is performed at the end of the paintChildren() method and
		 *  therefore is "frozen" in the backing store image.
		 */
//		public void paint( Graphics g )
//		{
//			Rectangle currentRect = getViewRect();
//
//			if( !recentRect.equals( currentRect )) {
//				recalcTransforms();
//			}
//
//			super.paint( g );
//
//			g.setColor( colrPosition );
//			g.drawLine( position, 0, position, recentRect.height );
//		}

//		/**
//		 *  See paint( Graphics ) for details
//		 */
//		private void paintDirty( Graphics g, Rectangle updateRect )
//		{
//			Image img = backingStoreImage;
//			if( img != null ) {
//				g.drawImage( img, updateRect.x, updateRect.y,
//							 updateRect.width + updateRect.x, updateRect.height + updateRect.y,
//							 updateRect.x, updateRect.y,
//							 updateRect.width + updateRect.x, updateRect.height + updateRect.y,
//							 this );
//			}
//			g.setColor( colrPosition );
//			g.drawLine( position, 0, position, recentRect.height );
//		}

		/**
		 *  Only call in the Swing thread!
		 */
		private void updatePositionAndRepaint( int ch )
		{
			boolean pEmpty, cEmpty;
			int		x, x2;
			
			pEmpty = (positionRect[ ch ].x + positionRect[ ch ].width < 0) || (positionRect[ ch ].x > recentRect.width);
			if( !pEmpty ) updateRect.setBounds( positionRect[ ch ]);

//			recalcTransforms();
			if( scale > 0f ) {
				position[ ch ] = (int) ((timelinePos[ ch ] - timelineVis.getStart()) * scale); // + 0.5);
				positionRect[ ch ].setBounds( position[ ch ], 0, 1, recentRect.height );
			} else {
				position[ ch ] = -1;
				positionRect[ ch ].setBounds( 0, 0, 0, 0 );
			}

			cEmpty = (positionRect[ ch ].x + positionRect[ ch ].width <= 0) || (positionRect[ ch ].x > recentRect.width);
			if( pEmpty ) {
				if( cEmpty ) return;
				x   = Math.max( 0, positionRect[ ch ].x );
				x2  = Math.min( recentRect.width, positionRect[ ch ].x + positionRect[ ch ].width );
				updateRect.setBounds( x, positionRect[ ch ].y, x2 - x, positionRect[ ch ].height );
			} else {
				if( cEmpty ) {
					x   = Math.max( 0, updateRect.x );
					x2  = Math.min( recentRect.width, updateRect.x + updateRect.width );
					updateRect.setBounds( x, updateRect.y, x2 - x, updateRect.height );
				} else {
					x   = Math.max( 0, Math.min( updateRect.x, positionRect[ ch ].x ));
					x2  = Math.min( recentRect.width, Math.max( updateRect.x + updateRect.width,
																positionRect[ ch ].x + positionRect[ ch ].width ));
					updateRect.setBounds( x, updateRect.y, x2 - x, updateRect.height );
				}
			}
			if( !updateRect.isEmpty() ) repaint( updateRect );
//			if( !updateRect.isEmpty() ) paintImmediately( updateRect );
//			Graphics g = getGraphics();
//			if( g != null ) {
//				paintDirty( g, updateRect );
//				g.dispose();
//			}
		}

		/**
		 *  Only call in the Swing thread!
		 */
		private void updateSelectionAndRepaint()
		{
			updateRect.setBounds( selectionRect );
			recalcTransforms( getViewRect() );
//			try {
//				doc.bird.waitShared( Session.DOOR_TIMETRNS | Session.DOOR_GRP );
				updateSelection();
//			}
//			finally {
//				doc.bird.releaseShared( Session.DOOR_TIMETRNS | Session.DOOR_GRP );
//			}
			if( updateRect.isEmpty() ) {
				updateRect.setBounds( selectionRect );
			} else if( !selectionRect.isEmpty() ) {
				updateRect = updateRect.union( selectionRect );
			}
			if( !updateRect.isEmpty() ) repaint( updateRect );
//			if( !updateRect.isEmpty() ) {
//				Graphics g = getGraphics();
//				if( g != null ) {
//					paintDirty( g, updateRect );
//				}
//				g.dispose();
//			}
		}
		
		/**
		 *  Only call in the Swing thread!
		 */
		private void updateTransformsAndRepaint( boolean verticalSelection )
		{
			updateRect.setBounds( selectionRect );
			for( int ch = 0; ch < position.length; ch++ ) {
				updateRect = updateRect.union( positionRect[ ch ]);
			}
			recalcTransforms( getViewRect() );
			if( verticalSelection ) updateSelection();
			updateRect = updateRect.union( selectionRect );
			for( int ch = 0; ch < position.length; ch++ ) {
				updateRect = updateRect.union( positionRect[ ch ]);
			}
			if( !updateRect.isEmpty() ) repaint( updateRect );
		}
		
		private void recalcTransforms( Rectangle newRect )
		{
			int x, w;
			
			recentRect = newRect; // getViewRect();
		
			if( !timelineVis.isEmpty() ) {
				scale           = (float) recentRect.width / (float) timelineVis.getLength(); // - 1;
				for( int ch = 0; ch < position.length; ch++ ) {
					position[ ch ] = (int) ((timelinePos[ ch ] - timelineVis.getStart()) * scale); // + 0.5);
					positionRect[ ch ].setBounds( position[ ch ], 0, 1, recentRect.height );
				}
				if( !timelineSel.isEmpty() ) {
					x			= (int) ((timelineSel.getStart() - timelineVis.getStart()) * scale + 0.5f) + recentRect.x;
					w			= Math.max( 1, (int) (timelineSel.getLength() * scale + 0.5f));
					selectionRect.setBounds( x, 0, w, recentRect.height );
				} else {
					selectionRect.setBounds( 0, 0, 0, 0 );
				}
			} else {
				scale			= 0.0f;
				for( int ch = 0; ch < position.length; ch++ ) {
					position[ ch ] = -1;
					positionRect[ ch ].setBounds( 0, 0, 0, 0 );
				}
				selectionRect.setBounds( 0, 0, 0, 0 );
			}
		}

		// sync: caller must sync on timeline + grp + tc
		private void updateSelection()
		{
			int					i;
//			TransmitterEditor   edit;
//			Transmitter			trns;

			selections.clear();
			selectionColors.clear();
			if( !timelineSel.isEmpty() ) {
selections.add( ggTrackPanel.getBounds() );
selectionColors.add( colrSelection );
//				for( i = 0; i < doc.activeTransmitters.size(); i++ ) {
//					trns	= (Transmitter) doc.activeTransmitters.get( i );
//					edit	= (TransmitterEditor) hashTransmittersToEditors.get( trns );
//					if( edit == null ) continue;
//					selections.add( edit.getView().getBounds() );
//					selectionColors.add( doc.selectedTransmitters.contains( trns ) ? colrSelection : colrSelection2 );
//				}
			}
		}
	}

	private abstract class TimelineTool
	extends AbstractTool
	{
		private final java.util.List	collObservedComponents	= new ArrayList();
//		private final java.util.List	collOldCursors			= new ArrayList();
	
		public void toolAcquired( Component c )
		{
			super.toolAcquired( c );
			
			if( c instanceof Container ) addMouseListeners( (Container) c );
		}
		
		// additionally installs mouse input listeners on child components
		private void addMouseListeners( Container c )
		{
			Component	c2;
//			Cursor		csr	= c.getCursor();
			
			for( int i = 0; i < c.getComponentCount(); i++ ) {
				c2 = c.getComponent( i );
				collObservedComponents.add( c2 );
//				collOldCursors.add( csr );
				c2.addMouseListener( this );
				c2.addMouseMotionListener( this );
//				c2.setCursor( c.getCursor() );
				if( c2 instanceof Container ) addMouseListeners( (Container) c2 );	// recurse
			}
		}
		
		// additionally removes mouse input listeners from child components
		private void removeMouseListeners()
		{
			Component	c;
//			Cursor		csr;
		
			while( !collObservedComponents.isEmpty() ) {
				c	= (Component) collObservedComponents.remove( 0 );
//				csr	= (Cursor) collOldCursors.remove( 0 );
				c.removeMouseListener( this );
				c.removeMouseMotionListener( this );
//				c.setCursor( csr );
			}
		}

		public void toolDismissed( Component c )
		{
			super.toolDismissed( c );

			removeMouseListeners();
		}
	}
	
	/*
	 *	Keyboard modifiers are consistent with Bias Peak:
	 *	Shift+Click = extend selection, Meta+Click = select all,
	 *	Alt+Drag = drag timeline position; double-click = Play
	 */
	private class TimelinePointerTool
	extends TimelineTool
	{
		private boolean shiftDrag, ctrlDrag, validDrag = false, dragStarted = false;
		private long startPos;
		private int startX;
	
		public void paintOnTop( Graphics2D g )
		{
			// not necessary
		}
		
		public void mousePressed( MouseEvent e )
		{
			if( e.isMetaDown() ) {
//				editSelectAll( null );
				selectRegion( e );
				dragStarted = false;
				validDrag	= false;
			} else {
				shiftDrag	= e.isShiftDown();
				ctrlDrag	= e.isControlDown();
				dragStarted = false;
				validDrag	= true;
				startX		= e.getX();
				processDrag( e, false ); 
			}
		
//			selectionStart  = -1;
//			dragTimelinePosition( e );
		}

		public void mouseDragged( MouseEvent e )
		{
			if( validDrag ) {
				if( !dragStarted ) {
					if( shiftDrag || ctrlDrag || Math.abs( e.getX() - startX ) > 2 ) {
						dragStarted = true;
					} else return;
				}
				processDrag( e, true );
			}
		}
		
		private void selectRegion( MouseEvent e )
		{
			final Point pt	= SwingUtilities.convertPoint( e.getComponent(), e.getPoint(), ggTrackPanel );

			Span			span, span2;
			long			pos, start, stop;
// INERTIA
//			UndoableEdit	edit;
			int				idx;
			Marker			mark;

			if( !doc.bird.attemptExclusive( Session.DOOR_TIME, 250 )) return;
			try {
				span        = timelineVis; // doc.timeline.getVisibleSpan();
				span2		= timelineSel; // doc.timeline.getSelectionSpan();
				pos			= span.getStart() + (long) ((double) pt.getX() / (double) getComponent().getWidth() *
														(double) span.getLength());
				pos			= Math.max( 0, Math.min( doc.timeline.getLength(), pos ));

				stop		= doc.timeline.getLength();
				start		= 0;

				if( markAxis.isVisible() ) {
					idx		= doc.markers.indexOf( pos, true );
					if(	idx >= 0 ) {
						mark	= doc.markers.getMarker( idx );
						stop	= mark.pos;
					}
					idx		= doc.markers.indexOf( stop, false );
					if( idx >= 0 ) {
						mark	= doc.markers.getMarker( idx );
						start	= mark.pos;
					}
				}
				
				span	= new Span( start, stop );
				if( span.equals( span2 )) {
					span	= new Span( 0, doc.timeline.getLength() );
				}
				if( !span.equals( span2 )) {
// INERTIA
//					edit = new EditSetTimelineSelection( this, doc, span );
//					doc.getUndoManager().addEdit( edit );
doc.timeline.setSelectionSpan( this, span );
				}
			}
			finally {
				doc.bird.releaseExclusive( Session.DOOR_TIME );
			}
		}

		private void processDrag( MouseEvent e, boolean hasStarted )
		{
			final Point pt	= SwingUtilities.convertPoint( e.getComponent(), e.getPoint(), ggTrackPanel );
			
			Span			span, span2;
			long			position;
// INERTIA
//			UndoableEdit	edit;
		   
			if( !doc.bird.attemptExclusive( Session.DOOR_TIME, 250 )) return;
			try {
				span        = timelineVis; // doc.timeline.getVisibleSpan();
				span2		= timelineSel; // doc.timeline.getSelectionSpan();
				position    = span.getStart() + (long) ((double) pt.getX() / (double) getComponent().getWidth() *
														(double) span.getLength());
				position    = Math.max( 0, Math.min( doc.timeline.getLength(), position ));
				if( !hasStarted && !ctrlDrag ) {
					if( shiftDrag ) {
						if( span2.isEmpty() ) {
							span2 = new Span( timelinePos[ activeChannel ], timelinePos[ activeChannel ]);
						}
						startPos = Math.abs( span2.getStart() - position ) >
								   Math.abs( span2.getStop() - position ) ?
										span2.getStart() : span2.getStop();
						span2	= new Span( Math.min( startPos, position ),
											Math.max( startPos, position ));
// INERTIA
//						edit	= new EditSetTimelineSelection( this, doc, span2 );
doc.timeline.setSelectionSpan( this, span2 );
					} else {
						startPos = position;
						if( span2.isEmpty() ) {
// INERTIA
//							edit = new EditSetTimelinePosition( this, doc, position );
doc.timeline.setPosition( this, position );
						} else {
// INERTIA
//							edit = new CompoundEdit();
//							edit.addEdit( new EditSetTimelineSelection( this, doc, new Span() ));
//							edit.addEdit( new EditSetTimelinePosition( this, doc, position ));
//							((CompoundEdit) edit).end();
doc.timeline.setSelectionSpan( this, new Span() );
doc.timeline.setPosition( this, position );
						}
					}
				} else {
					if( ctrlDrag ) {
// INERTIA
//						edit	= new EditSetTimelinePosition( this, doc, position );
doc.timeline.setPosition( this, position );
					} else {
						span2	= new Span( Math.min( startPos, position ),
											Math.max( startPos, position ));
// INERTIA
//						edit	= new EditSetTimelineSelection( this, doc, span2 );
doc.timeline.setSelectionSpan( this, span2 );
					}
				}
// INERTIA
//				doc.getUndoManager().addEdit( edit );
			}
			finally {
				doc.bird.releaseExclusive( Session.DOOR_TIME );
			}
		}

		public void mouseReleased( MouseEvent e )
		{
			Span span2;

			if( dragStarted && !shiftDrag && !ctrlDrag ) {
				if( !doc.bird.attemptExclusive( Session.DOOR_TIME, 250 )) return;
				try {
					span2 = timelineSel; // doc.timeline.getSelectionSpan();
					if( !span2.isEmpty() && timelinePos[ activeChannel ] != span2.getStart() ) {
						doc.getUndoManager().addEdit( new EditSetTimelinePosition( this, doc, span2.getStart() ));
					}
				}
				finally {
					doc.bird.releaseExclusive( Session.DOOR_TIME );
				}
			}
			
			dragStarted = false;
			validDrag	= false;
		}

		public void mouseClicked( MouseEvent e )
		{
			if( (e.getClickCount() == 2) && !e.isMetaDown() && !transport.isRunning( activeChannel )) {
				transport.goPlay( activeChannel, doc.timeline.getPosition(), 1.0f );
			}
		}

		// on Mac, Ctrl+Click is interpreted as
		// popup trigger by the system which means
		// no successive mouseDragged calls are made,
		// instead mouseMoved is called ...
		public void mouseMoved( MouseEvent e )
		{
			mouseDragged( e );
		}

		public void mouseEntered( MouseEvent e ) {}
		public void mouseExited( MouseEvent e ) {}
	}

	private class TimelineZoomTool
	extends TimelineTool
	{
		private boolean					validDrag	= false, dragStarted = false;
		private long					startPos;
		private Point					startPt;
		private long					position;
		private final javax.swing.Timer	zoomTimer;
		private final Rectangle			zoomRect	= new Rectangle();

		private TimelineZoomTool()
		{
			zoomTimer = new javax.swing.Timer( 250, new ActionListener() {
				public void actionPerformed( ActionEvent e )
				{
					vpTrackPanel.setZoomRect( zoomRect );
				}
			});
		}

		public void paintOnTop( Graphics2D g )
		{
			// not necessary
		}
		
		public void mousePressed( MouseEvent e )
		{
			if( e.isAltDown() ) {
				dragStarted = false;
				validDrag	= false;
				actionIncWidth.perform();
			} else {
				dragStarted = false;
				validDrag	= true;
				processDrag( e, false ); 
			}
		}

		public void mouseDragged( MouseEvent e )
		{
			if( validDrag ) {
				if( !dragStarted ) {
					if( Math.abs( e.getX() - startPt.x ) > 2 ) {
						dragStarted = true;
						zoomTimer.restart();
					} else return;
				}
				processDrag( e, true );
			}
		}

		public void mouseReleased( MouseEvent e )
		{
			Span span;

			if( dragStarted ) {
				zoomTimer.stop();
				vpTrackPanel.setZoomRect( null );
				if( !doc.bird.attemptExclusive( Session.DOOR_TIME, 250 )) return;
				try {
					span = new Span( Math.min( startPos, position ),
									 Math.max( startPos, position ));
					if( !span.isEmpty() ) {
						doc.getUndoManager().addEdit( new EditSetTimelineScroll( this, doc, span ));
					}
				}
				finally {
					doc.bird.releaseExclusive( Session.DOOR_TIME );
				}
			}
			
			dragStarted = false;
			validDrag	= false;
		}

		public void mouseClicked( MouseEvent e )
		{
			if( !e.isAltDown() ) {
				actionDecWidth.perform();
			}
		}

		private void processDrag( MouseEvent e, boolean hasStarted )
		{
			final Point pt	= SwingUtilities.convertPoint( e.getComponent(), e.getPoint(), ggTrackPanel );
			
			Span	span;
			int		zoomX, zoomY;
		   
			if( !doc.bird.attemptShared( Session.DOOR_TIME, 250 )) return;
			try {
				span        = timelineVis; // doc.timeline.getVisibleSpan();
				position    = span.getStart() + (long) ((double) pt.getX() / (double) getComponent().getWidth() *
														(double) span.getLength());
				position    = Math.max( 0, Math.min( doc.timeline.getLength(), position ));
				if( !hasStarted ) {
					startPos= position;
					startPt	= pt;
				} else {
					zoomX	= Math.min( startPt.x, pt.x );
					zoomY	= Math.min( startPt.y, pt.y );
					zoomRect.setBounds( zoomX, zoomY, Math.abs( startPt.x - pt.x ),
													  Math.abs( startPt.y - pt.y ));
					vpTrackPanel.setZoomRect( zoomRect );
				}
			}
			finally {
				doc.bird.releaseShared( Session.DOOR_TIME );
			}
		}

		public void mouseEntered( MouseEvent e ) {}
		public void mouseExited( MouseEvent e ) {}
		public void mouseMoved( MouseEvent e ) {}
	}
}
