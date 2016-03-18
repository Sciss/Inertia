/*
 *  RunnableProcessing.java
 *  de.sciss.util package
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
 *		07-Aug-05	copied from de.sciss.eisenkraut.util.RunnableProcessing
 */

package de.sciss.util;

/**
 *  Interface for custom processes
 *  managed by a separate ProcessingThread host
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.61, 09-Aug-04
 */
public interface RunnableProcessing
{
	/**
	 *  Does the processing. This is called inside
	 *  a separate asynchronous thread.
	 *
	 *  @param  context		the corresponding thread. call <code>context.setProgression()</code>
	 *						to update visual progress feedback.
	 *  @param  argument	passed directly from context's constructor.
	 *  @return				<code>true</code> on success, <code>false</code> on failure.
	 *						The implementing class may which to call the 
	 *						context's <code>setException</code> method if an error occurs.
	 *
	 *  @see	ProcessingThread#setProgression( float )
	 *  @see	ProcessingThread#setException( Exception )
	 *
	 *  @synchronization	like Thread's run method, this is called inside
	 *						a custom thread
	 */
	public boolean run( ProcessingThread context, Object argument );

	/**
	 *  This gets invoked when <code>run()</code> is finished or
	 *  aborted. It's useful to place GUI related stuff
	 *  in here since this gets called inside the
	 *  Swing thread.
	 *
	 *  @param  context		the corresponding thread. in case of failure
	 *						can be used to query the exception.
	 *  @param  argument	passed directly from context's constructor.
	 *  @param  success		the return value from run().
	 *
	 *  @synchronization	this is called in the event thread
	 */
	public void finished( ProcessingThread context, Object argument, boolean success );
}