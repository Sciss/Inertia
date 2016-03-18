/*
 *  Track.java
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
import java.net.*;
import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.*;

import de.sciss.inertia.net.*;

import de.sciss.io.*;
import de.sciss.util.*;

// kind of corresponds to Track in Eisenkraut
public class Track
extends AbstractSessionObject
{
	public static final String		ATOM_NAME_PREFIX	= "A";
	public static final String		MOLECULE_NAME_PREFIX= "M";

	public final SessionCollection	molecules			= new SessionCollection();
	public final SessionCollection	selectedMolecules	= new SessionCollection();
	public final SessionCollection	selectedAtoms		= new SessionCollection();

	public static final String		MAP_KEY_ABSTRACTION	= "abstraction";

	private static final String		XML_VALUE_MOLECULES	= "molecules";

	private final java.util.List	collMolecByStart	= new ArrayList();	// sorted using Molecule.startComparator
	private final java.util.List	collMolecByStop		= new ArrayList();	// sorted using Molecule.stopComparator

	private final Object sync	= new Object();

	// radiation is a running incremental
	// counter which is used to determine which
	// molecule spans have already been calculated / processed.
	// each time the transport is started (supercolliderplayer
	// calling increaseRadiation()), the
	// counter is increased, thereby forcing all molecules
	// to appear to be virgin.
	// ETERNAL VIRGINITY, what a sick concept, dude!
	private final int[]				radiation			= new int[ Session.NUM_MOVIES ];

	private static final int		flagsMuted	= FLAGS_MUTE | FLAGS_VIRTUALMUTE;

	public Track()
	{
		super();

		molecules.addListener( new SessionCollection.Listener() {
			public void sessionCollectionChanged( SessionCollection.Event e )
			{
				java.util.List collMolecs = e.getCollection();
				switch( e.getModificationType() ) {
				case SessionCollection.Event.ACTION_ADDED:
					for( int i = 0; i < collMolecs.size(); i++ ) {
						sortAddMolecule( (Molecule) collMolecs.get( i ));
//						System.err.println( "added "+((SessionObject) collMolecs.get( i )).getName() );
					}
					break;

				case SessionCollection.Event.ACTION_REMOVED:
					synchronized( sync ) {
						collMolecByStart.removeAll( collMolecs );
						collMolecByStop.removeAll( collMolecs );
					}
					break;
				
				default:
					break;
				}
			}

			// when molec changes
			public void sessionObjectMapChanged( SessionCollection.Event e )
			{
//				System.err.println( "Track("+getName()+") : molec listener : sessionObjectMapChanged" );
//				getMap().dispatchOwnerModification( this, OWNER_PROB_INTERIEUR, e.getCollection() );
			}

			// when molec bounds change
			public void sessionObjectChanged( SessionCollection.Event e )
			{
				if( e.getModificationType() == Molecule.OWNER_SPAN ) {
//					System.err.println( "Track("+getName()+") : molec listener : sessionObjectChanged span" );
					resortMolecules( e.getCollection() );
				}
			}
		});
		
		molecules.setName( XML_VALUE_MOLECULES );
	}

//	public void addMolecule( Object source, Molecule molec )
//	{
//		synchronized( sync ) {
//			sortAddMolecule( molec );
//		}
//		molecules.add( source, molec );
//	}

	/*
	 *	the algorithm for determining the
	 *	relevant molecules seems to be working
	 *	quite well : two lists are maintained
	 *	(collMolecByStart and collMolecByStop)
	 *	which are ordered by zone start and stop
	 *	; for display the common subset of all
	 *	elements in collMolecByStart whose
	 *	start position is smaller than the visual
	 *	span's stop and of all elements in collMolecByStop whose
	 *	stop position is greater than the visual
	 *	span's start is calculated, using
	 *	Collections.binarySearch both for getting
	 *	the sublists and for getting the common elements.
	 *	Note that List.retainAll() is comparably
	 *	hell slow for determining the common elements.
	 */
	public java.util.List getMolecules( double startSec, double stopSec )
	{
		final java.util.List	collUntil, collFrom, collA, collB;
		final Comparator		comp;
		int						idx;
		Object					o;
	
		synchronized( sync ) {
			idx			= Collections.binarySearch( collMolecByStart, new Double( stopSec ), Molecule.startComparator );
			if( idx < 0 ) idx = -(idx + 1);
			collUntil	= collMolecByStart.subList( 0, idx );
			idx			= Collections.binarySearch( collMolecByStop, new Double( startSec ), Molecule.stopComparator );
			if( idx < 0 ) idx = -(idx + 1);
			collFrom	= collMolecByStop.subList( idx, collMolecByStop.size() );
			
			if( collUntil.size() < collFrom.size() ) {
				collA  = new ArrayList( collUntil );
				collB  = collFrom;
				comp   = Molecule.stopComparator;
			} else {
				collA  = new ArrayList( collFrom );
				collB  = collUntil;
				comp   = Molecule.startComparator;
			}

			for( Iterator iter = collA.iterator(); iter.hasNext(); ) {
				if( Collections.binarySearch( collB, iter.next(), comp ) < 0 ) iter.remove();
			}
		}
		return collA;
	}

