# Lintas

Cross-border remittance practice engine. Small on purpose: one ledger, one quote/transfer flow, one
fake payout partner — and a pile of tests about the parts that actually break in a remittance
system.

> Ini repo latihan, bukan portfolio. Tujuannya: bikin tangan kotor di idempotency, callback
> duplikat/out-of-order, timeout, refund, dan rekonsiliasi — sebelum mulai kerja 1 Desember.

## Stack

| Item | Versi |
|---|---|
| Java | 21 (Temurin) |
| Spring Boot | 4.1.2 |
| Spring Modulith | 2.1.1 (struktur modul, belum dipakai untuk verifikasi otomatis) |
| Build | Maven (pakai `./mvnw`, nggak perlu install Maven) |
| Storage | in-memory repository — sengaja, biar fokus ke perilaku, bukan ke JPA |

## Jalanin

```bash
./mvnw test                       # 13 test merah: itu titik mulai lo
./mvnw spring-boot:run            # API di http://localhost:8080
```

Test pertama yang harus hijau: `LedgerServiceTest` drill 1-4. Jangan sentuh file test-nya.

## Peta modul

```
com.rogasper.lintas
├── shared      Money (minor units), Currency, TimeConfig (Clock bean)
├── ledger      Account, Posting, Entry, LedgerService  ← double-entry, append-only
├── transfer    Quote, Transfer (state machine), TransferService, TransferCallbackService
├── partner     PartnerClient + FakePartnerClient (timeout/reject/duplikat bisa di-script)
└── recon       ReconService: bandingin transfer kita vs statement partner
```

Arah dependensi: `shared ← ledger ← transfer → partner`, `recon → transfer`. Nggak ada siklus —
kalau lo bikin siklus, itu tandanya logic-nya salah tempat.

Yang **sudah** jalan: `Money`/`Currency`, semua repository in-memory, `createQuote` (pricing +
rounding HALF_UP), controller HTTP, `FakePartnerClient`.

Yang **kosong dan harus lo isi** (semua `throw new UnsupportedOperationException("TODO drill ...")`):
`LedgerService.post`, `LedgerService.balanceOf`, `TransferService.execute`, `TransferService.cancel`,
`TransferCallbackService.handle`, `ReconService.reconcile`.

## 13 drill

| # | Test | Yang dilatih | Petunjuk |
|---|---|---|---|
| 1 | `drill01_balancedPostingUpdatesBalances` | double-entry basic | balance = credit − debit, dihitung dari entry |
| 2 | `drill02_unbalancedPostingIsRefused` | journal harus balance | validasi **sebelum** nulis apa pun |
| 3 | `drill03_mixedCurrencyPostingIsRefused` | no implicit FX | cek currency tiap posting ke account-nya |
| 4 | `drill04_duplicateTransactionReferenceIsIdempotent` | idempotency di ledger | `hasTransaction` → balikin entry lama |
| 5 | `drill05_expiredQuoteIsRefused` | quote expiry | bandingin `clock.instant()` vs `quote.expiresAt()` |
| 6 | `drill06_idempotencyKeyIsNotChargedTwice` | request retry | cek `findByIdempotencyKey` dulu, baru bikin |
| 7 | `drill07_duplicateCallbackIsIgnored` | webhook at-least-once | dedupe via `lastPartnerSequence` |
| 8 | `drill08_outOfOrderCallbackKeepsFinalState` | out-of-order event | status nggak boleh mundur |
| 9 | `drill09_partnerTimeoutResumesWithoutDoubleDebit` | timeout ≠ gagal | simpan transfer `PENDING_PARTNER`, resume pakai key yang sama |
| 10 | `drill10_permanentFailureIsRefunded` | compensating entry | refund = posting balik, bukan hapus entry |
| 11 | `drill11_concurrentExecuteCreatesOneTransfer` | race condition | 4 thread, 1 key → 1 transfer, 1 debit |
| 12 | `drill12_reconFlagsEveryDifference` | reconciliation | bandingin per `partnerRef`, amount pakai target currency |
| 13 | `drill13_reconIsCleanWhenBothSidesAgree` | no false positive | jangan laporin selisih yang nggak ada |

Urutan yang gue saranin: 1–4 (ledger), 5–6, 9–10, 7–8, 11, 12–13. Drill 11 paling gampang bikin
lo stuck, jadi taruh belakangan.

## Aturan main

1. **Test nggak boleh diubah.** Kalau test kelihatan salah, itu justru bahan diskusi.
2. Satu drill = satu commit. Commit message: `drill 6: idempotency key`.
3. Kalau macet > 45 menit, tulis di commit/notes apa yang lo coba, lanjut drill lain.
4. Nggak perlu bikin cantik. Yang dinilai (sama diri sendiri) adalah perilaku di bawah kondisi gagal.

## Setelah 13 drill hijau (opsional, urut prioritas)

- Ganti repository in-memory → JDBC + `UNIQUE (idempotency_key)` beneran, plus transaksi DB.
- Tambah `ApplicationModules.of(...).verify()` biar batas modul dijaga otomatis.
- Verifikasi signature webhook + replay window.
- Rate lock: quote yang expired tapi sudah dibayar — siapa yang nanggung selisihnya?
- Retry dengan exponential backoff + dead-letter untuk callback yang berkali-kali gagal.

## API

```bash
# 1. bikin quote
curl -s localhost:8080/api/quotes -H 'Content-Type: application/json' -d '{
  "customerRef":"CUST-1","sourceAmount":100.00,"sourceCurrency":"USD",
  "targetCurrency":"IDR","rate":15800.00,"fee":1.50,"ttlSeconds":300}'

# 2. eksekusi (Idempotency-Key wajib)
curl -s localhost:8080/api/transfers -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-1' -d '{"quoteId":"Q-xxxxxxxx"}'

# 3. partner callback (boleh dikirim dua kali — hasilnya harus sama)
curl -s localhost:8080/webhooks/partner -H 'Content-Type: application/json' -d '{
  "partnerRef":"P-1","transferId":"TRF-...","status":"PAID","sequence":1,
  "occurredAt":"2026-11-04T09:05:00Z"}'
```
