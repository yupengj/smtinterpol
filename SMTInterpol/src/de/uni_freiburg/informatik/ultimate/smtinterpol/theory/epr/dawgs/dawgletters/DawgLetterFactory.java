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
package de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.dawgs.dawgletters;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.EprHelpers;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.EprTheorySettings;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.dawgs.DawgFactory;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.util.NestedMap2;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.util.NestedMap3;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.util.NestedMap4;

/**
 * 
 * @author Alexander Nutz (nutz@informatik.uni-freiburg.de)
 *
 * @param <LETTER>
 * @param <COLNAMES>
 */
public class DawgLetterFactory<LETTER, COLNAMES> {
	
	
	private final Map<Object, UniversalDawgLetter<LETTER, COLNAMES>> mSortToUniversalDawgLetter =
		new HashMap<Object, UniversalDawgLetter<LETTER,COLNAMES>>();
	private final Map<Object, EmptyDawgLetter<LETTER, COLNAMES>> mSortToEmptyDawgLetter =
		new HashMap<Object, EmptyDawgLetter<LETTER,COLNAMES>>();

	private final NestedMap2<Object, Set<LETTER>, SimpleDawgLetter<LETTER, COLNAMES>> mSortToLettersToSimpleDawgLetter
		 = new NestedMap2<Object, Set<LETTER>, SimpleDawgLetter<LETTER,COLNAMES>>();
	private final NestedMap2<Object, Set<LETTER>, SimpleComplementDawgLetter<LETTER, COLNAMES>> mSortToLettersToSimpleComplementDawgLetter
		 = new NestedMap2<Object, Set<LETTER>, SimpleComplementDawgLetter<LETTER,COLNAMES>>();
	
	private final NestedMap4<Object, 
			Set<LETTER>, 
			Set<COLNAMES>, 
			Set<COLNAMES>, 
			DawgLetterWithEqualities<LETTER, COLNAMES>> mDawgLettersWithEqualitiesStore = 
				new NestedMap4<Object, Set<LETTER>, Set<COLNAMES>, Set<COLNAMES>, DawgLetterWithEqualities<LETTER,COLNAMES>>();
	private final NestedMap4<Object, 
			Set<LETTER>, 
			Set<COLNAMES>, 
			Set<COLNAMES>, 
			ComplementDawgLetterWithEqualities<LETTER, COLNAMES>> mComplementDawgLettersWithEqualitiesStore = 
				new NestedMap4<Object, Set<LETTER>, Set<COLNAMES>, Set<COLNAMES>, ComplementDawgLetterWithEqualities<LETTER,COLNAMES>>();
	private final NestedMap3<Object, 
			Set<COLNAMES>, 
			Set<COLNAMES>, 
			UniversalDawgLetterWithEqualities<LETTER, COLNAMES>> mUniversalDawgLettersWithEqualitiesStore = 
				new NestedMap3<Object, Set<COLNAMES>, Set<COLNAMES>, UniversalDawgLetterWithEqualities<LETTER,COLNAMES>>();

	private final DawgFactory<LETTER, COLNAMES> mDawgFactory;
	

	public DawgLetterFactory(DawgFactory<LETTER, COLNAMES> dawgFactory) {
		mDawgFactory = dawgFactory;
	}

	public IDawgLetter<LETTER, COLNAMES> getSingletonSetDawgLetter(LETTER element, Object sortId) {
		if (useSimpleDawgLetters()) {
			return getSimpleDawgLetter(Collections.singleton(element), sortId);
		} else {
			Set<COLNAMES> es = Collections.emptySet();
			return getDawgLetterWithEqualities(Collections.singleton(element), es, es, sortId);
		}
	}
	
	public UniversalDawgLetter<LETTER, COLNAMES> getUniversalDawgLetter(Object sortId) {
		UniversalDawgLetter<LETTER, COLNAMES> result = mSortToUniversalDawgLetter.get(sortId);
		if (result == null) {
			result = new UniversalDawgLetter<LETTER, COLNAMES>(this, sortId);
			mSortToUniversalDawgLetter.put(sortId, result);
		}
		return result;
	}
	