//	public void moveMolecules( Object source, java.util.List collMolecs, String probName,
//							   double delta, double min, double max )
//	{
////		final boolean	isTime	= probName.equals( Atom.PROB_TIME );
//		Molecule		molec;
//
//		for( int i = 0; i < collMolecs.size(); i++ ) {
//			molec = (Molecule) collMolecs.get( i );
//			molec.move( source, probName, delta, min, max );
//		}
//		
////		if( isTime ) {
////			resortMolecules( collMolecs );
////		}
//	}

//	public void shiftMoleculesStart( Object source, java.util.List collMolecs, String probName,
//									 double delta, double min, double max )
//	{
////		final boolean	isTime	= probName.equals( Atom.PROB_TIME );
//		Molecule		molec;
//		
//		for( int i = 0; i < collMolecs.size(); i++ ) {
//			molec = (Molecule) collMolecs.get( i );
//			molec.shiftStart( source, probName, delta, min, max );
//		}
//		
////		if( isTime ) {
////			resortMolecules( collMolecs );
////		}
//	}

//	public void shiftMoleculesStop( Object source, java.util.List collMolecs, String probName,
//									double delta, double min, double max )
//	{
////		final boolean	isTime	= probName.equals( Atom.PROB_TIME );
//		Molecule		molec;
//		
//		for( int i = 0; i < collMolecs.size(); i++ ) {
//			molec = (Molecule) collMolecs.get( i );
//			molec.shiftStop( source, probName, delta, min, max );
//		}
//		
////		if( isTime ) {
////			resortMolecules( collMolecs );
////		}
//	}

	// inserts in collMolecByStart/Stop, NOT molecules
	// sync : call inside synchronized( sync ) !
	private void sortAddMolecule( Molecule molec )
	{
//System.err.println( "sortAddMolecule : "+molec.getName() );
		int	idx;

		idx		= Collections.binarySearch( collMolecByStart, molec, Molecule.startComparator );
		if( idx < 0 ) idx = -(idx + 1);
		collMolecByStart.add( idx, molec );
		idx		= Collections.binarySearch( collMolecByStop, molec, Molecule.stopComparator );
		if( idx < 0 ) idx = -(idx + 1);
		collMolecByStop.add( idx, molec );
	}
	
	// sync: synchronizes on sync
	private void resortMolecules( java.util.List collMolecs )
	{
		synchronized( sync ) {
			collMolecByStart.removeAll( collMolecs );
			collMolecByStop.removeAll( collMolecs );

			for( int i = 0; i < collMolecs.size(); i++ ) {
				sortAddMolecule( (Molecule) collMolecs.get( i ));
			}
		}
	}

//	public void removeMolecules( Object source, java.util.List collMolecs )
//	{
//		synchronized( sync ) {
//			collMolecByStart.removeAll( collMolecs );
//			collMolecByStop.removeAll( collMolecs );
//			selectedMolecules.removeAll( source, collMolecs );
//			molecules.removeAll( source, collMolecs );
//		}
//	}
//
//	public void removeMolecule( Object source, Molecule molec )
//	{
//		synchronized( sync ) {
//			collMolecByStart.remove( molec );
//			collMolecByStop.remove( molec );
//			selectedMolecules.remove( source, molec );
//			molecules.remove( source, molec );
//		}
//	}
//
//	public void addMolecules( Object source, java.util.List newMolec )
//	{
//		Molecule molec;
//	
//		for( int i = 0; i < newMolec.size(); i++ ) {
//			molec	= (Molecule) newMolec.get( i );
//			addMolecule( source, molec );
//		}
//	}
	
