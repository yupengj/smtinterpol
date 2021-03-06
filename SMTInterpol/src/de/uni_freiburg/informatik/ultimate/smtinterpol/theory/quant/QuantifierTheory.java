/*
 * Copyright (C) 2018 University of Freiburg
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
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.logic.FunctionSymbol;
import de.uni_freiburg.informatik.ultimate.logic.Rational;
import de.uni_freiburg.informatik.ultimate.logic.Sort;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.logic.Theory;
import de.uni_freiburg.informatik.ultimate.smtinterpol.Config;
import de.uni_freiburg.informatik.ultimate.smtinterpol.LogProxy;
import de.uni_freiburg.informatik.ultimate.smtinterpol.convert.Clausifier;
import de.uni_freiburg.informatik.ultimate.smtinterpol.convert.SMTAffineTerm;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.Clause;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.DPLLAtom;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.DPLLEngine;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.ILiteral;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.ITheory;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.Literal;
import de.uni_freiburg.informatik.ultimate.smtinterpol.proof.SourceAnnotation;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.cclosure.CCTerm;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.cclosure.CClosure;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.util.Pair;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.linar.LinArSolve;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.quant.ematching.EMatching;
import de.uni_freiburg.informatik.ultimate.util.datastructures.ScopedArrayList;

/**
 * Solver for quantified formulas within the almost uninterpreted fragment (Restrictions on terms and literals are
 * explained in the corresponding classes. For reference, see Ge & de Moura, 2009).
 *
 * This may be merged with the EPR solver implementation by Alexander Nutz in the future; for now, we keep it separate.
 *
 * @author Tanja Schindler
 */
public class QuantifierTheory implements ITheory {

	private final Clausifier mClausifier;
	private final LogProxy mLogger;
	private final Theory mTheory;
	private final DPLLEngine mEngine;

	final CClosure mCClosure;
	final LinArSolve mLinArSolve;

	private final EMatching mEMatching;
	private final InstantiationManager mInstantiationManager;
	private final Map<Sort, Term> mLambdas;

	/**
	 * Clauses that only the QuantifierTheory knows, i.e. that contain at least one literal with an (implicitly)
	 * universally quantified variable.
	 */
	private final ScopedArrayList<QuantClause> mQuantClauses;

	/**
	 * Literals (not atoms!) mapped to potential conflict and unit clauses that they are contained in. At creation, the
	 * clauses would have been conflicts or unit clauses if the corresponding theories had already known the contained
	 * literals. In the next checkpoint, false literals should have been propagated by the other theories, but we might
	 * still have one undefined literal (and is a unit clause).
	 */
	private final Map<Literal, Set<InstClause>> mPotentialConflictAndUnitClauses;
	private int mDecideLevelOfLastCheckpoint;

	// Statistics
	long mNumInstancesProduced, mNumInstancesDER, mNumInstancesProducedCP, mNumInstancesProducedFC;
	private long mNumCheckpoints, mNumCheckpointsWithNewEval, mNumConflicts, mNumProps, mNumFinalcheck;
	private long mCheckpointTime, mFindEmatchingTime, mFinalCheckTime, mEMatchingTime, mDawgTime;

	// Options
	boolean mUseEMatching;
	boolean mUseUnknownTermValueInDawgs;
	boolean mPropagateNewAux;
	boolean mPropagateNewTerms;

	public QuantifierTheory(final Theory th, final DPLLEngine engine, final Clausifier clausifier,
			final boolean useEMatching, final boolean useUnknownTermDawgs, final boolean propagateNewTerms,
			final boolean propagateNewAux) {
		mClausifier = clausifier;
		mLogger = clausifier.getLogger();
		mTheory = th;
		mEngine = engine;

		mUseEMatching = useEMatching;
		mUseUnknownTermValueInDawgs = useUnknownTermDawgs;
		mPropagateNewTerms = propagateNewTerms;
		mPropagateNewAux = propagateNewAux;

		mCClosure = clausifier.getCClosure();
		mLinArSolve = clausifier.getLASolver();

		mEMatching = new EMatching(this);
		mInstantiationManager = new InstantiationManager(mClausifier, this);
		mLambdas = new HashMap<>();

		mQuantClauses = new ScopedArrayList<>();

		mPotentialConflictAndUnitClauses = new LinkedHashMap<>();
		mDecideLevelOfLastCheckpoint = mEngine.getDecideLevel();
	}