	public EmptyDawgLetter<LETTER, COLNAMES> getEmptyDawgLetter(Object sortId) {
		EmptyDawgLetter<LETTER, COLNAMES> result = mSortToEmptyDawgLetter.get(sortId);
		if (result == null) {
			result = new EmptyDawgLetter<LETTER, COLNAMES>(this, sortId);
			mSortToEmptyDawgLetter.put(sortId, result);
		}
		return result;
	}

	public AbstractDawgLetter<LETTER, COLNAMES> getComplementDawgLetterWithEqualities(Set<LETTER> complementLetters, Set<COLNAMES> equalColnames,
			Set<COLNAMES> inequalColnames, Object sortId) {
		if (complementLetters.isEmpty()) {
			return getUniversalDawgLetterWithEqualities(equalColnames, inequalColnames, sortId);
		}
		
		ComplementDawgLetterWithEqualities<LETTER, COLNAMES> result = 
				mComplementDawgLettersWithEqualitiesStore.get(sortId, complementLetters, equalColnames, inequalColnames);
		if (result == null) {
			result = new ComplementDawgLetterWithEqualities<LETTER, COLNAMES>(complementLetters, 
					equalColnames, inequalColnames, this, sortId);
			mComplementDawgLettersWithEqualitiesStore.put(sortId, complementLetters, equalColnames, inequalColnames, result);
		}
		return result;
	}

	public UniversalDawgLetterWithEqualities<LETTER, COLNAMES> getUniversalDawgLetterWithEqualities(
			Set<COLNAMES> equalColnames, Set<COLNAMES> unequalColnames, Object sortId) {
		
		UniversalDawgLetterWithEqualities<LETTER, COLNAMES> result = 
				mUniversalDawgLettersWithEqualitiesStore.get(sortId, equalColnames, unequalColnames);
		if (result == null) {
			result = new UniversalDawgLetterWithEqualities<LETTER, COLNAMES>(this, equalColnames, unequalColnames, sortId);
			mUniversalDawgLettersWithEqualitiesStore.put(sortId, equalColnames, unequalColnames, result);
		}
		return result;
	}

	public AbstractDawgLetter<LETTER, COLNAMES> getDawgLetterWithEqualities(Set<LETTER> newLetters, Set<COLNAMES> equalColnames,
			Set<COLNAMES> inequalColnames, Object sortId) {

		if (newLetters.isEmpty()) {
			// if the set of LETTERs is empty, the (in)equalities don't matter
			return getEmptyDawgLetter(sortId);
		}
	
		DawgLetterWithEqualities<LETTER, COLNAMES> result = mDawgLettersWithEqualitiesStore.get(sortId, newLetters, equalColnames, inequalColnames);
		if (result == null) {
			result = new DawgLetterWithEqualities<LETTER, COLNAMES>(newLetters, equalColnames, inequalColnames, this, sortId);
			mDawgLettersWithEqualitiesStore.put(sortId, newLetters, equalColnames, inequalColnames, result);
		}
		
		return result;
	}
	
	public Set<LETTER> getAllConstants(Object sortId) {
		Set<LETTER> result = mDawgFactory.getAllConstants(sortId);
		assert result != null;
		return result;
	}

	/**
	 * Takes a set of DawgLetters and returns a set of DawgLetters that represents the complement
	 * of the LETTERs represented by the input set.
	 * 
	 * Conceptually a set of DawgLetters is a kind of DNF (a DawgLetter is a cube with one set-constraint
	 * and some equality and inequality constraints).
	 * This method has to negate the DNF and bring the result into DNF again.
	 * 
	 * @param outgoingDawgLetters
	 * @return
	 */
	public Set<IDawgLetter<LETTER, COLNAMES>> complementDawgLetterSet(
			Set<IDawgLetter<LETTER, COLNAMES>> dawgLetters) {
		assert EprHelpers.dawgLettersHaveSameSort(dawgLetters);
		assert !dawgLetters.isEmpty() : "do we need this case??";

		final Object sortId = ((AbstractDawgLetter<LETTER, COLNAMES>) dawgLetters.iterator().next()).getSortId();

		Set<IDawgLetter<LETTER, COLNAMES>> result = new HashSet<IDawgLetter<LETTER,COLNAMES>>();
		result.add(getUniversalDawgLetter(sortId));

		for (IDawgLetter<LETTER, COLNAMES> currentDawgLetter: dawgLetters) {

			final Set<IDawgLetter<LETTER, COLNAMES>> newResult = new HashSet<IDawgLetter<LETTER,COLNAMES>>();

			/*
			 * for every dawgLetter d in the intermediate result and every constraint c in the current dawgLetter,
			 * 	add a new DawgLetter to the newResult
			 *   the new DawgLetter is the conjunction of the constraints in d with _not_ c.
			 */
			for (IDawgLetter<LETTER, COLNAMES> dawgLetterInIntermediateResult : result) {

				for (IDawgLetter<LETTER, COLNAMES> singleNegatedConstraintDlFromCurrentDl : currentDawgLetter.complement()) {
					newResult.add(dawgLetterInIntermediateResult.intersect(singleNegatedConstraintDlFromCurrentDl));
				}
			}
			result = newResult;
		}

		return result;
	}

