/*
 *	SortedTableModel.java
 *	Inertia
 *
 *	This is a wrapper table model mainly taken from sun's swing demos
 *	(original class name TableSorter). see the class commentary below
 *	for details and authors. The class has been modified in some ways:
 *	<ul>
 *	<li>the triangle sorting UI (ArrayIcon) has been exchanged with aqua lnf</li>
 *	<li>removed multi-column sorting</li>
 *	<li>the model is always sorted by some column. it's not possible
 *	to switch back to NOT_SORTED</li>
 *	<li>shift+mouseclick and ctrl+mouseclick have been disabled</li>
 *	<li>tooltip has been removed</li>
 *	<li>code has been reformatted for my personal style</li>
 *	</ul>
 *
 *	Changelog:
 *		02-Dec-05	created from TableSorter class by milne et al.
 */

package de.sciss.inertia.gui;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;

/**
 *	Original class commentary:
 * SortedTableModel is a decorator for TableModels; adding sorting
 * functionality to a supplied TableModel. SortedTableModel does
 * not store or copy the data in its TableModel; instead it maintains
 * a map from the row indexes of the view to the row indexes of the
 * model. As requests are made of the sorter (like getValueAt(row, col))
 * they are passed to the underlying model after the row numbers
 * have been translated via the internal mapping array. This way,
 * the SortedTableModel appears to hold another copy of the table
 * with the rows in a different order.
 * <p/>
 * SortedTableModel registers itself as a listener to the underlying model,
 * just as the JTable itself would. Events recieved from the model
 * are examined, sometimes manipulated (typically widened), and then
 * passed on to the SortedTableModel's listeners (typically the JTable).
 * If a change to the model has invalidated the order of SortedTableModel's
 * rows, a note of this is made and the sorter will resort the
 * rows the next time a value is requested.
 * <p/>
 * When the tableHeader property is set, either by using the
 * setTableHeader() method or the two argument constructor, the
 * table header may be used as a complete UI for SortedTableModel.
 * The default renderer of the tableHeader is decorated with a renderer
 * that indicates the sorting status of each column. In addition,
 * a mouse listener is installed with the following behavior:
 * <ul>
 * <li>
 * Mouse-click: Clears the sorting status of all other columns
 * and advances the sorting status of that column through three
 * values: {NOT_SORTED, ASCENDING, DESCENDING} (then back to
 * NOT_SORTED again).
 * <li>
 * SHIFT-mouse-click: Clears the sorting status of all other columns
 * and cycles the sorting status of the column through the same
 * three values, in the opposite order: {NOT_SORTED, DESCENDING, ASCENDING}.
 * <li>
 * CONTROL-mouse-click and CONTROL-SHIFT-mouse-click: as above except
 * that the changes to the column do not cancel the statuses of columns
 * that are already sorting - giving a way to initiate a compound
 * sort.
 * </ul>
 * <p/>
 * This is a long overdue rewrite of a class of the same name that
 * first appeared in the swing table demos in 1997.
 * 
 *	@author		Philip Milne
 *	@author		Brendon McLean 
 *	@author		Dan van Enckevort
 *	@author		Parwinder Sekhon
 *	@author		Hanns Holger Rutz
 *	@version	0.3, 02-Dec-05
 *
 *	@todo		row selection should be updated when switching sorting
 */

