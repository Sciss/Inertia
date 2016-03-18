//
//  FileTable.java
//  Inertia
//
//  Created by Hanns Holger Rutz on 07.08.05.
//  Copyright 2005 __MyCompanyName__. All rights reserved.
//

package de.sciss.inertia.gui;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;

import de.sciss.inertia.session.*;

import de.sciss.app.AbstractApplication;
import de.sciss.app.WindowHandler;

import de.sciss.gui.GUIUtil;
import de.sciss.gui.ModificationButton;

import de.sciss.io.AudioFile;
import de.sciss.io.AudioFileDescr;

public class FileTable
extends JPanel
{
	private final java.util.List	files;
	private final java.util.Set		setFiles	= new HashSet();

	public FileTable( final java.util.List files, final JFrame parent )
	{
		super( new BorderLayout() );
		
		this.files	= files;
		createFileSet();
		
		final JButton		ggPlus, ggMinus;
//		final JButton		ggInfo;
		final Box			b;
		final JScrollPane	scroll;
		final JTable		table;
		final TableModel	tm;
	
		tm			= new TableModel();
		table		= new JTable( tm );
		table.getTableHeader().setReorderingAllowed( false );
//		table.setCellSelectionEnabled( true );
		table.setColumnSelectionAllowed( false );
		table.setShowGrid( true );
		table.setGridColor( Color.lightGray );
		table.setSelectionMode( ListSelectionModel.SINGLE_INTERVAL_SELECTION );

//		table.setDragEnabled( true );
//		table.setTransferHandler( new MapTransferHandler() );

//		img = new BufferedImage( 1, pntMapSize, BufferedImage.TYPE_INT_ARGB );
//		img.setRGB( 0, 0, 1, pntMapSize, pntMapNormGradientPixels, 0, 1 );
//		pntMapNormal = new TexturePaint( img, new Rectangle( 0, 0, 1, pntMapSize ));
//		img = new BufferedImage( 1, pntMapSize, BufferedImage.TYPE_INT_ARGB );
//		img.setRGB( 0, 0, 1, pntMapSize, pntMapSelGradientPixels, 0, 1 );
//		pntMapSelected = new TexturePaint( img, new Rectangle( 0, 0, 1, pntMapSize ));
//		tcr			= new MappingRenderer();
//		setColumnRenderersAndWidths( table, tcr );

		scroll		= new JScrollPane( table, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
											  JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED );
		
		b			= Box.createHorizontalBox();
		ggPlus		= new ModificationButton( ModificationButton.SHAPE_PLUS );
		ggMinus		= new ModificationButton( ModificationButton.SHAPE_MINUS );
//		ggInfo		= new ModificationButton( ModificationButton.SHAPE_INFO );
		ggMinus.setEnabled( false );
//		ggInfo.setEnabled( false );
		ggPlus.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent e )
			{
				int row = table.getSelectedRow() + table.getSelectedRowCount();
				if( row <= 0 ) row = files.size();

				final FileDialog		dlg;
				final String			dirName;
				final String			fileName;
				final File				f;
				final AudioFile			af;
				final AudioFileDescr	afd;

				dlg			= new FileDialog( parent, "Add Audio File", FileDialog.LOAD );
				dlg.setVisible( true ); // show();
				
				dirName		= dlg.getDirectory();
				fileName	= dlg.getFile();
				if( dirName == null || fileName == null ) return;
				
				f			= new File( dirName, fileName );
				if( setFiles.contains( f )) {
					JOptionPane.showMessageDialog( parent, "File already in list: " + f.getName() );
					return;
				}
				
				try {
					af		= AudioFile.openAsRead( f );
					afd		= af.getDescr();
					files.add( row, afd );
					setFiles.add( afd.file );
					tm.fireTableRowsInserted( row, row );
					table.setRowSelectionInterval( row, row );
				}
				catch( IOException e1 ) {
					GUIUtil.displayError( parent, e1, "Retrieving Audio File Information" );
				}
			}
		});
		ggMinus.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent e )
			{
				final int firstRow	= Math.max( 0, table.getSelectedRow() );
				final int lastRow	= Math.min( files.size(), firstRow + table.getSelectedRowCount() ) - 1;
				AudioFileDescr afd;
				
				if( firstRow <= lastRow ) {
					for( int i = lastRow; i >= firstRow; i-- ) {
						afd = (AudioFileDescr) files.remove( i );
						setFiles.remove( afd.file );
					}
					tm.fireTableRowsDeleted( firstRow, lastRow );
				}
			}
		});
//		ggInfo.addActionListener( new ActionListener() {
//			public void actionPerformed( ActionEvent e )
//			{
//				final int firstRow	= table.getSelectedRow();
//				
//				if( firstRow >= 0 && firstRow < files.size() ) {
//					new AudioFileDescrEditor( (AudioFileDescr) files.get( firstRow ));
//				}
//			}
//		});
		b.add( ggPlus );
		b.add( ggMinus );
//		b.add( ggInfo );
		b.add( Box.createHorizontalGlue() );

		table.getSelectionModel().addListSelectionListener( new ListSelectionListener() {
			public void valueChanged( ListSelectionEvent e )
			{
				ggMinus.setEnabled( table.getSelectedRowCount() > 0 );
//				ggInfo.setEnabled( table.getSelectedRowCount() == 1 );
			}
		});

		this.add( new JLabel( getClass().getName() ), BorderLayout.NORTH );
		this.add( scroll, BorderLayout.CENTER );
		this.add( b, BorderLayout.SOUTH );

		GUIUtil.setDeepFont( this, AbstractApplication.getApplication().getWindowHandler().getDefaultFont() );
	}

	private String createUniqueName( String test )
	{
		String name = test;
		for( int i = 1; setFiles.contains( name ); i++ ) {
			name = test + " " + i;
		}
		return name;		
	}

	private void createFileSet()
	{
		setFiles.clear();
		
		AudioFileDescr afd;
		
		for( int i = 0; i < files.size(); i++ ) {
			afd	= (AudioFileDescr) files.get( i );
			setFiles.add( afd.file );
		}
	}

// ----------------- internal classes -----------------

	private class TableModel
	extends AbstractTableModel
	{
		public String getColumnName( int col )
		{
			switch( col ) {
			case 0:
				return "name";
			case 1:
				return "format";
			default:
				assert false : col;
				return null;
			}
		}
		
		public int getRowCount()
		{
			return files.size();
		}
		
		public int getColumnCount()
		{
			return 2;
		}
		
		public Object getValueAt( int row, int col )
		{
			if( row > files.size() ) return null;
			
			final AudioFileDescr afd = (AudioFileDescr) files.get( row );
	
			switch( col ) {
			case 0:
				return afd.file.getName();
			case 1:
				return afd.getFormat();
			default:
				assert false: col;
				return null;
			}
		}

	    public Class getColumnClass( int col )
		{
			return String.class;
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
}