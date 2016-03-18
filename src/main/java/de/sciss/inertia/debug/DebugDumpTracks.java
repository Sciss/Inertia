//
//  DebugDumpTracks.java
//  Inertia
//
//  Created by Admin on 16.08.05.
//  Copyright 2005 __MyCompanyName__. All rights reserved.
//

package de.sciss.inertia.debug;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import javax.swing.*;

import de.sciss.inertia.session.*;

import de.sciss.app.*;
import de.sciss.util.*;

public class DebugDumpTracks
extends AbstractAction
{
	public DebugDumpTracks()
	{
		super( "Dump selected tracks" );
	}
	
	public void actionPerformed( ActionEvent e )
	{
		Session doc = (Session) AbstractApplication.getApplication().getDocumentHandler().getActiveDocument();
		if( doc == null ) return;
		
		System.err.println( "------------ dump of selected tracks -----------" );

		SessionObject so;

		for( int i = 0; i < doc.selectedTracks.size(); i++ ) {
			so = doc.selectedTracks.get( i );
			if( so instanceof AbstractSessionObject ) {
				((AbstractSessionObject) so).debugDump( 1 );
			}
			System.err.println( "...verifying..." );
			((Track) so).verifyTimeSorting();
		}
	}
}