	@Override
	public Clause startCheck() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void endCheck() {
		// TODO Auto-generated method stub

	}

	@Override
	public Clause setLiteral(final Literal literal) {
		// Remove clauses that have become true from potential conflict and unit clauses.
		if (mPotentialConflictAndUnitClauses.containsKey(literal)) {
			for (final InstClause instClause : mPotentialConflictAndUnitClauses.remove(literal)) {
				assert instClause.mLits.contains(literal);
				for (final Literal otherLit : instClause.mLits) {
					if (otherLit != literal) {
						final Set<InstClause> clauses = mPotentialConflictAndUnitClauses.get(otherLit);
						if (clauses != null) {
							clauses.remove(instClause);
							if (clauses.isEmpty()) {
								mPotentialConflictAndUnitClauses.remove(otherLit);
							}
						}
					}
				}
			}
		}
		// Remove former undef negated lit (now false) from map and decrease number of undef lits in clauses containing
		// the negated lit.
		if (mPotentialConflictAndUnitClauses.containsKey(literal.negate())) {
			for (final InstClause instClause : mPotentialConflictAndUnitClauses.remove(literal.negate())) {
				assert instClause.mNumUndefLits > 0;
				instClause.mNumUndefLits -= 1;
				if (instClause.isConflict()) {
					assert !Config.EXPENSIVE_ASSERTS || instClause.countAndSetUndefLits() == 0;
					mLogger.debug("Quant conflict: %s", instClause);
					mNumConflicts++;
					return instClause.toClause(mEngine.isProofGenerationEnabled());
				}
			}
		}
		return null;
	}

	@Override
	public void backtrackLiteral(final Literal literal) {
		// we throw the potential conflict and unit clauses away after backtracking.
	}

	@Override
	public Clause checkpoint() {
		mNumCheckpoints++;
		long time;
		if (Config.PROFILE_TIME) {
			time = System.nanoTime();
		}
		// Don't search for new conflict and unit clauses if there are still potential conflict and unit clauses in the
		// queue.
		if (mLinArSolve == null) {
			assert mPotentialConflictAndUnitClauses.isEmpty()
					|| mEngine.getDecideLevel() <= mDecideLevelOfLastCheckpoint;
		}
		mDecideLevelOfLastCheckpoint = mEngine.getDecideLevel();
		if (!mPotentialConflictAndUnitClauses.isEmpty()) {
			return null;
		}

		mNumCheckpointsWithNewEval++;
		final Collection<InstClause> conflictAndUnitInstances;
		if (mUseEMatching) {
			mEMatching.run();
			conflictAndUnitInstances = mInstantiationManager.findConflictAndUnitInstancesWithEMatching();
			if (Config.PROFILE_TIME) {
				mFindEmatchingTime += System.nanoTime() - time;
			}
		} else { // TODO for comparison
			for (final QuantClause clause : mQuantClauses) {
				if (mEngine.isTerminationRequested()) {
					return null;
				}
				clause.updateInterestingTermsAllVars();
			}
			conflictAndUnitInstances = mInstantiationManager.findConflictAndUnitInstances();
		}
		final Clause conflict = addPotentialConflictAndUnitClauses(conflictAndUnitInstances);
		if (conflict != null) {
			mLogger.debug("Quant conflict: %s", conflict);
			mEngine.learnClause(conflict);
			mNumConflicts++;
		}
		if (Config.PROFILE_TIME) {
			mCheckpointTime += System.nanoTime() - time;
		}
		return conflict;
	}

	@Override
	public Clause computeConflictClause() {
		long time;
		if (Config.PROFILE_TIME) {
			time = System.nanoTime();
		}
		mNumFinalcheck++;
		assert mPotentialConflictAndUnitClauses.isEmpty();
		for (final QuantClause clause : mQuantClauses) {
			if (mEngine.isTerminationRequested()) {
				return null;
			}
			clause.updateInterestingTermsAllVars();
		}
		final Clause conflict = mInstantiationManager.instantiateSomeNotSat();
		if (conflict != null) {
			mNumConflicts++;
			mEngine.learnClause(conflict);
		}
		if (Config.PROFILE_TIME) {
			mFinalCheckTime += System.nanoTime() - time;
		}
		return conflict;
	}

