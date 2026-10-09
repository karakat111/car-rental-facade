package com.carrental.facade;

/**
 * Persisted progress of the A->B->C chain for a single rental request.
 */
public enum ChainStatus {
    PENDING_A,          // chain row written, A not confirmed yet
    PENDING_B,          // A committed
    PENDING_C,          // A and B committed
    COMPLETED,          // A, B and C committed
    COMPENSATED_A,      // only A was undone (B failed)
    COMPENSATED_B_A,    // B then A were undone (C failed)
    REJECTED_A          // A itself refused the request - nothing was committed
}
