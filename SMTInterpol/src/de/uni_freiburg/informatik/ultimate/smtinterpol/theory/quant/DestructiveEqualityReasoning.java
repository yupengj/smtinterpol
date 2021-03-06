/*
 * Copyright (C) 2019 University of Freiburg
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
package de.uni_freiburg.informatik.ultimate.smtinterpol.theory.quant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.logic.FormulaUnLet;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.smtinterpol.convert.Clausifier;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.Literal;
import de.uni_freiburg.informatik.ultimate.smtinterpol.proof.IProofTracker;
import de.uni_freiburg.informatik.ultimate.smtinterpol.proof.SourceAnnotation;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.quant.QuantLiteral.NegQuantLiteral;

/**
 * Apply destructive equality reasoning to a quantified clause.
 * <p>
 * This is, if a quantified clause contains a literal (x != t), every occurrence of x is substituted by t, and the
 * literal is dropped.
 *
 * @author Tanja Schindler
 *
 */
class DestructiveEqualityReasoning {

	private final QuantifierTheory mQuantTheory;
	private final Clausifier mClausifier;

	private final Literal[] mGroundLits;
	private final QuantLiteral[] mQuantLits;
	private final SourceAnnotation mSource;

	private final Map<TermVariable, Term> mSigma;
	private boolean mIsChanged;
	private boolean mIsTriviallyTrue;
	private Literal[] mGroundLitsAfterDER;
	private QuantLiteral[] mQuantLitsAfterDER;

	DestructiveEqualityReasoning(final QuantifierTheory quantTheory, final Literal[] groundLits, final QuantLiteral[] quantLits,
			final SourceAnnotation source) {
		mQuantTheory = quantTheory;
		mClausifier = quantTheory.getClausifier();

		mGroundLits = groundLits;
		mQuantLits = quantLits;
		mSource = source;

		mSigma = new LinkedHashMap<>();
		mIsChanged = false;
		mIsTriviallyTrue = false;
	}

	/**
	 * Apply destructive equality reasoning.
	 * <p>
	 * If something has changed, the result can be obtained by calling getGroundLitsAfterDER() and
	 * getQuantLitsAfterDER(), respectively.
	 *
	 * @return true if DER changed something, i.e., a variable has been removed; false otherwise.
	 */
	boolean applyDestructiveEqualityReasoning() {
		collectSubstitution();
		if (!mSigma.isEmpty()) {
			applySubstitution();
			mIsChanged = true;
		}
		return mIsChanged;
	}

	/**
	 * Check if the clause is trivially true.
	 *
	 * @return true, if the clause is trivially true; false otherwise.
	 */
	public boolean isTriviallyTrue() {
		return mIsTriviallyTrue;
	}

	/**
	 * Get the ground literals after destructive equality reasoning was performed.
	 *
	 * @return an array containing the ground literals after DER. Can have length 0.
	 */
	Literal[] getGroundLitsAfterDER() {
		assert !mIsTriviallyTrue : "Should never be called on trivially true clauses!";
		if (!mIsChanged) {
			return mGroundLits;
		}
		return mGroundLitsAfterDER;
	}

	/**
	 * Get the quantified literals after destructive equality reasoning was performed.
	 *
	 * @return an array containing the quantified literals after DER. Can have length 0.
	 */
	QuantLiteral[] getQuantLitsAfterDER() {
		assert !mIsTriviallyTrue : "Should never be called on trivially true clauses!";
		if (!mIsChanged) {
			return mQuantLits;
		}
		return mQuantLitsAfterDER;
	}

	Map<TermVariable, Term> getSigma() {
		return mSigma;
	}

