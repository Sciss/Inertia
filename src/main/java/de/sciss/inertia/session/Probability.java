//
//  Probability.java
//  Inertia
//
//  Created by Hanns Holger Rutz on 07.08.05.
//  Copyright 2005 __MyCompanyName__. All rights reserved.
//

package de.sciss.inertia.session;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.undo.CompoundEdit;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.*;

import de.sciss.inertia.edit.EditPutMapValue;

import de.sciss.io.*;
import de.sciss.gui.StringItem;
import de.sciss.util.*;

public class Probability
extends AbstractSessionObject
{
//	private double				min, max;
//	private ProbabilityTable	table;
//
	private static final String  MAP_KEY_MIN		= "min";
	private static final String  MAP_KEY_MAX		= "max";
	private static final String  MAP_KEY_TYPE		= "type";
	private static final String  MAP_KEY_BF			= "bf";	// 'brownian factor' a la grinder
	private static final String  MAP_KEY_SAMPLES	= "samples";
	private static final String  MAP_KEY_TABLE		= "table";
	
	private Session doc = null;
	
	private static final Random	rnd	= new Random( System.currentTimeMillis() );
	
	private static final StringItem[]	TYPE_ITEMS	= {
		new StringItem( "white", "White" ),
		new StringItem( "brown", "Brownian Walk" ),
		new StringItem( "table", "Density Table" ),
		new StringItem( "seq", "Sequence" ),
		new StringItem( "series", "Series" ),
		new StringItem( "alea", "Alea" ),
		new StringItem( "rota", "Rotation" )
	};
	
	private static final int TYPE_WHITE		= 0;
	private static final int TYPE_BROWN		= 1;
	private static final int TYPE_TABLE		= 2;
	private static final int TYPE_SEQ		= 3;
	private static final int TYPE_SERIES	= 4;
	private static final int TYPE_ALEA		= 5;
	private static final int TYPE_ROTA		= 6;
	
	private double[] samples		= new double[0];
	private double[] seriesSamples	= new double[0];
	private int		 seriesLen		= 0;
	private final Object sampleSync	= new Object();
	
	private int sampleIdx		= 0;
	private int type			= TYPE_WHITE;
	private int rotaInc			= 1;
	
	public Probability()
	{
		super();

		MapManager	map	= getMap();

		map.putContext( this, MAP_KEY_MIN, new MapManager.Context( MapManager.Context.FLAG_OBSERVER_DISPLAY,
			MapManager.Context.TYPE_DOUBLE, null, null, null, null ));
		map.putContext( this, MAP_KEY_MAX, new MapManager.Context( MapManager.Context.FLAG_OBSERVER_DISPLAY,
			MapManager.Context.TYPE_DOUBLE, null, null, null, null ));
		map.putContext( this, MAP_KEY_TYPE, new MapManager.Context( MapManager.Context.FLAG_OBSERVER_DISPLAY,
			MapManager.Context.TYPE_STRING, TYPE_ITEMS, null, null, TYPE_ITEMS[ TYPE_WHITE ].getKey() ));
		map.putContext( this, MAP_KEY_BF, new MapManager.Context( MapManager.Context.FLAG_OBSERVER_DISPLAY,
			MapManager.Context.TYPE_DOUBLE, null, null, null, new Double( 0.1 )));
		map.putContext( this, MAP_KEY_SAMPLES, new MapManager.Context( MapManager.Context.FLAG_OBSERVER_DISPLAY,
			MapManager.Context.TYPE_STRING, null, null, null, "0.0 1.0" ));
		map.putContext( this, MAP_KEY_TABLE, new MapManager.Context( MapManager.Context.FLAG_OBSERVER_DISPLAY,
			MapManager.Context.TYPE_STRING, null, null, null, "P1" ));
	}

	public Probability duplicate()
	{
		Probability result = new Probability();
		result.setName( this.getName() );
		result.getMap().cloneMap( this.getMap() );
		result.setDocument( doc );
		
		return result;
	}

	public void setMinSpace( NumberSpace spc, Number defaultValue, String label )
	{
		final MapManager.Context c = getMap().getContext( MAP_KEY_MIN );
		getMap().putContext( this, MAP_KEY_MIN, new MapManager.Context( c.flags, c.type, spc, label, null, defaultValue ));
	}

	public void setMaxSpace( NumberSpace spc, Number defaultValue, String label )
	{
		final MapManager.Context c = getMap().getContext( MAP_KEY_MAX );
		getMap().putContext( this, MAP_KEY_MAX, new MapManager.Context( c.flags, c.type, spc, label, null, defaultValue ));
	}

	public double realize()
	{
		final double	d;
		final double	min = getMin();
		final double	max = getMax();
	
		synchronized( sampleSync ) {
			switch( type ) {
			case TYPE_WHITE:
				d	= rnd.nextDouble();
				break;
				
			case TYPE_BROWN:
				d	= rnd.nextDouble();
				System.err.println( "brownian not yet implemented!!!" );
				break;

			case TYPE_TABLE:
				final ProbabilityTable	pt	= getTable();
				if( pt == null ) {
					System.err.println( "table not found" );
					d	= 0.0;
				} else {
					d	= pt.realize();
				}
				break;

			case TYPE_SEQ:
				if( samples.length == 0 ) return getMin();
				d			= samples[ sampleIdx ];
				sampleIdx	= (sampleIdx + 1) % samples.length;
				break;
			
			case TYPE_SERIES:
				if( samples.length == 0 ) return getMin();
				sampleIdx	= rnd.nextInt( seriesLen );
				d			= seriesSamples[ sampleIdx ];
				if( --seriesLen == 0 ) {
					System.arraycopy( samples, 0, seriesSamples, 0, samples.length );
					seriesLen = samples.length;
				} else {
					System.arraycopy( seriesSamples, sampleIdx + 1, seriesSamples, sampleIdx, seriesLen - sampleIdx );
				}
				break;
			
			case TYPE_ALEA:
				if( samples.length == 0 ) return getMin();
				sampleIdx	= rnd.nextInt( samples.length );
				d			= samples[ sampleIdx ];
				break;
				
			case TYPE_ROTA:
				if( samples.length == 0 ) return getMin();
				d			= samples[ sampleIdx ];
				sampleIdx	= sampleIdx + rotaInc;
				if( sampleIdx < 0 ) {
					sampleIdx = (-sampleIdx) % samples.length;
					rotaInc	  = -rotaInc;
				} else if( sampleIdx >= samples.length ) {
					sampleIdx = Math.max( 0, ((samples.length - 1) << 1) - sampleIdx );
					rotaInc	  = -rotaInc;
				}
				break;

			default:
				System.err.println( "!probability "+getName()+" has invalid type "+type+"!" );
				d = 0.0;
				break;
			}
		} // synchronized( sampleSync )
		
		return( d * (max - min) + min );
	}
	
	public double getMin()
	{
//		return min;
		final Number num = (Number) getMap().getValue( MAP_KEY_MIN );
		if( num != null ) {
			return num.doubleValue();
		} else {
			return 0.0;
		}
	}

	public double getMax()
	{
//		return max;
		final Number num = (Number) getMap().getValue( MAP_KEY_MAX );
		if( num != null ) {
			return num.doubleValue();
		} else {
			return getMin();
		}
	}

	private ProbabilityTable getTable()
	{
//		return table;
		final String tabName = (String) getMap().getValue( MAP_KEY_TABLE );
		if( (tabName != null) && (doc != null) ) {
			return( (ProbabilityTable) doc.tables.findByName( tabName ));
		} else {
			return null;
		}
	}

	public void setDocument( Session doc )
	{
		this.doc = doc;
	}

    public void setMin( Object source, double min )
    {
		getMap().putValue( source, MAP_KEY_MIN, new Double( min ));
	}

	public void setMin( Object source, double min, CompoundEdit ce, LockManager lm, int doors )
    {
		ce.addEdit( new EditPutMapValue( source, lm, doors, getMap(), MAP_KEY_MIN, new Double( min )));
    }

    public void setMax( Object source, double max )
    {
		getMap().putValue( source, MAP_KEY_MAX, new Double( max ));
	}

    public void setMax( Object source, double max, CompoundEdit ce, LockManager lm, int doors )
    {
		ce.addEdit( new EditPutMapValue( source, lm, doors, getMap(), MAP_KEY_MAX, new Double( max )));
    }

//    public void setTable( Object source, ProbabilityTable table )
//    {
//		getMap().putValue( source, MAP_KEY_TABLE, table.getName() );
//    }

	public void move( Object source, double delta, double tot_min, double tot_max,
					  CompoundEdit ce, LockManager lm, int doors )
	{
		final double min = getMin();
		final double max = getMax();
	
		if( delta > 0 ) {
			delta = Math.min( delta, tot_max - max );
		} else {
			delta = Math.max( delta, tot_min - min );
		}
		setMax( source, max + delta, ce, lm, doors );
		setMin( source, min + delta, ce, lm, doors );
	}
	
	public void shiftStart( Object source, double delta, double tot_min, double tot_max,
							CompoundEdit ce, LockManager lm, int doors )
	{
		final double min = getMin();
		final double max = getMax();
	
//System.err.println( "delta was "+delta+"; min "+min+"; max "+max );
		if( delta > 0 ) {
			delta = Math.min( delta, max - min );
		} else {
			delta = Math.max( delta, tot_min - min );
		}
		setMin( source, min + delta, ce, lm, doors );
//System.err.println( "now delta is "+delta+"; min "+getMin()+"; max "+getMax() );
	}
	
	public void shiftStop( Object source, double delta, double tot_min, double tot_max,
						   CompoundEdit ce, LockManager lm, int doors )
	{
		final double min = getMin();
		final double max = getMax();

//System.err.println( "delta was "+delta+"; min "+min+"; max "+max );
		if( delta > 0 ) {
			delta = Math.min( delta, tot_max - max );
		} else {
			delta = Math.max( delta, min - max );
		}
		setMax( source, max + delta, ce, lm, doors );
//System.err.println( "now delta is "+delta+"; min "+getMin()+"; max "+getMax() );
	}
	
	public Class getDefaultEditor()
	{
		return null;
	}

	public void debugDump( int indent )
	{
		super.debugDump( indent );
		printIndented( indent, "--- table :" );
		final ProbabilityTable table = getTable();
		if( table != null ) {
			table.debugDump( indent + 1 );
		} else {
			printIndented( indent + 1, "null" );
		}
	}

	
// ---------------- MapManager.Listener interface ---------------- 

	public void mapChanged( MapManager.Event e )
	{
		super.mapChanged( e );
		
		final Object source = e.getSource();
		
		if( source == this ) return;
		
		final Set	keySet		= e.getPropertyNames();
		Object		val;

		if( keySet.contains( MAP_KEY_TYPE )) {
			val		= e.getManager().getValue( MAP_KEY_TYPE );
			if( val != null ) {
				for( int i = 0; i < TYPE_ITEMS.length; i++ ) {
					if( val.equals( TYPE_ITEMS[ i ].getKey() )) {
						type = i;
						break;
					}
				}
			}
		}
		if( keySet.contains( MAP_KEY_SAMPLES )) {
			val		= e.getManager().getValue( MAP_KEY_SAMPLES );
			if( val != null ) {
				final StringTokenizer strTok = new StringTokenizer( val.toString() );
				try {
					synchronized( sampleSync ) {
						samples			= new double[ strTok.countTokens() ];
						seriesSamples	= new double[ samples.length ];
						for( int i = 0; i < samples.length; i++ ) {
							samples[ i ]= Math.max( 0.0, Math.min( 1.0, Double.parseDouble( strTok.nextToken() )));
							seriesSamples[ i ] = samples[ i ];
						}
						sampleIdx		= 0;
						seriesLen		= samples.length;
						rotaInc			= 1;
					} // synchronized( sampleSync )
				}
				catch( NumberFormatException e1 ) {
					System.err.println( e1.getClass().getName() + " : " + e1.getLocalizedMessage() );
				}
			}
		}
	}

// ---------------- XMLRepresentation interface ---------------- 

	public void toXML( org.w3c.dom.Document domDoc, Element node, Map options )
	throws IOException
	{
		super.toXML( domDoc, node, options );
	}

	public void fromXML( org.w3c.dom.Document domDoc, Element node, Map options )
	throws IOException
	{
		doc = (Session) options.get( Session.OPTIONS_KEY_SESSION );
	
		super.fromXML( domDoc, node, options );
	}
}
