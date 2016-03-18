/*
 *  Molecule.java
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
 *		14-Aug-05	created
 */

package de.sciss.inertia.session;

import java.io.*;
import java.text.*;
import java.util.*;
import javax.swing.undo.CompoundEdit;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.*;

import de.sciss.inertia.edit.EditPutMapValue;

import de.sciss.io.AudioFile;
import de.sciss.io.AudioFileDescr;
import de.sciss.io.IOUtil;
import de.sciss.io.Span;

import de.sciss.util.LockManager;
import de.sciss.util.MapManager;
import de.sciss.util.XMLRepresentation;

public class Molecule
extends AbstractSessionObject
{
	public static final String		VERTICAL		= "vertical";

	public static final Comparator	startComparator	= new StartComparator();
	public static final Comparator	stopComparator	= new StopComparator();

	public final SessionCollection	atoms			= new SessionCollection();

	private double					start, stop;	// in seconds

	public static final int			OWNER_SPAN				=	0x3000;	// start, stop
	public static final int			OWNER_ATOM_INTERIEUR	=	0x3001;
	public static final int			OWNER_ATOM_COLL			=	0x3002;

	private static final String		XML_VALUE_ATOMS	= "atoms";
	
	private final int[]				radiation		= new int[ Session.NUM_MOVIES ];
	
	private final Random			rnd				= new Random( System.currentTimeMillis() );
	
	private final java.util.List	collAtomSeries	= new ArrayList();	// pour random mixage

	public static final String		MAP_KEY_Y		= "y";
	public static final String		MAP_KEY_HEIGHT	= "height";
	public static final String		MAP_KEY_FREEZE	= "freeze";
	
	private boolean freeze		= false;
	private Atom	frozenAtom	= null;
		
	private static final java.util.List	atomTimeKeys	= new ArrayList();
	
	static {
		atomTimeKeys.add( Atom.MAP_KEY_AFSTART );
		atomTimeKeys.add( Atom.MAP_KEY_AFSTOP );
	}
	
	public Molecule()
	{
		super();
		
		atoms.addListener( new SessionCollection.Listener() {
			public void sessionCollectionChanged( SessionCollection.Event e )
			{
				synchronized( collAtomSeries ) {
					collAtomSeries.clear();	// re-triggers schnucki phonics when getRealizedAtoms is called
					frozenAtom = null;
					recalcStartStop( e.getSource() );
				}
				getMap().dispatchOwnerModification( e.getSource(), OWNER_ATOM_COLL, e.getCollection() );
			}
			
			// when atom changes
			public void sessionObjectMapChanged( SessionCollection.Event e )
			{
				if( e.setContainsAny( atomTimeKeys )) {
//					System.err.println( "Molecule("+getName()+") : atom listener : sessionObjectMapChanged time keys" );
					recalcStartStop( e.getSource() );
				}
			
				// dispatching is performed inside recalcStartStop !
//				getMap().dispatchOwnerModification( this, OWNER_PROB_INTERIEUR, e.getCollection() );
			}

			// i.e. atom renamed, it's probs changed
			public void sessionObjectChanged( SessionCollection.Event e )
			{
				if( e.getModificationType() == Atom.OWNER_PROB_INTERIEUR ) {
					// the modified probs; e.getCollection() are the atoms!!
					final java.util.List	coll			= (java.util.List) e.getModificationParam();
					boolean					timeAffected	= false;
					for( int i = 0; i < coll.size(); i++ ) {
						if( ((Probability) coll.get( i )).getName().equals( Atom.PROB_TIME )) {
//							System.err.println( "yo its time!" );
							timeAffected = true;
							break;
						}
					}
					if( timeAffected ) {
						recalcStartStop( e.getSource() );
					}
				}
				getMap().dispatchOwnerModification( e.getSource(), OWNER_ATOM_INTERIEUR, e.getCollection() );
			}
		});
		
		getMap().putContext( this, MAP_KEY_Y, new MapManager.Context( 0,
			MapManager.Context.TYPE_DOUBLE, null, null, null, new Double( 0.0 )));
		getMap().putContext( this, MAP_KEY_HEIGHT, new MapManager.Context( 0,
			MapManager.Context.TYPE_DOUBLE, null, null, null, new Double( 1.0 )));
		getMap().putContext( this, MAP_KEY_FREEZE, new MapManager.Context( MapManager.Context.FLAG_OBSERVER_DISPLAY,
			MapManager.Context.TYPE_BOOLEAN, null, null, null, new Boolean( false )));
			
//		getMap().putValue( null, MAP_KEY_FREEZE, new Boolean( false ));
		atoms.setName( XML_VALUE_ATOMS );
	}
	
//	public void addAtom( Object source, Atom a )
//	{
//		Probability prob = (Probability) a.probabilities.findByName( Atom.PROB_TIME );
//		atoms.add( source, a );
//		if( prob != null ) {
//			double audioStop = prob.getMax() + a.getFileLength();
//			if( atoms.size() > 1 ) {
//				if( (prob.getMin() < start) || (audioStop > stop) ) {
//					this.start	= Math.min( this.start, prob.getMin() );
//					this.stop	= Math.max( this.stop, audioStop );
//					if( source != null ) getMap().dispatchOwnerModification( source, OWNER_SPAN, null );
//				}
//			} else {
//				this.start	= prob.getMin();
//				this.stop	= audioStop;
//				if( source != null ) getMap().dispatchOwnerModification( source, OWNER_SPAN, null );
//			}
//		}
//		collAtomSeries.clear();	// re-triggers schnucki phonics when getRealizedAtoms is called
//		frozenAtom = null;
//	}
	
	public Atom iterateAtomSeries()
	{
		synchronized( collAtomSeries ) {
			if( freeze && (frozenAtom != null) ) return frozenAtom;
		
			if( collAtomSeries.isEmpty() ) {
				if( atoms.isEmpty() ) return null;
				collAtomSeries.addAll( atoms.getAll() );
			}
			final int	idx = rnd.nextInt( collAtomSeries.size() );
			
			frozenAtom = (Atom) collAtomSeries.remove( idx );
			
			return frozenAtom;
		}
	}
	
//	public void addAtoms( Object source, java.util.List newAtoms )
//	{
//		Atom atom;
//	
//		for( int i = 0; i < newAtoms.size(); i++ ) {
//			atom	= (Atom) newAtoms.get( i );
//			addAtom( source, atom );
//		}
//	}
//	
//	public void removeAtoms( Object source, java.util.List atoms )
//	{
//		Atom atom;
//	
//		for( int i = 0; i < atoms.size(); i++ ) {
//			atom	= (Atom) atoms.get( i );
//			removeAtom( source, atom );
//		}
//	}
//	
//	public void removeAtom( Object source, Atom a )
//	{
//		Probability prob;
//		
//		prob = (Probability) a.probabilities.findByName( Atom.PROB_TIME );
//		atoms.remove( source, a );
//		if( prob != null ) {
//			recalcStartStop( source );
//		}
//	}
	
	private void recalcStartStop( Object source )
	{
		final double	oldStart	= start;
		final double	oldStop		= stop;
		Probability		prob;
		Atom			a;

		start						= Double.POSITIVE_INFINITY;
		stop						= 0.0;
					
		for( int i = 0; i < atoms.size(); i++ ) {
			a		= (Atom) atoms.get( i );
			prob	= (Probability) a.probabilities.findByName( Atom.PROB_TIME );
			if( prob != null ) {
				start	= Math.min( start, prob.getMin() );
				stop	= Math.max( stop, prob.getMax() + a.getFileLength() );
//System.err.println( a.getName() + " : prob time has min "+prob.getMin()+"; max "+prob.getMax() );
//System.err.println( getName() + " : now start is "+start +"; stop is "+stop );
			}
		}
		if( start == Double.POSITIVE_INFINITY ) start = 0.0;
		
//System.err.println( getName() + " : finally start is "+start +"; stop is "+stop );
		if( ((start != oldStart) || (stop != oldStop)) && (source != null) ) {
			getMap().dispatchOwnerModification( source, OWNER_SPAN, null );
		}
	}

	public void moveVertical( Object source, double delta, double min, double max,
							  CompoundEdit ce, LockManager lm, int doors )
	{
		if( delta > 0 ) {
			setY( source, Math.min( max - getHeight(), getY() + delta ), ce, lm, doors );
		} else {
			setY( source, Math.max( min, getY() + delta ), ce, lm, doors );
		}
	}

	// note the name is the same and should
	// thus be changed
	public Molecule duplicate()
	{
		Molecule result = new Molecule();
		result.setName( this.getName() );
		result.getMap().cloneMap( this.getMap() );
		
		for( int i = 0; i < this.atoms.size(); i++ ) {
			result.atoms.add( this, ((Atom) this.atoms.get( i )).duplicate() );
		}
		
		return result;
	}

//	public void move( Object source, String probName, double delta, double min, double max )
//	{
////		final boolean	isTime	= probName.equals( Atom.PROB_TIME );
//		final boolean	isVert	= probName.equals( VERTICAL );
//		Atom			a;
//		Probability		prob;
//		
//		if( isVert ) {
//			if( delta > 0 ) {
//				setY( source, Math.min( max - getHeight(), getY() + delta ));
//			} else {
//				setY( source, Math.max( min, getY() + delta ));
//			}
//		} else {
//			for( int i = 0; i <  atoms.size(); i++ ) {
//				a		= (Atom) atoms.get( i );
//				prob	= (Probability) a.probabilities.findByName( probName );
//				if( prob != null ) {
//					prob.move( source, delta, min, max );
//				}
//			}
//			
////			if( isTime ) recalcStartStop( source );
//		}
//	}

	public void shiftVerticalStart( Object source, double delta, double min, double max,
									CompoundEdit ce, LockManager lm, int doors )
	{
		if( delta > 0 ) {
			delta = Math.min( delta, getHeight() );
			setY( source, getY() + delta, ce, lm, doors );
			setHeight( source, getHeight() - delta, ce, lm, doors );
		} else {
			delta = Math.max( delta, -getY() + min );
			setY( source, getY() + delta, ce, lm, doors  );
			setHeight( source, getHeight() - delta, ce, lm, doors );
		}
	}
	
//	public void shiftStart( Object source, String probName, double delta, double min, double max )
//	{
////		final boolean	isTime	= probName.equals( Atom.PROB_TIME );
//		final boolean	isVert	= probName.equals( VERTICAL );
//		Atom			a;
//		Probability		prob;
//		
//		if( isVert ) {
//			if( delta > 0 ) {
//				delta = Math.min( delta, getHeight() );
//				setY( source, getY() + delta );
//				setHeight( source, getHeight() - delta );
//			} else {
//				delta = Math.max( delta, -getY() + min );
//				setY( source, getY() + delta );
//				setHeight( source, getHeight() - delta );
//			}
//		} else {
//			for( int i = 0; i <  atoms.size(); i++ ) {
//				a		= (Atom) atoms.get( i );
//				prob	= (Probability) a.probabilities.findByName( probName );
//				if( prob != null ) {
//					prob.shiftStart( source, delta, min, max );
//				}
//			}
//			
////			if( isTime ) recalcStartStop( source );
//		}
//	}

	public void shiftVerticalStop( Object source, double delta, double min, double max,
								   CompoundEdit ce, LockManager lm, int doors )
	{
		if( delta > 0 ) {
			delta = Math.min( delta, max - getHeight() - getY() );
			setHeight( source, getHeight() + delta, ce, lm, doors );
		} else {
			delta = Math.max( delta, -getHeight() );
			setHeight( source, getHeight() + delta, ce, lm, doors );
		}
	}
	
//	public void shiftStop( Object source, String probName, double delta, double min, double max )
//	{
////		final boolean	isTime	= probName.equals( Atom.PROB_TIME );
//		final boolean	isVert	= probName.equals( VERTICAL );
//		Atom			a;
//		Probability		prob;
//		
//		if( isVert ) {
//			if( delta > 0 ) {
//				delta = Math.min( delta, max - getHeight() - getY() );
//				setHeight( source, getHeight() + delta );
//			} else {
//				delta = Math.max( delta, -getHeight() );
//				setHeight( source, getHeight() + delta );
//			}
//		} else {
//			for( int i = 0; i <  atoms.size(); i++ ) {
//				a		= (Atom) atoms.get( i );
//				prob	= (Probability) a.probabilities.findByName( probName );
//				if( prob != null ) {
//					prob.shiftStop( source, delta, min, max );
//				}
//			}
//			
////			if( isTime ) recalcStartStop( source );
//		}
//	}

	public int getRadiation( int ch )
	{
		return radiation[ ch ];
	}
	
	public int setRadiation( int ch, int nuRaad )
	{
		final int owldRaad	= radiation[ ch ];
		radiation[ ch ]		= nuRaad;
		
		return owldRaad;
	}
	
	public void clear( Object source )
	{
		atoms.clear( source );
		start	= 0.0;
		stop	= 0.0;
	}

	public Class getDefaultEditor()
	{
		return null;
	}
	
	public double getStart()
	{
		return start;
	}
	
	public double getStop()
	{
		return stop;
	}
	
	public double getLength()
	{
		return( stop - start );
	}

//	public void setStart( Object source, double start )
//	{
//		this.start	= start;
//	}
//	
//	public void setStop( Object source, double stop )
//	{
//		this.stop	= stop;
//	}
	
	public double getY()
	{
		final Number num = (Number) getMap().getValue( MAP_KEY_Y );
		if( num != null ) {
			return num.doubleValue();
		} else {
			return 0.0;
		}
	}
	
	public double getHeight()
	{
		final Number num = (Number) getMap().getValue( MAP_KEY_HEIGHT );
		if( num != null ) {
			return num.doubleValue();
		} else {
			return( 1.0 - getY() );
		}
	}

	public void setY( Object source, double y, CompoundEdit ce, LockManager lm, int doors )
	{
		ce.addEdit( new EditPutMapValue( source, lm, doors, getMap(), MAP_KEY_Y, new Double( y )));
	}
	
	public void setY( Object source, double y )
	{
		getMap().putValue( this, MAP_KEY_Y, new Double( y ));
	}
	
	public void setHeight( Object source, double height, CompoundEdit ce, LockManager lm, int doors )
	{
		ce.addEdit( new EditPutMapValue( source, lm, doors, getMap(), MAP_KEY_HEIGHT, new Double( height )));
	}

	public void setHeight( Object source, double height )
	{
		getMap().putValue( this, MAP_KEY_HEIGHT, new Double( height ));
	}
	
	public void debugDump( int indent )
	{
		super.debugDump( indent );
		printIndented( indent, "start = "+start+"; stop = "+stop );
		printIndented( indent, "--- atoms :" );
		atoms.debugDump( indent + 1 );
	}
	
// ---------------- MapManager.Listener interface ---------------- 

	public void mapChanged( MapManager.Event e )
	{
		super.mapChanged( e );
		
		final Object source = e.getSource();
		
		if( source == this ) return;
		
		final Set	keySet		= e.getPropertyNames();
		Object		val;

		if( keySet.contains( MAP_KEY_FREEZE )) {
			val		= e.getManager().getValue( MAP_KEY_FREEZE );
			if( val != null ) {
				freeze	= ((Boolean) val).booleanValue();
			}
		}
//		if( keySet.contains( MAP_KEY_STOP )) {
//			val		= e.getManager().getValue( MAP_KEY_STOP );
//			if( val != null ) {
//				afStop	= ((Number) val).doubleValue();
//			}
//		}
	}

// ---------------- XMLRepresentation interface ---------------- 

	public void toXML( org.w3c.dom.Document domDoc, Element node, Map options )
	throws IOException
	{
		super.toXML( domDoc, node, options );

		Element				childElement, child2;
		NodeList			nl;
		SessionObject		so;

		// atom collection
		childElement = (Element) node.appendChild( domDoc.createElement( XML_ELEM_COLL ));
		childElement.setAttribute( XML_ATTR_NAME, XML_VALUE_ATOMS );
		for( int i = 0; i < atoms.size(); i++ ) {
			so		= atoms.get( i );
			child2	= (Element) childElement.appendChild( domDoc.createElement( XML_ELEM_OBJECT ));
			if( so instanceof XMLRepresentation ) {
				((XMLRepresentation) so).toXML( domDoc, child2, options );
			}
		}
	}

	public void fromXML( org.w3c.dom.Document domDoc, Element node, Map options )
	throws IOException
	{
		super.fromXML( domDoc, node, options );
	
		NodeList			nl;
		Element				elem, elem2;
		String				key, val;
		SessionObject		so;
		Object				o;
		final ArrayList		soList		= new ArrayList();
		final NodeList		rootNL		= node.getChildNodes();

		atoms.pauseDispatcher();
		try {
			clear( null );
			
			for( int k = 0; k < rootNL.getLength(); k++ ) {
				if( !(rootNL.item( k ) instanceof Element) ) continue;
				
				elem	= (Element) rootNL.item( k );
				val		= elem.getTagName();

				// zero or one "map" element
				if( val.equals( XML_ELEM_MAP )) {
					// nothing to do, super.fromXML() dealt with this already!

				// zero or more "object" elements
				} else if( val.equals( XML_ELEM_OBJECT )) {
					System.err.println( "Warning: unknown session object type: '"+val+"'" );
					
				} else if( val.equals( XML_ELEM_COLL )) {
					val		= elem.getAttribute( XML_ATTR_NAME );

					if( val.equals( XML_VALUE_ATOMS )) {
						soList.clear();
						nl = elem.getChildNodes();
						for( int m = 0; m < nl.getLength(); m++ ) {
							elem2	= (Element) nl.item( m );
							val		= elem2.getTagName();
							if( !val.equals( XML_ELEM_OBJECT )) continue;

							if( elem2.hasAttribute( XML_ATTR_CLASS )) {
								val	= elem2.getAttribute( XML_ATTR_CLASS );
							} else {	// #IMPLIED
								val = "de.sciss.inertia.session.Atom";
							}
							so = (SessionObject) Class.forName( val ).newInstance();
							if( so instanceof XMLRepresentation ) {
								((XMLRepresentation) so).fromXML( domDoc, elem2, options );
							}
							atoms.getMap().copyContexts( null, MapManager.Context.FLAG_DYNAMIC,
															 MapManager.Context.NONE_EXCLUSIVE, so.getMap() );
							soList.add( so );
						}
						atoms.addAll( this, soList );
//						recalcStartStop( null );

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
		finally {
			atoms.resumeDispatcher();
		}
	}

// ----------------------- internal classes -----------------------

	private static class StartComparator
	implements Comparator
	{
		public int compare( Object o1, Object o2 )
		{
			final double d1, d2;
		
			if( o1 instanceof Molecule ) {
				if( o2 instanceof Molecule ) {
					d1 = ((Molecule) o1).start;
					d2 = ((Molecule) o2).start;
				} else if( o2 instanceof Number ) {
					d1 = ((Molecule) o1).start;
					d2 = ((Number) o2).doubleValue();
				} else throw new ClassCastException();
			} else if( o1 instanceof Number ) {
				if( o2 instanceof Molecule ) {
					d1 = ((Number) o1).doubleValue();
					d2 = ((Molecule) o2).start;
				} else if( o2 instanceof Number ) {
					d1 = ((Number) o1).doubleValue();
					d2 = ((Number) o2).doubleValue();
				} else throw new ClassCastException();
			} else throw new ClassCastException();
			
			if( d1 < d2 ) return -1;
			if( d1 > d2 ) return 1;
			return 0;
		}
				   
		public boolean equals( Object o )
		{
			return( (o != null) && (o instanceof StartComparator) );
		}
	}

	private static class StopComparator
	implements Comparator
	{
		public int compare( Object o1, Object o2 )
		{
			final double d1, d2;
		
			if( o1 instanceof Molecule ) {
				if( o2 instanceof Molecule ) {
					d1 = ((Molecule) o1).stop;
					d2 = ((Molecule) o2).stop;
				} else if( o2 instanceof Number ) {
					d1 = ((Molecule) o1).stop;
					d2 = ((Number) o2).doubleValue();
				} else throw new ClassCastException();
			} else if( o1 instanceof Number ) {
				if( o2 instanceof Molecule ) {
					d1 = ((Number) o1).doubleValue();
					d2 = ((Molecule) o2).stop;
				} else if( o2 instanceof Number ) {
					d1 = ((Number) o1).doubleValue();
					d2 = ((Number) o2).doubleValue();
				} else throw new ClassCastException();
			} else throw new ClassCastException();
			
			if( d1 < d2 ) return -1;
			if( d1 > d2 ) return 1;
			return 0;
		}
				   
		public boolean equals( Object o )
		{
			return( (o != null) && (o instanceof StopComparator) );
		}
	}
}