//
//	SpringPanel.java
//	de.sciss.gui package
//	GNU GPL
//
//	Created by Hanns Holger Rutz on 07.09.05.
//	Copyright 2005 __MyCompanyName__. All rights reserved.
//

package de.sciss.gui;

import java.awt.*;
import java.awt.event.*;
import java.beans.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;

// @version	0.26, 18-Sep-05
public class SpringPanel
extends JPanel
{
	private final	SpringLayout	layout;
	private			int				xPad, yPad;
	private			int				initialX, initialY;
	
	private static final Object GRID	= "de.sciss.gui.GRID";

	public SpringPanel()
	{
		super();
		
		layout = new SpringLayout();
		setLayout( layout );
	}

//	public SpringPanel( int rows, int cols )
//	{
//		this();
//		
//		this.rows	= rows;
//		this.cols	= cols;
//	}
//
//	public SpringPanel( int rows, int cols, int initialX, int initialY, int xPad, int yPad )
//	{
//		this( rows, cols );
//		
//		this.initialX	= initialX;
//		this.initialY	= initialY;
//		this.xPad		= xPad;
//		this.yPad		= yPad;
//	}
	
	public void setInitialX( int x )
	{
		initialX = x;
	}

	public void setInitialY( int y )
	{
		initialY = y;
	}

	public void setXPad( int x )
	{
		xPad = x;
	}

	public void setYPad( int y )
	{
		yPad = y;
	}
	
	public void setPadding( int initialX, int initialY, int xPad, int yPad )
	{
		this.initialX	= initialX;
		this.initialY	= initialY;
		this.xPad		= xPad;
		this.yPad		= yPad;
	}
	
	public void gridAdd( JComponent c, int x, int y )
	{
		gridAdd( c, x, y, 1, 1 );
	}

	public void gridAdd( JComponent c, int x, int y, int width, int height )
	{
		gridAdd( c, new Rectangle( x, y, width, height ));
	}

	public void gridAdd( JComponent c, Rectangle r )
	{
		c.putClientProperty( GRID, r );
		add( c );
	}
	
	public void setTitle( String title )
	{
		setBorder( BorderFactory.createTitledBorder( null, title ));
	}
	
//	public void setColumns( int cols )
//	{
//		this.cols	= cols;
//	}
//
//	public void setRows( int rows )
//	{
//		this.rows	= rows;
//	}
	
	public void makeGrid( boolean elastic )
	{
		final java.util.List		realOnes		= new ArrayList( getComponentCount() );
		final Spring				xPadSpring, yPadSpring, initialXSpring, initialYSpring;
		final int[]					colCnt;
		final int[]					rowCnt;
		final int					effCols, effRows;

		Spring						maxWidthSpring, maxHeightSpring, spX, spY, spW, spH;
		SpringLayout.Constraints	cons;
//		SpringLayout.Constraints	lastCons		= null;
//		SpringLayout.Constraints	lastRowCons		= null;
		Rectangle					r;
		Component					comp;
		JComponent					jc;
		int							rows			= 0;
		int							cols			= 0;

		xPadSpring		= Spring.constant( xPad );
		yPadSpring		= Spring.constant( yPad );
		initialXSpring	= Spring.constant( initialX );
		initialYSpring	= Spring.constant( initialY );

		for( int i = 0; i < getComponentCount(); i++ ) {
			comp			= getComponent( i );
			if( !(comp instanceof JComponent) || !comp.isVisible() ) continue;
			jc				= (JComponent) comp;
			r				= (Rectangle) jc.getClientProperty( GRID );
			if( r == null ) continue;
			realOnes.add( jc );
			cols			= Math.max( cols, r.x + r.width );
			rows			= Math.max( rows, r.y + r.height );
		}
		
		if( (cols == 0) || (rows == 0) ) return;
		
		colCnt = new int[ cols ];
		rowCnt = new int[ rows ];
		
		for( int i = 0; i < realOnes.size(); i++ ) {
			jc				= (JComponent) realOnes.get( i );
			r				= (Rectangle) jc.getClientProperty( GRID );
			for( int col = r.x; col < r.x + r.width; col++ ) {
				colCnt[ col ]++;
			}
			for( int row = r.y; row < r.y + r.height; row++ ) {
				rowCnt[ row ]++;
			}
		}
		
		for( int col = 0, colOff = 0; col < cols; col++ ) {
			if( colCnt[ col ] > 0 ) {
				colCnt[ col ] = colOff++;
			}
		}
		for( int row = 0, rowOff = 0; row < rows; row++ ) {
			if( rowCnt[ row ] > 0 ) {
				rowCnt[ row ] = rowOff++;
			}
		}

		effCols = colCnt[ cols - 1 ] + 1;
		effRows = rowCnt[ rows - 1 ] + 1;

		if( elastic ) {
//			maxWidthSpring	= Spring.constant( 64 );
//			maxHeightSpring = Spring.constant( 32 );
			maxWidthSpring	= new ComponentWidthRatioSpring( this, 1, effCols );
			maxHeightSpring = new ComponentHeightRatioSpring( this, 1, effRows );
		} else {
			// Calculate Springs that are the max of the width/height so that all
			// cells have the same size.
			maxWidthSpring	= Spring.constant( 0 );
			maxHeightSpring = Spring.constant( 0 );
		}
		for( int i = 0; i < realOnes.size(); i++ ) {
			jc				= (JComponent) realOnes.get( i );
			r				= (Rectangle) jc.getClientProperty( GRID );
			cons			= layout.getConstraints( jc );
			spW				= new RatioSpring( cons.getWidth(), 1, r.width );
			spH				= new RatioSpring( cons.getHeight(), 1, r.height );
			maxWidthSpring	= Spring.max( maxWidthSpring, spW );
			maxHeightSpring = Spring.max( maxHeightSpring, spH );
		}
		
		System.err.println( "cols "+cols+"; rows "+rows+"; maxWidthSpring "+maxWidthSpring.getValue()+
			"; maxHeightSpring "+maxHeightSpring.getValue() );

		// Apply the new width/height Spring. This forces all the
		// components to have the same size.
		// Adjust the x/y constraints of all the cells so that they
		// are aligned in a grid.
		for( int i = 0; i < realOnes.size(); i++ ) {
			jc		= (JComponent) realOnes.get( i );
			r		= (Rectangle) jc.getClientProperty( GRID );
			cons	= layout.getConstraints( jc );
			spW		= new RatioSpring( maxWidthSpring, r.width, 1 );
			spH		= new RatioSpring( maxHeightSpring, r.height, 1 );
			cons.setWidth( spW );
			cons.setHeight( spH );

			spX		= initialXSpring;
			if( colCnt[ r.x ] > 0 ) {
				spX	= Spring.sum( spX, new RatioSpring( maxWidthSpring, colCnt[ r.x ], 1 ));
			}
			spY		= initialYSpring;
			if( rowCnt[ r.y ] > 0 ) {
				spY	= Spring.sum( spY, new RatioSpring( maxHeightSpring, rowCnt[ r.y ], 1 ));
			}
			cons.setX( spX );
			cons.setY( spY );
			
			if( jc instanceof AbstractButton ) {
				System.out.println( "For "+((AbstractButton) jc).getText()+
					" spX "+spX.getValue()+"; spY "+spY.getValue()+"; spW "+spW.getValue()+"; spH "+spH.getValue()+
					"; r.x "+r.x+"; r.y "+r.y+"; r.width "+r.width+"; r.height "+r.height );
			}
		}

//		// Then adjust the x/y constraints of all the cells so that they
//		// are aligned in a grid.
//		for( int i = 0; i < realOnes.size(); i++ ) {
//			jc		= (JComponent) realOnes.get( i );
//			r		= (Rectangle) jc.getClientProperty( GRID );
//			cons	= layout.getConstraints( jc );
//			if( i % cols == 0 ) {	// start of new row
//				lastRowCons = lastCons;
//				cons.setX( initialXSpring );
//			} else {				// x position depends on previous component
//				cons.setX( Spring.sum( lastCons.getConstraint( SpringLayout.EAST ), xPadSpring ));
//			}
//
//			if( i / cols == 0 ) {	// first row
//				cons.setY( initialYSpring );
//			} else {				// y position depends on previous row
//				cons.setY( Spring.sum( lastRowCons.getConstraint( SpringLayout.SOUTH ), yPadSpring ));
//			}
//			lastCons = cons;
//		}

System.err.println( "effCols = "+effCols+"; effRows = "+effRows );

		if( !elastic ) {
			// Set the parent's size.
			spX		= Spring.sum( initialXSpring, Spring.sum( xPadSpring, new RatioSpring( maxWidthSpring, effCols, 1 )));
			spY		= Spring.sum( initialYSpring, Spring.sum( yPadSpring, new RatioSpring( maxHeightSpring, effRows, 1 )));
			
	System.err.println( " yields east : "+(Spring.sum( Spring.constant( xPad ), spX )).getValue()+
		"; south : "+(Spring.sum( Spring.constant( yPad ), spY )).getValue() );
			
			cons = layout.getConstraints( this );
			cons.setConstraint( SpringLayout.EAST, spX );
			cons.setConstraint( SpringLayout.SOUTH, spY );
	//		cons.setConstraint( SpringLayout.SOUTH, Spring.sum( Spring.constant( yPad ),
	//							lastCons.getConstraint( SpringLayout.SOUTH )));
	//		cons.setConstraint( SpringLayout.EAST, Spring.sum( Spring.constant( xPad ),
	//							lastCons.getConstraint( SpringLayout.EAST )));
		}
	}

    public void makeCompactGrid()
	{
		makeCompactGrid( false );
	}
	
    public void makeCompactGrid( boolean elastic )
	{
if( elastic == true ) System.err.println( "makeCompactGrid : elastic mode not yet implemented" );

		final java.util.List		realOnes		= new ArrayList( getComponentCount() );
		final Spring				xPadSpring, yPadSpring, initialXSpring, initialYSpring;
		final int[]					colCnt;
		final int[]					rowCnt;
		final Spring[]				spXs, spYs, spWs, spHs;

		Spring						spX, spY, spW, spH;
		SpringLayout.Constraints	cons;
//		SpringLayout.Constraints	lastCons		= null;
//		SpringLayout.Constraints	lastRowCons		= null;
		Rectangle					r;
		Component					comp;
		JComponent					jc;
		int							rows			= 0;
		int							cols			= 0;

		xPadSpring		= Spring.constant( xPad );
		yPadSpring		= Spring.constant( yPad );
		initialXSpring	= Spring.constant( initialX );
		initialYSpring	= Spring.constant( initialY );

		for( int i = 0; i < getComponentCount(); i++ ) {
			comp			= getComponent( i );
			if( !(comp instanceof JComponent) || !comp.isVisible() ) continue;
			jc				= (JComponent) comp;
			r				= (Rectangle) jc.getClientProperty( GRID );
			if( r == null ) continue;
			realOnes.add( jc );
			cols			= Math.max( cols, r.x + r.width );
			rows			= Math.max( rows, r.y + r.height );
		}
		
		if( (cols == 0) || (rows == 0) ) return;
		
		colCnt	= new int[ cols ];
		rowCnt	= new int[ rows ];
		spXs	= new Spring[ cols ];
		spYs	= new Spring[ rows ];
		spWs	= new Spring[ cols ];
		spHs	= new Spring[ rows ];
		
		for( int i = 0; i < realOnes.size(); i++ ) {
			jc		= (JComponent) realOnes.get( i );
			r		= (Rectangle) jc.getClientProperty( GRID );
			cons	= layout.getConstraints( jc );
			for( int col = r.x; col < r.x + r.width; col++ ) {
				colCnt[ col ]++;
				spWs[ col ] = spWs[ col ] == null ?
					cons.getWidth() : Spring.max( spWs[ col ], new RatioSpring( cons.getWidth(), 1, r.width ));
			}
			for( int row = r.y; row < r.y + r.height; row++ ) {
				rowCnt[ row ]++;
				spHs[ row ] = spHs[ row ] == null ?
					cons.getHeight() : Spring.max( spHs[ row ], new RatioSpring( cons.getHeight(), 1, r.height ));
			}
		}
		
		spX = Spring.constant( initialX );
		spY = Spring.constant( initialY );
		
		for( int col = 0, colOff = 0; col < cols; col++ ) {
			if( colCnt[ col ] > 0 ) {
				colCnt[ col ] = colOff++;
				spXs[ col ]	= spX;
				spX			= Spring.sum( spX, Spring.sum( spWs[ col ], xPadSpring ));
			}
		}
		for( int row = 0, rowOff = 0; row < rows; row++ ) {
			if( rowCnt[ row ] > 0 ) {
				rowCnt[ row ] = rowOff++;
				spYs[ row ]	= spY;
				spY			= Spring.sum( spY, Spring.sum( spHs[ row ], yPadSpring ));
			}
		}

		for( int i = 0; i < realOnes.size(); i++ ) {
			jc		= (JComponent) realOnes.get( i );
			r		= (Rectangle) jc.getClientProperty( GRID );
			cons	= layout.getConstraints( jc );
			spW		= spWs[ r.x ];
			for( int j = 1; j < r.width; j++ ) {
				spW	= Spring.sum( spW, spWs[ r.x + j ]);
			}
			spH		= spHs[ r.y ];
			for( int j = 1; j < r.height; j++ ) {
				spH	= Spring.sum( spH, spHs[ r.y + j ]);
			}
			// XXX TEST
			if( !(jc instanceof JComboBox) && !(jc instanceof JCheckBox) ) {
				cons.setWidth( spW );
			}
			cons.setHeight( spH );

			cons.setX( spXs[ r.x ]);
			cons.setY( spYs[ r.y ]);
			
//			if( jc instanceof AbstractButton ) {
//				System.out.println( "For "+((AbstractButton) jc).getText()+
//					" spX "+spX.getValue()+"; spY "+spY.getValue()+"; spW "+spW.getValue()+"; spH "+spH.getValue()+
//					"; r.x "+r.x+"; r.y "+r.y+"; r.width "+r.width+"; r.height "+r.height );
//			}
		}

		if( elastic ) {
			// XXX
		} else {
			spX		= Spring.sum( spXs[ cols - 1 ], Spring.sum( xPadSpring, spWs[ cols - 1 ]));
			spY		= Spring.sum( spYs[ rows - 1 ], Spring.sum( yPadSpring, spHs[ rows - 1 ]));
			
			cons = layout.getConstraints( this );
			cons.setConstraint( SpringLayout.EAST, spX );
			cons.setConstraint( SpringLayout.SOUTH, spY );
		}
	}

// --------------- internal classes ---------------

	private static class RatioSpring
	extends Spring
	{
		final Spring	s;
		final int		nom, div;

		public RatioSpring( Spring s, int nom, int div )
		{
			this.s		= s;
			this.nom	= nom;
			this.div	= div;
		}
		
		public int getMinimumValue()
		{
			return s.getMinimumValue() * nom / div;
		}
		
		public int getPreferredValue()
		{
			return s.getPreferredValue() * nom / div;
		}
		
		public int getMaximumValue()
		{
			return s.getMaximumValue() * nom / div;
		}
		
		public int getValue()
		{
			return s.getValue() * nom / div;
		}
		
		public void setValue( int value )
		{
			s.setValue( value * div / nom );
		}
	}

	private static class ComponentHeightRatioSpring
	extends Spring
	{
		final Component	c;
		final int		nom, div;

		public ComponentHeightRatioSpring( Component c, int nom, int div )
		{
			this.c		= c;
			this.nom	= nom;
			this.div	= div;
		}
		
		public int getMinimumValue()
		{
			return c.getMinimumSize().height * nom / div;
		}
		
		public int getPreferredValue()
		{
			return getValue(); // c.getPreferredSize().height * nom / div;
		}
		
		public int getMaximumValue()
		{
			return c.getMaximumSize().height * nom / div;
		}
		
		public int getValue()
		{
			return c.getHeight() * nom / div;
		}
		
		public void setValue( int value )
		{
	//		s.setValue( value * div / nom );
		}
	}

	private static class ComponentWidthRatioSpring
	extends Spring
	{
		final Component	c;
		final int		nom, div;

		public ComponentWidthRatioSpring( Component c, int nom, int div )
		{
			this.c		= c;
			this.nom	= nom;
			this.div	= div;
		}
		
		public int getMinimumValue()
		{
			return c.getMinimumSize().width * nom / div;
		}
		
		public int getPreferredValue()
		{
			return getValue(); // c.getPreferredSize().width * nom / div;
		}
		
		public int getMaximumValue()
		{
			return c.getMaximumSize().width * nom / div;
		}
		
		public int getValue()
		{
			return c.getWidth() * nom / div;
		}
		
		public void setValue( int value )
		{
	//		s.setValue( value * div / nom );
		}
	}
}