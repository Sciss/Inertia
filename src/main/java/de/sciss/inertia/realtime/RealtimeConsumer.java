/*
 *  RealtimeConsumer.java
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
 *		07-Aug-05	created from de.sciss.eisenkraut.realtime.RealtimeConsumer
 */

package de.sciss.inertia.realtime;

/**
 *	An object implements the RealtimeConsumer
 *	interface to receiver stream based data
 *	such as trajectory or sense or to be notified
 *	in equal intervals during transport playback.
 *	<p>
 *	The consumer registers with the transport when
 *	it wants to start to receive information. The
 *	transport will query the consumer's profile
 *	using <code>createRequest</code> and ask
 *	the realtime producer to integrate this new
 *	profile. Depending on the settings of the profile,
 *	some of the other tick and block methods of the
 *	consumer get called.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.67, 01-Sep-04
 *
 */
public interface RealtimeConsumer
{
	/**
	 *  Requests a new RealtimeInfo from the Consumer,
	 *  specifying the preferred data and notification
	 *  rate as well as the required data streams.
	 *  Note that as opposed to the rendering analogies,
	 *  since there can be any number of consumers in the
	 *  realtime process, not just one, we do not provide
	 *  a RealtimeProducer. Source object here to deal with the
	 *  stream requests. Instead the requests are filled out
	 *  in the returned RealtimeConsumerRequest and will be
	 *  analysed by the RealtimeHost and/or RealtimeProducer.
	 *  
	 *  @param  context		the context of the realtime performance,
	 *						including the transmitters, receivers,
	 *						data rate and buffer size.
	 *  @return				a newly created and adjusted
	 *						RealtimeConsumerRequest. the consumer is
	 *						filled in by the constructor, as well as
	 *						the number of requests. The consumer should
	 *						take care of specifying the rates and setting
	 *						appropriate stream requests to 'true'.
	 *
	 *	@synchronization	This is always called from the event thread.
	 */
	public RealtimeConsumerRequest createRequest( RealtimeContext context );

	/**
	 *  Invoked by the RealtimeHost, when a new notification interval
	 *  (specified by notifyStep by the consumer's info) is reached.
	 *  <p>
	 *  Beware: unless we define a synchronization method, there's a
	 *  small chance that the buffer contents change while the consumer
	 *  is reading them. This can occur if the producer if the CPU load
	 *  is very high and the consumer misses the (not provided) deadline.
	 *  However this should be acceptable for the realtime situation and
	 *  shouldn't cause any harm.
	 *
	 *  @param  context		the context (which is the same as in the
	 *						createInfo() call).
	 *  @param  currentPos  abs time in senserate frames
	 *
	 *	@synchronization	This is called from the RealtimeHost's thread, e.g. the transport
	 *						thread. Any non-thread-safe swing methods should thus be deferred.
	 */
//	public void realtimeTick( RealtimeContext context, RealtimeProducer.Source source, long currentPos );
	public void realtimeTick( RealtimeContext context, int which, long currentPos );

	/**
	 *  Invoked by the RealtimeHost, when a timeline movement is made
	 *  manually by dragging the timeline and transport is stopped.
	 *  This is only called
	 *  when the consumer's info has notifyOffhand set to 'true'.
	 *
	 *  @param  context		the context (which is the same as in the
	 *						createInfo() call).
	 *  @param  source		the object containing all the stream data.
	 *						the consumer should take the momentary
	 *						values out of the trajOffhand and senseOffhand
	 *						fields which correspond to currentPos.
	 *  @param  currentPos  abs time in senserate frames
	 *
	 *	@synchronization	This is called from the event thread. 
	 */
//	public void offhandTick( RealtimeContext context, RealtimeProducer.Source source, long currentPos );
//	public void offhandTick( RealtimeContext context, int which, long currentPos );

	/**
	 *  Invoked by the RealtimeHost, when a now block of stream data
	 *  was generated and the consumer's info has notifyBlock set to
	 *  'true'.
	 *
	 *  @param  context		the context (which is the same as in the
	 *						createInfo() call).
	 *  @param  source		the object containing all the stream data.
	 *  @param  even		true if the new data is in the first half,
	 *						false if in the second half of the buffers.
	 *
	 *	@synchronization	This is called from the event thread. 
	 */
//	public void realtimeBlock( RealtimeContext context, RealtimeProducer.Source source, boolean even );
}