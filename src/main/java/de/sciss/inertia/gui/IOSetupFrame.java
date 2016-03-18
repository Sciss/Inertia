/*
 *  IOSetupFrame.java
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
 *  Change log:
 *		11-Aug-05	copied from de.sciss.eisenkraut.gui.IOSetup
 */

package de.sciss.inertia.gui;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.prefs.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;

// INERTIA
//import de.sciss.eisenkraut.*;
//import de.sciss.eisenkraut.io.*;
//import de.sciss.eisenkraut.util.*;
import de.sciss.inertia.Main;
import de.sciss.inertia.io.RoutingConfig;
import de.sciss.inertia.util.PrefsUtil;

import de.sciss.app.AbstractApplication;

import de.sciss.gui.GUIUtil;
import de.sciss.gui.HelpGlassPane;
import de.sciss.gui.ModificationButton;

/**
 *  This is the frame that
 *  displays the user adjustable
 *  input/output configuration
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.5, 03-Aug-05
 */
public class IOSetupFrame
extends BasicPalette // BasicFrame
{
	private final Main				root;
	
	private final java.util.List	collConfigs		= new ArrayList();
	private final java.util.Set		setNames		= new HashSet();
	private final Preferences		audioPrefs;
	private int						audioOutputs;
	
	private static final String[]	staticColNames	= { "ioConfig", "ioNumChannels", "ioStartAngle" };
	private static final int		MAPPING_WIDTH	= 24;
	private static final Font		fnt				= GraphicsUtil.smallGUIFont;

	private static final int[] pntMapNormGradientPixels = { 0xFFF4F4F4, 0xFFF1F1F1, 0xFFEEEEEE, 0xFFECECEC,
															0xFFECECEC, 0xFFECECEC, 0xFFEDEDED, 0xFFDADADA,
															0xFFDFDFDF, 0xFFE3E3E3, 0xFFE7E7E7, 0xFFEBEBEB,
															0xFFF0F0F0, 0xFFF3F3F3, 0xFFF9F9F9 };
	private static final int	pntMapSize			= 15;
	private static final int[] pntMapSelGradientPixels = {	0xFFD8DBE0, 0xFFCAD0D5, 0xFFC2C9CE, 0xFFBEC4CB,
															0xFFBBC2C8, 0xFFB8BEC6, 0xFFB6BCC6, 0xFF9EA8B4,
															0xFFA4ADB9, 0xFFAAB4BF, 0xFFAFB9C6, 0xFFB8C2CE,
															0xFFBBC5D0, 0xFFBFCAD4, 0xFFC7D1DD };

	private final Paint pntMapNormal, pntMapSelected;

	private static final DataFlavor	mapFlavor	= new DataFlavor( MapTransferable.class, "io_mapping" );
	private static final DataFlavor[] mapFlavors = { mapFlavor };

	/**
	 *  Creates a new i/o setup frame
	 *
	 *  @param  root	application root
	 */
    public IOSetupFrame( final Main root )
    {
		super( AbstractApplication.getApplication().getResourceString( "frameIOSetup" ));

		this.root   = root;

		final Container					cp		= getContentPane();
		final de.sciss.app.Application	app		= AbstractApplication.getApplication();
		LayoutManager					lay;

		audioPrefs	= app.getUserPrefs().node( PrefsUtil.NODE_AUDIO );

		JPanel						tab, buttonPanel;
		JButton						ggButton;
		JTabbedPane					ggTabPane;
		final JTable				table;
		final AbstractTableModel	tm;
		TableCellRenderer			tcr;
		JScrollPane					scroll;
		JTextArea					lbTextArea;
		Box							b;
		final AbstractButton		ggPlus, ggMinus;
		BufferedImage				img;

		audioOutputs		= audioPrefs.getInt( PrefsUtil.KEY_AUDIOOUTPUTS, 0 );
		fromPrefs();

		ggTabPane			= new JTabbedPane();

		// ---------- global pane ----------

		tab			= new JPanel();
		lay			= new BorderLayout();
		tab.setLayout( lay );
		
		lbTextArea	= new JTextArea( getResourceString( "ioOutputInfo" ));
		lbTextArea.setEditable( false );
		lbTextArea.setBackground( null );
		tab.add( lbTextArea, BorderLayout.NORTH );
		
		tm			= new TableModel();
		table		= new JTable( tm );
		table.getTableHeader().setReorderingAllowed( false );
//		table.getTableHeader().setResizingAllowed( false );
		table.setCellSelectionEnabled( true );
		table.setColumnSelectionAllowed( false );
		table.setDragEnabled( true );
		table.setShowGrid( true );
		table.setGridColor( Color.lightGray );
		table.setSelectionMode( ListSelectionModel.SINGLE_INTERVAL_SELECTION );
		table.setTransferHandler( new MapTransferHandler() );

		img = new BufferedImage( 1, pntMapSize, BufferedImage.TYPE_INT_ARGB );
		img.setRGB( 0, 0, 1, pntMapSize, pntMapNormGradientPixels, 0, 1 );
		pntMapNormal = new TexturePaint( img, new Rectangle( 0, 0, 1, pntMapSize ));
		img = new BufferedImage( 1, pntMapSize, BufferedImage.TYPE_INT_ARGB );
		img.setRGB( 0, 0, 1, pntMapSize, pntMapSelGradientPixels, 0, 1 );
		pntMapSelected = new TexturePaint( img, new Rectangle( 0, 0, 1, pntMapSize ));

		tcr			= new MappingRenderer();
		setColumnRenderersAndWidths( table, tcr );

		scroll		= new JScrollPane( table, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
											  JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED );
		
		tab.add( scroll, BorderLayout.CENTER );
		
		b			= Box.createHorizontalBox();
		ggPlus		= new ModificationButton( ModificationButton.SHAPE_PLUS );
		ggMinus		= new ModificationButton( ModificationButton.SHAPE_MINUS );
		ggMinus.setEnabled( false );
		ggPlus.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent e )
			{
				int row = table.getSelectedRow() + table.getSelectedRowCount();
				if( row <= 0 ) row = collConfigs.size();
				final RoutingConfig cfg = new RoutingConfig( createUniqueName( getResourceString( "ioDefaultOutName" )));
				collConfigs.add( row, cfg );
				setNames.add( cfg.name );
				tm.fireTableRowsInserted( row, row );
				table.setRowSelectionInterval( row, row );
			}
		});
		ggMinus.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent e )
			{
				final int firstRow	= Math.max( 0, table.getSelectedRow() );
				final int lastRow	= Math.min( collConfigs.size(), firstRow + table.getSelectedRowCount() ) - 1;
				RoutingConfig cfg;
				
				if( firstRow <= lastRow ) {
					for( int i = lastRow; i >= firstRow; i-- ) {
						cfg = (RoutingConfig) collConfigs.remove( i );
						setNames.remove( cfg.name );
					}
					tm.fireTableRowsDeleted( firstRow, lastRow );
				}
			}
		});
		b.add( ggPlus );
		b.add( ggMinus );
		b.add( Box.createHorizontalGlue() );

		table.getSelectionModel().addListSelectionListener( new ListSelectionListener() {
			public void valueChanged( ListSelectionEvent e )
			{
				ggMinus.setEnabled( table.getSelectedRowCount() > 0 );
			}
		});
		
		tab.add( b, BorderLayout.SOUTH );
		
		ggTabPane.addTab( app.getResourceString( "labelOutputs" ), null, tab, null );

		// ---------- generic gadgets ----------

        buttonPanel = new JPanel( new FlowLayout( FlowLayout.RIGHT, 4, 4 ));
        ggButton	= new JButton( app.getResourceString( "buttonOk" ));
        ggButton.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent e )
			{
				if( toPrefs() ) {
					disposeAndClose();
// XXX MainFrame cannot rely on prefs since more than one message arrives
((MainFrame) root.getComponent( Main.COMP_MAIN )).refillConfigs();
				}
			}	
		});
        buttonPanel.add( ggButton );
        ggButton	= new JButton( app.getResourceString( "buttonCancel" ));
        buttonPanel.add( ggButton );
        ggButton.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent e )
			{
				disposeAndClose();
			}	
		});

		cp.setLayout( new BorderLayout() );
		cp.add( ggTabPane, BorderLayout.CENTER );
        cp.add( buttonPanel, BorderLayout.SOUTH );
		GUIUtil.setDeepFont( cp, fnt );

		// ---------- ----------

		setDefaultCloseOperation( DO_NOTHING_ON_CLOSE );
		root.addComponent( Main.COMP_IOSETUP, this );

        HelpGlassPane.setHelp( getRootPane(), "IOSetup" );

		init( root );