//	public void addAtom( Object source, Atom atom, Molecule molec )
//	{
//		synchronized( sync ) {
//			collMolecByStart.remove( molec );
//			collMolecByStop.remove( molec );
//			
//			molec.addAtom( source, atom );	// updates start / stop of molec
//			
//			sortAddMolecule( molec );
//		}
//	}

	public void increaseRadiation( int ch )
	{
		radiation[ ch ]++;
	}

	public java.util.List getRealizedAtoms( int ch, double startSec, double stopSec )
	{
		final java.util.List	collMolec	= getMolecules( startSec, stopSec );
		final java.util.List	collRaz		= new ArrayList();
		Molecule				molec;
		Atom.Realized			ra;

		// track muted
		if( (((Number) getMap().getValue( MAP_KEY_FLAGS )).intValue() & flagsMuted) != 0 ) return collRaz;
		
//System.err.println( "rad "+radiation );
//int molRad;
		for( Iterator iter = collMolec.iterator(); iter.hasNext(); ) {
			molec	= (Molecule) iter.next();
			if( (((Number) molec.getMap().getValue( MAP_KEY_FLAGS )).intValue() & flagsMuted) != 0 ) continue;
//molRad = molec.setRadiation( radiation );
//System.err.println( "  mol "+molec.getName()+" : rad "+molRad );
//			if( molRad < radiation ) {	// ok dis wan's not yet calculated
			if( molec.setRadiation( ch, radiation[ ch ]) < radiation[ ch ]) {	// ok dis wan's not yet calculated
				ra	= molec.iterateAtomSeries().realize();
				if( ra.startTime >= startSec ) {
					collRaz.add( ra );
//				} else {	// deposit 'm in abou ghraib
//					iter.remove();
				}
			} else {		// leave 'm to guantanamo white terrorism
//				iter.remove();
if( SuperColliderPlayer.RAZ_DEBUG ) System.err.println( "already realized : "+this.getName()+"->"+molec.getName()+" @ ch "+ch );
			}
		}
		
		return collRaz;
	}

	public Class getDefaultEditor()
	{
		return null;
	}
	
	public void clear( Object source )
	{
		synchronized( sync ) {
			selectedAtoms.clear( source );
			molecules.clear( source );
			selectedMolecules.clear( source );
			collMolecByStart.clear();
			collMolecByStop.clear();
		}
	}
	
	public void verifyTimeSorting()
	{
		if( (collMolecByStart.size() != collMolecByStop.size()) ||
			(collMolecByStart.size() != molecules.size()) ) {
		
			System.err.println( "List's corropted.  molecules.size() = "+ molecules.size()+
				"; collMolecByStart.size() = "+collMolecByStart.size()+
				"; collMolecByStop.size() = "+collMolecByStop.size() );
		}
	
		double lastTime = 0.0;
		Molecule molec;
	
		for( int i = 0; i < collMolecByStart.size(); i++ ) {
			molec = (Molecule) collMolecByStart.get( i );
			if( molec.getStart() < lastTime ) {
				System.err.println( "collMolecByStart corrupted. Molec "+molec.getName()+" has start time "+
					molec.getStart() + "; predecessor has " + lastTime );
			}
			lastTime = molec.getStart();
		}

		lastTime = 0.0;
		for( int i = 0; i < collMolecByStop.size(); i++ ) {
			molec = (Molecule) collMolecByStop.get( i );
			if( molec.getStop() < lastTime ) {
				System.err.println( "collMolecByStop corrupted. Molec "+molec.getName()+" has stop time "+
					molec.getStop() + "; predecessor has " + lastTime );
			}
			lastTime = molec.getStop();
		}
	}
	
	public void debugDump( int indent )
	{
		super.debugDump( indent );
		molecules.debugDump( indent + 1 );
		printIndented( indent, "--- selected molecules: " );
		for( int i = 0; i < selectedMolecules.size(); i++ ) {
			printIndented( indent + 2, selectedMolecules.get( i ).getName() + " ; " );
		}
	}
	
// ---------------- XMLRepresentation interface ---------------- 

	public void toXML( org.w3c.dom.Document domDoc, Element node, Map options )
	throws IOException
	{
		super.toXML( domDoc, node, options );

		Element				childElement, child2;
		NodeList			nl;
		SessionObject		so;

		// molecule collection
		childElement = (Element) node.appendChild( domDoc.createElement( XML_ELEM_COLL ));
		childElement.setAttribute( XML_ATTR_NAME, XML_VALUE_MOLECULES );
		for( int i = 0; i < molecules.size(); i++ ) {
			so		= molecules.get( i );
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

		try {
			selectedAtoms.pauseDispatcher();
			molecules.pauseDispatcher();
			selectedMolecules.pauseDispatcher();

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

					if( val.equals( XML_VALUE_MOLECULES )) {
						soList.clear();
						nl = elem.getChildNodes();
						for( int m = 0; m < nl.getLength(); m++ ) {
							elem2	= (Element) nl.item( m );
							val		= elem2.getTagName();
							if( !val.equals( XML_ELEM_OBJECT )) continue;

							if( elem2.hasAttribute( XML_ATTR_CLASS )) {
								val	= elem2.getAttribute( XML_ATTR_CLASS );
							} else {	// #IMPLIED
								val = "de.sciss.inertia.session.Molecule";
							}
							so = (SessionObject) Class.forName( val ).newInstance();
							if( so instanceof XMLRepresentation ) {
								((XMLRepresentation) so).fromXML( domDoc, elem2, options );
							}
							molecules.getMap().copyContexts( null, MapManager.Context.FLAG_DYNAMIC,
															 MapManager.Context.NONE_EXCLUSIVE, so.getMap() );
							soList.add( so );
						}
						molecules.addAll( this, soList );
//						collMolecByStart.addAll( soList );
//						collMolecByStop.addAll( soList );
//						Collections.sort( collMolecByStart, Molecule.startComparator );
//						Collections.sort( collMolecByStop,  Molecule.stopComparator );

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
			selectedAtoms.resumeDispatcher();
			molecules.resumeDispatcher();
			selectedMolecules.resumeDispatcher();
		}
	}
}
