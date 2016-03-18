/*
 *  Atom.java
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

import java.io.*;
import java.text.*;
import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.*;

import de.sciss.io.AudioFile;
import de.sciss.io.AudioFileDescr;
import de.sciss.io.IOUtil;
import de.sciss.io.Span;

import de.sciss.util.MapManager;
import de.sciss.util.NumberSpace;
import de.sciss.util.XMLRepresentation;

public class Atom
extends AbstractSessionObject
{
	public static final String		PROB_TIME			= "time";
	public static final String		PROB_VOLUME			= "volume";
	public static final String		PROB_PITCH			= "pitch";

	public static final String[]	PROB_ALL			= { PROB_TIME, PROB_VOLUME, PROB_PITCH };

	public final SessionCollection	probabilities		= new SessionCollection();

	public static final int			OWNER_PROB_OBJECT	=	0x2000;	// prob added the first time
	public static final int			OWNER_PROB_INTERIEUR=	0x2001;	// min, max or table

	private static final String		XML_VALUE_PROBS		= "probs";

	public static final String		MAP_KEY_AUDIOFILE	= "audiofile";
	public static final String		MAP_KEY_AFSTART		= "afstart";
	public static final String		MAP_KEY_AFSTOP		= "afstop";
	public static final String		MAP_KEY_FADEIN		= "fadein";
	public static final String		MAP_KEY_FADEOUT		= "fadeout";

	public Atom()
	{
		super();

		probabilities.addListener( new SessionCollection.Listener() {
			// when prob is first added
			public void sessionCollectionChanged( SessionCollection.Event e )
			{
				getMap().dispatchOwnerModification( this, OWNER_PROB_OBJECT, e.getCollection() );
			}

			// when prob changes (min, max or table)
			public void sessionObjectMapChanged( SessionCollection.Event e )
			{
				getMap().dispatchOwnerModification( this, OWNER_PROB_INTERIEUR, e.getCollection() );
			}

			// shouldn't occur ?
			public void sessionObjectChanged( SessionCollection.Event e )
			{
				getMap().dispatchOwnerModification( this, OWNER_PROB_OBJECT, e.getCollection() );
			}
		});

		final MapManager	map	= getMap();
		final NumberSpace	spc	= new NumberSpace( 0.0, Double.POSITIVE_INFINITY, 0.001 );

		map.putContext( this, MAP_KEY_AFSTART, new MapManager.Context( MapManager.Context.FLAG_OBSERVER_DISPLAY,
			MapManager.Context.TYPE_DOUBLE, spc, null, null, new Double( 0.0 )));
		map.putContext( this, MAP_KEY_AFSTOP, new MapManager.Context( MapManager.Context.FLAG_OBSERVER_DISPLAY,
			MapManager.Context.TYPE_DOUBLE, spc, null, null, new Double( 0.0 )));	// XXX  test
		map.putContext( this, MAP_KEY_AUDIOFILE, new MapManager.Context( MapManager.Context.FLAG_OBSERVER_DISPLAY,
			MapManager.Context.TYPE_FILE, null, null, null, new File( "" )));
		map.putContext( this, MAP_KEY_FADEIN, new MapManager.Context( MapManager.Context.FLAG_OBSERVER_DISPLAY,
			MapManager.Context.TYPE_DOUBLE, spc, null, null, new Double( 0.05 )));
		map.putContext( this, MAP_KEY_FADEOUT, new MapManager.Context( MapManager.Context.FLAG_OBSERVER_DISPLAY,
			MapManager.Context.TYPE_DOUBLE, spc, null, null, new Double( 0.1 )));
			
		probabilities.setName( XML_VALUE_PROBS );
	}
	
	// note the name is the same and should
	// thus be changed
	public Atom duplicate()
	{
		Atom result = new Atom();
		result.setName( this.getName() );
		result.getMap().cloneMap( this.getMap() );
		
		assert result.probabilities.isEmpty() : "Atom.duplicate : probs not initially empty";
		
		for( int i = 0; i < this.probabilities.size(); i++ ) {
			result.probabilities.add( this, ((Probability) this.probabilities.get( i )).duplicate() );
		}
		
		return result;
	}
	
	public void createDefaultProbs( Session doc )
	{
		Probability prob;
		NumberSpace	spc;
		
		prob = new Probability();
		prob.setName( PROB_TIME );
		spc	= new NumberSpace( 0.0, Double.POSITIVE_INFINITY, 0.01 );
		prob.setMinSpace( spc, new Double( 0.0 ), null );
		prob.setMaxSpace( spc, new Double( 0.0 ), null );
		probabilities.add( this, prob );
		
		prob = new Probability();
		prob.setName( PROB_VOLUME );
		spc	= new NumberSpace( -96.0, 24.0, 0.1 );
		prob.setMinSpace( spc, new Double( 0.0 ), null );
		prob.setMaxSpace( spc, new Double( 0.0 ), null );
		probabilities.add( this, prob );

		prob = new Probability();
		prob.setName( PROB_PITCH );
		spc	= new NumberSpace( -72.0, 24.0, 0.01 );
		prob.setMinSpace( spc, new Double( 0.0 ), null );
		prob.setMaxSpace( spc, new Double( 0.0 ), null );
		probabilities.add( this, prob );
	}

	public Probability getTime()
	{
		return( (Probability) probabilities.findByName( PROB_TIME ));
	}
	
//	// short hand for getting min of time prob
//	public double getStart()
//	{
//		final Probability prob = (Probability) probabilities.get( PROB_TIME );
//		if( prob != null ) {
//			return prob.min;
//		} else {
//			return 0.0;
//		}
//	}
//
//	// short hand for getting max of time prob
//	public double getStop()
//	{
//		final Probability prob = (Probability) probabilities.get( PROB_TIME );
//		if( prob != null ) {
//			return prob.max;
//		} else {
//			return 0.0;
//		}
//	}

	public double getTimeStart()
	{
		final Probability	p	= (Probability) probabilities.findByName( PROB_TIME );
		if( p != null ) {
			return p.getMin();
		} else {
			return 0.0;
		}
	}

	public double getTimeStop()
	{
		final Probability	p	= (Probability) probabilities.findByName( PROB_TIME );
		if( p != null ) {
			return p.getMax();
		} else {
			return 0.0;
		}
	}

	public double getTimeLength()
	{
		return getTimeStop() - getTimeStart();
	}
	
	public double getFileStart()
	{
		final Number	num = (Number) getMap().getValue( MAP_KEY_AFSTART );
		if( num != null ) {
			return num.doubleValue();
		} else {
			return 0.0;
		}
	}

	public double getFileStop()
	{
		final Number	num = (Number) getMap().getValue( MAP_KEY_AFSTOP );
		if( num != null ) {
			return num.doubleValue();
		} else {
			return 0.0;
		}
	}

	public double getFileLength()
	{
		return getFileStop() - getFileStart();
	}

//	public AudioFileDescr getDescr()
//	{
//		return afd;
//	}

    public void setFileStart( Object source, double afStart )
    {
		getMap().putValue( source, MAP_KEY_AFSTART, new Double( afStart ));
    }

    public void setFileStop( Object source, double afStop )
    {
		getMap().putValue( source, MAP_KEY_AFSTOP, new Double( afStop ));
    }

//    public void setDescr( Object source, AudioFileDescr afd )
//    {
//        this.afd = afd;
////		if( source != null ) dispatchChange( source );
//		getMap().putValue( this, MAP_KEY_AUDIOFILE, afd.file );
//    }
	
	public Realized realize()
	{		
		return new Realized( this );
	}
	
	public Class getDefaultEditor()
	{
		return null;
	}

	public void debugDump( int indent )
	{
		super.debugDump( indent );
		printIndented( indent, "--- probs :" );
		probabilities.debugDump( indent + 1 );
	}
	
// ---------------- MapManager.Listener interface ---------------- 

//	public void mapChanged( MapManager.Event e )
//	{
//		super.mapChanged( e );
//		
//		final Object source = e.getSource();
//		
//		if( source == this ) return;
//		
//		final Set	keySet		= e.getPropertyNames();
//		Object		val;
//
//		if( keySet.contains( MAP_KEY_AFSTART )) {
//			val		= e.getManager().getValue( MAP_KEY_AFSTART );
//			if( val != null ) {
//				afStart	= ((Number) val).doubleValue();
//			}
//		}
//		if( keySet.contains( MAP_KEY_AFSTOP )) {
//			val		= e.getManager().getValue( MAP_KEY_AFSTOP );
//			if( val != null ) {
//				afStop	= ((Number) val).doubleValue();
//			}
//		}
//		if( keySet.contains( MAP_KEY_AUDIOFILE )) {
//			val		= e.getManager().getValue( MAP_KEY_AUDIOFILE );
//			if( val != null ) {
//				try {
//					final AudioFile af = AudioFile.openAsRead( (File) val );
//					afd = af.getDescr();
//					af.close();
//				}
//				catch( IOException e1 ) {
//					System.err.println( e1.getClass().getName() + " : " + e1.getLocalizedMessage() );
//				}
//			}
//		}
//	}

// ---------------- XMLRepresentation interface ---------------- 

	public void toXML( Document domDoc, Element node, Map options )
	throws IOException
	{
		super.toXML( domDoc, node, options );	// map

		Element			childElement, child2;
		SessionObject	so;
	
		// prob collection
		childElement = (Element) node.appendChild( domDoc.createElement( Session.XML_ELEM_COLL ));
		childElement.setAttribute( Session.XML_ATTR_NAME, XML_VALUE_PROBS );
		for( int i = 0; i < probabilities.size(); i++ ) {
			so		= probabilities.get( i );
			child2	= (Element) childElement.appendChild( domDoc.createElement( Session.XML_ELEM_OBJECT ));
			if( so instanceof XMLRepresentation ) {
				((XMLRepresentation) so).toXML( domDoc, child2, options );
			}
		}
	}

	public void fromXML( Document domDoc, Element node, Map options )
	throws IOException
	{
		final NodeList			nl		= node.getChildNodes();
		final java.util.List	soList	= new ArrayList();
		NodeList				nl2;
		Element					elem, elem2;
		String					val;
		SessionObject			so;

		try {
			setName( node.getAttribute( XML_ATTR_NAME ));
			for( int k = 0; k < nl.getLength(); k++ ) {
				if( !(nl.item( k ) instanceof Element) ) continue;
				
				elem	= (Element) nl.item( k );
				val		= elem.getTagName();

				// zero or one "map" element
				if( val.equals( Session.XML_ELEM_MAP )) {
					getMap().fromXML( domDoc, elem, options );

				// zero or more "object" elements
				} else if( val.equals( XML_ELEM_OBJECT )) {
					val		= elem.getAttribute( XML_ATTR_NAME );
	//				if( val.equals( Timeline.XML_OBJECT_NAME )) {
	//					timeline.fromXML( domDoc, elem, options );
	//				} else {
						System.err.println( "Warning: unknown session object type: '"+val+"'" );
	//				}
					
				} else if( val.equals( Session.XML_ELEM_COLL )) {
					val		= elem.getAttribute( Session.XML_ATTR_NAME );

					if( val.equals( XML_VALUE_PROBS )) {

						soList.clear();
						nl2 = elem.getChildNodes();
						for( int m = 0; m < nl2.getLength(); m++ ) {
							elem2	= (Element) nl2.item( m );
							val		= elem2.getTagName();
							if( !val.equals( XML_ELEM_OBJECT )) continue;

							if( elem2.hasAttribute( XML_ATTR_CLASS )) {
								val	= elem2.getAttribute( XML_ATTR_CLASS );
							} else {	// #IMPLIED
								val = "de.sciss.inertia.session.Probability";
							}
							so = (SessionObject) Class.forName( val ).newInstance();
							if( so instanceof XMLRepresentation ) {
								((XMLRepresentation) so).fromXML( domDoc, elem2, options );
							}
							probabilities.getMap().copyContexts( null, MapManager.Context.FLAG_DYNAMIC,
								MapManager.Context.NONE_EXCLUSIVE, so.getMap() );
							soList.add( so );
						}
						probabilities.addAll( this, soList );

					} else {
						System.err.println( "Warning: unknown session collection type: '"+val+"'" );
					}
				
				// dtd doesn't allow other elements so we never get here
				} else {
					System.err.println( "Warning: unknown session node: '"+val+"'" );
				}
			} // for root-nodes
		}
		catch( ClassNotFoundException e1 ) {
			throw IOUtil.map( e1 );
		}
		catch( InstantiationException e2 ) {
			throw IOUtil.map( e2 );
		}
		catch( IllegalAccessException e3 ) {
			throw IOUtil.map( e3 );
		}
		catch( NumberFormatException e4 ) {
			throw IOUtil.map( e4 );
		}
		catch( ClassCastException e5 ) {
			throw IOUtil.map( e5 );
		}
	}

// ---------------- internal classes ----------------

	public static class Realized
	{
		public final java.util.Map	events;		// maps (String) prob name to (Double) realized value
		public final String			name;		// superflouous, only for debugging schnucki
		public final double			startTime, stopTime, fileStart, fadeIn, fadeOut;
		public final File			file;
	
		private Realized( Atom ptrn )
		{
			events	= new HashMap( ptrn.probabilities.size() );
			name	= ptrn.getName();
		
			Probability			prob;
			Number				n;
			final MapManager	map	= ptrn.getMap();
		
			for( int i = 0; i < ptrn.probabilities.size(); i++ ) {
				prob	= (Probability) ptrn.probabilities.get( i );
				events.put( prob.getName(), new Double( prob.realize() ));
			}

			n	= (Number) events.get( PROB_TIME );
			if( n != null ) {
				startTime			= n.doubleValue();
			} else {
				startTime			= 0.0;		// hmmm...
			}
			
			fileStart = ((Number) map.getValueAvecDefault( MAP_KEY_AFSTART, null )).doubleValue();
			n	= (Number) map.getValueAvecDefault( MAP_KEY_AFSTOP, null );
			if( n != null ) {
				stopTime			= startTime + n.doubleValue() - fileStart;
			} else {
				stopTime			= startTime;
			}
			file	= (File) map.getValue( MAP_KEY_AUDIOFILE );
			
			fadeIn	= Math.min( ((Number) map.getValueAvecDefault( MAP_KEY_FADEIN, null )).doubleValue(),
						stopTime - startTime );
			fadeOut	= Math.min( ((Number) map.getValueAvecDefault( MAP_KEY_FADEOUT, null )).doubleValue(),
						stopTime - startTime - fadeIn );
		}
	}
}