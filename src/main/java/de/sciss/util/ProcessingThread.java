/*
 *  ProcessingThread.java
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
 *		07-Aug-05	copied from de.sciss.eisenkraut.util.ProcessingThread
 */

package de.sciss.util;

import java.awt.*;
import java.util.*;
import javax.swing.*;

// INERTIA
//import de.sciss.eisenkraut.*;
//import de.sciss.eisenkraut.gui.*;

/**
 *  A subclass of Thread that is capable of
 *  dealing with synchronization issues.
 *  It will pause all event dispatching related
 *  to specified doors which will be locked
 *  during processing. It includes helper
 *  methods for updating a progress bar and
 *  displaying messages and exceptions.
 *
 *  @author		Hanns Holger Rutz
 *  @version	0.3, 13-Jul-05
 */
public class ProcessingThread
extends Thread
implements Runnable, ProgressComponent
{
	private final RunnableProcessing	rp;
	private final ProgressComponent		pc;
// INERTIA
//	private final Main					root;
	private final Object				rpArgument;
	private final int					requiredDoors;
	private final String				name;

	private final Runnable				runProgressUpdate, runProcessFinished;
	private float						progress;
	private boolean						procAlive;
	private boolean						success;
	private Exception					exception   = null;
	private final ProcessingThread		context		= this;
	private final LockManager			lm;

	/**
	 *  Creates a new ProcessingThread and starts processing.
	 *
	 *  @param  rp				Interface whose method runProcessing() is called
	 *							inside the new thread when it's started.
	 *  @param  pc				Component responsible for displaying progress bar etc.
	 *  @param  root			the current Main instance. During processing
	 *							all menubars related to root will be disabled.
	 *  @param  doc				the current Session instance
	 *  @param  procName		Name for the thread and the process monitoring
	 *  @param  rpArgument		anything the RunnableProcessing might need. This object
	 *							is just passed to the rp.run() method
	 *  @param  requiredDoors   when rp.run() is invoked, it's guaranteed that it's
	 *							Thread holds exclusively locks for these doors in doc.bird
	 *							it's crucial that the calling thread has *no* lock on any
	 *							of doors, because this constructor returns only after
	 *							the new thread has gained access to all doors!
	 *							Event dispatching for the related classes, e.g. doc.timeline
	 *							will be paused during processing.
	 *  @synchronization		must be called in the event thread
	 */
	public ProcessingThread( final RunnableProcessing rp, final ProgressComponent pc,
							 final LockManager lm, String procName, final Object rpArgument, int requiredDoors )
	{
		super( procName );
	
		this.rp				= rp;
		this.pc				= pc;
// INERTIA
//		this.root			= root;
		this.lm				= lm;
		this.rpArgument		= rpArgument;
		this.requiredDoors  = requiredDoors;
		this.name			= procName;

		// the progress update is called
		// from the rendering thread using
		// SwingUtilities.invokeLater( Runnable t )
		// because JProgressBar.setValue() is
		// not marked as being 'threadsafe'
		runProgressUpdate = new Runnable() {
			public void run()
			{
				pc.setProgression( progress );
			}
		};
		// same for MenuBar enabling
		runProcessFinished = new Runnable() {
			public void run()
			{
// INERTIA
//				if( lm != null ) root.menuFactory.setMenuBarsEnabled( true );
				pc.finishProgression( success );
				rp.finished( context, rpArgument, success );
				if( !success && (exception != null) ) {
					pc.displayError( exception, name );
				}
			}
		};

		pc.resetProgression();
		pc.setProgressionText( name );
		this.setDaemon( true );
		synchronized( this ) {
			procAlive = true;
			start();
			try {
				this.wait();	// we will be notified when the locks have been attached!
// INERTIA
//				if( lm != null ) root.menuFactory.setMenuBarsEnabled( false );
			} catch( InterruptedException e1 ) {
				// XXX
			}
		}
	}
	
	/**
	 *  Puts the calling thread in
	 *  wait mode until the processing
	 *  is finished.
	 *
	 *  @return		<code>true</code> on success of the process,
	 *				<code>false</code> if the process failed
	 */
	public boolean sync()
	{
		while( isAlive() && procAlive ) {
			try {
				synchronized( this ) {
					this.wait();
				}
			} catch( InterruptedException e1 ) {}
		}
		return success;
	}
	
	/**
	 *  The is the main method of a thread and will
	 *  lock the requested doors; it pauses the
	 *  appropriate dispatchers (receiverCollection,
	 *  transmitterCollection, timeline), invokes
	 *  the rp's run method. when this returns, dispatchers
	 *  are restarted and doors are unlocked.
	 *
	 *  @synchronization	waitExclusive on the doors specified
	 *						in the constructor
	 */
	public void run()
	{
		try {
			if( lm != null ) lm.waitExclusive( requiredDoors );
			success = false;
			setProgression( 0.0f );

			// pause event dispatching
//			if( (requiredDoors & Session.DOOR_RCV) != 0 ) {
//				doc.receivers.pauseDispatcher();
//				doc.selectedReceivers.pauseDispatcher();
//			}
//			if( (requiredDoors & Session.DOOR_TRNS) != 0 ) {
//				doc.transmitters.pauseDispatcher();
//				doc.selectedTransmitters.pauseDispatcher();
//			}
//			if( (requiredDoors & Session.DOOR_TIME) != 0 ) {
//				doc.timeline.pauseDispatcher();
//			}
			
			// now it's safe to resume the Swing thread
			synchronized( this ) {
				this.notifyAll();
			}

			success = rp.run( context, rpArgument );
		}
		catch( Exception e1 ) {
			exception = e1;
		}
		finally {
			if( lm != null ) lm.releaseExclusive( requiredDoors );
			// resume event dispatching
//			if( (requiredDoors & Session.DOOR_RCV) != 0 ) {
//				doc.receivers.resumeDispatcher();
//				doc.selectedReceivers.resumeDispatcher();
//			}
//			if( (requiredDoors & Session.DOOR_TRNS) != 0 ) {
//				doc.transmitters.resumeDispatcher();
//				doc.selectedTransmitters.resumeDispatcher();
//			}
//			if( (requiredDoors & Session.DOOR_TIME) != 0 ) {
//				doc.timeline.resumeDispatcher();
//			}
			synchronized( this ) {
				procAlive = false;
				this.notifyAll();
			}
			SwingUtilities.invokeLater( runProcessFinished );
		}
	}
	
	/**
	 *  If the rp is capable of catching
	 *  an exception in its execution block,
	 *  it should pass it to the pt calling this
	 *  method.
	 *
	 *  @param  e   exception which was thrown in the rp's
	 *				run method. when the thread stops it
	 *				will display this error to the user.
	 */
	public void setException( Exception e )
	{
		this.exception = e;
	}

	/**
	 *  Queries the last exception thrown in the run method.
	 *
	 *  @return the most recent exception or null
	 *			if no exception was thrown
	 */
	public Exception getException()
	{
		return exception;
	}


// ------------------ ProgressComponent interface ------------------

	public Component getComponent()
	{
		return pc.getComponent();
	}

	/**
	 *  Called by the rp to update
	 *  the progress bar.
	 *
	 *  @param  p   new progression between zero and one
	 */
	public void setProgression( float p )
	{
		progress = p;
		SwingUtilities.invokeLater( runProgressUpdate );
	}
	
	public void resetProgression() {}
	public void finishProgression( boolean success ) {}
	public void setProgressionText( String text ) {}
	public void showMessage( int type, String text ) {}
	public void displayError( Exception e, String processName ) {}
}