	@Override
	public Literal getPropagatedLiteral() {
		for (final Map.Entry<Literal, Set<InstClause>> entry : mPotentialConflictAndUnitClauses.entrySet()) {
			if (mEngine.isTerminationRequested()) {
				return null;
			}
			final Literal lit = entry.getKey();
			for (final InstClause inst : entry.getValue()) {
				if (inst.isUnit()) {
					assert !Config.EXPENSIVE_ASSERTS || inst.countAndSetUndefLits() == 1;
					final Clause expl = inst.toClause(mEngine.isProofGenerationEnabled());
					lit.getAtom().mExplanation = expl;
					mEngine.learnClause(expl);
					mNumProps++;
					mLogger.debug("Quant Prop: %s Reason: %s", lit, lit.getAtom().mExplanation);
					return lit;
				} else {
					mLogger.debug("Not propagated: %s Clause: %s", lit, inst.mLits);
				}
			}
		}
		return null;
	}

	@Override
	public Clause getUnitClause(final Literal literal) {
		assert false : "Should never be called.";
		return null;
	}

	@Override
	public Literal getSuggestion() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void printStatistics(final LogProxy logger) {
		logger.info("Quant: DER produced %d ground clause(s).", mNumInstancesDER);
		logger.info("Quant: Instances produced: %d (Checkpoint: %d, Final check: %d)", mNumInstancesProduced,
				mNumInstancesProducedCP, mNumInstancesProducedFC);
		logger.info("Quant: Conflicts: %d Props: %d Checkpoints (with new evaluation): %d (%d) Final Checks: %d",
				mNumConflicts, mNumProps, mNumCheckpoints, mNumCheckpointsWithNewEval, mNumFinalcheck);
		logger.info(
				"Quant times: Checkpoint: %.3f Find with E-matching: %.3f E-Matching: %.3f Dawg: %.3f Final Check: %.3f",
				mCheckpointTime / 1000 / 1000.0, mFindEmatchingTime / 1000 / 1000.0, mEMatchingTime / 1000 / 1000.0,
				mDawgTime / 1000 / 1000.0, mFinalCheckTime / 1000 / 1000.0);
	}

	@Override
	public void dumpModel(final LogProxy logger) {
		// TODO Auto-generated method stub

	}

	@Override
	public void increasedDecideLevel(final int currentDecideLevel) {
		// TODO Auto-generated method stub

	}

	@Override
	public void decreasedDecideLevel(final int currentDecideLevel) {
		// TODO Auto-generated method stub

	}

	@Override
	public Clause backtrackComplete() {
		final int decisionLevel = mClausifier.getEngine().getDecideLevel();
		mEMatching.undo(decisionLevel);
		mInstantiationManager.resetInterestingTerms();
		mPotentialConflictAndUnitClauses.clear();
		return null;
	}

	@Override
	public void restart(final int iteration) {
		// TODO Auto-generated method stub

	}

	@Override
	public void removeAtom(final DPLLAtom atom) {
		// TODO Auto-generated method stub

	}

	@Override
	public void push() {
		mQuantClauses.beginScope();
	}

	@Override
	public void pop() {
		mQuantClauses.endScope();
	}

	@Override
	public Object[] getStatistics() {
		return new Object[] { ":Quant",
				new Object[][] { { "DER ground results", mNumInstancesDER },
						{ "Instances produced", mNumInstancesProduced },
						{ "thereof in checkpoint", mNumInstancesProducedCP },
						{ "and in final check", mNumInstancesProducedFC }, { "Conflicts", mNumConflicts },
						{ "Propagations", mNumProps }, { "Checkpoints", mNumCheckpoints },
						{ "Checkpoints with new evaluation", mNumCheckpointsWithNewEval },
						{ "Final Checks", mNumFinalcheck },
						{ "Times",
								new Object[][] { { "Checkpoint", mCheckpointTime },
										{ "Find E-matching", mFindEmatchingTime }, { "E-Matching", mEMatchingTime },
										{ "Final Check", mFinalCheckTime } } } } };

	}