//		pack();
    }
	
	private void disposeAndClose()
	{
		root.removeComponent( Main.COMP_IOSETUP );	// needs to re-created each time!
		setVisible( false );
		dispose();
	}
	
	private void fromPrefs()
	{
		collConfigs.clear();
		setNames.clear();
		
		final Preferences	ocPrefs		= audioPrefs.node( PrefsUtil.NODE_OUTPUTCONFIGS );
		final String[]		arrayNames;
		RoutingConfig		cfg;
		Preferences			cfgPrefs;

		try {
			arrayNames = ocPrefs.childrenNames();
		}
		catch( BackingStoreException e1 ) {
			GUIUtil.displayError( this, e1, getResourceString( "errLoadPrefs" ));
			return;
		}
			
		for( int i = 0; i < arrayNames.length; i++ ) {
			cfgPrefs	= ocPrefs.node( arrayNames[ i ]);
			try {
				cfg		= new RoutingConfig( cfgPrefs );
				collConfigs.add( cfg );
				setNames.add( arrayNames[ i ]);
			}
			catch( NumberFormatException e1 ) {
				System.err.println( e1 );
			}
		}
	}
	
	private boolean toPrefs()
	{
		final Preferences	ocPrefs		= audioPrefs.node( PrefsUtil.NODE_OUTPUTCONFIGS );
		final String[]		arrayNames;
		RoutingConfig		cfg;
		Preferences			cfgPrefs;

		try {
			arrayNames = ocPrefs.childrenNames();
			for( int i = 0; i < arrayNames.length; i++ ) {
				cfgPrefs	= ocPrefs.node( arrayNames[ i ]);
				cfgPrefs.removeNode();
			}
		}
		catch( BackingStoreException e1 ) {
			GUIUtil.displayError( this, e1, getResourceString( "errSavePrefs" ));
			return false;
		}
		
		for( int i = 0; i < collConfigs.size(); i++ ) {
			cfg			= (RoutingConfig) collConfigs.get( i );
			cfgPrefs	= ocPrefs.node( cfg.name );
			cfg.toPrefs( cfgPrefs );
		}
		
		return true;
	}

	private String createUniqueName( String test )
	{
		String name = test;
		for( int i = 1; setNames.contains( name ); i++ ) {
			name = test + " " + i;
		}
		return name;		
	}

	private void setColumnRenderersAndWidths( JTable table, TableCellRenderer tcr )
	{
		final TableColumnModel	tcm	= table.getColumnModel();
		TableColumn				col;
	
		for( int i = staticColNames.length; i < table.getColumnCount(); i++) {
			col = tcm.getColumn( i );
			col.setPreferredWidth( MAPPING_WIDTH );
			col.setMinWidth( MAPPING_WIDTH );
			col.setMaxWidth( MAPPING_WIDTH );
			col.setCellRenderer( tcr );
		}
	}

	private static String getResourceString( String key )
	{
		return AbstractApplication.getApplication().getResourceString( key );
	}

