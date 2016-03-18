/*
 *  AbstractSessionObject.java
 *  Eisenkraut
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
 *		07-Aug-05	copied from de.sciss.eisenkraut.session.AbstractSessionObject
 *		16-Aug-05	added debugDump()
 */

package de.sciss.inertia.session;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.*;

// INERTIA
//import de.sciss.eisenkraut.io.*;
//import de.sciss.eisenkraut.util.*;
import de.sciss.util.MapManager;
import de.sciss.util.XMLRepresentation;

import de.sciss.io.IOUtil;

/**
 */
public abstract class AbstractSessionObject
implements SessionObject, XMLRepresentation, MapManager.Listener
{
	private	String		name;
	private MapManager	map		= new MapManager( this, new HashMap() );

	protected static final String XML_ATTR_NAME			= "name";
	protected static final String XML_ATTR_CLASS		= "class";
	protected static final String XML_ELEM_OBJECT		= "object";
	protected static final String XML_ELEM_COLL			= "coll";
	protected static final String XML_ELEM_MAP			= "map";

	/**
	 */
	protected AbstractSessionObject()
	{
		init();
	}

	/**
	 */
	protected AbstractSessionObject( AbstractSessionObject orig )
	{
		init();
		map.cloneMap( orig.map );
		this.setName( orig.getName() );
	}
	
	protected void init()
	{
		map.addListener( this );
		map.putContext( null, MAP_KEY_FLAGS, new MapManager.Context( MapManager.Context.FLAG_LIST_DISPLAY,
																	 MapManager.Context.TYPE_INTEGER, null, null,
																	 null, new Integer( 0 )));
	}

	public void dispose()
	{
	}

// ---------------- SessionObject interface ---------------- 

	/**
	 *  Retrieves the property map manager of the session
	 *	object. This manager may be used to read and
	 *	write properties and register listeners.
	 *
	 *	@return	the property map manager that stores
	 *			all the properties of this session object
	 */
	public MapManager getMap()
	{
		return map;
	}

	public void setName( String newName )
	{
		name = newName;
	}
	
	public String getName()
	{
		return name;
	}
	
	public void debugDump( int indent )
	{
		printIndented( indent, getName() + " (" + getClass().getName() + ") " );
		printIndented( indent, "map manager :" );
		getMap().debugDump( indent + 1 );
	}
	
	protected void printIndented( int indent, String text )
	{
		for( int i = 0; i < indent; i++ ) {
			System.err.print( "  " );
		}
		System.err.println( text );
	}

// ---------------- MapManager.Listener interface ---------------- 

	public void mapChanged( MapManager.Event e )
	{
	}

	public void mapOwnerModified( MapManager.Event e )
	{
	}

// ---------------- XMLRepresentation interface ---------------- 

	/**
	 */
	public void toXML( Document domDoc, Element node, Map options )
	throws IOException
	{
		try {
			node.setAttribute( XML_ATTR_CLASS, getClass().getName() );
			node.setAttribute( XML_ATTR_NAME, getName() );
			getMap().toXML( domDoc, (Element) node.appendChild( domDoc.createElement( XML_ELEM_MAP )), options );
		}
		catch( DOMException e1 ) {
			throw IOUtil.map( e1 );  // rethrow exception
		}
	}

	/**
	 */
	public void fromXML( Document domDoc, Element node, Map options )
	throws IOException
	{
		NodeList	nl	= node.getChildNodes();
		int			i;
		Element		xmlChild;
		
		setName( node.getAttribute( XML_ATTR_NAME ));
		for( i = 0; i < nl.getLength(); i++ ) {
			if( !(nl.item( i ) instanceof Element )) continue;
			xmlChild = (Element) nl.item( i );
			if( xmlChild.getTagName().equals( XML_ELEM_MAP )) {
				getMap().fromXML( domDoc, xmlChild, options );
//System.err.println( "found map" );
//Set keySet = getMap().keySet( MapManager.Context.ALL_INCLUSIVE, MapManager.Context.NONE_EXCLUSIVE );
//Iterator iter = keySet.iterator();
//MapManager.Context c;
//String key;
//while( iter.hasNext() ) {
//	key = iter.next().toString();
//	c = getMap().getContext( key );
//	System.err.println( "    key = "+key+ " flags = "+c.flags );
//}
				break;	// only one 'map' allowed
			}
		}
	}
}