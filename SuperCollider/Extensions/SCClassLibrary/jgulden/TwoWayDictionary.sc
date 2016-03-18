
/* 
 * SuperCollider3 source file "TwoWayDictionary.sc" 
 * Licensed under the GNU General Public License (GPL).
 */

// --- class TwoWayDictionary -------------------------------------------------
// 
TwoWayDictionary : Dictionary  {

    // --- attributes

    var reverse; // type  Dictionary


    // --- add(association) : void --------------------------------------------
    //     
    add { arg association; // type Association         
        var rev;
        
        super.add(association);
        if ( reverse == nil, {
            reverse = Dictionary.new;
        });
        rev = Association.new(association.value, association.key);
        reverse.add(rev);
    } // end add        


    // --- atValue(value) : Object --------------------------------------------
    //      
    atValue { arg value; // type Object         
        ^reverse.at(value);
    } // end atValue        


} // end TwoWayDictionary