// ----------- internal classes  -----------

	private class MapTransferHandler
	extends TransferHandler
	{
		private MapTransferHandler() {}

		/**
		 * Overridden to import a Pathname (Fileliste or String) if it is available.
		 */
		public boolean importData( JComponent c, Transferable t )
		{
			MapTransferable	mt;
			final JTable	table	= (JTable) c;
			final int		row		= table.getSelectedRow();
			final int		mapCh	= table.getSelectedColumn() - staticColNames.length;
			RoutingConfig cfg;
			int temp;
		
			try {
				if( mapCh >= 0 && (row < collConfigs.size()) && t.isDataFlavorSupported( mapFlavor )) {
					cfg	= (RoutingConfig) collConfigs.get( row );
					mt	= (MapTransferable) t.getTransferData( mapFlavor );
					// only allowed within same config
					if( mt.cfg == cfg ) {
//System.err.println( "original mapping : "+(mt.idx+1)+"->"+(mt.cfg.mapping[ mt.idx ]+1)+"; new target " +(mapCh+1));
						for( int i = 0; i < cfg.numChannels; i++ ) {
							// dragged onto already mapped spot
							if( cfg.mapping[ i ] == mapCh ) {
								if( i == mt.idx ) return false; // source == target, no action
								temp = cfg.mapping[ mt.idx ];
								cfg.mapping[ mt.idx ] = mapCh;
								cfg.mapping[ i ] = temp;	// simply swapped for now
								((AbstractTableModel) table.getModel()).fireTableRowsUpdated( row, row );
								return true;
							}
						}
						// dragged onto empty spot
						cfg.mapping[ mt.idx ] = mapCh;
						((AbstractTableModel) table.getModel()).fireTableRowsUpdated( row, row );
						return true;
					}
				}
			}
			catch( UnsupportedFlavorException e1 ) {}
			catch( IOException e2 ) {}

			return false;
		}
		
		public int getSourceActions( JComponent c )
		{
			return MOVE;
		}
		
		protected Transferable createTransferable( JComponent c )
		{
			final JTable	table	= (JTable) c;
			final int		row		= table.getSelectedRow();
			final int		mapCh	= table.getSelectedColumn() - staticColNames.length;
			RoutingConfig cfg;
			
			if( mapCh >= 0 && (row < collConfigs.size()) ) {
				cfg	= (RoutingConfig) collConfigs.get( row );
				for( int i = 0; i < cfg.numChannels; i++ ) {
					if( cfg.mapping[ i ] == mapCh ) {
						return new MapTransferable( cfg, i );
					}
				}
			}
			return null;
		}
		
		protected void exportDone( JComponent source, Transferable data, int action )
		{
//			System.err.println( "exportDone. Action == "+action );
		}

		public boolean canImport( JComponent c, DataFlavor[] flavors )
		{
// System.err.println( "canImport" );

			for( int i = 0; i < flavors.length; i++ ) {
				for( int j = 0; j < mapFlavors.length; j++ ) {
					if( flavors[i].equals( mapFlavors[j] )) return true;
				}
			}
			return false;
		}
	} // class MapTransferHandler

	private static class MapTransferable
	implements Transferable
	{
		private final RoutingConfig	cfg;
		private final int		idx;
	
		private MapTransferable( RoutingConfig cfg, int idx )
		{
			this.cfg	= cfg;
			this.idx	= idx;
		}
		
		public DataFlavor[] getTransferDataFlavors()
		{
			return mapFlavors;
		}
		
		public boolean isDataFlavorSupported( DataFlavor flavor )
		{
			for( int i = 0; i < mapFlavors.length; i++ ) {
				if( mapFlavors[ i ].equals( flavor )) return true;
			}
			return false;
		}
		
		public Object getTransferData( DataFlavor flavor )
		throws UnsupportedFlavorException, IOException
		{
			if( flavor.equals( mapFlavor )) {
				return this;
			}
			throw new UnsupportedFlavorException( flavor );
		}
	}

	private class MappingRenderer
	extends JComponent
	implements TableCellRenderer
	{
		private Paint pnt		= pntMapNormal;
		private String value	= null;
	
		private MappingRenderer()
		{
			super();
			setOpaque( true );
			setFont( fnt );
		}
	
		public Component getTableCellRendererComponent( JTable table, Object value,
														boolean isSelected, boolean hasFocus,
														int row, int column )
		{
			pnt	= hasFocus ? pntMapSelected : pntMapNormal;
			this.value = value == null ? null : value.toString();
			return this;
		}
		
		public void paintComponent( Graphics g )
		{
			super.paintComponent( g );
			
			Graphics2D	g2 = (Graphics2D) g;
			
			if( value == null ) {
				g2.setColor( Color.white );
				g2.fillRect( 0, 0, getWidth(), getHeight() );
			} else {
				final FontMetrics fm = g2.getFontMetrics( g2.getFont() );
				g2.setPaint( pnt );
				g2.fillRect( 0, 0, getWidth(), getHeight() );
				g2.setColor( Color.black );
				g2.drawString( value, (getWidth() - fm.stringWidth( value )) * 0.5f, fm.getAscent() );
			}
		}
	}
											   	
	private class TableModel
	extends AbstractTableModel
	{
		public String getColumnName( int col )
		{
			if( col < staticColNames.length ) {
				return getResourceString( staticColNames[ col ]);
			} else {
				return String.valueOf( col - staticColNames.length + 1 );
			}
		}
		
		public int getRowCount()
		{
			return collConfigs.size();
		}
		
		public int getColumnCount()
		{
			return audioOutputs + staticColNames.length;
		}
		
		public Object getValueAt( int row, int col )
		{
			if( row > collConfigs.size() ) return null;
			
			RoutingConfig c = (RoutingConfig) collConfigs.get( row );
		
			switch( col ) {
			case 0:
				return c.name;
			case 1:
				return new Integer( c.numChannels );
			case 2:
				return new Float( c.startAngle );
			default:
				col -= staticColNames.length;
				for( int i = 0; i < c.mapping.length; i++ ) {
					if( c.mapping[ i ] == col ) return new Integer( i + 1 );
				}
				return null;
			}
		}

	    public Class getColumnClass( int col )
		{
			switch( col ) {
			case 0:
				return String.class;
			case 1:
				return Integer.class;
			case 2:
				return Float.class;
			default:
				return Integer.class;
			}
		}
	
		public boolean isCellEditable( int row, int col )
		{
			return col < staticColNames.length;
		}
		
		public void setValueAt( Object value, int row, int col )
		{
			if( row > collConfigs.size() ) return;

			final RoutingConfig cfg				= (RoutingConfig) collConfigs.get( row );
			final int			oldChannels		= cfg.numChannels;
			int[]				newMapping;
			String				name;
			RoutingConfig		newCfg			= null;
			int					newChannels;
			float				newStartAngle;

			switch( col ) {
			case 0:
				name = value.toString();
				if( (name.length() > 0) && (name.length() < Preferences.MAX_NAME_LENGTH) &&
					!setNames.contains( name )) {

					newCfg = new RoutingConfig( name, cfg.numChannels, cfg.mapping, cfg.startAngle );
				}
				break;
				
			case 1:
				if( value instanceof Number ) {
					newChannels = Math.max( 0, ((Number) value).intValue() );
				} else if( value instanceof String ) {
					try {
						newChannels = Math.max( 0, Integer.parseInt( value.toString() ));
					}
					catch( NumberFormatException e1 ) {
						break;
					}
				} else {
					assert false : value;
					break;
				}
				if( newChannels < oldChannels ) {
					newMapping = new int[ newChannels ];
					System.arraycopy( cfg.mapping, 0, newMapping, 0, newChannels );
				} else if( newChannels > oldChannels ) {
					newMapping = new int[ newChannels ];
					System.arraycopy( cfg.mapping, 0, newMapping, 0, oldChannels );
					for( int i = oldChannels, minCh = 0; i < newChannels; i++ ) {
chanLp:					for( int ch = minCh; true; ch++ ) {
							for( int j = 0; j < i; j++ ) {
								if( newMapping[ j ] == ch ) continue chanLp;
							}
							newMapping[ i ] = ch;
							minCh = ch + 1;
							break chanLp;
						}
					}
				} else break;

				newCfg = new RoutingConfig( cfg.name, newChannels, newMapping, cfg.startAngle );
//System.err.print( "now mapping is " );
//for( int i = 0; i < cfg.mapping.length; i++ ) System.err.print( cfg.mapping[ i ] + "  " );
//System.err.println();
				break;
				
			case 2:
				if( value instanceof Number ) {
					newStartAngle = Math.max( -360f, Math.min( 360f, ((Number) value).floatValue() ));
				} else if( value instanceof String ) {
					try {
						newStartAngle = Math.max( -360f, Math.min( 360f, Float.parseFloat( value.toString() )));
					}
					catch( NumberFormatException e1 ) {
						break;
					}
				} else {
					assert false : value;
					break;
				}
				if( newStartAngle != cfg.startAngle ) {
					newCfg = new RoutingConfig( cfg.name, cfg.numChannels, cfg.mapping, newStartAngle );
				}
				break;
				
			default:
				// set by changing numChannels and drag+drop
				break;
			}
			
			if( newCfg != null ) {
				collConfigs.set( row, newCfg );
				setNames.remove( cfg.name );
				setNames.add( newCfg.name );
				if( col == 1 ) fireTableRowsUpdated( row, row );
			}
		}
	}
}