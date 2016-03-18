//
//  ProbabilityTableEditor.java
//  Inertia
//
//  Created by Hanns Holger Rutz on 07.08.05.
//  Copyright 2005 __MyCompanyName__. All rights reserved.
//

package de.sciss.inertia.gui;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;

import de.sciss.inertia.session.*;

import de.sciss.inertia.math.*;

public class ProbabilityTableEditor
extends JFrame
implements VectorDisplay.Listener, TopPainter
{
	private final Line2D	line25		= new Line2D.Double();
	private final Line2D	lineMedian	= new Line2D.Double();
	private final Line2D	line75		= new Line2D.Double();
	private final Paint		pntLine		= new Color( 0xFF, 0x00, 0x00, 0x7F );

	private final ProbabilityTable	table;
	private final VectorEditor		tableEditor;
	private final Axis				tableHAxis, tableVAxis;

	private static final String HUNIT	= "density";

	public ProbabilityTableEditor( ProbabilityTable t )
	{
		super( t.getName() );
		
		this.table	= t;
		
		final VectorSpace			tableSpace;
		final JPopupMenu			tablePopup;
		final Container				cp				= getContentPane();
		final JPanel				padPanel		= new JPanel( new BorderLayout() );
		final Box					box, box2, box3;
		final VectorEditorToolBar	vtb				= new VectorEditorToolBar();
		final Action				actionNormalize	= new actionNormalizeClass();
		final JRootPane				rp				= getRootPane();
		final InputMap				imap			= rp.getInputMap( JComponent.WHEN_IN_FOCUSED_WINDOW );
		final ActionMap				amap			= rp.getActionMap();
		final Action				actionIncVertical, actionDecVertical;

		tableSpace	= VectorSpace.createLinSpace( 0.0, 1.0, 0.0, 0.1, HUNIT, null, null, null );

		tableHAxis		= new Axis( Axis.HORIZONTAL, 0 );
		tableHAxis.setSpace( tableSpace );
		tableVAxis		= new Axis( Axis.VERTICAL, 0 );
		tableVAxis.setSpace( tableSpace );
		box				= Box.createHorizontalBox();
		box.add( Box.createHorizontalStrut( tableVAxis.getPreferredSize().width ));
		box.add( tableHAxis );
		
		tableEditor = new VectorEditor();
		tableEditor.setSpace( null, tableSpace );
		tablePopup  = VectorTransformer.createPopupMenu( tableEditor );

		padPanel.add( BorderLayout.CENTER, tableEditor );
		padPanel.add( box, BorderLayout.NORTH );
		padPanel.add( tableVAxis, BorderLayout.WEST );

		cp.setLayout( new BoxLayout( cp, BoxLayout.Y_AXIS ));
		box2		= Box.createHorizontalBox();
		box2.add( vtb );
		box2.add( Box.createHorizontalGlue() );
		cp.add( box2 );
		cp.add( padPanel );
		box3		= Box.createHorizontalBox();
		box3.add( vtb );
		box3.add( new JButton( actionNormalize ));
		cp.add( box3 );
		
		actionIncVertical	= new actionVerticalZoomClass( 2.0f );
		actionDecVertical	= new actionVerticalZoomClass( 0.5f );

		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_DOWN, KeyEvent.CTRL_MASK ), "inch" );
		amap.put( "inch", actionIncVertical );
		imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_UP, KeyEvent.CTRL_MASK ), "dech" );
		amap.put( "dech", actionDecVertical );

        // --- Listener ---
		tableEditor.addMouseListener( new PopupListener( tablePopup ));
		tableEditor.addListener( this );
		vtb.addToolActionListener( tableEditor );
		tableEditor.addTopPainter( this );
		
//		updateSpaces();
		setVectors();

		pack();
		setVisible( true );
		toFront();
	}

	private void setVectors()
	{
		tableEditor.setVector( null, table.density );
	
//		float[]	tab, editTab;
//
//		tab		= table.density;
//		editTab = tableEditor.getVector();
//		if( tab.length == editTab.length ) {
//			System.arraycopy( tab, 0, editTab, 0, tab.length );
//		} else {
//			editTab = (float[]) tab.clone();
//		}
//		tableEditor.setVector( null, editTab );
	}

// ---------------- VectorDisplay.Listener interface ---------------- 

	/**
	 *  This is called when one of the tables
	 *  change. This updates the receiver's data
	 *  structure by generting an <code>EditTableLookupRcvSense</code>
	 *  object.
	 *
	 *  @see	de.sciss.meloncillo.edit.EditTableLookupRcvSense
	 */
	public void vectorChanged( VectorDisplay.Event e )
	{
		table.density	= tableEditor.getVector();
	}

	public void vectorSpaceChanged( VectorDisplay.Event e ) {}

// ---------------- TopPainter interface ---------------- 

	public void paintOnTop( Graphics2D g2 )
	{
		double x;
		
		x = (double) table.percentile25 / (double) table.density.length;
		line25.setLine( x, 0, x, 1.0 );
		line25.setLine( x, 0, x, 1.0 );
		x = (double) table.median / (double) table.density.length;
		lineMedian.setLine( x, 0, x, 1.0 );
		lineMedian.setLine( x, 0, x, 1.0 );
		x = (double) table.percentile75 / (double) table.density.length;
		line75.setLine( x, 0, x, 1.0 );
		line75.setLine( x, 0, x, 1.0 );
		
		g2.setPaint( pntLine );
		g2.draw( tableEditor.virtualToScreen( line25 ));
		g2.draw( tableEditor.virtualToScreen( lineMedian ));
		g2.draw( tableEditor.virtualToScreen( line75 ));
	}

// ---------------- internal classes ---------------- 

	private class actionNormalizeClass
	extends AbstractAction
	{
		private actionNormalizeClass()
		{
			super( "Normalize" );
		}
		
		public void actionPerformed( ActionEvent e )
		{
			table.normalize();
			table.updatePercentiles();
			setVectors();
//			tableEditor.repaint();
		}
	}

	/**
	 *  Increase or decrease the height
	 *  of the rows of the selected transmitters
	 */
	private class actionVerticalZoomClass
	extends AbstractAction
	{
		private final float factor;
		
		/**
		 *  @param  factor  factors > 1 increase the row height,
		 *					factors < 1 decrease.
		 */
		private actionVerticalZoomClass( float factor )
		{
			super();
			this.factor = factor;
		}
		
		public void actionPerformed( ActionEvent e )
		{
			double	min, max;
			final	VectorSpace	oldSpace, newSpace;
			
			oldSpace	= tableEditor.getSpace();
			min			= oldSpace.vmin;
			max			= oldSpace.vmax;
			if( ((factor >= 1.0) & (max < 1.0)) || ((min < 1.0e-4) && (max > 1.0e-4)) ) {
				min		   *= factor;
				max		   *= factor;
				newSpace	= VectorSpace.createLinSpace( 0.0, 1.0, min, max, HUNIT, null, null, null );
				tableEditor.setSpace( null, newSpace );
//				tableHAxis.setSpace( newSpace );
				tableVAxis.setSpace( newSpace );
			}
		}
	} // class actionVerticalZoomClass
}