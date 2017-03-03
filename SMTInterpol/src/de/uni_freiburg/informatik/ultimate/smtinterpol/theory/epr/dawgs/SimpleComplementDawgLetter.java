package de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.dawgs;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SimpleComplementDawgLetter<LETTER, COLNAMES> implements IDawgLetter<LETTER, COLNAMES> {
	
	final DawgLetterFactory<LETTER, COLNAMES> mDawgLetterFactory;

	
	/**
	 * the letters that are not matched by this DawgLetter
	 */
	final Set<LETTER> mComplementSet;
	

	public SimpleComplementDawgLetter(DawgLetterFactory<LETTER, COLNAMES> dawgLetterFactory,
			Set<LETTER> complementSet) {
		mDawgLetterFactory = dawgLetterFactory;
		mComplementSet = complementSet;
	}

	@Override
	public Set<IDawgLetter<LETTER, COLNAMES>> complement() {
		return Collections.singleton(mDawgLetterFactory.getSimpleDawgLetter(mComplementSet));
	}

	@Override
	public Set<IDawgLetter<LETTER, COLNAMES>> difference(IDawgLetter<LETTER, COLNAMES> other) {
		final Set<IDawgLetter<LETTER, COLNAMES>> otherComplement = other.complement();
		assert otherComplement.size() == 1 : "should be the case for simpleDawgLetters, right?";
		return Collections.singleton(this.intersect(otherComplement.iterator().next()));
	}

	@Override
	public IDawgLetter<LETTER, COLNAMES> intersect(IDawgLetter<LETTER, COLNAMES> other) {
		if (other instanceof UniversalDawgLetter<?, ?>) {
			return this;
		} else if (other instanceof EmptyDawgLetter<?, ?>) {
			return other;
		} else if (other instanceof SimpleDawgLetter<?, ?>) {
			/*
			 * return a letter that accepts all letters that are in other's (positive) set,
			 * and not in this's (complement) set
			 */
			final Set<LETTER> othersLetters = ((SimpleDawgLetter<LETTER, COLNAMES>) other).getLetters();
			final Set<LETTER> newSet = new HashSet<LETTER>(othersLetters);
			newSet.removeAll(mComplementSet);
			return mDawgLetterFactory.getSimpleDawgLetter(newSet);
		} else if (other instanceof SimpleComplementDawgLetter<?, ?>) {
			/*
			 * return a DawgLetter that accepts all letters that are neither in this's
			 * set nor in the other's set
			 */
			final Set<LETTER> newComplement = new HashSet<LETTER>(mComplementSet);
			newComplement.addAll(((SimpleComplementDawgLetter<LETTER, COLNAMES>) other).getComplementLetters());
			return mDawgLetterFactory.getSimpleComplementDawgLetter(newComplement);
		} else {
			assert false : "not expected";
			return null;
		}
	}

	@Override
	public boolean matches(LETTER ltr, List<LETTER> word, Map<COLNAMES, Integer> colnamesToIndex) {
		return !mComplementSet.contains(ltr);
	}

	@Override
	public Collection<LETTER> allLettersThatMatch(List<LETTER> word, Map<COLNAMES, Integer> colnamesToIndex) {
		Set<LETTER> result = new HashSet<LETTER>(mDawgLetterFactory.getAllConstants());
		result.removeAll(mComplementSet);
		return result;
	}

	@Override
	public IDawgLetter<LETTER, COLNAMES> restrictToLetter(LETTER selectLetter) {
		if (mComplementSet.contains(selectLetter)) {
			return mDawgLetterFactory.getEmptyDawgLetter();
		} else {
			return mDawgLetterFactory.getSingletonSetDawgLetter(selectLetter);
		}
	}

	public Set<LETTER> getComplementLetters() {
		return mComplementSet;
	}
}