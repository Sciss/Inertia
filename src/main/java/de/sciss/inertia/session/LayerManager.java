//
//  LayerManager.java
//  Inertia
//
//  Created by SeaM on 30.09.05.
//  Copyright 2005 __MyCompanyName__. All rights reserved.
//

package de.sciss.inertia.session;

import de.sciss.app.*;

/**
 *	@version	05-Oct-05
 */
public class LayerManager
implements EventManager.Processor
{
	private final int		numLayers;
	private final int[]		movies;		// array index = layer, element = movie quadrant
	private final float[]	volumes;		// array index = movie quadrant
	
	private EventManager	elm	= null;

	public LayerManager( int numLayers )
	{
		this.numLayers	= numLayers;
		movies			= new int[ numLayers ];
		volumes			= new float[ numLayers ];
		
		for( int ch = 0; ch < numLayers; ch++ ) {
			movies[ ch ]	= ch;
			volumes[ ch ]	= 1.0f;
		}
	}
	
	public void dispose()
	{
		if( elm != null ) elm.dispose();
	}
	
	/**
	 *	Switches the movie/sound sources
	 *	of two layers. I.e., when movieX plays
	 *	to layerA, and movieY plays to layerB,
	 *	calling this method will make movieX
	 *	play to layerB, and movieY play to layerA.
	 *
	 *	@param	source	the object which is responsible for the switch
	 */
	public void switchLayers( Object source, int layer1, int layer2 )
	{
		if( (layer1 < 0) || (layer1 >= numLayers) || (layer2 < 0) || (layer2 >= numLayers) ) {
			System.err.println( "switchLayers : illegal layers " + layer1 +", " + layer2 );
			return;
		}
	
		final int tmp		= movies[ layer1 ];
		movies[ layer1 ]	= movies[ layer2 ];
		movies[ layer2 ]	= tmp;
		
		if( (source != null) && (elm != null) ) dispatchLayerSwitch( source, layer1, layer2 );
	}
	
	public void setFilter( Object source, int layer, String filterName )
	{
		if( (source != null) && (elm != null) ) dispatchLayerFilter( source, layer, filterName );
	}

	public void addListener( LayerManager.Listener listener )
	{
		if( elm == null ) {
			elm = new EventManager( this );
		}
		elm.addListener( listener );
	}

	public void removeListener( LayerManager.Listener listener )
	{
		elm.removeListener( listener );
	}

	public int getNumLayers()
	{
		return numLayers;
	}
	
	public int getMovieForLayer( int layer )
	{
		return movies[ layer ];
	}
	
	// @warning	index is quadrant _not_ layer
	public float getVolume( int quadrant )
	{
		return volumes[ quadrant ];
	}

	// @warning	index is quadrant _not_ layer
	public void setVolume( int quadrant, float volume )
	{
		volumes[ quadrant ] = volume;
//		int quadrant;
//		for( layer = 0; layer < movies.length; layer++ ) {
//			if( movies[ layer ] == quadrant )
	}
	
	private void dispatchLayerSwitch( Object source, int layer1, int layer2 )
	{
		elm.dispatchEvent( new LayerManager.Event( this, source, Event.LAYERS_SWITCHED, layer1, layer2, null ));
	}

	private void dispatchLayerFilter( Object source, int layer, String filterName )
	{
		elm.dispatchEvent( new LayerManager.Event( this, source, Event.LAYERS_FILTERED, layer, layer, filterName ));
	}
	
// --------------------- EventManager.Processor interface ---------------------
	
	public void processEvent( BasicEvent e )
	{
		LayerManager.Listener listener;
		LayerManager.Event lme = (LayerManager.Event) e;
		
		for( int i = 0; i < elm.countListeners(); i++ ) {
			listener = (LayerManager.Listener) elm.getListener( i );
			switch( e.getID() ) {
			case LayerManager.Event.LAYERS_SWITCHED:
				listener.layersSwitched( lme );
				break;
			case LayerManager.Event.LAYERS_FILTERED:
				listener.layersFiltered( lme );
				break;
			default:
				assert false : e.getID();
				break;
			}
		} // for( i = 0; i < this.countListeners(); i++ )
	}

// ------------------ internal classses / interfaces ------------------
	
	public interface Listener
	{
		public void layersSwitched( LayerManager.Event e );
		public void layersFiltered( LayerManager.Event e );
	}
	
	public static class Event
	extends BasicEvent
	{
		public static final int LAYERS_SWITCHED	= 0;
		public static final int LAYERS_FILTERED	= 1;
		
		private final LayerManager	layers;
		private final Object		param;
		private final int			layer1, layer2;
	
		public Event( LayerManager layers, Object source, int ID, int layer1, int layer2, Object param )
		{
			super( source, ID, System.currentTimeMillis() );
			
			this.layers			= layers;
			this.layer1			= layer1;
			this.layer2			= layer2;
			this.param			= param;
		}
		
		public LayerManager getManager()
		{
			return layers;
		}
		
		public Object getParam()
		{
			return param;
		}

		public int getFirstLayer()
		{
			return layer1;
		}
		
		public int getSecondLayer()
		{
			return layer2;
		}

		/**
		 *  Returns false always at the moment
		 */
		public boolean incorporate( BasicEvent oldEvent )
		{
			return false;
		}
	}
}
