package com.rogasper.lintas.partner;

/** Port to the payout partner. Implementation is a fake in this repo; a real one is HTTP + retries. */
public interface PartnerClient {

    PartnerResult submit(PartnerRequest request);
}