	/**
	 * Collect the substitution sigma. Step 1: Go through all literals. For variables x,y,z, and a term t (can be a
	 * variable):<br>
	 * (i) For a literal (x != t), t ground or var, add {z -> t} to sigma if sigma*(x) = z.<br>
	 * (ii) For a literal (x != y), add {z -> x} to sigma if sigma*(y) = z.<br>
	 * First check (i) and only if it does not apply, check (ii).<br>
	 * (iii) For a literal (x != f(y1,...,yn)), add f(y1,...,yn) to potential substitutions for x if sigma*(x) = z.
	 * <p>
	 * Step 2:<br>
	 * (i) Build sigma := sigma*.<br>
	 * (ii) Find a substitution for all variables without ground substitution, but don't build cycles.
	 */
	private void collectSubstitution() {
		final Map<TermVariable, Term> groundAndVarSubsForVar = new LinkedHashMap<>();
		final Map<TermVariable, List<Term>> potentialSubsForVar = new LinkedHashMap<>();
		// Step 1:
		for (final QuantLiteral qLit : mQuantLits) {
			if (qLit.mIsDERUsable) {
				assert qLit instanceof NegQuantLiteral && qLit.getAtom() instanceof QuantEquality;
				final QuantEquality varEq = (QuantEquality) qLit.mAtom;
				assert varEq.getLhs() instanceof TermVariable;
				final TermVariable var = (TermVariable) varEq.getLhs();
				final Term varRep = findRep(var);
				final Term rhs = varEq.getRhs();
				if (varRep instanceof TermVariable) {
					if (rhs.getFreeVars().length == 0 || rhs instanceof TermVariable) { // (i)
						groundAndVarSubsForVar.put((TermVariable) varRep, rhs);
					} else { // (iii)
						if (!potentialSubsForVar.containsKey(varRep)) {
							potentialSubsForVar.put(var, new ArrayList<Term>());
						}
						potentialSubsForVar.get(var).add(rhs);
					}
				} else {
					if (rhs instanceof TermVariable) {
						final Term otherVarRep = findRep((TermVariable) rhs);
						if (otherVarRep instanceof TermVariable) { // (ii)
							groundAndVarSubsForVar.put((TermVariable) otherVarRep, varRep);
						}
					}
				}
			}
		}

		// Step 2 (i):
		if (!groundAndVarSubsForVar.isEmpty()) {
			final Set<TermVariable> visited = new HashSet<>();
			for (final TermVariable var : groundAndVarSubsForVar.keySet()) {
				if (!visited.contains(var)) {
					final Set<TermVariable> varsWithSameSubs = new LinkedHashSet<>();
					Term subs = var;
					while (subs instanceof TermVariable && !visited.contains(subs)) {
						visited.add((TermVariable) subs);
						varsWithSameSubs.add((TermVariable) subs);
						if (groundAndVarSubsForVar.containsKey(subs)) {
							subs = groundAndVarSubsForVar.get(subs);
						}
					}
					for (final TermVariable equiVar : varsWithSameSubs) {
						if (equiVar != subs) { // Don't use a substitution x->x.
							mSigma.put(equiVar, subs);
							if (!(subs instanceof TermVariable)) {
								potentialSubsForVar.remove(equiVar);
							}
						}
					}
				}
			}
		}
		// Step 2 (ii):
		if (!potentialSubsForVar.isEmpty()) {
			for (final TermVariable var : potentialSubsForVar.keySet()) {
				final Term varRep = findRep(var);
				if (varRep instanceof TermVariable) {
					for (final Term potentialSubs : potentialSubsForVar.get(var)) {
						if (!hasCycle(var, potentialSubs)) {
							final FormulaUnLet unletter = new FormulaUnLet();
							unletter.addSubstitutions(mSigma);
							Term subs = unletter.unlet(potentialSubs);
							final IProofTracker tracker = mClausifier.getTracker();
							subs = tracker.getProvedTerm(mClausifier.getTermCompiler().transform(subs));
							mSigma.put((TermVariable) varRep, subs);
						}
					}
				}
			}
		}
	}

	/**
	 * For a variable x, find sigma*(x).
	 *
	 * We define sigma(x) = x if x has no substitution so far.
	 *
	 * @return The Term sigma*(x).
	 */
	private Term findRep(final TermVariable var) {
		TermVariable nextVarRep = var;
		while (true) {
			if (mSigma.containsKey(nextVarRep)) {
				final Term subs = mSigma.get(nextVarRep);
				if (subs instanceof TermVariable) {
					nextVarRep = (TermVariable) subs;
				} else {
					return subs;
				}
			} else {
				return nextVarRep;
			}
		}
	}

	/**
	 * For a variable x and a potential substitution containing variables check if there is a cycle in the substitution
	 * sigma.
	 */
	private boolean hasCycle(final TermVariable var, final Term potentialSubs) {
		assert potentialSubs.getFreeVars().length > 0;
		for (final TermVariable dependentVar : potentialSubs.getFreeVars()) {
			if (Arrays.asList(findRep(dependentVar).getFreeVars()).contains(var)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Apply the substitution sigma collected from the disequalities in this clause, in order to get rid of some
	 * variables.
	 */
	private void applySubstitution() {
		final SubstitutionHelper subsHelper =
				new SubstitutionHelper(mQuantTheory, mGroundLits, mQuantLits, mSource, mSigma);
		subsHelper.substituteInClause();
		mGroundLitsAfterDER = subsHelper.getResultingGroundLits();
		mQuantLitsAfterDER = subsHelper.getResultingQuantLits();

		if (subsHelper.getResultingClauseTerm() == mQuantTheory.getTheory().mTrue) {
			mIsTriviallyTrue = true;
		}
	}
}