	/**
	 * This method builds new QuantEqualities and simultaneously checks if they lie in the almost uninterpreted
	 * fragment, i.e., if they are of the form (i) (euEUTerm = euTerm), pos. and neg. or (ii) (var = ground), integer
	 * only, or if they can be used for DER, i.e. (var != term[withoutthisvar])
	 * <p>
	 * This method also brings equality atoms in the form (var = term), if there exists a TermVariable at top level. For
	 * integers, only if the variable has factor ±1; for reals always.
	 */
	public QuantLiteral getQuantEquality(final boolean positive, final SourceAnnotation source,
			final Term lhs, final Term rhs) {
		// Bring atom to form (var = term) if there exists a variable at "top level".
		Term newLhs = lhs;
		Term newRhs = rhs;
		if (!lhs.getSort().isNumericSort()) {
			final TermVariable leftVar = lhs instanceof TermVariable ? (TermVariable) lhs : null;
			final TermVariable rightVar = rhs instanceof TermVariable ? (TermVariable) rhs : null;
			if (leftVar == null && rightVar != null) {
				newLhs = rightVar;
				newRhs = lhs;
			}
		} else {
			final SMTAffineTerm linAdded = SMTAffineTerm.create(lhs);
			linAdded.add(Rational.MONE, SMTAffineTerm.create(rhs));
			Rational fac = Rational.ONE;
			for (final Term smd : linAdded.getSummands().keySet()) {
				if (smd instanceof TermVariable) {
					fac = linAdded.getSummands().get(smd);
					if (smd.getSort().getName() == "Real") {
						newLhs = smd;
						linAdded.add(fac.negate(), smd);
						linAdded.mul(fac.negate());
						newRhs = linAdded.toTerm(lhs.getSort());
						break;
					} else {
						if (fac.abs() == Rational.ONE) {
							// Isolate first found variable (if exists).
							newLhs = smd;
							linAdded.add(fac.negate(), smd);
							if (fac == Rational.ONE) {
								linAdded.negate();
							}
							newRhs = linAdded.toTerm(lhs.getSort());
							break;
						}
					}
				}
			}
		}
		final Term newTerm = mTheory.term("=", newLhs, newRhs);
		final QuantLiteral atom = new QuantEquality(newTerm, newLhs, newRhs);

		// Check if the atom is almost uninterpreted or can be used for DER.
		if (!(newLhs instanceof TermVariable)) { // (euEUTerm = euTerm) is essentially and almost uninterpreted
			if (QuantifiedTermInfo.isEssentiallyUninterpreted(newLhs)
					&& QuantifiedTermInfo.isEssentiallyUninterpreted(newRhs)) {
				atom.mIsEssentiallyUninterpreted = atom.negate().mIsEssentiallyUninterpreted = true;
			}
		} else if (!(newRhs instanceof TermVariable)) {
			// (x = t) for t integer is arithmetical and almost uninterpreted
			if (newRhs.getFreeVars().length == 0 && newRhs.getSort().getName() == "Int") {
				atom.mIsArithmetical = true;
			}
			// (x != termwithoutx) can be used for DER
			if (!Arrays.asList(newRhs.getFreeVars()).contains(newLhs)) {
				atom.negate().mIsDERUsable = true;
			}
		} else { // (var = var) is not almost uninterpreted, but the negated form can be used for DER
			atom.negate().mIsDERUsable = true;
		}
		return atom;
	}

