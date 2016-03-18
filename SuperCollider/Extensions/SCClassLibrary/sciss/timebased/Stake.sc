/**
 *	(C)opyright 2006 Hanns Holger Rutz. All rights reserved.
 *	Distributed under the GNU General Public License (GPL).
 *
 *	Class dependancies: Span
 *
 *	A SuperCollider implementation of the java class de.sciss.timebased.BasicStake
 *
 *	Class dependancies: Span, Trail
 *
 *	@version	0.1, 31-Mar-06
 *	@author		Hanns Holger Rutz
 */
Stake {
	var span;
	var trail	= nil;

	*new { arg span;
		^super.new.prInitStake( span );
	}

	prInitStake { arg argSpan;
		span	= argSpan;
	}

	getSpan {
		^span;
	}
	
	dispose {
		trail	= nil;
	}

	setTrail { arg newTrail;
		trail	= newTrail;
	}

	// abstract
	duplicate {
		this.subclassResponsibility( thisMethod );
	}

	// abstract
	replaceStart { arg newStart;
		this.subclassResponsibility( thisMethod );
	}

	// abstract
	replaceStop { arg newStop;
		this.subclassResponsibility( thisMethod );
	}

	// abstract
	shiftVirtual { arg delta;
		this.subclassResponsibility( thisMethod );
	}

// ---------------- TreeNode interface ---------------- 

//	public TreeNode getChildAt( int childIndex )
//	{
//		return null;
//	}
//	
//	public int getChildCount()
//	{
//		return 0;
//	}
//	
//	public TreeNode getParent()
//	{
//		return trail;
//	}
//	
//	public int getIndex( TreeNode node )
//	{
//		return -1;
//	}
//	
//	public boolean getAllowsChildren()
//	{
//		return false;
//	}
//	
//	public boolean isLeaf()
//	{
//		return true;
//	}
//	
//	public Enumeration children()
//	{
//		return null;
//	}
}
