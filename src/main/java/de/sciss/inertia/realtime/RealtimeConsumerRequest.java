/*
 *  RealtimeConsumerRequest.java
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
 *		07-Aug-05	copied from de.sciss.eisenkraut.realtime.RealtimeConsumerRequest
 */
 
package de.sciss.inertia.realtime;

//import de.sciss.meloncillo.plugin.*;

/**
 *  A stream request satisfying a
 *  single realtime consumer.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.67, 01-Sep-04
 */
public class RealtimeConsumerRequest
//extends StreamRequest
{
	/**
	 *  To simplify organisation of the
	 *  realtime performance, the consumer
	 *  is referenced here. 
	 */
	public final RealtimeConsumer	consumer;
	/**
	 *  The frameStep
	 *  specifies the rate at which streaming data
	 *  is to be consumed. I.e., if the realtime
	 *  rate is say 1000 Hz, a frameStep of 1 means
	 *  the realtime producer should provide stream
	 *  data for every sample in a block, while
	 *  a frameStep of say 4 means the realtime producer
	 *  is allowed to generate data only every four
	 *  samples, i.e. at buffer indices 0, 4, 8 etc.
	 *  The producer will accumulate knowledge about
	 *  all registered consumers and produce as little
	 *  data as possible. Thus, if two consumers are
	 *  registered, one requesting a frameStep of 2,
	 *  the other one requesting 8, the producer will
	 *  produce data for every second sample. For
	 *  this mechanism to work it is required that the
	 *  frameStep be a power of two, minium being 1,
	 *  maximum being half of the source's buffer size.
	 *  This again requires the source's buffer size
	 *  to be a power of 2 as well.
	 */
	public int				frameStep;
	/**
	 *  The cosumer may wish to not be informed
	 *  about every production (frameStep). This
	 *  rate (again a power of 2) specifies when
	 *  a consumer's realtimeTick() method is
	 *  invoked. This value is ingored if notifyTicks
	 *  is false.
	 */
	public int				notifyTickStep;
	/**
	 *  States whether the consumer wishes to be
	 *  informed about the production process on
	 *  a blocking basis. (defaults to false). If
	 *  set to true, whenever the production of
	 *  a block of data is finished, the consumer's
	 *  notifyBlock() method will be called.
	 */
	public boolean			notifyBlocks	= false;
	/**
	 *  States whether the consumer wishes to be
	 *  informed about the production process on
	 *  a tick basis. (defaults to false). If
	 *  set to true, the notifyTick() method is
	 *  invoked at a rate given by notifyTickStep.
	 *  notifyBlock() method will be called.
	 */
	public boolean			notifyTicks		= false;
	/**
	 *  States whether the consumer wishes to be
	 *  informed about offhand movements on the 
	 *  timeline. These movements are reported whenever
	 *  the transport is NOT playing and the user
	 *  drags the timeline (or the timeline is positioned
	 *  as a side effect, e.g. when a time span is
	 *  removed from the session). Thus, all consumers
	 *  only active when transport is playing, should
	 *  leave this field to false (default), while
	 *  others that deal with GUI presentation should
	 *  set it to true. If true, movements are
	 *  reported through the consumer's offhandTick()
	 *  method.
	 */
	public boolean			notifyOffhand	= false;

	/**
	 *  This field is maintained by the transport.
	 *  When the realtime context needs to be reconfigured
	 *  during playback, all consumers are made
	 *  inactive for a short moment, until the producer
	 *  has dealt with their new requests. When the
	 *  reconfiguration is finished, they are made
	 *  active again.
	 */
	protected boolean		active			= false;
	
	/**
	 *  Creates a new RealtimeConsumerRequest. Thereby
	 *  the requests are all set to false and
	 *  frameStep and notifyTickStep are set to context.getSourceBlockSize() / 2.
	 *  active and notifyBlocks are false by default.
	 *
	 *	@param	massa	the consumer who wishes the request to be satisfied
	 *	@param	context	the current realtime context
	 */
	public RealtimeConsumerRequest( RealtimeConsumer massa, RealtimeContext context )
	{
		consumer		= massa;
//		frameStep		= context.getSourceBlockSize() >> 1;
		frameStep		= 0x10000;
		notifyTickStep  = frameStep;
	}
	
	/**
	 *  Calculates a valid frameStep from a
	 *  RenderContext and a preferred data rate in Hz,
	 *  such that 1 <= frameStep <= bufSizeH.
	 *
	 *	@param	context			the realtime context used as calculation basis
	 *	@param	preferredRate	reference frame rate. the method tries to find
	 *							a valid frameStep that is closest to this rate.
	 *	@return	the frameStep ready to be used in the request
	 */
	public static int approximateStep( RealtimeContext context, int preferredRate )
	{
		int optimum, below, above, bufSizeH;
		
//		bufSizeH	= context.getSourceBlockSize() >> 1;
//		optimum		= Math.max( 1, Math.min( bufSizeH,
//						(context.getSourceRate() + (preferredRate >> 1)) / preferredRate ));
		optimum		= Math.max( 1, (context.getSourceRate() + (preferredRate >> 1)) / preferredRate );
//		for( above = 2; above < optimum && above < bufSizeH; above <<= 1 ) ;
		for( above = 2; above < optimum; above <<= 1 ) ;
		below		= above >> 1;
		
		if( (double) above / optimum <= (double) optimum / below ) {
			return above;
		} else {
			return below;
		}
	}
}