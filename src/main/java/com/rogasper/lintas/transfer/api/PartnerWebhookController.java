package com.rogasper.lintas.transfer.api;

import com.rogasper.lintas.partner.PartnerCallback;
import com.rogasper.lintas.transfer.Transfer;
import com.rogasper.lintas.transfer.TransferCallbackService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Partner callback endpoint. In production this also needs signature verification and a replay
 * window — the partner will retry, and so will an attacker.
 */
@RestController
@RequestMapping("/webhooks")
public class PartnerWebhookController {

    private final TransferCallbackService callbacks;

    public PartnerWebhookController(TransferCallbackService callbacks) {
        this.callbacks = callbacks;
    }

    @PostMapping("/partner")
    public Transfer receive(@RequestBody PartnerCallback callback) {
        return callbacks.handle(callback);
    }
}
