/*
 *  CompactAudioDialog.java
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
 *		25-Aug-05	created
 *		02-Dec-05	added sorted table model
 *		09-Dec-95	important bug fix
 */

package de.sciss.inertia.gui;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.text.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import javax.swing.undo.CompoundEdit;

import de.sciss.inertia.edit.*;
import de.sciss.inertia.math.*;
import de.sciss.inertia.session.*;

import de.sciss.app.AbstractApplication;

import de.sciss.gui.*;

import de.sciss.io.AudioFile;
import de.sciss.io.AudioFileDescr;
import de.sciss.io.Span;

import de.sciss.util.*;

public class CompactAudioDialog
extends JDialog
{
	private static final TimeFormat		timeFormat		= new TimeFormat( 0, null, null, 3 , Locale.US );	// mm:ss.millis
	private static final NumberFormat	percentFormat	= NumberFormat.getPercentInstance( Locale.US );

	private final java.util.List	collInfos	= new ArrayList();
	private final JTable			table;
	private final TableModel		tm;
	private final NumberField		ggPadding;
	private final Session			doc;
	private final MessageFormat		frmtSize;
	private final JLabel			lbSizeInfo;
	private final PathField			ggFolder;

	private final JComponent			glassPane;
	private final FocusTraversalPolicy	focusAlive;
	private final FocusTraversalPolicy	focusFrozen;

	private static final int	COL_FILE			= 0;
	private static final int	COL_PATH			= 1;
	private static final int	COL_ATOMS			= 2;
	private static final int	COL_ORIGLEN			= 3;
	private static final int	COL_COMPLEN			= 4;
	private static final int	COL_REDUCTLEN		= 5;
	private static final int	COL_REDUCTPERCENT	= 6;
	private static final int	NUM_COLUMNS			= 7;
	
	private static final String[]	COL_NAMES		= {
		"file", "folder", "atoms", "original len", "compacted len", "len reduced", "% reduced"
	};

	private final SortedTableModel	stm;

	public CompactAudioDialog( final Session doc )
	{
		super( doc.getFrame(), AbstractApplication.getApplication().getResourceString( "menuCompactAudio" ),
			   true );

		this.doc = doc;

		final Container			cp;
		final JButton			ggClose, ggCommit;
		final JScrollPane		scroll;
		final Box				b, b2, b3;
		final JPanel			bottomPanel;
		final TableColumnModel	tcm;
		final TableCellRenderer	timeRenderer;
		final TableCellRenderer	percentRenderer;
		
		frmtSize		= new MessageFormat( getResourceString( "msgFileSizeReduction" ), Locale.US );
		tm				= new TableModel();
		stm				= new SortedTableModel( tm );
		table			= new JTable( stm );
        stm.setTableHeader( table.getTableHeader() );
		tcm				= table.getColumnModel();
		timeRenderer	= new TimeRenderer();
		percentRenderer	= new PercentRenderer();
		tcm.getColumn( COL_ORIGLEN ).setCellRenderer( timeRenderer );
		tcm.getColumn( COL_COMPLEN ).setCellRenderer( timeRenderer );
		tcm.getColumn( COL_REDUCTLEN ).setCellRenderer( timeRenderer );
		tcm.getColumn( COL_REDUCTPERCENT ).setCellRenderer( percentRenderer );
		table.setSelectionMode( ListSelectionModel.MULTIPLE_INTERVAL_SELECTION );
		table.getSelectionModel().addListSelectionListener( new ListSelectionListener() {
			public void valueChanged( ListSelectionEvent e )
			{
				if( !e.getValueIsAdjusting() ) {
					recalcSizeInfo();
				}
			}
		});
		
		scroll		= new JScrollPane( table, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
											  JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED );

		ggClose		= new JButton( new actionCloseClass() );
		ggCommit	= new JButton( new actionCommitClass() );
		ggFolder	= new PathField( PathField.TYPE_FOLDER, getResourceString( "labelCompactFolder" ));
//		ggFolder.addPathListener( new PathListener() {
//			public void pathChanged( PathEvent e )
//			{
//				findReplacements( e.getPath() );
//			}
//		});
		ggPadding	= new NumberField();
		ggPadding.setSpace( new NumberSpace( 0.0, 3600.0, 0.1 ));
		ggPadding.setNumber( new Double( 1.0 ));
		ggPadding.addListener( new NumberListener() {
			public void numberChanged( NumberEvent e )
			{
				createCompactInfos( doc );
				tm.fireTableDataChanged();
			}
		});
		lbSizeInfo	= new JLabel( "", JLabel.RIGHT );
		
		b			= Box.createHorizontalBox();
		b.add( Box.createHorizontalGlue() );
		b.add( ggClose );
		b2			= Box.createHorizontalBox();
		b2.add( Box.createHorizontalGlue() );
		b2.add( ggCommit );
		b3			= Box.createHorizontalBox();
		b3.add( new JLabel( getResourceString( "labelPaddingAmount" ) + " [secs]" ));
		b3.add( ggPadding );
		b3.add( Box.createHorizontalStrut( 32 ));
		b3.add( lbSizeInfo );
		b3.add( Box.createHorizontalGlue() );
		
		bottomPanel	= new JPanel( new SpringLayout() );
		bottomPanel.add( new JLabel( getResourceString( "labelCompactFolder" )));
		bottomPanel.add( ggFolder );
		bottomPanel.add( b3 );
		bottomPanel.add( b2 );
		bottomPanel.add( new JSeparator() );
		bottomPanel.add( b );

		GUIUtil.makeCompactSpringGrid( bottomPanel, 6, 1, 4, 2, 4, 2 );	// #row #col initx inity padx pady
		
		cp			= getContentPane();
		cp.setLayout( new BorderLayout() );
		cp.add( new JLabel( getResourceString( "labelListOfUsedAudioFiles" )), BorderLayout.NORTH );
		cp.add( bottomPanel, BorderLayout.SOUTH );
		cp.add( scroll, BorderLayout.CENTER );
		
		GUIUtil.setDeepFont( this, null );

		createCompactInfos( doc );
		if( !collInfos.isEmpty() ) {	// use first entry for default folder
			ggFolder.setPath( ((CompactInfo) collInfos.get( 0 )).afd.file.getParentFile() );
		}
		
		glassPane   = new HibernationGlassPane( bottomPanel, cp );
		setGlassPane( glassPane );
		focusAlive  = getFocusTraversalPolicy();
		focusFrozen = new NoFocusTraversalPolicy();

		// initially sort by filename
		stm.setSortingStatus( 0, SortedTableModel.ASCENDING );

		pack();
		setVisible( true ); // show();
	}

	private void hibernation( boolean freeze )
	{
		glassPane.setVisible( freeze );
		setFocusTraversalPolicy( freeze ? focusFrozen : focusAlive );
		if( freeze ) {
			glassPane.requestFocus();
		} else {
			getContentPane().requestFocus();
		}
	}
	
	private void createCompactInfos( Session doc )
	{
		final java.util.Map	mapFilesToInfos	= new HashMap();
		Track				t;
		Molecule			molec;
		Atom				a;
		MapManager			map;
		Span				span, span2;
		double				startSec, stopSec, maxRate;
		long				start, stop;
		File				file;
		AudioFile			af;
		AudioFileDescr		afd;
		Probability			prob;
		Exception			exc;
		CompactInfo			info;
		int					idx;

		final double		padding = ggPadding.getNumber().doubleValue();
		
		collInfos.clear();
	
		if( !doc.bird.attemptShared( Session.DOOR_TRACKS | Session.DOOR_MOLECULES, 1000 )) return;
		try {
			for( int i = 0; i < doc.tracks.size(); i++ ) {
				t			= (Track) doc.tracks.get( i );
				for( int j = 0; j < t.molecules.size(); j++ ) {
					molec	= (Molecule) t.molecules.get( j );
					for( int k = 0; k < molec.atoms.size(); k++ ) {
						a			= (Atom) molec.atoms.get( k );
						map			= a.getMap();
						file		= (File) map.getValue( Atom.MAP_KEY_AUDIOFILE );
						if( file == null ) continue;
						
						info		= (CompactInfo) mapFilesToInfos.get( file );
						if( info == null ) {	// first occurance ov dem file
							afd		= null;
							exc		= null;
							try {
								af	= AudioFile.openAsRead( file );
								afd	= af.getDescr();
								af.close();
							}
							catch( IOException e1 ) {
								exc	= e1;
							}
							info		= new CompactInfo( afd );
							info.exc	= exc == null ? null :
								(file.getAbsolutePath() + " : " + exc.getClass().getName() + " : " + exc.getLocalizedMessage());
							mapFilesToInfos.put( file, info );
							collInfos.add( info );
							if( afd == null ) continue;
						}
						startSec	= a.getFileStart();
						start		= (long) Math.max( 0, (startSec - padding) * info.afd.rate );
						prob		= (Probability) a.probabilities.findByName( Atom.PROB_PITCH );
						if( prob != null ) {
							maxRate	= Math.max( 1.0, MathUtil.pitchToRate( prob.getMax(), 12 ));
						} else {
							maxRate	= 1.0;
						}
						stopSec		= a.getFileStop();
						stopSec		= startSec + (stopSec - startSec) * maxRate;
						stop		= (long) Math.min( info.afd.length, (Math.ceil( (stopSec + padding) * info.afd.rate ) + 0.5));
						span		= new Span( start, stop );
if( stop <= start ) {
	System.err.println( "!! WARNING: zero or negative atom length for " + a.getName() + " in " + molec.getName() + " starting at " + molec.getStart() );
}
						idx			= Collections.binarySearch( info.usedSpans, span, Span.startComparator );
						if( idx < 0 ) idx = -(idx + 1);
						if( idx > 0 ) {	// check fusion with previous region
							span2	= (Span) info.usedSpans.get( idx - 1 );
							if( span2.touches( span )) {
								idx--;
								span	= Span.union( span2, span );
								info.usedSpans.remove( idx );
							}
						}
						// check fusion with successive regions
						for( boolean reCheck = true; reCheck && idx < info.usedSpans.size(); ) {
							span2	= (Span) info.usedSpans.get( idx );
							if( span2.touches( span )) {
								span	= Span.union( span, span2 );
								info.usedSpans.remove( idx );
							} else {
								reCheck	= false;
							}
						}
						
						info.usedSpans.add( idx, span );
						info.atoms.add( a );
					} // for atoms
				} // for molecs
			} // for tracks
		}
		finally {
			doc.bird.releaseShared( Session.DOOR_TRACKS | Session.DOOR_MOLECULES );
		}
		
		// now calc used length
		for( int i = collInfos.size() - 1; i >= 0; i-- ) {
			info			= (CompactInfo) collInfos.get( i );
			if( info.afd == null ) {
				System.err.println( "Audio file couldn't be opened, therefore doesn't appear in the list :\n   " + info.exc );
				collInfos.remove( i );
				continue;
			}
			info.usedLength	= 0;
			for( int j = 0; j < info.usedSpans.size(); j++ ) {
				span = (Span) info.usedSpans.get( j );
				info.usedLength += span.getLength();
			}
		}
	}
	
	private void recalcSizeInfo()
	{
		long		origSize		= 0;
		long		compactedSize	= 0;
		int			bytesPerFrame;
		CompactInfo	info;
	
		for( int i = 0; i < table.getRowCount(); i++ ) {
			if( table.isRowSelected( i )) {
				info			 = (CompactInfo) collInfos.get( stm.getModelIndex( i ));
				bytesPerFrame	 = info.afd.channels * (info.afd.bitsPerSample >> 3);
				origSize		+= info.afd.length * bytesPerFrame;
				compactedSize	+= info.usedLength * bytesPerFrame;
			}
		}
		
		lbSizeInfo.setText( frmtSize.format( new Object[] {
			new Double( (double) origSize / 0x100000 ), new Double( (double) compactedSize / 0x100000 )}));
	}

	private static String getResourceString( String key )
	{
		return AbstractApplication.getApplication().getResourceString( key );
	}
	
// ----------------- internal classes -----------------

	private class actionCloseClass
	extends AbstractAction
	{
		private actionCloseClass()
		{
			super( getResourceString( "buttonClose" ));
		}
		
		public void actionPerformed( ActionEvent e )
		{
			setVisible( false );
			dispose();
		}
	}
	
	private class actionCommitClass
	extends AbstractAction
	implements RunnableProcessing
	{
		private actionCommitClass()
		{
			super( getResourceString( "buttonCompactSelected" ));
		}
		
		public void actionPerformed( ActionEvent e )
		{
			final java.util.List collInfoProc = new ArrayList();

			for( int i = 0; i < table.getRowCount(); i++ ) {
				if( table.isRowSelected( i )) {
					collInfoProc.add( collInfos.get( stm.getModelIndex( i )));
//System.err.println( "added "+((CompactInfo) collInfos.get( stm.getModelIndex( i ))).afd.file.getAbsolutePath() );
				}
			}
			
			final Object[]	args = new Object[] { collInfoProc, ggFolder.getPath(), doc };

			new ProcessingThread( this, doc.getFrame(), doc.bird, getValue( NAME ).toString(), args, Session.DOOR_MOLECULES );
			hibernation( true );
		}

		public boolean run( ProcessingThread context, Object argument )
		{
			final Object[]			args			= (Object[]) argument;
			final java.util.List	collInfoProc	= (java.util.List) args[0];
			final File				folder			= (File) args[1];
			final Session			doc				= (Session) args[2];
			final SyncCompoundEdit	ce;
			
			CompactInfo				info;
			AudioFile				inF, outF;
			AudioFileDescr			afd;
			File					f;
			Atom					a;
			Span					span;
			String					name, ext;
			int						dupCnt, idx;
			long					prog, progLen, start, shift, lastStop;
			double					startSec, durSec;
			boolean					success			= false;

			ce	= new BasicSyncCompoundEdit( doc.bird, Session.DOOR_MOLECULES, context.getName() );

			progLen	= 0;
			prog	= 0;
			for( int i = 0; i < collInfoProc.size(); i++ ) {
				info		= (CompactInfo) collInfoProc.get( i );
				progLen	   += info.usedLength;
			}

			try {
				for( int i = 0; i < collInfoProc.size(); i++ ) {
					info		= (CompactInfo) collInfoProc.get( i );
					inF			= AudioFile.openAsRead( info.afd.file );
					name		= info.afd.file.getName();
					idx			= name.lastIndexOf( '.' );
					if( idx == -1 ) idx = name.length();
					ext			= name.substring( idx );
					name		= name.substring( 0, idx );
					dupCnt		= 0;
					do {
						f		= new File( folder, name + "Opt" + (dupCnt > 0 ? String.valueOf( dupCnt ) : "") + ext );
						dupCnt++;
					} while( f.exists() );	// don't overwrite anything
					afd			= inF.getDescr();
					if( (afd.type != info.afd.type) || (afd.channels != info.afd.channels) ||
						(afd.rate != info.afd.rate) || (afd.bitsPerSample != info.afd.bitsPerSample) ||
						(afd.sampleFormat != info.afd.sampleFormat) || (afd.length != info.afd.length) ) {
					
						throw new IOException( getResourceString( "errAFDChanged" ) + "\n" + info.afd.file.getAbsolutePath() );
					}
					afd			= new AudioFileDescr( afd );
					afd.file	= f;
					outF		= AudioFile.openAsWrite( afd );

					for( int j = 0; j < info.usedSpans.size(); j++ ) {
						span	= (Span) info.usedSpans.get( j );
//						if( span.getLength() < 0 ) {
//							throw new IOException( "Negative Atom Length in " ... );
//						}
						inF.seekFrame( span.getStart() );
						inF.copyFrames( outF, span.getLength() );
						prog   += span.getLength();
						context.setProgression( (float) prog / (float) progLen );
					}
					
					inF.close();
					outF.close();
					
					for( int j = 0; j < info.atoms.size(); j++ ) {
						a			= (Atom) info.atoms.get( j );
						startSec	= a.getFileStart();
						durSec		= a.getFileStop() - startSec;
						start		= (long) (startSec * info.afd.rate + 0.5);
						shift		= 0;
						lastStop	= 0;
						for( int k = 0; k < info.usedSpans.size(); k++ ) {
							span	= (Span) info.usedSpans.get( k );
							shift  += span.getStart() - lastStop;
							if( span.contains( start )) break;
							lastStop = span.getStop();
						}
//System.err.println( "startSec was "+startSec+"; durSec was "+durSec+"; start was "+start+"; shift is "+shift+"; start becomes "+
//	(start - shift) + "; startSec becomes " + ((double) (start - shift) / afd.rate) );
						start	   -= shift;
						startSec	= (double) start / afd.rate;
						ce.addEdit( new EditPutMapValue( this, doc.bird, Session.DOOR_MOLECULES,
							a.getMap(), Atom.MAP_KEY_AUDIOFILE, f ));
						ce.addEdit( new EditPutMapValue( this, doc.bird, Session.DOOR_MOLECULES,
							a.getMap(), Atom.MAP_KEY_AFSTART, new Double( startSec )));
						ce.addEdit( new EditPutMapValue( this, doc.bird, Session.DOOR_MOLECULES,
							a.getMap(), Atom.MAP_KEY_AFSTOP, new Double( startSec + durSec )));
					}
				}
				ce.end();
				doc.getUndoManager().addEdit( ce );
				success = true;
			}
			catch( IOException e1 ) {
				context.setException( e1 );
			}
			finally {
				if( !success ) {
					ce.cancel();
				}
			}
			return success;
		}

		public void finished( ProcessingThread context, Object argument, boolean success )
		{
			hibernation( false );
			
			if( success ) {
				final Object[]			args			= (Object[]) argument;
				final java.util.List	collInfoProc	= (java.util.List) args[0];
				
				collInfos.removeAll( collInfoProc );
				tm.fireTableDataChanged();
			}
		}
	}
	
	private class TableModel
	extends AbstractTableModel
	{
		public String getColumnName( int col )
		{
			return COL_NAMES[ col ];
		}
		
		public int getRowCount()
		{
			return collInfos.size();
		}
		
		public int getColumnCount()
		{
			return NUM_COLUMNS;
		}
		
		public Object getValueAt( int row, int col )
		{
			if( row > collInfos.size() ) return null;
			
			final CompactInfo info = (CompactInfo) collInfos.get( row );
	
			switch( col ) {
			case COL_FILE:
				return info.afd.file.getName();

			case COL_PATH:
				return info.afd.file.getParent();

			case COL_ATOMS:
				return new Integer( info.atoms.size() );

			case COL_ORIGLEN:
//				return timeFormat.formatTime( new Double( (double) info.afd.length / info.afd.rate ));
				return new Double( (double) info.afd.length / info.afd.rate );
				
			case COL_COMPLEN:
//				return timeFormat.formatTime( new Double( (double) info.usedLength / info.afd.rate ));
				return new Double( (double) info.usedLength / info.afd.rate );

			case COL_REDUCTLEN:
				return new Double( (double) (info.afd.length - info.usedLength) / info.afd.rate );

			case COL_REDUCTPERCENT:
				return new Double( 1.0 - (double) info.usedLength / (double) info.afd.length );

			default:
				assert false: col;
				return null;
			}
		}

	    public Class getColumnClass( int col )
		{
			switch( col ) {
			case COL_FILE:
			case COL_PATH:
				return String.class;
			
			case COL_ATOMS:
				return Integer.class;

			case COL_ORIGLEN:
			case COL_COMPLEN:
			case COL_REDUCTLEN:
			case COL_REDUCTPERCENT:
				return Double.class;

			default:
				return Object.class;
			}
		}
	
		public boolean isCellEditable( int row, int col )
		{
			return false;
		}
		
		public void setValueAt( Object value, int row, int col )
		{
			// not editable
		}
	}

	private static class TimeRenderer
	extends DefaultTableCellRenderer
	{
		private TimeRenderer()
		{
			super();
		}

		public Component getTableCellRendererComponent( JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column )
		{
			return super.getTableCellRendererComponent( table, timeFormat.formatTime( (Number) value ), isSelected, hasFocus, row, column );
		}
	}

	private static class PercentRenderer
	extends DefaultTableCellRenderer
	{
		private PercentRenderer()
		{
			super();
		}

		public Component getTableCellRendererComponent( JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column )
		{
			return super.getTableCellRendererComponent( table, percentFormat.format( value ), isSelected, hasFocus, row, column );
		}
	}

	private static class CompactInfo
//	implements Comparable
	{
		private final AudioFileDescr	afd;
		private final java.util.List	atoms		= new ArrayList();
		private final java.util.List	usedSpans	= new ArrayList();	// sorted by span start
		private long					usedLength;
		private String					exc;	// exception text if afd == null
		
		private CompactInfo( AudioFileDescr afd )
		{
			this.afd			= afd;
		}

//		public int compareTo( Object o )
//		{
//			return missing.compareTo( o );
//		}
	}
}