	/**
	 * 
	 * @param letters an implicitly disjunctive set of DawgLetters
	 * @return
	 */
	public boolean isUniversal(Set<IDawgLetter<LETTER, COLNAMES>> letters) {
		if (letters.size() == 0) {
			return false;
		} else if (letters.size() == 1 && letters.iterator().next() instanceof EmptyDawgLetter<?, ?>) {
			return false;
		} else if (letters.size() == 1 && letters.iterator().next() instanceof UniversalDawgLetter<?, ?>) {
			return true;
		} else if (letters.size() >= 1 && 
				(letters.iterator().next() instanceof SimpleDawgLetter<?, ?>
			|| letters.iterator().next() instanceof SimpleComplementDawgLetter<?, ?>)) {
			/**
			 * the DawgLetters are universal (as a disjunction) if and only if the SimpleDawgLetters
			 * and the SimpleComplementDawgLetters complement each other perfectly.
			 */
			Set<LETTER> unionNormal = new HashSet<LETTER>();
			Set<LETTER> unionComplement = new HashSet<LETTER>();
			for (IDawgLetter<LETTER, COLNAMES> outLetter : letters) {
				if (outLetter instanceof SimpleDawgLetter<?, ?>) {
					unionNormal.addAll(((SimpleDawgLetter<LETTER, COLNAMES>) outLetter).getLetters());
				} else if (outLetter instanceof SimpleComplementDawgLetter<?, ?>) {
					unionComplement.addAll(
							((SimpleComplementDawgLetter<LETTER, COLNAMES>) outLetter).getComplementLetters());
				} else if (outLetter instanceof UniversalDawgLetter) {
					assert false : "a universal dawg letter and another one?";
				} else if (outLetter instanceof EmptyDawgLetter<?, ?>) {
					// do nothing
				} else {
					assert false : "unexpected mixing of DawgLetter types";
				}
			}
			return unionNormal.equals(unionComplement);
		} else {
			final Set<IDawgLetter<LETTER, COLNAMES>> complementSet = complementDawgLetterSet(letters);
			return  complementSet.isEmpty();
		}
//		assert false : "TODO";
//		return false;
	}

	public IDawgLetter<LETTER, COLNAMES> getSimpleDawgLetter(Set<LETTER> letters, Object sortId) {
		if (letters.isEmpty()) {
			 return getEmptyDawgLetter(sortId);
		}
		
		IDawgLetter<LETTER, COLNAMES> result = mSortToLettersToSimpleDawgLetter.get(sortId, letters);
		if (result == null) {
			result = new SimpleDawgLetter<LETTER, COLNAMES>(this, letters, sortId);
			mSortToLettersToSimpleDawgLetter.put(sortId, letters, (SimpleDawgLetter<LETTER, COLNAMES>) result);
		}
		return result;
	}
	
	public IDawgLetter<LETTER, COLNAMES> getSimpleComplementDawgLetter(Set<LETTER> letters, Object sortId) {
		if (letters.isEmpty()) {
			 return getUniversalDawgLetter(sortId);
		}
		
		IDawgLetter<LETTER, COLNAMES> result = mSortToLettersToSimpleComplementDawgLetter.get(sortId, letters);
		if (result == null) {
			result = new SimpleComplementDawgLetter<LETTER, COLNAMES>(this, letters, sortId);
			mSortToLettersToSimpleComplementDawgLetter.put(sortId, letters, (SimpleComplementDawgLetter<LETTER, COLNAMES>) result);
		}
		return result;
	}

	public boolean useSimpleDawgLetters() {
		return EprTheorySettings.UseSimpleDawgLetters;
	}
}