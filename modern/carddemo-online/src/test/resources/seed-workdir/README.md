Fixed-width files exported by `legacy-runtime/batch.py --workdir <dir> --jobs POSTTRAN` (business clock
2022-07-06). Integration tests load them through the same `SeedLoader` the API uses, so test data equals the
data the legacy CICS region serves. Regenerate with `scripts/refresh-seed-fixture.sh`.