public class SortedTableModel
extends AbstractTableModel
{
	private TableModel tableModel;

	public static final int DESCENDING = -1;
	public static final int NOT_SORTED = 0;
	public static final int ASCENDING = 1;

	private static final Directive EMPTY_DIRECTIVE = new Directive( -1, NOT_SORTED );

	public static final Comparator COMPARABLE_COMAPRATOR = new Comparator() {
		public int compare( Object o1, Object o2 )
		{
			return( ((Comparable) o1).compareTo( o2 ));
		}
	};
	
	public static final Comparator LEXICAL_COMPARATOR = new Comparator() {
		public int compare( Object o1, Object o2 )
		{
			return( o1.toString().compareTo( o2.toString() ));
		}
	};

	private Row[] viewToModel;
	private int[] modelToView;

	private JTableHeader				tableHeader;
	private final MouseListener			mouseListener;
	private final TableModelListener	tableModelListener;
	private Map							columnComparators	= new HashMap();
	private java.util.List				sortingColumns		= new ArrayList();

	// sciss added
	private final Icon[]				icnArrow			= new Icon[ 3 ];

	public SortedTableModel()
	{
		this.mouseListener		= new MouseHandler();
		this.tableModelListener = new TableModelHandler();
		
		icnArrow[ 0 ]			= new ArrowIcon( DESCENDING );
		icnArrow[ 1 ]			= new ArrowIcon( NOT_SORTED );
		icnArrow[ 2 ]			= new ArrowIcon( ASCENDING );
	}

	public SortedTableModel( TableModel tableModel )
	{
		this();
		setTableModel( tableModel );
	}

	public SortedTableModel( TableModel tableModel, JTableHeader tableHeader )
	{
		this();
		setTableHeader( tableHeader );
		setTableModel( tableModel );
	}

	public TableModel getTableModel()
	{
		return tableModel;
	}

	public void setTableModel( TableModel tableModel )
	{
		if( this.tableModel != null ) {
			this.tableModel.removeTableModelListener( tableModelListener );
		}

		this.tableModel = tableModel;
		if( this.tableModel != null ) {
			this.tableModel.addTableModelListener( tableModelListener );
		}

		clearSortingState();
		fireTableStructureChanged();
	}

	public JTableHeader getTableHeader()
	{
		return tableHeader;
	}

	public void setTableHeader( JTableHeader tableHeader )
	{
		if( this.tableHeader != null ) {
			this.tableHeader.removeMouseListener( mouseListener );
			final TableCellRenderer defaultRenderer = this.tableHeader.getDefaultRenderer();
			if( defaultRenderer instanceof SortableHeaderRenderer ) {
				this.tableHeader.setDefaultRenderer( ((SortableHeaderRenderer) defaultRenderer).tableCellRenderer );
			}
		}
		this.tableHeader = tableHeader;
		if( this.tableHeader != null ) {
			this.tableHeader.addMouseListener( mouseListener );
			this.tableHeader.setDefaultRenderer(
					new SortableHeaderRenderer( this.tableHeader.getDefaultRenderer() ));
		}
	}

	/**
	 *	Queries whether the table is sorted by some column.
	 *	Initially the table is not sorted. As soon as the user
	 *	clicks on a column header or if setSortingStatus is
	 *	called, the table will be sorted.
	 *
	 *	@return	<code>true</code> if the table is sorted by some column
	 */
	public boolean isSorting()
	{
		return( sortingColumns.size() != 0 );
	}

	/**
	 *	Queries whether the table is sorted by some column.
	 *	Initially the table is not sorted. As soon as the user
	 *	clicks on a column header or if setSortingStatus is
	 *	called, the table will be sorted.
	 *
	 *	@param	column	the column index to check for sorting
	 *	@return	<code>true</code> if the table is sorted by the given column
	 */
	public int getSortingStatus( int column )
	{
		return( getDirective( column ).direction );
	}

	public void setSortingStatus( int column, int status )
	{
		final Directive directive = getDirective( column );
		if( directive != EMPTY_DIRECTIVE ) {
			sortingColumns.remove( directive );
		}
		if( status != NOT_SORTED ) {
			sortingColumns.add( new Directive( column, status ));
		}
		sortingStatusChanged();
	}

// SCISS REMOVED
//	protected Icon getHeaderRendererIcon( int column, int size )
//	{
//		final Directive directive = getDirective( column );
//		if( directive == EMPTY_DIRECTIVE ) {
//			return null;
//		}
//		return new Arrow( directive.direction == DESCENDING, size, sortingColumns.indexOf( directive ));
//	}

	private Icon getHeaderRendererIcon( int column )
	{
		return icnArrow[ getDirective( column ).direction + 1 ];
	}

	public void setColumnComparator( Class type, Comparator comparator )
	{
		if( comparator == null ) {
			columnComparators.remove( type );
		} else {
			columnComparators.put( type, comparator );
		}
	}

	private Comparator getComparator( int column )
	{
		final Class			columnType = tableModel.getColumnClass( column );
		final Comparator	comparator = (Comparator) columnComparators.get( columnType );

		if( comparator != null ) {
			return comparator;
		}
		if( Comparable.class.isAssignableFrom( columnType )) {
			return COMPARABLE_COMAPRATOR;
		}
		return LEXICAL_COMPARATOR;
	}

	public int getModelIndex( int viewIndex )
	{
		return getViewToModel()[ viewIndex ].modelIndex;
	}

	// private

	private void clearSortingState()
	{
		viewToModel = null;
		modelToView = null;
	}

	private Directive getDirective( int column )
	{
		Directive directive;
	
		for( int i = 0; i < sortingColumns.size(); i++ ) {
			directive = (Directive) sortingColumns.get( i );
			if( directive.column == column ) {
				return directive;
			}
		}
		return EMPTY_DIRECTIVE;
	}

	private void sortingStatusChanged()
	{
		clearSortingState();
		fireTableDataChanged();
		if( tableHeader != null ) {
			tableHeader.repaint();
		}
	}

	private void cancelSorting()
	{
		sortingColumns.clear();
		sortingStatusChanged();
	}

	private Row[] getViewToModel()
	{
		if( viewToModel == null ) {
			final int tableModelRowCount = tableModel.getRowCount();
			viewToModel = new Row[ tableModelRowCount ];
			for( int row = 0; row < tableModelRowCount; row++ ) {
				viewToModel[ row ] = new Row( row );
			}

			if( isSorting() ) {
				Arrays.sort( viewToModel );
			}
		}
		return viewToModel;
	}

	private int[] getModelToView()
	{
		if( modelToView == null ) {
			final int n = getViewToModel().length;
			modelToView = new int[ n ];
			for( int i = 0; i < n; i++ ) {
				modelToView[ getModelIndex( i )] = i;
			}
		}
		return modelToView;
	}

	// --------------- TableModel interface ---------------

	public int getRowCount()
	{
		return( (tableModel == null) ? 0 : tableModel.getRowCount() );
	}

	public int getColumnCount()
	{
		return( (tableModel == null) ? 0 : tableModel.getColumnCount() );
	}

	public String getColumnName( int column )
	{
		return( tableModel.getColumnName( column ));
	}

	public Class getColumnClass( int column )
	{
		return tableModel.getColumnClass( column );
	}

	public boolean isCellEditable( int row, int column )
	{
		return tableModel.isCellEditable( getModelIndex( row ), column );
	}

	public Object getValueAt( int row, int column )
	{
		return tableModel.getValueAt( getModelIndex( row ), column );
	}

	public void setValueAt( Object aValue, int row, int column )
	{
		tableModel.setValueAt( aValue, getModelIndex( row ), column );
	}

	// ------------- internal helper classes -------------
	
	private class Row
	implements Comparable
	{
		private int modelIndex;

		public Row( int index )
		{
			this.modelIndex = index;
		}

		public int compareTo( Object o )
		{
			final int	row1		= modelIndex;
			final int	row2		= ((Row) o).modelIndex;
			Directive	directive;
			int			column;
			Object		o1, o2;
			int			comparison;

			for( Iterator it = sortingColumns.iterator(); it.hasNext(); ) {
				directive	= (Directive) it.next();
				column		= directive.column;
				o1			= tableModel.getValueAt( row1, column );
				o2			= tableModel.getValueAt( row2, column );

				comparison = 0;
				// Define null less than everything, except null.
				if( (o1 == null) && (o2 == null) ) {
					comparison = 0;
				} else if( o1 == null ) {
					comparison = -1;
				} else if( o2 == null ) {
					comparison = 1;
				} else {
					comparison = getComparator( column ).compare( o1, o2 );
				}
				if( comparison != 0 ) {
					return( directive.direction == DESCENDING ? -comparison : comparison );
				}
			}
			return 0;
		}
	} // class Row

	private class TableModelHandler
	implements TableModelListener
	{
		public void tableChanged( TableModelEvent e )
		{
			int column, viewIndex;
		
			// If we're not sorting by anything, just pass the event along.				
			if( !isSorting() ) {
				clearSortingState();
				fireTableChanged( e );
				return;
			}
				
			// If the table structure has changed, cancel the sorting; the			   
			// sorting columns may have been either moved or deleted from			  
			// the model. 
			if( e.getFirstRow() == TableModelEvent.HEADER_ROW ) {
				cancelSorting();
				fireTableChanged( e );
				return;
			}

			// We can map a cell event through to the view without widening				
			// when the following conditions apply: 
			// 
			// a) all the changes are on one row (e.getFirstRow() == e.getLastRow()) and, 
			// b) all the changes are in one column (column != TableModelEvent.ALL_COLUMNS) and,
			// c) we are not sorting on that column (getSortingStatus(column) == NOT_SORTED) and, 
			// d) a reverse lookup will not trigger a sort (modelToView != null)
			//
			// Note: INSERT and DELETE events fail this test as they have column == ALL_COLUMNS.
			// 
			// The last check, for (modelToView != null) is to see if modelToView 
			// is already allocated. If we don't do this check; sorting can become 
			// a performance bottleneck for applications where cells  
			// change rapidly in different parts of the table. If cells 
			// change alternately in the sorting column and then outside of				
			// it this class can end up re-sorting on alternate cell updates - 
			// which can be a performance problem for large tables. The last 
			// clause avoids this problem. 
			column = e.getColumn();
			if( (e.getFirstRow() == e.getLastRow()) &&
				(column != TableModelEvent.ALL_COLUMNS) &&
				(getSortingStatus( column ) == NOT_SORTED) &&
				(modelToView != null) ) {
				
				viewIndex = getModelToView()[ e.getFirstRow() ];
				fireTableChanged( new TableModelEvent( SortedTableModel.this, 
													   viewIndex, viewIndex, 
													   column, e.getType() ));
				return;
			}

			// Something has happened to the data that may have invalidated the row order. 
			clearSortingState();
			fireTableDataChanged();
			return;
		}
	} // class TableModelHandler

	private class MouseHandler
	extends MouseAdapter
	{
		public void mouseClicked( MouseEvent e )
		{
			final JTableHeader		h			= (JTableHeader) e.getSource();
			final TableColumnModel	columnModel = h.getColumnModel();
			final int				viewColumn	= columnModel.getColumnIndexAtX( e.getX() );
			final int				column		= columnModel.getColumn( viewColumn ).getModelIndex();
			int						status;

			if( column != -1 ) {
// SCISS REMOVED
//				status = getSortingStatus( column );
//				if( !e.isControlDown() ) {
//					cancelSorting();
//				}
//				// Cycle the sorting states through {NOT_SORTED, ASCENDING, DESCENDING} or 
//				// {NOT_SORTED, DESCENDING, ASCENDING} depending on whether shift is pressed. 
//				status = status + (e.isShiftDown() ? -1 : 1);
//				status = (status + 4) % 3 - 1; // signed mod, returning {-1, 0, 1}
				status = getSortingStatus( column );
				cancelSorting();
				status = status == ASCENDING ? DESCENDING : ASCENDING;
				setSortingStatus( column, status );
			}
		}
	} // class MouseHandler

	private static class ArrowIcon
	implements Icon
	{
		private static final Color			colrBg = new Color( 0x70, 0x70, 0x70, 0xF0 );
		private static final GeneralPath	shpUpTri;
		private static final GeneralPath	shpDownTri;
		private final		 Shape			shp;
		
		static {
			shpUpTri = new GeneralPath();
			shpUpTri.moveTo( 0f, 7f );
			shpUpTri.lineTo( 3.5f, 0f );
			shpUpTri.lineTo( 7f, 7f );
			shpUpTri.closePath();

			shpDownTri = new GeneralPath();
			shpDownTri.moveTo( 0f, 0f );
			shpDownTri.lineTo( 3.5f, 7f );
			shpDownTri.lineTo( 7f, 0f );
			shpDownTri.closePath();
		}
	
		public ArrowIcon( int style )
		{
			switch( style ) {
			case ASCENDING:
				shp			= shpUpTri;
				break;
			case DESCENDING:
				shp			= shpDownTri;
				break;
			case NOT_SORTED:
				shp			= null;
				break;
			default:
				throw new IllegalArgumentException( String.valueOf( style ));
			}
		}

		public void paintIcon( Component c, Graphics g, int x, int y )
		{
			if( shp == null ) return;
		
			final Graphics2D g2 = (Graphics2D) g;
		
			g2.translate( x, y );
			g2.setColor( colrBg );
			g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
			g2.fill( shp );
			g2.translate( -x, -y );
		}				

		public int getIconWidth()
		{
			return 8;
		}

		public int getIconHeight()
		{
			return 8;
		}
	} // class ArrowIcon

	private class SortableHeaderRenderer
	implements TableCellRenderer
	{
		private final TableCellRenderer tableCellRenderer;

		public SortableHeaderRenderer( TableCellRenderer tableCellRenderer )
		{
			this.tableCellRenderer = tableCellRenderer;
		}

		public Component getTableCellRendererComponent(
			JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column )
		{
			final Component c = tableCellRenderer.getTableCellRendererComponent(
									table, value, isSelected, hasFocus, row, column );
			final JLabel	l;
			final int		modelColumn;
			
			if( c instanceof JLabel ) {
				l			= (JLabel) c;
				modelColumn = table.convertColumnIndexToModel( column );
				l.setHorizontalTextPosition( JLabel.LEFT );
				l.setIcon( getHeaderRendererIcon( modelColumn ));
			}
			return c;
		}
	} // class SortableHeaderRenderer

	private static class Directive
	{
		private final int column;
		private final int direction;

		public Directive( int column, int direction )
		{
			this.column		= column;
			this.direction	= direction;
		}
	} // class Directive
}