	/**
	 * This method builds new QuantInequalities and simultaneously checks if they lie in the almost uninterpreted
	 * fragment, i.e., if they are of the form (i) (eu <= 0), pos. and neg., (ii) (var < var), (iii) (var < ground), or
	 * (iv) (ground < var).
	 * <p>
	 * For integers x, positive (x-t<=0) are rewritten into ~(t+1<=x), or more precisely, ~(-x+t+1<=0). For reals x, we
	 * normalize atoms (kx-t<= 0) to get (±x-t<=0).
	 * <p>
	 * TODO Offsets? (See paper)
	 */
	public QuantLiteral getQuantInequality(final boolean positive, final SourceAnnotation source, final Term lhs) {

		boolean rewrite = false; // Set to true when rewriting positive (x-t<=0) into ~(t+1<=x) for x integer
		final SMTAffineTerm linTerm = SMTAffineTerm.create(lhs);
		TermVariable var = null;
		Rational fac = Rational.ONE;
		boolean hasUpperBound = false;
		for (final Term smd : linTerm.getSummands().keySet()) {
			if (smd instanceof TermVariable) {
				fac = linTerm.getSummands().get(smd);
				if (smd.getSort().getName() == "Real") { // In the real case, we normalize the term for this var.
					var = (TermVariable) smd;
					if (linTerm.getSummands().get(smd).isNegative()) {
						hasUpperBound = true;
					} else {
						hasUpperBound = false;
					}
					break;
				} else {
					if (fac == Rational.MONE) {
						var = (TermVariable) smd;
						hasUpperBound = true;
						break;
					} else if (fac == Rational.ONE) {
						var = (TermVariable) smd;
						hasUpperBound = false;
						break;
					}
				}
			}
		}
		if (positive && var != null && lhs.getSort().getName() == "Int") {
			// Rewrite positive (x-t<=0) into ~(t+1<=x), or more precisely, ~(-x+t+1<=0) for x integer.
			// Similarly (t-x<=0) into ~(x-t+1<=0)
			rewrite = true;
			linTerm.negate();
			linTerm.add(Rational.ONE);
			hasUpperBound = !hasUpperBound;
		} else if (var != null && lhs.getSort().getName() == "Real") {
			// var should have coefficient 1 or -1.
			linTerm.div(fac.abs());
		}

		final Term newTerm = mTheory.term("<=", linTerm.toTerm(lhs.getSort()), Rational.ZERO.toTerm(lhs.getSort()));
		final QuantLiteral atom = new QuantBoundConstraint(newTerm, linTerm);

		// Check if the atom is almost uninterpreted.
		if (var == null) { // (euTerm <= 0), pos. and neg., is essentially and almost uninterpreted.
			boolean hasOnlyEU = true;
			for (final Term smd : linTerm.getSummands().keySet()) {
				hasOnlyEU = hasOnlyEU && QuantifiedTermInfo.isEssentiallyUninterpreted(smd);
			}
			if (hasOnlyEU) {
				atom.mIsEssentiallyUninterpreted = atom.negate().mIsEssentiallyUninterpreted = true;
			}
		} else { // (var < var), (var < ground), or (ground < var) are arithmetical and almost uninterpreted
			final SMTAffineTerm remainderAff = new SMTAffineTerm();
			remainderAff.add(linTerm);
			remainderAff.add(hasUpperBound ? Rational.ONE : Rational.MONE, var);
			if (!hasUpperBound) {
				remainderAff.negate();
			}
			final Term remainder = remainderAff.toTerm(lhs.getSort());
			if (remainder instanceof TermVariable || remainder.getFreeVars().length == 0) {
				atom.negate().mIsArithmetical = true;
			}
		}
		return rewrite ? atom.negate() : atom;
	}

	/**
	 * Get copies for quantified literals that are uniquely assigned to a clause.
	 *
	 * @param qLits
	 *            the quantified literals.
	 * @param qClause
	 *            the quantified clause these literals occur in.
	 * @return copies of the quantified literals that know their clause.
	 */
	public QuantLiteral[] getLiteralCopies(final QuantLiteral[] lits, final QuantClause clause) {
		final QuantLiteral[] clauseLiterals = new QuantLiteral[lits.length];
		for (int i = 0; i < lits.length; i++) {
			final QuantLiteral atom = lits[i].getAtom();
			final QuantLiteral clauseAtom;
			if (atom instanceof QuantBoundConstraint) {
				clauseAtom = new QuantBoundConstraint(atom.getTerm(), ((QuantBoundConstraint) atom).getAffineTerm());
			} else {
				clauseAtom = new QuantEquality(atom.getTerm(), ((QuantEquality) atom).getLhs(),
						((QuantEquality) atom).getRhs());
			}
			clauseAtom.mClause = clause;
			clauseAtom.mIsEssentiallyUninterpreted = atom.mIsEssentiallyUninterpreted;
			clauseAtom.mIsArithmetical = atom.mIsArithmetical;
			clauseAtom.mIsDERUsable = atom.mIsDERUsable;
			clauseAtom.mNegated.mClause = clause;
			clauseAtom.mNegated.mIsEssentiallyUninterpreted = atom.mNegated.mIsEssentiallyUninterpreted;
			clauseAtom.mNegated.mIsArithmetical = atom.mNegated.mIsArithmetical;
			clauseAtom.mNegated.mIsDERUsable = atom.mNegated.mIsDERUsable;

			clauseLiterals[i] = lits[i].isNegated() ? clauseAtom.negate() : clauseAtom;
		}
		return clauseLiterals;
	}

