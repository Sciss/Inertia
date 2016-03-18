/*
 *  RealtimeContext.java
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
 *		07-Aug-05	copied from de.sciss.eisenkraut.realtime.RealtimeContext
 */

package de.sciss.inertia.realtime;

import java.util.*;

// INERTIA
//import de.sciss.eisenkraut.*;
//import de.sciss.eisenkraut.util.*;

import de.sciss.io.Span;

/**
 *  Analogon to RenderContext
 *  for the realtime engine
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.5, 02-Aug-05
 *
 *  @todo	users should make use of
 *			set/getSourceBlockSize() !
 */
public class RealtimeContext
//extends PlugInContext
{
	private final Span				time;
	private final int				sourceRate;
	private final java.util.List	tracks;
	private final MultiTransport	transport;
	
	private final HashMap			options			= new HashMap();
	private final HashSet			modifiedOptions = new HashSet();

	/**
	 *  Constructs a new RealtimeContext.
	 *
	 *  @param  host				the object responsible for hosting
	 *								the realtime process
	 *  @param  collReceivers		the receivers involved in the realtime performance
	 *  @param  collTransmitters	the transmitters involved in the realtime performance
	 *	@param	time				the realtime time span which is usually
	 *								(0 ... timeline-length)
	 *  @param  sourceRate			the source sense data rate
	 */
	public RealtimeContext( MultiTransport transport, java.util.List tracks, Span time, int sourceRate )
	{
		this.transport		= transport;
		this.time			= time;
		this.sourceRate		= sourceRate;
		this.tracks			= tracks;
	}

	/**
	 */
	public MultiTransport getTransport()
	{
		return transport;
	}

	/**
	 */
	public java.util.List getTracks()
	{
		return tracks;
	}

	/**
	 *  Replaces a value for an option
	 *  (or create a new option if no
	 *  value was previously set). The
	 *  option is added to the list of
	 *  modifications, see getModifiedOptions().
	 *
	 *	@param	key		key of the option such as KEY_PREFBLOCKSIZE
	 *	@param	value	corresponding value. Hosts and plug-ins
	 *					should "know" what kind of key required what
	 *					kind of value class
	 */
	public void setOption( Object key, Object value )
	{
		options.put( key, value );
		modifiedOptions.add( key );
	}
	
	/**
	 *  Performs setOption() on a series
	 *  of key/value pairs.
	 *
	 *	@param	map		a map whose key/value pairs
	 *					are copied to the context options and
	 *					appear in the modified options list
	 */
	public void setOptions( Map map )
	{
		options.putAll( map );
		modifiedOptions.addAll( map.keySet() );
	}
	
	/**
	 *  Queries the value of an options.
	 *
	 *	@return		the value corresponding to the key
	 *				or null if the option wasn't set.
	 */
	public Object getOption( Object key )
	{
		return options.get( key );
	}
	
	/**
	 *  Returns a set of all options modified
	 *  since last calling this method. Calling
	 *  this method twice in succession will
	 *  result in an empty set. All options
	 *  set using setOption() after calling
	 *  getModifiedOptions() will be present
	 *  at the next invocation of this method.
	 *
	 *	@return	a set of keys which were modified
	 *			since the last invocation of this method
	 */
	public java.util.Set getModifiedOptions()
	{
		java.util.Set result = new HashSet( modifiedOptions );
		modifiedOptions.clear();
	
		return result;
	}

	/**
	 *  Returns the time span to render
	 *
	 *	@return	the rendering time span as passed to the constructor
	 */
	public Span getTimeSpan()
	{
		return time;
	}

	/**
	 *  Returns the source sense data rate
	 *
	 *	@return	the source rate (in hertz) as passed to the constructor
	 */
	public int getSourceRate()
	{
		return sourceRate;
	}
}