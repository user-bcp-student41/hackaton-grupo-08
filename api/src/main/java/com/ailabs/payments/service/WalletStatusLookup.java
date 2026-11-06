package com.ailabs.payments.service;

import java.util.Optional;

/** B7: consulta si la wallet del pagador está {@code ACTIVE} (dato originado en HU-01). */
public interface WalletStatusLookup {

    /** @return el {@code walletId} si la wallet existe y está ACTIVE; vacío en cualquier otro caso. */
    Optional<String> activeWalletId(String userId);
}