	/**
	 * Perform destructive equality reasoning.
	 *
	 * @param groundLits
	 *            The ground literals of the clause.
	 * @param quantLits
	 *            The quantified literals of the clause.
	 * @param source
	 *            The source of the clause.
	 * @return an array of ILiteral containing all literals after DER; null if the clause became true.
	 */
	public Pair<ILiteral[], Map<TermVariable, Term>> performDestructiveEqualityReasoning(final Literal[] groundLits,
			final QuantLiteral[] quantLits,
			final SourceAnnotation source) {
		final DestructiveEqualityReasoning der =
				new DestructiveEqualityReasoning(this, groundLits, quantLits, source);
		final ArrayList<ILiteral> litsAfterDER = new ArrayList<>(groundLits.length + quantLits.length);
		if (der.applyDestructiveEqualityReasoning()) {
			if (der.isTriviallyTrue()) {
				return null; // Don't add trivially true clauses.
			}
			final Literal[] groundLitsAfterDER = der.getGroundLitsAfterDER();
			final QuantLiteral[] quantLitsAfterDER = der.getQuantLitsAfterDER();
			if (quantLitsAfterDER.length == 0) {
				mLogger.debug("Quant: DER returned ground clause.");
				mNumInstancesDER++;
			}
			litsAfterDER.addAll(Arrays.asList(groundLitsAfterDER));
			litsAfterDER.addAll(Arrays.asList(quantLitsAfterDER));
		} else {
			litsAfterDER.addAll(Arrays.asList(groundLits));
			litsAfterDER.addAll(Arrays.asList(quantLits));
		}
		return new Pair<>(litsAfterDER.toArray(new ILiteral[litsAfterDER.size()]),
				der.getSigma());
	}

	/**
	 * Add a QuantClause for a given set of literals and quantified literals.
	 *
	 * Call this only after performing DER.
	 *
	 * @param iLits
	 *            ground and quantified literals of the clause to add.
	 * @param source
	 *            the source of the clause
	 */
	public void addQuantClause(final ILiteral[] iLits, final SourceAnnotation source) {
		boolean isQuant = false;
		for (final ILiteral lit : iLits) {
			if (lit instanceof QuantLiteral) {
				isQuant = true;
			}
		}
		if (!isQuant) {
			throw new IllegalArgumentException("Cannot add clause to QuantifierTheory: No quantified literal!");
		}

		final ArrayList<Literal> groundLits = new ArrayList<>(iLits.length);
		final ArrayList<QuantLiteral> quantLits = new ArrayList<>(iLits.length);
		for (final ILiteral lit : iLits) {
			if (lit instanceof Literal) {
				groundLits.add((Literal) lit);
			} else {
				final QuantLiteral qLit = (QuantLiteral) lit;
				if (!qLit.isAlmostUninterpreted()) {
					mLogger.warn("Quant: Clause contains literal that is not almost uninterpreted: " + qLit);
				} else if (qLit.isNegated() && qLit.mIsDERUsable) {
					mLogger.warn("Quant: Clause contains disequality on variable not eliminated by DER: " + qLit);
				}
				quantLits.add((QuantLiteral) lit);
			}
		}

		final QuantClause clause = new QuantClause(groundLits.toArray(new Literal[groundLits.size()]),
				quantLits.toArray(new QuantLiteral[quantLits.size()]), this, source);
		mQuantClauses.add(clause);

		mEMatching.addPatterns(clause);
		mInstantiationManager.addClause(clause);

		if (mLogger.isDebugEnabled()) {
			mLogger.debug("Quant: Added clause: " + clause);
		}
	}

	public void addEMatchingTime(final long time) {
		mEMatchingTime += time;
	}

