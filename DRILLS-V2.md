# Drills v2 — 6 test merah baru

Cabang `drills-v2`, di atas `main` @ `05d033e` (13/13 hijau). Tiga belas test lama **tidak
disentuh**; kalau ada yang berubah di folder `src/test/java/.../{ledger,recon,transfer}` selain
file `Drill1x*`, itu bukan dari cabang ini.

Alasannya ada di review 14 Nov: 13 drill membuktikan perilaku mesin, tapi semuanya masih lolos
dengan asumsi yang tidak berlaku begitu sistem jalan di lebih dari satu JVM, dengan database
sungguhan, dan dengan partner yang berbohong soal urutan kejadian. Enam drill ini menembak tepat
ke enam celah itu.

## Aturan main (sama seperti v1)

1. **Test tidak boleh diubah.** Kalau test kelihatan salah, itu bahan diskusi — bukan alasan edit.
2. Satu drill = satu commit. Pesan: `drill 16: attempt counter`.
3. Jalanin satu per satu: `./mvnw test -Dtest='Drill16*'`. Target akhir: **25/25 hijau**.
4. Kalau macet > 45 menit, tulis apa yang dicoba di commit/notes, lanjut drill lain.

## Enam drill

| # | Test | Yang dilatih | Jebakan hari ini | Petunjuk |
|---|---|---|---|---|
| 14 | `Drill14TwoNodesIdempotencyTest` | idempotency **lintas instance** | `synchronized` cuma mengunci satu heap; dua pod, satu baris | jaminan pindah ke store: satu baris per `idempotency_key`; yang kalah balapan baca baris pemenang, bukan melempar error ke caller |
| 15 | `Drill15ConcurrentPostTest` | atomicity jurnal | `hasTransaction` lalu `append` = check-then-act; dua instance → dua jurnal | append-if-absent, atau unique index `(transaction_ref, account_id, direction)` — cek dan tulis harus satu langkah |
| 16 | `Drill16AttemptCounterTest` | lost update | cabang refund menyimpan salinan lama, `attempts` mundur ke 0 | optimistic locking (`version`), bukan "ingat bawa salinan yang baru" |
| 17 | `Drill17PaidIsTerminalTest` | terminal state | guard cuma memblokir `PAID → ACCEPTED`; `PAID → FAILED` lolos | state machine eksplisit; PAID final; jangan lempar error ke webhook (partner bakal retry selamanya) |
| 18 | `Drill18RefundedIsTerminalTest` | terminal state + uang sudah kembali | `ACCEPTED`/`PAID` setelah `REFUNDED` menghidupkan transfer lagi | REFUNDED final; event kontradiktif = insiden untuk recon, bukan status baru |
| 19 | `Drill19CancelTest` | refund dengan tenggat | `cancel()` masih `UnsupportedOperationException` | legal hanya di `PENDING_PARTNER` (state yang ditinggalkan timeout), idempotent, `CANCELLED` final, id ngawur = `IllegalArgumentException` |

Urutan yang disarankan: **16 → 17 → 18 → 19 → 14 → 15**. Dua yang terakhir paling bikin stuck,
dan 14/15 baru masuk akal setelah 16–19 selesai (karena duplikasi bikin semua ledger kacau).

## Soal `RaceGate`

`src/test/java/com/rogasper/lintas/support/RaceGate.java` itu scaffolding, bukan production code.
Dua thread bertemu di situ dan masing-masing menunggu yang lain — **maksimal 2 detik**. Kenapa
pakai timeout: kalau lo "memperbaiki" balapan dengan lock, thread pertama berhenti menunggu dan
jalan terus, jadi build selesai (lambat) alih-alih menggantung. Kalau gate-nya menggantung, itu
bug di test, bukan di kode lo — laporin.

`GatedTransferRepository` dan `GatedLedgerRepository` sengaja `extends` store in-memory, bukan
`implements` interface-nya: kalau lo menambah method ke repository, test tetap compile.

## Kenapa drill 19 `cancel()`, bukan "recon dari mutasi"

Di review, temuan #6 bilang `ReconService` membandingkan **niat** (`Transfer.targetAmount`) dengan
statement, bukan mutasi ledger. Itu benar, tapi tidak bisa diperbaiki di model ini tanpa menambah
sesuatu yang lebih besar:

- statement partner dalam **IDR** (target), jurnal kita dalam **USD** (source) — tidak ada akun
  posisi FX, jadi tidak ada angka yang bisa dibandingkan langsung;
- callback `PAID` juga belum memposting settlement ke akun partner (temuan #5).

Jadi recon yang benar butuh dua hal dulu: akun **settlement partner per currency** dan posting di
callback `PAID`. Kalau itu ada, recon jadi: saldo akun settlement per `partnerRef` vs statement.
Itu pekerjaan tersendiri (dan lebih besar dari satu drill). Sampai itu ada, temuan #6 dicatat
sebagai batas model di `REVIEW-v1.md`, dan slot drill 19 dipakai untuk lubang API publik yang
paling kelihatan: `cancel()`.

## Setelah 25/25

- Ganti store in-memory → JDBC + `UNIQUE (idempotency_key)` dan `UNIQUE (transaction_ref, account_id, direction)`
  beneran, plus transaksi DB. Drill 14/15 jadi tidak perlu lock sama sekali.
- Tambah akun settlement partner + posting di callback `PAID`, lalu recon berbasis mutasi.
- `Clock` sudah di-inject di semua service — `LedgerService.post` masih pakai `Instant.now()`,
  ganti ke `clock.instant()` supaya test berbasis waktu deterministik.
