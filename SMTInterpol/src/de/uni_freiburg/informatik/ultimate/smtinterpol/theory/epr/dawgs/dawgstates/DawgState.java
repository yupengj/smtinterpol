/*
 * Copyright (C) 2016-2017 Alexander Nutz (nutz@informatik.uni-freiburg.de)
 * Copyright (C) 2016-2017 University of Freiburg
 *
 * This file is part of SMTInterpol.
 *
 * SMTInterpol is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMTInterpol is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with SMTInterpol.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.dawgs.dawgstates;

/**
 * 
 * @author Alexander Nutz (nutz@informatik.uni-freiburg.de)
 *
 */
public class DawgState {
	
	final DawgState mReplacement;
	
	public DawgState() {
		mReplacement = this;
	}
	
	protected DawgState(DawgState replacement) {
		mReplacement = replacement;
	}

	@Override
	public String toString() {
		return String.format("DawgState#%d", this.hashCode() % 10000);
	}
	
	/**
	 * Returns a fresh DawgState that can be used to replace this DawgState in the flattening operation (which is used 
	 * to avoid that we further and further nest Pair-, Set-, and RenameAndReorderDawgStates)
	 * 
	 * @return
	 */
	public DawgState getFlatReplacement() { 
		return mReplacement;
	}
}