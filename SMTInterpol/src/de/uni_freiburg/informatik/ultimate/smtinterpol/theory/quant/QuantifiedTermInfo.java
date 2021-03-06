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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.logic.ApplicationTerm;
import de.uni_freiburg.informatik.ultimate.logic.FunctionSymbol;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.smtinterpol.convert.SMTAffineTerm;

/**
 * Info class for quantified terms. This class contains helper methods to classify quantified terms.
 * 
 * @author Tanja Schindler
 */
public class QuantifiedTermInfo {

	private QuantifiedTermInfo() {
		// Not meant to be instantiated
	}

	/**
	 * Check if a given term is essentially uninterpreted, i.e., it is ground or variables only appear as arguments of
	 * uninterpreted functions.
	 * 
	 * TODO Nonrecursive.
	 * 
	 * @param term
	 *            The term to check.
	 * @return true if the term is essentially uninterpreted, false otherwise.
	 */
	public static boolean isEssentiallyUninterpreted(final Term term) {
		if (term.getFreeVars().length == 0) {
			return true;
		} else if (term instanceof ApplicationTerm) {
			final ApplicationTerm appTerm = (ApplicationTerm) term;
			final FunctionSymbol func = appTerm.getFunction();
			if (!func.isInterpreted()) {
				for (Term arg : appTerm.getParameters()) {
					if (!(arg instanceof TermVariable)) {
						if (!isEssentiallyUninterpreted(arg)) {
							return false;
						}
					}
				}
				return true;
			} else if (func.getName() == "select") {
				final Term[] params = appTerm.getParameters();
				if (params[0] instanceof TermVariable || !isEssentiallyUninterpreted(params[0])) {
					return false; // Quantified arrays are not allowed.
				}
				if (!(params[1] instanceof TermVariable) && !isEssentiallyUninterpreted(params[1])) {
					return false;
				}
				return true;
			} else if (func.getName() == "+" || func.getName() == "-" || func.getName() == "*") {
				final SMTAffineTerm affineTerm = SMTAffineTerm.create(term);
				for (Term summand : affineTerm.getSummands().keySet()) {
					if (!isEssentiallyUninterpreted(summand)) {
						return false;
					}
				}
				return true;
			} else {
				return false;
			}
		} else {
			return false;
		}
	}

	public static boolean containsArithmeticOnlyAtTopLevel(final QuantLiteral atom) {
		assert !atom.isNegated();
		if (atom instanceof QuantBoundConstraint) {
			return containsArithmeticOnlyAtTopLevel(((QuantBoundConstraint) atom).getAffineTerm());
		} else {
			final QuantEquality eq = (QuantEquality) atom;
			final SMTAffineTerm lhsAff = new SMTAffineTerm(eq.getLhs());
			if (containsArithmeticOnlyAtTopLevel(lhsAff)) {
				final SMTAffineTerm rhsAff = new SMTAffineTerm(eq.getRhs());
				return containsArithmeticOnlyAtTopLevel(rhsAff);
			}
		}
		return false;
	}

	public static boolean containsAppTermsForEachVar(final QuantLiteral atom) {
		assert !atom.isNegated();
		final Set<Term> allSummandsWithoutCoeffs = new HashSet<>();
		if (atom instanceof QuantEquality) {
			final QuantEquality eq = (QuantEquality) atom;
			final Term lhs = eq.getLhs();
			final Term rhs = eq.getRhs();
			final SMTAffineTerm lhsAff = new SMTAffineTerm(lhs);
			final SMTAffineTerm rhsAff = new SMTAffineTerm(rhs);
			allSummandsWithoutCoeffs.addAll(lhsAff.getSummands().keySet());
			allSummandsWithoutCoeffs.addAll(rhsAff.getSummands().keySet());
		} else {
			final QuantBoundConstraint bc = (QuantBoundConstraint) atom;
			allSummandsWithoutCoeffs.addAll(bc.getAffineTerm().getSummands().keySet());
		}
		final Set<Term> varTerms = new HashSet<>();
		final Set<TermVariable> varsInApps = new HashSet<>();
		for (final Term smd : allSummandsWithoutCoeffs) {
			if (smd instanceof TermVariable) {
				varTerms.add(smd);
			} else if (smd.getFreeVars().length != 0) {
				varsInApps.addAll(Arrays.asList(smd.getFreeVars()));
			}
		}
		varTerms.removeAll(varsInApps);
		return varTerms.isEmpty();
	}

	public static boolean containsArithmeticOnlyAtTopLevel(final SMTAffineTerm at) {
		for (final Term smd : at.getSummands().keySet()) {
			if (!(smd instanceof TermVariable) && !isNonVarUFTerm(smd)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Check if a term is a "simple" EU term, i.e., it is ground, or an application of an uninterpreted function where
	 * all arguments are variables or simple EU terms. (Exception: select behaves as an uninterpreted function but may
	 * not have a variable as first argument.)
	 * 
	 * TODO Nonrecursive.
	 * 
	 * @param term
	 *            the term to check.
	 * @return true, if the term is a "simple" EU term, false otherwise.
	 */
	public static boolean isNonVarUFTerm(final Term term) {
		if (term.getFreeVars().length != 0) {
			if (term instanceof TermVariable) {
				return false;
			} else {
				assert term instanceof ApplicationTerm;
				final ApplicationTerm at = (ApplicationTerm) term;
				final FunctionSymbol func = at.getFunction();
				if (func.isInterpreted() && !func.getName().equals("select")) {
					return false;
				}
				final Term[] args = at.getParameters();
				if (func.getName().equals("select")) {
					if (!isNonVarUFTerm(args[0])) {
						return false; // Quantified arrays are not allowed.
					}
					if (!(args[1] instanceof TermVariable) && !isNonVarUFTerm(args[1])) {
						return false;
					}
				} else {
					for (final Term arg : args) {
						if (!(arg instanceof TermVariable) && !isNonVarUFTerm(arg)) {
							return false;
						}
					}
				}
			}
		}
		return true;
	}

	/**
	 * Check if a term is an application term with an internal {@literal @}AUX function used by the clausifier.
	 * @param term
	 * @return
	 */
	public static boolean isAuxApplication(final Term term) {
		if (term instanceof ApplicationTerm) {
			FunctionSymbol fsym = ((ApplicationTerm) term).getFunction();
			return fsym.isIntern() && fsym.getName().startsWith("@AUX");
		}
		return false;
	}

}