	public void addDawgTime(final long time) {
		mDawgTime += time;
	}

	public Clausifier getClausifier() {
		return mClausifier;
	}

	public CClosure getCClosure() {
		return mCClosure;
	}

	public EMatching getEMatching() {
		return mEMatching;
	}

	public DPLLEngine getEngine() {
		return mEngine;
	}

	public LinArSolve getLinAr() {
		return mLinArSolve;
	}

	public InstantiationManager getInstantiator() {
		return mInstantiationManager;
	}

	public LogProxy getLogger() {
		return mLogger;
	}

	public Collection<QuantClause> getQuantClauses() {
		return mQuantClauses;
	}

	public Theory getTheory() {
		return mTheory;
	}

	protected Term getLambda(final Sort sort) {
		if (mLambdas.containsKey(sort)) {
			return mLambdas.get(sort);
		}
		Term lambda;
		if (sort.getName().equals("Bool")) {
			lambda = mTheory.mTrue;
		} else {
			final FunctionSymbol fsym = mTheory.getFunctionWithResult("@0", null, sort, new Sort[0]);
			lambda = mTheory.term(fsym);
		}
		mLambdas.put(sort, lambda);
		return lambda;
	}

	/**
	 * Check if there exists a not yet satisfied clause that contains a literal outside of the almost uninterpreted
	 * fragment. If so, returns INCOMPLETE to inform the DPLL engine of incompleteness.
	 *
	 * @return DPLLEngine.COMPLETE, if a model exists, DPLLEngine.INCOMPLETE_* if unsure.
	 */
	@Override
	public int checkCompleteness() {
		for (final QuantClause qClause : mQuantClauses) {
			if (!qClause.hasTrueGroundLits()) {
				for (final QuantLiteral qLit : qClause.getQuantLits()) {
					if (!qLit.isAlmostUninterpreted()) {
						return DPLLEngine.INCOMPLETE_QUANTIFIER;
					}
				}
				for (final Term lambda : mLambdas.values()) {
					if (!lambda.getSort().isNumericSort()) {
						final CCTerm lambdaCC = mClausifier.getCCTerm(lambda);
						if (lambdaCC != null && lambdaCC.getNumMembers() > 1) {
							return DPLLEngine.INCOMPLETE_QUANTIFIER;
						}
					}
				}
			}
		}
		return DPLLEngine.COMPLETE;
	}

	/**
	 * Add potential conflict and unit clauses to the map from undefined literals to clauses. We stop as soon as we find
	 * an actual conflict.
	 *
	 * @param instances
	 *            a set of potential conflict and unit clauses
	 * @return a conflict
	 */
	private Clause addPotentialConflictAndUnitClauses(final Collection<InstClause> instances) {
		if (instances == null) {
			return null;
		}
		for (final InstClause inst : instances) {
			if (mEngine.isTerminationRequested()) {
				return null;
			}
			final int numUndefLits = inst.countAndSetUndefLits();
			if (numUndefLits == -1) { // Instance is true.
				continue;
			}
			if (numUndefLits == 0) {
				return inst.toClause(mEngine.isProofGenerationEnabled());
			}
			for (final Literal lit : inst.mLits) {
				if (lit.getAtom().getDecideStatus() == null) {
					if (!mPotentialConflictAndUnitClauses.containsKey(lit)) {
						mPotentialConflictAndUnitClauses.put(lit, new LinkedHashSet<>());
					}
					mPotentialConflictAndUnitClauses.get(lit).add(inst);
				}
			}
		}
		return null;
	}

	Term getRepresentativeTerm(final Term term) {
		final CCTerm ccTerm = getClausifier().getCCTerm(term);
		return ccTerm == null ? term : ccTerm.getRepresentative().getFlatTerm();
	}

	public enum InstanceOrigin {
		DER(":DER"), CHECKPOINT(":Checkpoint"), FINALCHECK(":Finalcheck");
		String mOrigin;

		private InstanceOrigin(final String origin) {
			mOrigin = origin;
		}

		/**
		 * Get the name of the instance origin. This can be used in an annotation for the lemma.
		 *
		 * @return the annotation key for the instantiation lemma.
		 */
		public String getOrigin() {
			return mOrigin;
		}
	}
}
