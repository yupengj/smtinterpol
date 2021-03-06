(set-option :produce-proofs true)
(set-option :proof-check-mode true)
(set-option :model-check-mode true)
(set-option :print-terms-cse false)

(set-logic QF_UF)
(declare-fun p () Bool)
(declare-fun q () Bool)
(declare-fun r () Bool)

(push 1)
(assert (or false false false false))
(check-sat)
(get-proof)
(pop 1)

(push 1)
(assert (not (= (or false p false false) p)))
(check-sat)
(get-proof)
(pop 1)

(push 1)
(assert (not (= (or p q) (or false p false q))))
(check-sat)
(get-proof)
(pop 1)

(push 1)
(assert (not (= (or p q) (or p q p p q q p q))))
(check-sat)
(get-proof)
(pop 1)

(push 1)
(assert (not (= (or q p) (or p q p p q q p q))))
(check-sat)
(get-proof)
(pop 1)
